// Input: JPA mapping for tender_keyword_subscription_keyword table
// Output: TenderKeywordSubscriptionKeyword entity
// Pos: Entity/订阅关键词实体
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
 * 订阅关键词实体
 */
@Entity
@Table(name = "tender_keyword_subscription_keyword")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderKeywordSubscriptionKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
