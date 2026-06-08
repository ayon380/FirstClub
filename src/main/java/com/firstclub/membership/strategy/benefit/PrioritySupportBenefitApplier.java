package com.firstclub.membership.strategy.benefit;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.enums.BenefitType;
import org.springframework.stereotype.Component;

@Component
public class PrioritySupportBenefitApplier implements BenefitApplier {

    @Override
    public boolean supports(BenefitType type) {
        return type == BenefitType.PRIORITY_SUPPORT;
    }

    @Override
    public void apply(OrderContext context, BenefitConfig config) {
        context.setPrioritySupport(true);
    }
}
