package com.firstclub.membership.strategy.benefit;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.entity.OrderItem;
import com.firstclub.membership.domain.enums.BenefitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DiscountBenefitApplier implements BenefitApplier {

    @Override
    public boolean supports(BenefitType type) {
        return type == BenefitType.PERCENTAGE_DISCOUNT;
    }

    @Override
    public void apply(OrderContext context, BenefitConfig config) {
        if (config.getParams() == null) return;
        
        Object discountPercentObj = config.getParams().get("discountPercent");
        if (discountPercentObj == null) return;
        
        BigDecimal discountPercent = new BigDecimal(discountPercentObj.toString());
        String targetCategory = (String) config.getParams().get("category");
        
        BigDecimal discount = BigDecimal.ZERO;
        if (targetCategory != null && !targetCategory.trim().isEmpty()) {
            BigDecimal categoryTotal = BigDecimal.ZERO;
            if (context.getItems() != null) {
                for (OrderItem item : context.getItems()) {
                    if (targetCategory.equalsIgnoreCase(item.getCategory())) {
                        BigDecimal lineTotal = item.getLineTotal();
                        if (lineTotal == null) {
                            lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                        }
                        categoryTotal = categoryTotal.add(lineTotal);
                    }
                }
            }
            discount = categoryTotal.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = context.getRawTotal().multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        
        context.setDiscountApplied(context.getDiscountApplied().add(discount));
    }
}
