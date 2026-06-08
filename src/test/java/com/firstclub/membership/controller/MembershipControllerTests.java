package com.firstclub.membership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.domain.entity.*;
import com.firstclub.membership.domain.enums.SubscriptionStatus;
import com.firstclub.membership.dto.request.ChangeTierRequest;
import com.firstclub.membership.dto.request.SubscribeRequest;
import com.firstclub.membership.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MembershipControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private MembershipTierRepository tierRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionHistoryRepository subscriptionHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSeeder dataSeeder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private MembershipPlan monthlyPlan;
    private MembershipTier goldTier;
    private MembershipTier silverTier;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionHistoryRepository.deleteAll();
        subscriptionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        planRepository.deleteAll();
        tierRepository.deleteAll();

        dataSeeder.run();

        testUser = userRepository.save(User.builder()
                .name("Membership controller User")
                .email("mcontroller@test.com")
                .build());

        monthlyPlan = planRepository.findByName("Monthly Basic").orElseThrow();
        goldTier = tierRepository.findByName("GOLD").orElseThrow();
        silverTier = tierRepository.findByName("SILVER").orElseThrow();
    }

    @Test
    void getPlans_Success() throws Exception {
        mockMvc.perform(get("/api/memberships/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Monthly Basic"))
                .andExpect(jsonPath("$[0].price").exists());
    }

    @Test
    void getTiers_Success() throws Exception {
        mockMvc.perform(get("/api/memberships/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("PLATINUM"))
                .andExpect(jsonPath("$[1].name").value("GOLD"))
                .andExpect(jsonPath("$[2].name").value("SILVER"));
    }

    @Test
    void subscribe_Success() throws Exception {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());

        mockMvc.perform(post("/api/memberships/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tierName").value("SILVER"))
                .andExpect(jsonPath("$.planName").value("Monthly Basic"));
    }

    @Test
    void subscribe_Twice_ReturnsConflict() throws Exception {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(testUser.getId());
        req.setPlanId(monthlyPlan.getId());

        mockMvc.perform(post("/api/memberships/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/memberships/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User already has an active subscription"));
    }

    @Test
    void subscribe_NonExistentUser_ReturnsBadRequest() throws Exception {
        SubscribeRequest req = new SubscribeRequest();
        req.setUserId(99999L);
        req.setPlanId(monthlyPlan.getId());

        mockMvc.perform(post("/api/memberships/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void changeTier_Success() throws Exception {
        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        ChangeTierRequest changeReq = new ChangeTierRequest();
        changeReq.setTierId(goldTier.getId());

        mockMvc.perform(patch("/api/memberships/" + testUser.getId() + "/tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierName").value("GOLD"));
    }

    @Test
    void changeTier_SameTier_ReturnsBadRequest() throws Exception {
        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        ChangeTierRequest changeReq = new ChangeTierRequest();
        changeReq.setTierId(silverTier.getId());

        mockMvc.perform(patch("/api/memberships/" + testUser.getId() + "/tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Already on tier: SILVER"));
    }

    @Test
    void changeTier_NoActiveSubscription_ReturnsNotFound() throws Exception {
        ChangeTierRequest changeReq = new ChangeTierRequest();
        changeReq.setTierId(goldTier.getId());

        mockMvc.perform(patch("/api/memberships/" + testUser.getId() + "/tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No active subscription found for user: " + testUser.getId()));
    }

    @Test
    void cancel_Success() throws Exception {
        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        mockMvc.perform(delete("/api/memberships/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_NoActiveSubscription_ReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/memberships/" + testUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No active subscription found for user: " + testUser.getId()));
    }

    @Test
    void getStatus_Success() throws Exception {
        subscriptionRepository.save(Subscription.builder()
                .user(testUser)
                .plan(monthlyPlan)
                .tier(silverTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(1))
                .build());

        mockMvc.perform(get("/api/memberships/" + testUser.getId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planName").value("Monthly Basic"));
    }

    @Test
    void getStatus_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/memberships/99999/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No subscription found for user: 99999"));
    }
}
