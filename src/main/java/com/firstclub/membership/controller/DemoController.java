package com.firstclub.membership.controller;

import com.firstclub.membership.domain.entity.MembershipPlan;
import com.firstclub.membership.domain.entity.Subscription;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.CartItem;
import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.dto.response.ConcurrencyTestResult;
import com.firstclub.membership.dto.response.ConcurrencyTestResult.ThreadResult;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse;
import com.firstclub.membership.repository.MembershipPlanRepository;
import com.firstclub.membership.repository.SubscriptionRepository;
import com.firstclub.membership.service.CheckoutService;
import com.firstclub.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DemoController {

    private final CheckoutService checkoutService;
    private final MembershipService membershipService;
    private final SubscriptionRepository subscriptionRepository;
    private final MembershipPlanRepository planRepository;

    @Qualifier("virtualThreadExecutor")
    private final ExecutorService virtualThreadExecutor;

    @PostMapping("/concurrency-test")
    public ConcurrencyTestResult runConcurrencyTest(@RequestBody Map<String, Object> body) throws InterruptedException {
        Long userId = Long.valueOf(body.get("userId").toString());
        int threads = Integer.parseInt(body.getOrDefault("threads", 5).toString());

        // Auto-subscribe user to Monthly Basic if they don't have an active subscription
        Optional<Subscription> activeSub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (activeSub.isEmpty()) {
            try {
                MembershipPlan basicPlan = planRepository.findByName("Monthly Basic")
                        .orElseThrow(() -> new IllegalStateException("Monthly Basic plan not found"));
                SubscribeRequest subReq = new SubscribeRequest();
                subReq.setUserId(userId);
                subReq.setPlanId(basicPlan.getId());
                membershipService.subscribe(subReq);
                log.info("Auto-subscribed user {} to Monthly Basic for concurrency test", userId);
            } catch (Exception e) {
                log.error("Failed to auto-subscribe user {} for concurrency test", userId, e);
            }
        }

        // Reset optimistic lock counter in MembershipService
        membershipService.getAndResetOptimisticLockCount();

        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int threadNum = i;
            futures.add(virtualThreadExecutor.submit(() -> {
                Map<String, Object> threadResult = new HashMap<>();
                threadResult.put("threadId", "vthread-" + threadNum);
                try {
                    CheckoutRequest req = buildSyntheticOrder(userId, threadNum);
                    var resp = checkoutService.placeOrder(req);
                    threadResult.put("success", true);
                    threadResult.put("orderId", resp.getOrderId());
                } catch (Exception e) {
                    threadResult.put("success", false);
                    threadResult.put("error", e.getMessage());
                } finally {
                    latch.countDown();
                }
                return threadResult;
            }));
        }

        // Wait for all threads to complete (max 30 seconds)
        latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;

        List<ThreadResult> threadResults = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> res = future.get();
                boolean success = (boolean) res.get("success");
                if (success) {
                    successCount++;
                    orderIds.add((Long) res.get("orderId"));
                } else {
                    failureCount++;
                }

                threadResults.add(ThreadResult.builder()
                        .threadId((String) res.get("threadId"))
                        .success(success)
                        .error((String) res.get("error"))
                        .build());
            } catch (Exception e) {
                failureCount++;
                threadResults.add(ThreadResult.builder()
                        .threadId("unknown")
                        .success(false)
                        .error(e.getMessage())
                        .build());
            }
        }

        // Wait a short time to let the async virtual thread upgrades run/finish in background
        Thread.sleep(500);

        // Fetch final status
        String finalTierName = "NONE";
        try {
            SubscriptionStatusResponse finalStatus = membershipService.getStatus(userId);
            finalTierName = finalStatus.getTierName();
        } catch (Exception e) {
            log.warn("Could not fetch final status for user: {}", userId, e);
        }
        int lockCount = membershipService.getAndResetOptimisticLockCount();

        return ConcurrencyTestResult.builder()
                .userId(userId)
                .threadsLaunched(threads)
                .successCount(successCount)
                .failureCount(failureCount)
                .optimisticLockRetries(lockCount)
                .finalTierName(finalTierName)
                .orderIds(orderIds)
                .durationMs(duration)
                .threadResults(threadResults)
                .build();
    }

    private CheckoutRequest buildSyntheticOrder(Long userId, int threadNum) {
        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(userId);

        CartItem item = new CartItem();
        item.setProductName("Synthetic Item " + threadNum);
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00)); // spend ₹1000 per order
        item.setQuantity(1);

        request.setItems(List.of(item));
        request.setDeliveryFee(BigDecimal.valueOf(50.00));
        return request;
    }
}
