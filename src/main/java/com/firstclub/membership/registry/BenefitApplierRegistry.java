package com.firstclub.membership.registry;

import com.firstclub.membership.context.OrderContext;
import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.strategy.benefit.BenefitApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BenefitApplierRegistry {
    private final List<BenefitApplier> appliers; // Spring auto-injects all implementations

    public void applyAll(OrderContext context, List<BenefitConfig> benefits) {
        if (benefits == null) return;
        for (BenefitConfig benefit : benefits) {
            appliers.stream()
                .filter(a -> a.supports(benefit.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No applier for: " + benefit.getType()))
                .apply(context, benefit);
        }
    }
}
