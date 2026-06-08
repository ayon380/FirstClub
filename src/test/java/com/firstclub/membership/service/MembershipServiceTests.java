package com.firstclub.membership.service;

import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.ChangeTierRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse;
import com.firstclub.membership.exception.SubscriptionNotFoundException;
import com.firstclub.membership.repository.MembershipPlanRepository;
import com.firstclub.membership.repository.MembershipTierRepository;
import com.firstclub.membership.repository.SubscriptionRepository;
import com.firstclub.membership.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MembershipServiceTests {

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

    private User testUser;
    private MembershipPlan monthlyPlan;
    private MembershipTier silverTier;
    private MembershipTier goldTier;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Integration User")
                .email("integration@test.com")
                .build());

        silverTier = tierRepository.findByName("SILVER").orElseThrow();
        goldTier = tierRepository.findByName("GOLD").orElseThrow();
        monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();
    }

    @Test
    void subscribe_CreatesActiveSubscriptionAndHistory() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());

        SubscriptionStatusResponse resp = membershipService.subscribe(req);

        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        assertThat(resp.getTierName()).isEqualTo(silverTier.getName());
        assertThat(resp.getPlanName()).isEqualTo(monthlyPlan.getName());

        Subscription sub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getTier().getId()).isEqualTo(silverTier.getId());

        assertThat(resp.getHistory()).hasSize(1);
        assertThat(resp.getHistory().get(0).getReason()).isEqualTo("SUBSCRIBE");
    }

    @Test
    void subscribe_TwiceThrowsException() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());

        membershipService.subscribe(req);

        // Subscribing again should throw IllegalStateException
        assertThatThrownBy(() -> membershipService.subscribe(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already has an active subscription");
    }

    @Test
    void changeTier_ManualUpgradeAndDowngrade() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());
        membershipService.subscribe(req);

        // Upgrade
        ChangeTierRequest upgradeReq = new ChangeTierRequest();
        upgradeReq.setTierId(goldTier.getId());
        SubscriptionStatusResponse upgradeResp = membershipService.changeTier(testUser.getId(), upgradeReq);

        assertThat(upgradeResp.getTierName()).isEqualTo(goldTier.getName());
        assertThat(upgradeResp.getHistory().get(0).getReason()).isEqualTo("MANUAL_UPGRADE");

        // Downgrade
        ChangeTierRequest downgradeReq = new ChangeTierRequest();
        downgradeReq.setTierId(silverTier.getId());
        SubscriptionStatusResponse downgradeResp = membershipService.changeTier(testUser.getId(), downgradeReq);

        assertThat(downgradeResp.getTierName()).isEqualTo(silverTier.getName());
        assertThat(downgradeResp.getHistory().get(0).getReason()).isEqualTo("MANUAL_DOWNGRADE");
    }

    @Test
    void cancel_CancelsSubscription() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());
        membershipService.subscribe(req);

        SubscriptionStatusResponse cancelResp = membershipService.cancel(testUser.getId());

        assertThat(cancelResp.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED.name());
        assertThat(cancelResp.getHistory().get(0).getReason()).isEqualTo("CANCEL");

        Subscription sub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void getStatus_NonExistentUser_ThrowsSubscriptionNotFoundException() {
        assertThatThrownBy(() -> membershipService.getStatus(99999L))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("No subscription found for user: 99999");
    }

    @Test
    void cancel_NonExistentActiveSubscription_ThrowsSubscriptionNotFoundException() {
        assertThatThrownBy(() -> membershipService.cancel(testUser.getId()))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("No active subscription found for user: " + testUser.getId());
    }

    @Test
    void changeTier_NonExistentActiveSubscription_ThrowsSubscriptionNotFoundException() {
        ChangeTierRequest changeReq = new ChangeTierRequest();
        changeReq.setTierId(goldTier.getId());

        assertThatThrownBy(() -> membershipService.changeTier(testUser.getId(), changeReq))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("No active subscription found for user: " + testUser.getId());
    }

    @Test
    void subscribe_NonExistentUser_ThrowsIllegalArgumentException() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(99999L);
        req.setPlanId(monthlyPlan.getId());

        assertThatThrownBy(() -> membershipService.subscribe(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void subscribe_NonExistentPlan_ThrowsIllegalArgumentException() {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(99999L);

        assertThatThrownBy(() -> membershipService.subscribe(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan not found");
    }
}
