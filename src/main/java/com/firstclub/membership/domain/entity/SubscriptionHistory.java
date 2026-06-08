package com.firstclub.membership.domain.entity;

import com.firstclub.membership.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.EAGER)
    private MembershipTier fromTier;   // null on initial subscribe

    @ManyToOne(fetch = FetchType.EAGER)
    private MembershipTier toTier;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus toStatus;

    private String changeReason;       // "MANUAL_UPGRADE", "AUTO_UPGRADE", "CANCEL", "SUBSCRIBE"

    @Column(nullable = false)
    private LocalDateTime changedAt;
}
