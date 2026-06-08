package com.firstclub.membership.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeTierRequest {
    @NotNull(message = "Tier ID is required")
    private Long tierId;
}
