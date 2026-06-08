package com.firstclub.membership.domain.entity;

import com.firstclub.membership.domain.enums.BillingPeriod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "membership_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // "Monthly Basic", "Quarterly Plus", "Yearly Premium"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingPeriod billingPeriod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Which tier the user starts at when subscribing to this plan
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_tier_id", nullable = false)
    private MembershipTier defaultTier;
}
