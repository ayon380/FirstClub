package com.firstclub.membership.domain.entity;

import com.firstclub.membership.domain.enums.RuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleConfig {
    private RuleType type;
    private Map<String, Object> params;
}
