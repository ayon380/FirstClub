package com.firstclub.membership.config;

import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.*;
import com.firstclub.membership.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MembershipTierRepository tierRepository;
    private final MembershipPlanRepository planRepository;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return; // Already seeded
        }

        // 1. Seed Tiers
        // SILVER
        List<BenefitConfig> silverBenefits = List.of(
                BenefitConfig.builder()
                        .type(BenefitType.PERCENTAGE_DISCOUNT)
                        .params(Map.of("discountPercent", 5))
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.FREE_DELIVERY)
                        .params(Map.of("minOrderValue", 500))
                        .build()
        );
        MembershipTier silver = MembershipTier.builder()
                .name("SILVER")
                .priority(1)
                .benefits(silverBenefits)
                .upgradeRules(new ArrayList<>())
                .build();
        silver = tierRepository.save(silver);

        // GOLD
        List<BenefitConfig> goldBenefits = List.of(
                BenefitConfig.builder()
                        .type(BenefitType.PERCENTAGE_DISCOUNT)
                        .params(Map.of("discountPercent", 10))
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.FREE_DELIVERY)
                        .params(Map.of("minOrderValue", 0))
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.EXCLUSIVE_COUPON)
                        .params(Map.of("couponCode", "GOLD20"))
                        .build()
        );
        List<RuleConfig> goldRules = List.of(
                RuleConfig.builder()
                        .type(RuleType.ORDER_COUNT_THRESHOLD)
                        .params(Map.of("threshold", 5, "operator", "OR"))
                        .build(),
                RuleConfig.builder()
                        .type(RuleType.MONTHLY_SPEND_THRESHOLD)
                        .params(Map.of("thresholdAmount", 5000, "operator", "OR"))
                        .build()
        );
        MembershipTier gold = MembershipTier.builder()
                .name("GOLD")
                .priority(2)
                .benefits(goldBenefits)
                .upgradeRules(goldRules)
                .build();
        gold = tierRepository.save(gold);

        // PLATINUM
        List<BenefitConfig> platinumBenefits = List.of(
                BenefitConfig.builder()
                        .type(BenefitType.PERCENTAGE_DISCOUNT)
                        .params(Map.of("discountPercent", 20))
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.FREE_DELIVERY)
                        .params(Map.of("minOrderValue", 0))
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.PRIORITY_SUPPORT)
                        .params(Map.of())
                        .build(),
                BenefitConfig.builder()
                        .type(BenefitType.EXCLUSIVE_COUPON)
                        .params(Map.of("couponCode", "PLAT50"))
                        .build()
        );
        List<RuleConfig> platinumRules = List.of(
                RuleConfig.builder()
                        .type(RuleType.ORDER_COUNT_THRESHOLD)
                        .params(Map.of("threshold", 20, "operator", "OR"))
                        .build(),
                RuleConfig.builder()
                        .type(RuleType.COHORT_MATCH)
                        .params(Map.of("cohort", "PREMIUM_COHORT", "operator", "OR"))
                        .build()
        );
        MembershipTier platinum = MembershipTier.builder()
                .name("PLATINUM")
                .priority(3)
                .benefits(platinumBenefits)
                .upgradeRules(platinumRules)
                .build();
        platinum = tierRepository.save(platinum);

        // 2. Seed Plans
        MembershipPlan monthlyBasic = MembershipPlan.builder()
                .name("Monthly Basic")
                .billingPeriod(BillingPeriod.MONTHLY)
                .price(BigDecimal.valueOf(199.00))
                .defaultTier(silver)
                .build();
        planRepository.save(monthlyBasic);

        MembershipPlan quarterlyPlus = MembershipPlan.builder()
                .name("Quarterly Plus")
                .billingPeriod(BillingPeriod.QUARTERLY)
                .price(BigDecimal.valueOf(499.00))
                .defaultTier(gold)
                .build();
        planRepository.save(quarterlyPlus);

        MembershipPlan yearlyPremium = MembershipPlan.builder()
                .name("Yearly Premium")
                .billingPeriod(BillingPeriod.YEARLY)
                .price(BigDecimal.valueOf(1499.00))
                .defaultTier(platinum)
                .build();
        planRepository.save(yearlyPremium);

        // 3. Seed Users
        User arjun = User.builder()
                .name("Arjun Mehta")
                .email("arjun@firstclub.com")
                .cohort("PREMIUM_COHORT")
                .build();
        userRepository.save(arjun);

        User priya = User.builder()
                .name("Priya Sharma")
                .email("priya@firstclub.com")
                .cohort("EARLY_ADOPTER")
                .build();
        userRepository.save(priya);

        User rohan = User.builder()
                .name("Rohan Das")
                .email("rohan@firstclub.com")
                .cohort(null)
                .build();
        userRepository.save(rohan);
    }
}
