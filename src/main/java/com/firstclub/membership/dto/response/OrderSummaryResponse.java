package com.firstclub.membership.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OrderSummaryResponse {
    private Long orderId;
    private Long userId;
    private BigDecimal rawTotal;
    private BigDecimal discountApplied;
    private BigDecimal deliveryFee;
    private BigDecimal finalTotal;
    private String couponCode;
    private boolean prioritySupport;
    private List<String> benefitsApplied;
    private String tierName;
}
