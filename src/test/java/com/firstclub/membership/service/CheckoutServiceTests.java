package com.firstclub.membership.service;

import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.CartItem;
import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.dto.response.OrderSummaryResponse;
import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CheckoutServiceTests {

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
    private SubscriptionHistoryRepository subscriptionHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSeeder dataSeeder;

    private User testUser;
    private MembershipPlan monthlyPlan;
    private MembershipPlan quarterlyPlan;

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
                .name("Checkout User")
                .email("checkout@test.com")
                .build());

        monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();
        quarterlyPlan = planRepository.findByName("Quarterly Plus").orElseThrow();
    }

    @Test
    void calculate_WithNoSubscription_NoDiscountDefaultDelivery() {
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Laptop");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        OrderSummaryResponse summary = checkoutService.calculate(req);

        assertThat(summary.getDiscountApplied()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(summary.getFinalTotal()).isEqualByComparingTo(BigDecimal.valueOf(1050.00));
        assertThat(summary.getBenefitsApplied()).isEmpty();
    }

    @Test
    void calculate_WithGoldTier_Applies10PercentAndFreeDelivery() {
        // Subscribe to Quarterly Plus which starts at GOLD tier
        SubscribeRequest subReq = new SubscribeRequest();
        subReq.setUserId(testUser.getId());
        subReq.setPlanId(quarterlyPlan.getId());
        membershipService.subscribe(subReq);

        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Laptop");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        OrderSummaryResponse summary = checkoutService.calculate(req);

        // Gold: 10% discount on 1000 = 100
        assertThat(summary.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        // Gold: Unconditional free delivery = 0
        assertThat(summary.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        // Final = 1000 - 100 + 0 = 900
        assertThat(summary.getFinalTotal()).isEqualByComparingTo(BigDecimal.valueOf(900.00));
        assertThat(summary.getBenefitsApplied()).containsExactlyInAnyOrder(
                "Discount applied: saved ₹100.00",
                "Free Delivery applied",
                "Exclusive coupon code unlocked: GOLD20"
        );
    }

    @Test
    void placeOrder_SavesOrderAndTriggersAutoUpgradeOn5thOrder() throws Exception {
        // Subscribe to Monthly Basic which starts at SILVER tier
        SubscribeRequest subReq = new SubscribeRequest();
        subReq.setUserId(testUser.getId());
        subReq.setPlanId(monthlyPlan.getId());
        membershipService.subscribe(subReq);

        // Verify starting tier is SILVER
        Subscription subBefore = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(subBefore.getTier().getName()).isEqualTo("SILVER");

        // Place 4 orders
        for (int i = 1; i <= 4; i++) {
            CheckoutRequest req = new CheckoutRequest();
            req.setUserId(testUser.getId());
            req.setDeliveryFee(BigDecimal.valueOf(50.00));

            CartItem item = new CartItem();
            item.setProductName("Product " + i);
            item.setCategory("Fashion");
            item.setUnitPrice(BigDecimal.valueOf(600.00)); // silver free delivery on >= 500
            item.setQuantity(1);
            req.setItems(List.of(item));

            checkoutService.placeOrder(req);
        }

        // Wait brief time for async upgrades
        Thread.sleep(200);

        // Still silver because we need >= 5 orders for Gold
        Subscription subMiddle = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(subMiddle.getTier().getName()).isEqualTo("SILVER");

        // Place 5th order
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Product 5");
        item.setCategory("Fashion");
        item.setUnitPrice(BigDecimal.valueOf(600.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        checkoutService.placeOrder(req);

        // Wait up to 5 seconds for the async auto-upgrade to complete
        Subscription subAfter = null;
        for (int i = 0; i < 50; i++) {
            subAfter = subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
            if ("GOLD".equals(subAfter.getTier().getName())) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(subAfter.getTier().getName()).isEqualTo("GOLD");
    }

    @Test
    void calculate_DiscountExceedsRawTotal_CapsDiscountAndFinalTotal() {
        MembershipTier superTier = tierRepository.save(MembershipTier.builder()
                .name("SUPER")
                .priority(5)
                .benefits(List.of(BenefitConfig.builder()
                        .type(com.firstclub.membership.domain.enums.BenefitType.PERCENTAGE_DISCOUNT)
                        .params(java.util.Map.of("discountPercent", 150))
                        .build()))
                .build());

        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(superTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(10.00));

        CartItem item = new CartItem();
        item.setProductName("Gadget");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(100.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        OrderSummaryResponse summary = checkoutService.calculate(req);

        assertThat(summary.getRawTotal()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(summary.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(summary.getFinalTotal()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    @Test
    void calculate_SubscriptionTierWithNullBenefits_NoBenefitsApplied() {
        MembershipTier emptyTier = tierRepository.save(MembershipTier.builder()
                .name("EMPTY")
                .priority(4)
                .benefits(null)
                .build());

        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(emptyTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Book");
        item.setCategory("Books");
        item.setUnitPrice(BigDecimal.valueOf(200.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        OrderSummaryResponse summary = checkoutService.calculate(req);

        assertThat(summary.getDiscountApplied()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(summary.getBenefitsApplied()).isEmpty();
    }
}
