package com.firstclub.membership.registry;

import com.firstclub.membership.context.EvaluationContext;
import com.firstclub.membership.domain.entity.RuleConfig;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.strategy.rule.TierRuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TierRuleEvaluatorRegistry {
    private final List<TierRuleEvaluator> evaluators;

    /**
     * Evaluates all rules. Defaults to AND logic (allMatch).
     * If any rule config has an "operator" parameter set to "OR",
     * it evaluates the rules as OR logic (anyMatch).
     */
    public boolean allRulesPass(User user, List<RuleConfig> rules, EvaluationContext ctx) {
        if (rules == null || rules.isEmpty()) return true;

        boolean isOrLogic = rules.stream()
                .anyMatch(r -> r.getParams() != null && "OR".equalsIgnoreCase(String.valueOf(r.getParams().get("operator"))));

        if (isOrLogic) {
            return rules.stream().anyMatch(rule ->
                evaluators.stream()
                    .filter(e -> e.supports(rule.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No evaluator for: " + rule.getType()))
                    .evaluate(user, rule, ctx)
            );
        } else {
            return rules.stream().allMatch(rule ->
                evaluators.stream()
                    .filter(e -> e.supports(rule.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No evaluator for: " + rule.getType()))
                    .evaluate(user, rule, ctx)
            );
        }
    }
}
