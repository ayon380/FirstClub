package com.firstclub.membership.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity //to tell its a Table in SQL database
@Table(name = "users") // name of table in SQL database
@Data // getters and setters
@NoArgsConstructor // no arguments constructor
@AllArgsConstructor // all arguments constructor
@Builder // builder pattern
public class User {
    @Id //primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) //auto increment
    private Long id; // ID of the user

    @Column(nullable = false)
    private String name; // Name of the user

    @Column(nullable = false, unique = true) // Unique email of the user
    private String email;

    // Cohort is a free-form string: "PREMIUM_COHORT", "EARLY_ADOPTER", etc.
    private String cohort;
}
