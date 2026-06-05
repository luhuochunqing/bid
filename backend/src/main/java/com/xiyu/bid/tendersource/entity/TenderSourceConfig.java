package com.xiyu.bid.tendersource.entity;

import jakarta.persistence.Column;
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

/**
 * 标讯源配置实体。
 * 单例模式（id 始终为 1），整个团队共享一份生效配置。
 * FR-015 ~ FR-018
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

    @Column(name = "platforms_json", columnDefinition = "JSON")
    private String platformsJson;

    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @Column(name = "api_key_encrypted", length = 512)
    private String apiKeyEncrypted;

    @Column(length = 500)
    private String keywords;

    @Column(name = "regions_json", columnDefinition = "JSON")
    private String regionsJson;

    @Column(name = "business_units_json", columnDefinition = "JSON")
    private String businessUnitsJson;

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

    /**
     * 平台列表。
     */
    public java.util.List<String> getPlatforms() {
        return parseJsonArray(platformsJson);
    }

    public void setPlatforms(java.util.List<String> platforms) {
        this.platformsJson = toJsonArray(platforms);
    }

    /**
     * 地区列表。
     */
    public java.util.List<String> getRegions() {
        return parseJsonArray(regionsJson);
    }

    public void setRegions(java.util.List<String> regions) {
        this.regionsJson = toJsonArray(regions);
    }

    private java.util.List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return java.util.List.of();
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                return java.util.Arrays.stream(inner.split(","))
                        .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } catch (Exception ignored) {
        }
        return java.util.List.of();
    }

    private String toJsonArray(java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        return items.stream()
                .map(s -> "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
