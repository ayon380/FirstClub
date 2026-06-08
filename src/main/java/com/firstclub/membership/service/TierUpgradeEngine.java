package com.firstclub.membership.service;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.MembershipTier;
import com.firstclub.membership.domain.entity.Subscription;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.registry.TierRuleEvaluatorRegistry;
import com.firstclub.membership.repository.MembershipTierRepository;
import com.firstclub.membership.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TierUpgradeEngine {
    private final MembershipTierRepository tierRepository;
    private final OrderRepository orderRepository;
    private final TierRuleEvaluatorRegistry evaluatorRegistry;

    /**
     * Called after every order placement.
     * Finds the highest tier whose rules all pass and upgrades if it's better than current.
     * Returns the new tier (may be same as current if no upgrade).
     */
    public MembershipTier evaluate(User user, Subscription subscription) {
        return evaluate(user, subscription, false);
    }

    public MembershipTier evaluate(User user, Subscription subscription, boolean allowDowngrades) {
        // Build evaluation context (single DB query, reused for all evaluators)
        LocalDateTime windowStart = LocalDateTime.now().minusDays(30);
        long orderCount = orderRepository.findByUserAndOrderDateAfter(user, windowStart).size();
        BigDecimal totalSpend = orderRepository.sumFinalTotalByUserAndDateAfter(user, windowStart);
        System.out.println("DEBUG EVALUATE: userId=" + user.getId() + " count=" + orderCount + " spend=" + totalSpend + " currentTier=" + subscription.getTier().getName() + " allowDowngrades=" + allowDowngrades);

        EvaluationContext ctx = EvaluationContext.builder()
            .orderCountLast30Days(orderCount)
            .totalSpendLast30Days(totalSpend)
            .build();

        // Get all tiers sorted by priority descending (try highest first)
        List<MembershipTier> allTiers = tierRepository.findAllByOrderByPriorityDesc();

        MembershipTier currentTier = subscription.getTier();
        MembershipTier defaultTier = subscription.getPlan().getDefaultTier();

        for (MembershipTier candidate : allTiers) {
            // If downgrades are NOT allowed, we only look at tiers with higher priority
            if (!allowDowngrades && candidate.getPriority() <= currentTier.getPriority()) {
                continue;
            }

            // A subscription tier can never drop below the plan's default tier
            if (candidate.getPriority() < defaultTier.getPriority()) {
                continue;
            }

            // If it is the default tier, they automatically qualify
            if (candidate.getId().equals(defaultTier.getId())) {
                return candidate;
            }

            if (candidate.getUpgradeRules() == null || candidate.getUpgradeRules().isEmpty()) {
                continue;
            }

            boolean qualifies = evaluatorRegistry.allRulesPass(user, candidate.getUpgradeRules(), ctx);
            if (qualifies) {
                return candidate;
            }
        }

        return currentTier; // no change
    }}
