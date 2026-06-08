package com.firstclub.membership.strategy;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.entity.OrderItem;
import com.firstclub.membership.domain.enums.BenefitType;
import com.firstclub.membership.strategy.benefit.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitApplierTests {

    @Test
    void discountBenefitApplier_AppliesCartWideDiscount() {
        DiscountBenefitApplier applier = new DiscountBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.PERCENTAGE_DISCOUNT)
                .params(Map.of("discountPercent", 10))
                .build();

        OrderContext context = OrderContext.builder()
                .rawTotal(BigDecimal.valueOf(1000.00))
                .discountApplied(BigDecimal.ZERO)
                .build();

        applier.apply(context, config);
        assertThat(context.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    void discountBenefitApplier_AppliesCategoryScopedDiscount() {
        DiscountBenefitApplier applier = new DiscountBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.PERCENTAGE_DISCOUNT)
                .params(Map.of("discountPercent", 20, "category", "Electronics"))
                .build();

        OrderItem item1 = OrderItem.builder()
                .category("Electronics")
                .unitPrice(BigDecimal.valueOf(500.00))
                .quantity(1)
                .build();

        OrderItem item2 = OrderItem.builder()
                .category("Groceries")
                .unitPrice(BigDecimal.valueOf(100.00))
                .quantity(2)
                .build();

        OrderContext context = OrderContext.builder()
                .items(List.of(item1, item2))
                .rawTotal(BigDecimal.valueOf(700.00))
                .discountApplied(BigDecimal.ZERO)
                .build();

        applier.apply(context, config);
        // Only Electronics item (500) gets 20% discount = 100
        assertThat(context.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    void freeDeliveryBenefitApplier_WaivesFeeAboveThreshold() {
        FreeDeliveryBenefitApplier applier = new FreeDeliveryBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.FREE_DELIVERY)
                .params(Map.of("minOrderValue", 500))
                .build();

        // Above threshold
        OrderContext context1 = OrderContext.builder()
                .rawTotal(BigDecimal.valueOf(600.00))
                .deliveryFee(BigDecimal.valueOf(50.00))
                .build();
        applier.apply(context1, config);
        assertThat(context1.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);

        // Below threshold
        OrderContext context2 = OrderContext.builder()
                .rawTotal(BigDecimal.valueOf(400.00))
                .deliveryFee(BigDecimal.valueOf(50.00))
                .build();
        applier.apply(context2, config);
        assertThat(context2.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
    }

    @Test
    void exclusiveCouponBenefitApplier_SetsCouponCode() {
        ExclusiveCouponBenefitApplier applier = new ExclusiveCouponBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.EXCLUSIVE_COUPON)
                .params(Map.of("couponCode", "TEST20"))
                .build();

        OrderContext context = OrderContext.builder().build();
        applier.apply(context, config);
        assertThat(context.getCouponCode()).isEqualTo("TEST20");
    }

    @Test
    void prioritySupportBenefitApplier_SetsFlag() {
        PrioritySupportBenefitApplier applier = new PrioritySupportBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.PRIORITY_SUPPORT)
                .build();

        OrderContext context = OrderContext.builder().prioritySupport(false).build();
        applier.apply(context, config);
        assertThat(context.isPrioritySupport()).isTrue();
    }

    @Test
    void discountBenefitApplier_MissingParams_NoDiscountApplied() {
        DiscountBenefitApplier applier = new DiscountBenefitApplier();
        OrderContext context = OrderContext.builder()
                .rawTotal(BigDecimal.valueOf(1000.00))
                .discountApplied(BigDecimal.ZERO)
                .build();

        applier.apply(context, BenefitConfig.builder().build());
        assertThat(context.getDiscountApplied()).isEqualByComparingTo(BigDecimal.ZERO);

        applier.apply(context, BenefitConfig.builder().params(Map.of()).build());
        assertThat(context.getDiscountApplied()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void discountBenefitApplier_CategoryDiscountNullItemsAndNullLineTotal() {
        DiscountBenefitApplier applier = new DiscountBenefitApplier();
        BenefitConfig config = BenefitConfig.builder()
                .type(BenefitType.PERCENTAGE_DISCOUNT)
                .params(Map.of("discountPercent", 10, "category", "Electronics"))
                .build();

        OrderContext context1 = OrderContext.builder()
                .items(null)
                .rawTotal(BigDecimal.valueOf(1000.00))
                .discountApplied(BigDecimal.ZERO)
                .build();
        applier.apply(context1, config);
        assertThat(context1.getDiscountApplied()).isEqualByComparingTo(BigDecimal.ZERO);

        OrderItem item = OrderItem.builder()
                .category("Electronics")
                .unitPrice(BigDecimal.valueOf(500.00))
                .quantity(2)
                .lineTotal(null)
                .build();
        OrderContext context2 = OrderContext.builder()
                .items(List.of(item))
                .rawTotal(BigDecimal.valueOf(1000.00))
                .discountApplied(BigDecimal.ZERO)
                .build();
        applier.apply(context2, config);
        assertThat(context2.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    void freeDeliveryBenefitApplier_NullParams_DefaultsToFreeDelivery() {
        FreeDeliveryBenefitApplier applier = new FreeDeliveryBenefitApplier();
        OrderContext context = OrderContext.builder()
                .rawTotal(BigDecimal.valueOf(100.00))
                .deliveryFee(BigDecimal.valueOf(50.00))
                .build();

        applier.apply(context, BenefitConfig.builder().build());
        assertThat(context.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
