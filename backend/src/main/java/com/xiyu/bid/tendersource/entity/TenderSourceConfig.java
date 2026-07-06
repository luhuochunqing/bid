package com.xiyu.bid.tendersource.entity;

import com.xiyu.bid.common.util.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯源配置实体。
 * 单例模式（id 始终为 1），整个团队共享一份生效配置。
 * FR-015 ~ FR-018
 *
 * <p>CO-469 第八轮 P1 审计后重构：JSON 字段统一使用 JPA AttributeConverter，
 * 业务代码直接操作 List<String>，无需关心序列化细节。
 * 替代原手写 toJsonArray / parseJsonArray 私有方法，消除重复造轮子。
 */
@Entity
@Table(name = "tender_source_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSourceConfig {

    @Id
    private Long id;

    /**
     * 平台列表（JSON 数组存储）。
     * 使用 StringListJsonConverter 自动序列化/反序列化。
     */
    @Column(name = "platforms_json", columnDefinition = "JSON")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> platforms = List.of();

    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @Column(name = "api_key_encrypted", length = 512)
    private String apiKeyEncrypted;

    @Column(length = 500)
    private String keywords;

    /**
     * 地区列表（JSON 数组存储）。
     */
    @Column(name = "regions_json", columnDefinition = "JSON")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> regions = List.of();

    /**
     * 事业部门列表（JSON 数组存储）。
     */
    @Column(name = "business_units_json", columnDefinition = "JSON")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> businessUnits = List.of();

    @Column(name = "budget_min", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal budgetMin = BigDecimal.ZERO;

    @Column(name = "budget_max", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal budgetMax = new BigDecimal("1000");

    @Column(name = "auto_sync", nullable = false)
    @Builder.Default
    private Boolean autoSync = false;

    @Column(name = "sync_interval_minutes", nullable = false)
    @Builder.Default
    private Integer syncIntervalMinutes = 1440;

    @Column(name = "auto_dedupe", nullable = false)
    @Builder.Default
    private Boolean autoDedupe = true;

    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
