package com.firstclub.membership.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<CartItem> items;

    private BigDecimal deliveryFee; // default fee from UI, e.g. 50.00
}
