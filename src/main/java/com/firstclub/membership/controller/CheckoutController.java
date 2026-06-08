package com.firstclub.membership.controller;

import com.firstclub.membership.dto.request.CheckoutRequest;
import com.firstclub.membership.dto.response.OrderSummaryResponse;
import com.firstclub.membership.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/calculate")
    public OrderSummaryResponse calculate(@Valid @RequestBody CheckoutRequest request) {
        return checkoutService.calculate(request);
    }

    @PostMapping("/place-order")
    public OrderSummaryResponse placeOrder(@Valid @RequestBody CheckoutRequest request) {
        return checkoutService.placeOrder(request);
    }
}
