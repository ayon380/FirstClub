package com.firstclub.membership.service;

import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TierUpgradeEngineTests {

    @Autowired
    private TierUpgradeEngine upgradeEngine;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private MembershipTierRepository tierRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionHistoryRepository subscriptionHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSeeder dataSeeder;

    private User testUser;
    private MembershipPlan monthlyPlan;
    private MembershipTier silverTier;
    private MembershipTier goldTier;
    private MembershipTier platinumTier;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionHistoryRepository.deleteAll();
        subscriptionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        planRepository.deleteAll();
        tierRepository.deleteAll();

        dataSeeder.run();

        testUser = userRepository.save(User.builder()
                .name("Engine User")
                .email("engine@test.com")
                .build());

        monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();
        silverTier = tierRepository.findByName("SILVER").orElseThrow();
        goldTier = tierRepository.findByName("GOLD").orElseThrow();
        platinumTier = tierRepository.findByName("PLATINUM").orElseThrow();
    }

    @Test
    void evaluate_NoOrders_ReturnsCurrentTier() {
        Subscription subscription = Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .build();

        MembershipTier result = upgradeEngine.evaluate(testUser, subscription);
        assertThat(result.getId()).isEqualTo(silverTier.getId());
    }

    @Test
    void evaluate_QualifiesForGoldAndPlatinum_UpgradesToHighestPriority() {
        // Set cohort to PREMIUM_COHORT -> qualifies for PLATINUM
        // Also place 5 orders -> qualifies for GOLD
        testUser.setCohort("PREMIUM_COHORT");
        testUser = userRepository.save(testUser);

        for (int i = 0; i < 5; i++) {
            orderRepository.save(Order.builder()
                    .user(testUser)
                    .rawTotal(BigDecimal.valueOf(100.00))
                    .discountApplied(BigDecimal.ZERO)
                    .deliveryFee(BigDecimal.ZERO)
                    .finalTotal(BigDecimal.valueOf(100.00))
                    .orderDate(LocalDateTime.now())
                    .build());
        }

        Subscription subscription = Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .build();

        MembershipTier result = upgradeEngine.evaluate(testUser, subscription);
        // Should directly upgrade to PLATINUM since it is priority 3 (GOLD is priority 2)
        assertThat(result.getId()).isEqualTo(platinumTier.getId());
    }

    @Test
    void evaluate_CurrentIsPlatinumButStatsDropped_NoDowngrade() {
        // User has 0 orders and null cohort, but currently is PLATINUM
        Subscription subscription = Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(platinumTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .build();

        MembershipTier result = upgradeEngine.evaluate(testUser, subscription);
        // Should remain PLATINUM
        assertThat(result.getId()).isEqualTo(platinumTier.getId());
    }
}
