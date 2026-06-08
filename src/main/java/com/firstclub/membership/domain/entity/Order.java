package com.firstclub.membership.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "order")
    private List<OrderItem> items;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rawTotal;        // sum of item prices before any discount

    @Column(precision = 10, scale = 2)
    private BigDecimal discountApplied; // amount saved

    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryFee;     // 0 if FREE_DELIVERY benefit applied

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal finalTotal;      // rawTotal - discountApplied + deliveryFee

    private String couponCode;          // populated by EXCLUSIVE_COUPON benefit
    private boolean prioritySupport;    // set by PRIORITY_SUPPORT benefit

    @Column(nullable = false)
    private LocalDateTime orderDate;
}
