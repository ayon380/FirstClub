package com.firstclub.membership.strategy.rule;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MonthlySpendRuleEvaluator implements TierRuleEvaluator {

    @Override
    public boolean supports(RuleType type) {
        return type == RuleType.MONTHLY_SPEND_THRESHOLD;
    }

    @Override
    public boolean evaluate(User user, RuleConfig config, EvaluationContext context) {
        if (config.getParams() == null) return false;
        
        Object thresholdAmountObj = config.getParams().get("thresholdAmount");
        if (thresholdAmountObj == null) return false;
        
        BigDecimal thresholdAmount = new BigDecimal(thresholdAmountObj.toString());
        return context.getTotalSpendLast30Days().compareTo(thresholdAmount) >= 0;
    }
}
