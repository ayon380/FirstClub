package com.firstclub.membership.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConcurrencyTestResult {
    private Long userId;
    private int threadsLaunched;
    private int successCount;
    private int failureCount;
    private int optimisticLockRetries;
    private String finalTierName;
    private List<Long> orderIds;
    private long durationMs;
    private List<ThreadResult> threadResults;

    @Data
    @Builder
    public static class ThreadResult {
        private String threadId;
        private boolean success;
        private String error;
    }
}
