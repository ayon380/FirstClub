package com.firstclub.membership.strategy.benefit;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.enums.BenefitType;
import org.springframework.stereotype.Component;

@Component
public class ExclusiveCouponBenefitApplier implements BenefitApplier {

    @Override
    public boolean supports(BenefitType type) {
        return type == BenefitType.EXCLUSIVE_COUPON;
    }

    @Override
    public void apply(OrderContext context, BenefitConfig config) {
        if (config.getParams() != null && config.getParams().containsKey("couponCode")) {
            String couponCode = (String) config.getParams().get("couponCode");
            context.setCouponCode(couponCode);
        }
    }
}
