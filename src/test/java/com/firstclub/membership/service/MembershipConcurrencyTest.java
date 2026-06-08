package com.firstclub.membership.service;

import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.dto.request.CartItem;
import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MembershipConcurrencyTest {

    @Autowired
    private CheckoutService checkoutService;

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        subscriptionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        planRepository.deleteAll();
        tierRepository.deleteAll();

        dataSeeder.run();
    }

    @Test
    void concurrentOrdersDoNotDuplicateTierUpgrade() throws Exception {
        User testUser = userRepository.save(User.builder()
                .name("Concurrency User")
                .email("concurrency@test.com")
                .build());

        MembershipPlan monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();

        SubscribeRequest subReq = new SubscribeRequest();
        subReq.setUserId(testUser.getId());
        subReq.setPlanId(monthlyPlan.getId());
        membershipService.subscribe(subReq);

        // Pre-place 4 orders so the next order triggers upgrade
        for (int i = 1; i <= 4; i++) {
            CheckoutRequest req = buildOrder(testUser.getId(), i);
            checkoutService.placeOrder(req);
        }

        // Wait brief time for async upgrades to process
        Thread.sleep(200);

        Subscription subBefore = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(subBefore.getTier().getName()).isEqualTo("SILVER");

        // Launch 10 virtual threads simultaneously placing order #5
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            final int index = i + 5;
            exec.submit(() -> {
                try {
                    checkoutService.placeOrder(buildOrder(testUser.getId(), index));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // log but do not rethrow to verify total checkout success
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        exec.close();

        // Assert all orders were placed successfully
        assertThat(successes.get()).isEqualTo(10);

        // Wait brief time for background virtual thread upgrades to execute
        Thread.sleep(1000);

        Subscription sub = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(sub.getTier().getName()).isEqualTo("GOLD");

        List<SubscriptionHistory> history = historyRepository.findBySubscriptionOrderByChangedAtDesc(sub);
        long autoUpgrades = history.stream()
                .filter(h -> "AUTO_UPGRADE".equals(h.getChangeReason()))
                .count();

        // Exactly one auto-upgrade history entry is logged
        assertThat(autoUpgrades).isEqualTo(1);
    }

    private CheckoutRequest buildOrder(Long userId, int index) {
        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(userId);
        request.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Item " + index);
        item.setCategory("Fashion");
        item.setUnitPrice(BigDecimal.valueOf(600.00));
        item.setQuantity(1);

        request.setItems(List.of(item));
        return request;
    }
}
