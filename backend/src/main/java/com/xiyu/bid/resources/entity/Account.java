package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(length = 500)
    private String contactInfo;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditLevel creditLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountType {
        CLIENT,
        SUPPLIER,
        PARTNER,
        GOVERNMENT,
        OTHER
    }

    public enum CreditLevel {
        A,
        B,
        C,
        D
    }
}
