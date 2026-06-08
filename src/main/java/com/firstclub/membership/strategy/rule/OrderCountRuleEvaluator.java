package com.firstclub.membership.strategy.rule;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;
import org.springframework.stereotype.Component;

@Component
public class OrderCountRuleEvaluator implements TierRuleEvaluator {

    @Override
    public boolean supports(RuleType type) {
        return type == RuleType.ORDER_COUNT_THRESHOLD;
    }

    @Override
    public boolean evaluate(User user, RuleConfig config, EvaluationContext context) {
        if (config.getParams() == null) return false;
        
        Object thresholdObj = config.getParams().get("threshold");
        if (thresholdObj == null) return false;
        
        int threshold = Integer.parseInt(thresholdObj.toString());
        return context.getOrderCountLast30Days() >= threshold;
    }
}
