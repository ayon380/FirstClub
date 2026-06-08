package com.firstclub.membership.service;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.BillingPeriod;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.ChangeTierRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse.HistoryEntry;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse.ProgressToNextTier;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse.RuleProgress;
import com.firstclub.membership.exception.InvalidTierTransitionException;
import com.firstclub.membership.exception.SubscriptionNotFoundException;
import com.firstclub.membership.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final UserRepository userRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipTierRepository tierRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final OrderRepository orderRepository;
    private final TierUpgradeEngine upgradeEngine;
    private final UserLockManager userLockManager;
    private final java.util.concurrent.atomic.AtomicInteger optimisticLockCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    @Autowired
    @Lazy
    private MembershipService self;

    // --- Subscribe ---
    @Transactional
    public SubscriptionStatusResponse subscribe(SubscribeRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Subscription> existingSub = subscriptionRepository.findByUserId(request.getUserId());
        if (existingSub.isPresent() && existingSub.get().getStatus() == SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("User already has an active subscription");
        }

        MembershipPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        MembershipTier defaultTier = plan.getDefaultTier();

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = calculateEndDate(startDate, plan.getBillingPeriod());

        Subscription subscription;
        if (existingSub.isPresent()) {
            subscription = existingSub.get();
            subscription.setPlan(plan);
            subscription.setTier(defaultTier);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStartDate(startDate);
            subscription.setEndDate(endDate);
        } else {
            subscription = Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .tier(defaultTier)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }

        subscription = subscriptionRepository.save(subscription);

        // Record history
        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(subscription)
                .fromTier(null)
                .toTier(defaultTier)
                .fromStatus(null)
                .toStatus(SubscriptionStatus.ACTIVE)
                .changeReason("SUBSCRIBE")
                .changedAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        return buildStatusResponse(subscription);
    }

    // --- Change Tier (Upgrade/Downgrade Manual) ---
    // Lock-outside-Transaction pattern
    public SubscriptionStatusResponse changeTier(Long userId, ChangeTierRequest request) {
        return userLockManager.executeWithLock(userId, () -> self.changeTierTransactional(userId, request));
    }

    @Transactional
    public SubscriptionStatusResponse changeTierTransactional(Long userId, ChangeTierRequest request) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));

        MembershipTier targetTier = tierRepository.findById(request.getTierId())
                .orElseThrow(() -> new IllegalArgumentException("Target tier not found"));

        MembershipTier currentTier = subscription.getTier();
        if (currentTier.getId().equals(targetTier.getId())) {
            throw new InvalidTierTransitionException("Already on tier: " + targetTier.getName());
        }

        String direction = targetTier.getPriority() > currentTier.getPriority() ? "MANUAL_UPGRADE" : "MANUAL_DOWNGRADE";

        subscription.setTier(targetTier);
        subscription = subscriptionRepository.save(subscription);

        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(subscription)
                .fromTier(currentTier)
                .toTier(targetTier)
                .fromStatus(SubscriptionStatus.ACTIVE)
                .toStatus(SubscriptionStatus.ACTIVE)
                .changeReason(direction)
                .changedAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        return buildStatusResponse(subscription);
    }

    // --- Cancel ---
    @Transactional
    public SubscriptionStatusResponse cancel(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("No active subscription found for user: " + userId));

        SubscriptionStatus oldStatus = subscription.getStatus();
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription = subscriptionRepository.save(subscription);

        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(subscription)
                .fromTier(subscription.getTier())
                .toTier(subscription.getTier())
                .fromStatus(oldStatus)
                .toStatus(SubscriptionStatus.CANCELLED)
                .changeReason("CANCEL")
                .changedAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        return buildStatusResponse(subscription);
    }

    // --- Get Status ---
    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriptionNotFoundException("No subscription found for user: " + userId));

        return buildStatusResponse(subscription);
    }

    // --- Internal: Auto-Upgrade (called after order placed) ---
    // Lock-outside-Transaction pattern
    public void triggerAutoUpgrade(Long userId) {
        System.out.println("DEBUG triggerAutoUpgrade called for userId=" + userId);
        userLockManager.executeWithLock(userId, () -> {
            try {
                System.out.println("DEBUG triggerAutoUpgrade in lock for userId=" + userId);
                self.triggerAutoUpgradeTransactional(userId);
            } catch (ObjectOptimisticLockingFailureException e) {
                optimisticLockCounter.incrementAndGet();
                System.out.println("DEBUG triggerAutoUpgrade optimistic lock exception for userId=" + userId);
                log.warn("Optimistic locking conflict during auto-upgrade for user: {}. Will skip as it's best-effort.", userId);
            } catch (Exception e) {
                System.out.println("DEBUG triggerAutoUpgrade exception: " + e.getMessage());
                log.error("Error during auto-upgrade for user: {}", userId, e);
            }
            return null;
        });
    }

    public int getAndResetOptimisticLockCount() {
        return optimisticLockCounter.getAndSet(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerAutoUpgradeTransactional(Long userId) {
        System.out.println("DEBUG triggerAutoUpgradeTransactional called for userId=" + userId);
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (subscription == null) {
            System.out.println("DEBUG triggerAutoUpgradeTransactional: subscription is null for userId=" + userId);
            return;
        }

        MembershipTier currentTier = subscription.getTier();
        MembershipTier targetTier = upgradeEngine.evaluate(subscription.getUser(), subscription);

        if (!currentTier.getId().equals(targetTier.getId())) {
            subscription.setTier(targetTier);
            subscriptionRepository.save(subscription);

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .subscription(subscription)
                    .fromTier(currentTier)
                    .toTier(targetTier)
                    .fromStatus(SubscriptionStatus.ACTIVE)
                    .toStatus(SubscriptionStatus.ACTIVE)
                    .changeReason("AUTO_UPGRADE")
                    .changedAt(LocalDateTime.now())
                    .build();
            historyRepository.save(history);

            log.info("Auto-upgraded user {} from {} to {}", userId, currentTier.getName(), targetTier.getName());
        }
    }

    // Lock-outside-Transaction pattern for scheduled tier evaluation
    public void triggerScheduledTierEvaluation(Long userId) {
        userLockManager.executeWithLock(userId, () -> {
            try {
                self.triggerScheduledTierEvaluationTransactional(userId);
            } catch (ObjectOptimisticLockingFailureException e) {
                optimisticLockCounter.incrementAndGet();
                log.warn("Optimistic locking conflict during scheduled evaluation for user: {}. Will skip as it is periodic.", userId);
            } catch (Exception e) {
                log.error("Error during scheduled evaluation for user: {}", userId, e);
            }
            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerScheduledTierEvaluationTransactional(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (subscription == null) {
            return;
        }

        MembershipTier currentTier = subscription.getTier();
        // Pass allowDowngrades = true to evaluate both upgrades and downgrades
        MembershipTier targetTier = upgradeEngine.evaluate(subscription.getUser(), subscription, true);

        if (!currentTier.getId().equals(targetTier.getId())) {
            subscription.setTier(targetTier);
            subscriptionRepository.save(subscription);

            String reason = targetTier.getPriority() > currentTier.getPriority() ? "AUTO_UPGRADE" : "AUTO_DOWNGRADE";

            SubscriptionHistory history = SubscriptionHistory.builder()
                    .subscription(subscription)
                    .fromTier(currentTier)
                    .toTier(targetTier)
                    .fromStatus(SubscriptionStatus.ACTIVE)
                    .toStatus(SubscriptionStatus.ACTIVE)
                    .changeReason(reason)
                    .changedAt(LocalDateTime.now())
                    .build();
            historyRepository.save(history);

            log.info("Scheduled evaluation: tier changed for user {} from {} to {} ({})", 
                userId, currentTier.getName(), targetTier.getName(), reason);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }

        SubscriptionStatus oldStatus = subscription.getStatus();
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscriptionRepository.save(subscription);

        SubscriptionHistory history = SubscriptionHistory.builder()
                .subscription(subscription)
                .fromTier(subscription.getTier())
                .toTier(subscription.getTier())
                .fromStatus(oldStatus)
                .toStatus(SubscriptionStatus.EXPIRED)
                .changeReason("AUTO_EXPIRY")
                .changedAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        log.info("Expired subscription ID {} for user {}", subscriptionId, subscription.getUser().getId());
    }

    // --- Helper Methods ---
    private LocalDate calculateEndDate(LocalDate start, BillingPeriod period) {
        return switch (period) {
            case MONTHLY -> start.plusMonths(1);
            case QUARTERLY -> start.plusMonths(3);
            case YEARLY -> start.plusYears(1);
        };
    }

    private SubscriptionStatusResponse buildStatusResponse(Subscription subscription) {
        LocalDate today = LocalDate.now();
        long daysRemaining = 0;
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE && subscription.getEndDate().isAfter(today)) {
            daysRemaining = ChronoUnit.DAYS.between(today, subscription.getEndDate());
        }

        // Fetch 30-day user stats for rule progressions
        LocalDateTime windowStart = LocalDateTime.now().minusDays(30);
        long orderCount = orderRepository.findByUserAndOrderDateAfter(subscription.getUser(), windowStart).size();
        BigDecimal totalSpend = orderRepository.sumFinalTotalByUserAndDateAfter(subscription.getUser(), windowStart);

        // Compute progress to next tier
        ProgressToNextTier progress = computeProgress(subscription.getUser(), subscription.getTier(), orderCount, totalSpend);

        // Fetch subscription history
        List<HistoryEntry> historyEntries = historyRepository.findBySubscriptionOrderByChangedAtDesc(subscription)
                .stream()
                .map(h -> HistoryEntry.builder()
                        .fromTier(h.getFromTier() != null ? h.getFromTier().getName() : null)
                        .toTier(h.getToTier() != null ? h.getToTier().getName() : null)
                        .reason(h.getChangeReason())
                        .changedAt(h.getChangedAt())
                        .build())
                .collect(Collectors.toList());

        return SubscriptionStatusResponse.builder()
                .userId(subscription.getUser().getId())
                .subscriptionId(subscription.getId())
                .planName(subscription.getPlan().getName())
                .billingPeriod(subscription.getPlan().getBillingPeriod().name())
                .tierName(subscription.getTier().getName())
                .tierPriority(subscription.getTier().getPriority())
                .status(subscription.getStatus().name())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .daysRemaining(daysRemaining)
                .activeBenefits(subscription.getTier().getBenefits())
                .progressToNextTier(progress)
                .history(historyEntries)
                .build();
    }

    private ProgressToNextTier computeProgress(User user, MembershipTier currentTier, long orderCount, BigDecimal totalSpend) {
        // Find all tiers
        List<MembershipTier> allTiers = tierRepository.findAllByOrderByPriorityDesc();
        
        // Find next tier (lowest priority tier that has priority strictly greater than currentTier)
        MembershipTier nextTier = null;
        for (int i = allTiers.size() - 1; i >= 0; i--) {
            MembershipTier candidate = allTiers.get(i);
            if (candidate.getPriority() > currentTier.getPriority()) {
                nextTier = candidate;
                break;
            }
        }

        if (nextTier == null || nextTier.getUpgradeRules() == null || nextTier.getUpgradeRules().isEmpty()) {
            return null;
        }

        List<RuleProgress> rulesProgress = new ArrayList<>();
        boolean isOr = nextTier.getUpgradeRules().stream()
                .anyMatch(r -> r.getParams() != null && "OR".equalsIgnoreCase(String.valueOf(r.getParams().get("operator"))));

        for (RuleConfig rule : nextTier.getUpgradeRules()) {
            Object currentValue = null;
            Object requiredValue = null;
            boolean met = false;

            switch (rule.getType()) {
                case ORDER_COUNT_THRESHOLD:
                    int threshold = Integer.parseInt(rule.getParams().get("threshold").toString());
                    currentValue = orderCount;
                    requiredValue = threshold;
                    met = orderCount >= threshold;
                    break;
                case MONTHLY_SPEND_THRESHOLD:
                    BigDecimal thresholdAmount = new BigDecimal(rule.getParams().get("thresholdAmount").toString());
                    currentValue = totalSpend;
                    requiredValue = thresholdAmount;
                    met = totalSpend.compareTo(thresholdAmount) >= 0;
                    break;
                case COHORT_MATCH:
                    String requiredCohort = rule.getParams().get("cohort").toString();
                    currentValue = user.getCohort() != null ? user.getCohort() : "";
                    requiredValue = requiredCohort;
                    met = user.getCohort() != null && user.getCohort().equalsIgnoreCase(requiredCohort);
                    break;
            }

            rulesProgress.add(RuleProgress.builder()
                    .ruleType(rule.getType().name())
                    .currentValue(currentValue)
                    .requiredValue(requiredValue)
                    .met(met)
                    .build());
        }

        return ProgressToNextTier.builder()
                .nextTierName(nextTier.getName())
                .rules(rulesProgress)
                .build();
    }
}
