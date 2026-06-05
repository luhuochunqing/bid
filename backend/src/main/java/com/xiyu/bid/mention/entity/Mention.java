package com.xiyu.bid.mention.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mention")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "mentioner_user_id", nullable = false)
    private Long mentionerUserId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;

    @Column(name = "source_entity_type", length = 50)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
