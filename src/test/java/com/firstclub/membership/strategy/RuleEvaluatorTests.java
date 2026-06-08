package com.firstclub.membership.strategy;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;
import com.firstclub.membership.strategy.rule.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTests {

    @Test
    void orderCountRuleEvaluator_EvaluatesCorrectly() {
        OrderCountRuleEvaluator evaluator = new OrderCountRuleEvaluator();
        RuleConfig config = RuleConfig.builder()
                .type(RuleType.ORDER_COUNT_THRESHOLD)
                .params(Map.of("threshold", 5))
                .build();

        User user = new User();

        // Under threshold
        EvaluationContext ctx1 = EvaluationContext.builder().orderCountLast30Days(4).build();
        assertThat(evaluator.evaluate(user, config, ctx1)).isFalse();

        // At threshold
        EvaluationContext ctx2 = EvaluationContext.builder().orderCountLast30Days(5).build();
        assertThat(evaluator.evaluate(user, config, ctx2)).isTrue();
    }

    @Test
    void monthlySpendRuleEvaluator_EvaluatesCorrectly() {
        MonthlySpendRuleEvaluator evaluator = new MonthlySpendRuleEvaluator();
        RuleConfig config = RuleConfig.builder()
                .type(RuleType.MONTHLY_SPEND_THRESHOLD)
                .params(Map.of("thresholdAmount", 5000))
                .build();

        User user = new User();

        // Under threshold
        EvaluationContext ctx1 = EvaluationContext.builder().totalSpendLast30Days(BigDecimal.valueOf(4999.99)).build();
        assertThat(evaluator.evaluate(user, config, ctx1)).isFalse();

        // At threshold
        EvaluationContext ctx2 = EvaluationContext.builder().totalSpendLast30Days(BigDecimal.valueOf(5000.00)).build();
        assertThat(evaluator.evaluate(user, config, ctx2)).isTrue();
    }

    @Test
    void cohortRuleEvaluator_EvaluatesCorrectly() {
        CohortRuleEvaluator evaluator = new CohortRuleEvaluator();
        RuleConfig config = RuleConfig.builder()
                .type(RuleType.COHORT_MATCH)
                .params(Map.of("cohort", "PREMIUM_COHORT"))
                .build();

        // Matches exact/case-insensitive
        User user1 = User.builder().cohort("premium_cohort").build();
        EvaluationContext ctx = EvaluationContext.builder().build();
        assertThat(evaluator.evaluate(user1, config, ctx)).isTrue();

        // Mismatches
        User user2 = User.builder().cohort("REGULAR").build();
        assertThat(evaluator.evaluate(user2, config, ctx)).isFalse();

        // Null cohort
        User user3 = User.builder().cohort(null).build();
        assertThat(evaluator.evaluate(user3, config, ctx)).isFalse();
    }
}
