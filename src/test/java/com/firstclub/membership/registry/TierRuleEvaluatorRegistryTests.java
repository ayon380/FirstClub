package com.firstclub.membership.registry;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.domain.enums.RuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TierRuleEvaluatorRegistryTests {

    @Autowired
    private TierRuleEvaluatorRegistry registry;

    @Test
    void allRulesPass_NullOrEmptyRules_ReturnsTrue() {
        User user = new User();
        EvaluationContext ctx = EvaluationContext.builder().build();

        assertThat(registry.allRulesPass(user, null, ctx)).isTrue();
        assertThat(registry.allRulesPass(user, List.of(), ctx)).isTrue();
    }

    @Test
    void allRulesPass_NoEvaluatorForType_ThrowsIllegalStateException() {
        TierRuleEvaluatorRegistry emptyRegistry = new TierRuleEvaluatorRegistry(List.of());
        User user = new User();
        EvaluationContext ctx = EvaluationContext.builder().build();

        RuleConfig rule = RuleConfig.builder()
                .type(RuleType.ORDER_COUNT_THRESHOLD)
                .build();

        assertThatThrownBy(() -> emptyRegistry.allRulesPass(user, List.of(rule), ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No evaluator for: ORDER_COUNT_THRESHOLD");
    }

    @Test
    void allRulesPass_AndLogic_CorrectlyEvaluated() {
        User user = User.builder().cohort("COHORT_A").build();
        EvaluationContext ctx = EvaluationContext.builder()
                .orderCountLast30Days(10)
                .totalSpendLast30Days(BigDecimal.valueOf(100.00))
                .build();

        RuleConfig rule1 = RuleConfig.builder()
                .type(RuleType.ORDER_COUNT_THRESHOLD)
                .params(Map.of("threshold", 5))
                .build();

        RuleConfig rule2 = RuleConfig.builder()
                .type(RuleType.COHORT_MATCH)
                .params(Map.of("cohort", "COHORT_A"))
                .build();

        assertThat(registry.allRulesPass(user, List.of(rule1, rule2), ctx)).isTrue();

        RuleConfig rule3 = RuleConfig.builder()
                .type(RuleType.COHORT_MATCH)
                .params(Map.of("cohort", "COHORT_B"))
                .build();

        assertThat(registry.allRulesPass(user, List.of(rule1, rule3), ctx)).isFalse();
    }

    @Test
    void allRulesPass_OrLogic_CorrectlyEvaluated() {
        User user = User.builder().cohort("COHORT_A").build();
        EvaluationContext ctx = EvaluationContext.builder()
                .orderCountLast30Days(2)
                .totalSpendLast30Days(BigDecimal.valueOf(100.00))
                .build();

        RuleConfig rule1 = RuleConfig.builder()
                .type(RuleType.ORDER_COUNT_THRESHOLD)
                .params(Map.of("threshold", 5, "operator", "OR"))
                .build();

        RuleConfig rule2 = RuleConfig.builder()
                .type(RuleType.COHORT_MATCH)
                .params(Map.of("cohort", "COHORT_A", "operator", "OR"))
                .build();

        assertThat(registry.allRulesPass(user, List.of(rule1, rule2), ctx)).isTrue();

        RuleConfig rule3 = RuleConfig.builder()
                .type(RuleType.COHORT_MATCH)
                .params(Map.of("cohort", "COHORT_B", "operator", "OR"))
                .build();

        assertThat(registry.allRulesPass(user, List.of(rule1, rule3), ctx)).isFalse();
    }
}
