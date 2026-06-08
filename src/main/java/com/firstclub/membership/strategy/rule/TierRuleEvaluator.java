package com.firstclub.membership.strategy.rule;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;

public interface TierRuleEvaluator {
    boolean supports(RuleType type);
    boolean evaluate(User user, RuleConfig config, EvaluationContext context);
}
