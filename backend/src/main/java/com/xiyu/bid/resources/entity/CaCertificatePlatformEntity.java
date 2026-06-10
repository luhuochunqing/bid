package com.xiyu.bid.resources.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ca_certificate_platforms", indexes = {
    @Index(name = "idx_cap_ca_id", columnList = "ca_certificate_id"),
    @Index(name = "idx_cap_platform_id", columnList = "platform_account_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_cap_ca_platform",
        columnNames = {"ca_certificate_id", "platform_account_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CaCertificatePlatformEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ca_certificate_id", nullable = false)
    private Long caCertificateId;

    @Column(name = "platform_account_id", nullable = false)
    private Long platformAccountId;
}
