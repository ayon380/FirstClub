package com.firstclub.membership.scheduler;

import com.firstclub.membership.domain.entity.Subscription;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.repository.SubscriptionRepository;
import com.firstclub.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final MembershipService membershipService;

    /**
     * Periodically runs to expire subscriptions whose end dates are in the past
     * and evaluates remaining active memberships for tier upgrades/downgrades.
     */
    @Scheduled(cron = "${membership.scheduler.cron:0 0 1 * * ?}") // Daily at 1:00 AM
    public void processExpiredSubscriptionsAndTiers() {
        log.info("Starting scheduled membership expiry and tier evaluation task...");
        
        LocalDate today = LocalDate.now();

        // 1. Find and transition expired subscriptions
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByStatusAndEndDateBefore(
                SubscriptionStatus.ACTIVE, today);
        
        log.info("Found {} expired subscriptions to process.", expiredSubscriptions.size());
        for (Subscription sub : expiredSubscriptions) {
            try {
                membershipService.expireSubscription(sub.getId());
            } catch (Exception e) {
                log.error("Failed to expire subscription ID: {}", sub.getId(), e);
            }
        }

        // 2. Fetch all currently active subscriptions to evaluate upgrades/downgrades based on trailing 30 days
        List<Subscription> activeSubscriptions = subscriptionRepository.findAll().stream()
                .filter(sub -> sub.getStatus() == SubscriptionStatus.ACTIVE)
                .toList();

        log.info("Evaluating tiers for {} active subscriptions.", activeSubscriptions.size());
        for (Subscription sub : activeSubscriptions) {
            try {
                membershipService.triggerScheduledTierEvaluation(sub.getUser().getId());
            } catch (Exception e) {
                log.error("Failed to evaluate tier for user ID: {}", sub.getUser().getId(), e);
            }
        }

        log.info("Scheduled membership expiry and tier evaluation task completed.");
    }
}
