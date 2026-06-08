package com.firstclub.membership.context;

import com.firstclub.membership.domain.entity.OrderItem;
import com.firstclub.membership.domain.entity.Subscription;
import com.firstclub.membership.domain.entity.User;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data // getters and setters mutable object
@Builder // builder pattern
public class OrderContext {
    private User user;
    private Subscription subscription;        // may be null if no active subscription
    private List<OrderItem> items;
    private BigDecimal rawTotal;

    // Appliers modify these:
    private BigDecimal discountApplied;       // starts at ZERO
    private BigDecimal deliveryFee;           // starts at default (e.g. 50.00)
    private String couponCode;
    private boolean prioritySupport;
}
