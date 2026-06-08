package com.firstclub.membership.scheduler;

import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.repository.*;
import com.firstclub.membership.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SubscriptionExpirySchedulerTests {

    @Autowired
    private SubscriptionExpiryScheduler scheduler;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private MembershipTierRepository tierRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionHistoryRepository historyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSeeder dataSeeder;

    private User testUser;
    private MembershipPlan monthlyPlan;
    private MembershipPlan quarterlyPlan;
    private MembershipTier silverTier;
    private MembershipTier goldTier;

    @BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        subscriptionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        planRepository.deleteAll();
        tierRepository.deleteAll();

        dataSeeder.run();

        testUser = userRepository.save(User.builder()
                .name("Scheduler User")
                .email("scheduler@test.com")
                .build());

        silverTier = tierRepository.findByName("SILVER").orElseThrow();
        goldTier = tierRepository.findByName("GOLD").orElseThrow();
        monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();
        quarterlyPlan = planRepository.findByName("Quarterly Plus").orElseThrow();
    }

    @Test
    void scheduler_ExpiresOverdueSubscriptions() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());
        membershipService.subscribe(req);

        Subscription sub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        // Force expiry
        sub.setEndDate(LocalDate.now().minusDays(1));
        subscriptionRepository.save(sub);

        scheduler.processExpiredSubscriptionsAndTiers();

        Subscription updatedSub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(updatedSub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        List<SubscriptionHistory> history = historyRepository.findBySubscriptionOrderByChangedAtDesc(updatedSub);
        assertThat(history.get(0).getChangeReason()).isEqualTo("AUTO_EXPIRY");
        assertThat(history.get(0).getToStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void scheduler_DowngradesTierWhenThresholdNotMet() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(quarterlyPlan.getId());
        membershipService.subscribe(req);

        Subscription sub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(sub.getTier().getName()).isEqualTo("GOLD");

        // Manually upgrade to PLATINUM
        sub.setTier(tierRepository.findByName("PLATINUM").orElseThrow());
        subscriptionRepository.save(sub);

        scheduler.processExpiredSubscriptionsAndTiers();

        Subscription updatedSub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(updatedSub.getTier().getName()).isEqualTo("GOLD");

        List<SubscriptionHistory> history = historyRepository.findBySubscriptionOrderByChangedAtDesc(updatedSub);
        assertThat(history.get(0).getChangeReason()).isEqualTo("AUTO_DOWNGRADE");
        assertThat(history.get(0).getFromTier().getName()).isEqualTo("PLATINUM");
        assertThat(history.get(0).getToTier().getName()).isEqualTo("GOLD");
    }
}
