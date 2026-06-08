package com.firstclub.membership.dto.response;

import com.firstclub.membership.domain.entity.BenefitConfig;
import com.firstclub.membership.domain.entity.RuleConfig;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TierResponse {
    private Long id;
    private String name;
    private int priority;
    private List<BenefitConfig> benefits;
    private List<RuleConfig> upgradeRules;
}
