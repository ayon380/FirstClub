package com.firstclub.membership.strategy.rule;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;
import org.springframework.stereotype.Component;

@Component
public class CohortRuleEvaluator implements TierRuleEvaluator {

    @Override
    public boolean supports(RuleType type) {
        return type == RuleType.COHORT_MATCH;
    }

    @Override
    public boolean evaluate(User user, RuleConfig config, EvaluationContext context) {
        if (config.getParams() == null) return false;
        
        Object cohortObj = config.getParams().get("cohort");
        if (cohortObj == null) return false;
        
        String targetCohort = cohortObj.toString();
        return user.getCohort() != null && user.getCohort().equalsIgnoreCase(targetCohort);
    }
}
