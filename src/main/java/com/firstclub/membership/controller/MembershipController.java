package com.firstclub.membership.controller;

import com.firstclub.membership.dto.request.ChangeTierRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.dto.response.PlanResponse;
import com.firstclub.membership.dto.response.SubscriptionStatusResponse;
import com.firstclub.membership.dto.response.TierResponse;
import com.firstclub.membership.repository.MembershipPlanRepository;
import com.firstclub.membership.repository.MembershipTierRepository;
import com.firstclub.membership.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipPlanRepository planRepository;
    private final MembershipTierRepository tierRepository;

    @GetMapping("/plans")
    public List<PlanResponse> getPlans() {
        return planRepository.findAll().stream()
                .map(p -> PlanResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .billingPeriod(p.getBillingPeriod().name())
                        .price(p.getPrice())
                        .defaultTierName(p.getDefaultTier().getName())
                        .build())
                .collect(Collectors.toList());
    }

    @GetMapping("/tiers")
    public List<TierResponse> getTiers() {
        return tierRepository.findAllByOrderByPriorityDesc().stream()
                .map(t -> TierResponse.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .priority(t.getPriority())
                        .benefits(t.getBenefits())
                        .upgradeRules(t.getUpgradeRules())
                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionStatusResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        return membershipService.subscribe(request);
    }

    @PatchMapping("/{userId}/tier")
    public SubscriptionStatusResponse changeTier(@PathVariable Long userId, @Valid @RequestBody ChangeTierRequest request) {
        return membershipService.changeTier(userId, request);
    }

    @DeleteMapping("/{userId}")
    public SubscriptionStatusResponse cancel(@PathVariable Long userId) {
        return membershipService.cancel(userId);
    }

    @GetMapping("/{userId}/status")
    public SubscriptionStatusResponse getStatus(@PathVariable Long userId) {
        return membershipService.getStatus(userId);
    }
}
