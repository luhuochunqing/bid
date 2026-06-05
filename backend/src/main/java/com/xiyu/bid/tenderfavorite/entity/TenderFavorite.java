package com.xiyu.bid.tenderfavorite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标讯收藏实体
 * <p>记录用户对标讯的收藏关系。</p>
 */
@Entity
@Table(name = "tender_favorite", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_tender", columnNames = {"user_id", "tender_id"})
}, indexes = {
    @Index(name = "idx_tender_favorite_user", columnList = "user_id"),
    @Index(name = "idx_tender_favorite_tender", columnList = "tender_id"),
    @Index(name = "idx_tender_favorite_created", columnList = "user_id, created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 收藏用户ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 标讯ID */
    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    /** 收藏时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
