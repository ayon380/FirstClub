package com.firstclub.membership.strategy.benefit;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.enums.BenefitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FreeDeliveryBenefitApplier implements BenefitApplier {

    @Override
    public boolean supports(BenefitType type) {
        return type == BenefitType.FREE_DELIVERY;
    }

    @Override
    public void apply(OrderContext context, BenefitConfig config) {
        BigDecimal minOrderValue = BigDecimal.ZERO;
        if (config.getParams() != null && config.getParams().containsKey("minOrderValue")) {
            Object minOrderValueObj = config.getParams().get("minOrderValue");
            if (minOrderValueObj != null) {
                minOrderValue = new BigDecimal(minOrderValueObj.toString());
            }
        }
        
        if (context.getRawTotal().compareTo(minOrderValue) >= 0) {
            context.setDeliveryFee(BigDecimal.ZERO);
        }
    }
}
