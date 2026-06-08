package com.firstclub.membership.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "membership_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // "SILVER", "GOLD", "PLATINUM"

    // Higher priority = higher tier. SILVER=1, GOLD=2, PLATINUM=3
    @Column(nullable = false)
    private int priority;

    // Stored as JSON array: [{"type":"PERCENTAGE_DISCOUNT","params":{"discountPercent":5}}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<BenefitConfig> benefits;

    // ALL rules must pass for upgrade to this tier
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<RuleConfig> upgradeRules;
}
