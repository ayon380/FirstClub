package com.firstclub.membership.domain.enums;

public enum RuleType {
    ORDER_COUNT_THRESHOLD,    // number of orders in last 30 days > X
    MONTHLY_SPEND_THRESHOLD,  // total spend in last 30 days > Y
    COHORT_MATCH              // user.cohort == config value
}
