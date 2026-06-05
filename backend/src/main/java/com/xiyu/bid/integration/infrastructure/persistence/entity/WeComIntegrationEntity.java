package com.xiyu.bid.integration.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity for wecom_integration table.
 * Single-row storage: fixed id = 1 (upsert semantics).
 */
@Entity
@Table(name = "wecom_integration")
@Getter
@Setter
@NoArgsConstructor
public class WeComIntegrationEntity {

    @Id
    private Long id;

    @Column(name = "corp_id", nullable = false, length = 64)
    private String corpId;

    @Column(name = "agent_id", nullable = false, length = 32)
    private String agentId;

    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "text")
    private String encryptedSecret;

    @Column(name = "sso_enabled", nullable = false)
    private boolean ssoEnabled;

    @Column(name = "message_enabled", nullable = false)
    private boolean messageEnabled;

    @Column(name = "notify_user_ids", length = 512)
    private String notifyUserIds;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}
