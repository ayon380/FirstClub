package com.firstclub.membership.context;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value // Immutable objects uses private fields and final fields
@Builder // Builder pattern 
public class EvaluationContext {
    long orderCountLast30Days;
    BigDecimal totalSpendLast30Days;
}
