package com.firstclub.membership.dto.response;

import com.firstclub.membership.domain.entity.BenefitConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SubscriptionStatusResponse {
    private Long userId;
    private Long subscriptionId;
    private String planName;
    private String billingPeriod;
    private String tierName;
    private int tierPriority;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private long daysRemaining;
    private List<BenefitConfig> activeBenefits;
    private ProgressToNextTier progressToNextTier;
    private List<HistoryEntry> history;

    @Data
    @Builder
    public static class ProgressToNextTier {
        private String nextTierName;
        private List<RuleProgress> rules;
    }

    @Data
    @Builder
    public static class RuleProgress {
        private String ruleType;
        private Object currentValue;
        private Object requiredValue;
        private boolean met;
    }

    @Data
    @Builder
    public static class HistoryEntry {
        private String fromTier;
        private String toTier;
        private String reason;
        private LocalDateTime changedAt;
    }
}
