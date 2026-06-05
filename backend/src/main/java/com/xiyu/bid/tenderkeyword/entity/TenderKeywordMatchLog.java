// Input: JPA mapping for tender_keyword_match_log table
// Output: TenderKeywordMatchLog entity
// Pos: Entity/标讯关键词匹配日志实体
package com.xiyu.bid.tenderkeyword.entity;

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

/**
 * 标讯关键词匹配日志实体
 */
@Entity
@Table(name = "tender_keyword_match_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderKeywordMatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "tender_title", nullable = false, length = 500)
    private String tenderTitle;

    @Column(name = "matched_keywords", length = 1000)
    private String matchedKeywords;

    @Column(name = "notified", nullable = false)
    @Builder.Default
    private Boolean notified = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (notified == null) {
            notified = false;
        }
    }
}
