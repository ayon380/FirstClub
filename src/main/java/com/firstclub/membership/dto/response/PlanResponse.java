package com.firstclub.membership.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlanResponse {
    private Long id;
    private String name;
    private String billingPeriod;
    private BigDecimal price;
    private String defaultTierName;
}
