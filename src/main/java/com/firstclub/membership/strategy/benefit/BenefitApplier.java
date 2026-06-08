package com.firstclub.membership.strategy.benefit;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.enums.BenefitType;

public interface BenefitApplier {
    boolean supports(BenefitType type);
    void apply(OrderContext context, BenefitConfig config);
}
