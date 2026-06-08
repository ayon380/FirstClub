package com.firstclub.membership.service;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.CartItem;
import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.dto.response.OrderSummaryResponse;
import com.firstclub.membership.registry.BenefitApplierRegistry;
import com.firstclub.membership.repository.OrderRepository;
import com.firstclub.membership.repository.SubscriptionRepository;
import com.firstclub.membership.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final BenefitApplierRegistry benefitApplierRegistry;
    private final MembershipService membershipService;

    @Qualifier("virtualThreadExecutor")
    private final ExecutorService virtualThreadExecutor;

    @Transactional(readOnly = true)
    public OrderSummaryResponse calculate(CheckoutRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(request.getUserId(), SubscriptionStatus.ACTIVE)
                .orElse(null);

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal rawTotal = BigDecimal.ZERO;
        for (CartItem item : request.getItems()) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            rawTotal = rawTotal.add(lineTotal);
            orderItems.add(OrderItem.builder()
                    .productName(item.getProductName())
                    .category(item.getCategory())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        BigDecimal defaultDeliveryFee = request.getDeliveryFee() != null ? request.getDeliveryFee() : BigDecimal.valueOf(50.00);

        OrderContext context = OrderContext.builder()
                .user(user)
                .subscription(subscription)
                .items(orderItems)
                .rawTotal(rawTotal)
                .discountApplied(BigDecimal.ZERO)
                .deliveryFee(defaultDeliveryFee)
                .build();

        if (subscription != null && subscription.getTier().getBenefits() != null) {
            benefitApplierRegistry.applyAll(context, subscription.getTier().getBenefits());
        }

        BigDecimal discountApplied = context.getDiscountApplied();
        if (discountApplied.compareTo(rawTotal) > 0) {
            discountApplied = rawTotal;
        }

        BigDecimal finalTotal = rawTotal.subtract(discountApplied).add(context.getDeliveryFee());
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }

        List<String> benefitsApplied = new ArrayList<>();
        if (subscription != null) {
            if (context.getDiscountApplied().compareTo(BigDecimal.ZERO) > 0) {
                benefitsApplied.add("Discount applied: saved ₹" + context.getDiscountApplied());
            }
            if (context.getDeliveryFee().compareTo(BigDecimal.ZERO) == 0 && defaultDeliveryFee.compareTo(BigDecimal.ZERO) > 0) {
                benefitsApplied.add("Free Delivery applied");
            }
            if (context.getCouponCode() != null) {
                benefitsApplied.add("Exclusive coupon code unlocked: " + context.getCouponCode());
            }
            if (context.isPrioritySupport()) {
                benefitsApplied.add("Priority Support enabled");
            }
        }

        return OrderSummaryResponse.builder()
                .userId(request.getUserId())
                .rawTotal(rawTotal)
                .discountApplied(discountApplied)
                .deliveryFee(context.getDeliveryFee())
                .finalTotal(finalTotal)
                .couponCode(context.getCouponCode())
                .prioritySupport(context.isPrioritySupport())
                .benefitsApplied(benefitsApplied)
                .tierName(subscription != null ? subscription.getTier().getName() : "NONE")
                .build();
    }

    @Transactional
    public OrderSummaryResponse placeOrder(CheckoutRequest request) {
        OrderSummaryResponse summary = calculate(request);

        User user = userRepository.findById(request.getUserId()).orElseThrow();

        List<OrderItem> items = new ArrayList<>();
        Order order = Order.builder()
                .user(user)
                .rawTotal(summary.getRawTotal())
                .discountApplied(summary.getDiscountApplied())
                .deliveryFee(summary.getDeliveryFee())
                .finalTotal(summary.getFinalTotal())
                .couponCode(summary.getCouponCode())
                .prioritySupport(summary.isPrioritySupport())
                .orderDate(LocalDateTime.now())
                .build();

        for (CartItem item : request.getItems()) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            items.add(OrderItem.builder()
                    .order(order)
                    .productName(item.getProductName())
                    .category(item.getCategory())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }
        order.setItems(items);

        Order savedOrder = orderRepository.save(order);
        summary.setOrderId(savedOrder.getId());

        // Asynchronously evaluate tier upgrades using a virtual thread after the transaction commits
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        virtualThreadExecutor.submit(() -> membershipService.triggerAutoUpgrade(user.getId()));
                    }
                }
            );
        } else {
            virtualThreadExecutor.submit(() -> membershipService.triggerAutoUpgrade(user.getId()));
        }

        return summary;
    }
}
