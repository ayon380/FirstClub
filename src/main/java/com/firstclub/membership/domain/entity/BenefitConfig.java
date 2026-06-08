package com.firstclub.membership.domain.entity;

import com.firstclub.membership.domain.enums.BenefitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitConfig {
    private BenefitType type;
    private Map<String, Object> params;
}
