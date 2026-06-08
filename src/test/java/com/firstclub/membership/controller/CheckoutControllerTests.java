package com.firstclub.membership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.config.DataSeeder;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.dto.request.CartItem;
import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerTests {

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
                .name("Checkout Controller User")
                .email("checkoutc@test.com")
                .build());
    }

    @Test
    void calculate_Success() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Laptop");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        mockMvc.perform(post("/api/checkout/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawTotal").value(1000.00))
                .andExpect(jsonPath("$.finalTotal").value(1050.00))
                .andExpect(jsonPath("$.deliveryFee").value(50.00));
    }

    @Test
    void calculate_InvalidUser_ReturnsBadRequest() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(99999L);

        CartItem item = new CartItem();
        item.setProductName("Laptop");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        mockMvc.perform(post("/api/checkout/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void calculate_InvalidRequest_ReturnsBadRequest() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        // Items are empty
        req.setItems(List.of());

        mockMvc.perform(post("/api/checkout/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed: items Items cannot be empty"));
    }

    @Test
    void placeOrder_Success() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.setUserId(testUser.getId());
        req.setDeliveryFee(BigDecimal.valueOf(50.00));

        CartItem item = new CartItem();
        item.setProductName("Laptop");
        item.setCategory("Electronics");
        item.setUnitPrice(BigDecimal.valueOf(1000.00));
        item.setQuantity(1);
        req.setItems(List.of(item));

        mockMvc.perform(post("/api/checkout/place-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.finalTotal").value(1050.00));
    }
}
