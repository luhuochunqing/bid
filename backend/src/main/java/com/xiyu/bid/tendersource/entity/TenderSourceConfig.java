package com.xiyu.bid.tendersource.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class TenderSourceConfig {

    /**
     * CO-469 第八轮 P1 审计：全仓 JSON 字段写入路径隐患统一治理。
     * 引入静态 ObjectMapper 替代手写字符串拼接，确保输出符合 RFC 8259 JSON 规范。
     * 原 toJsonArray 仅转义 \\ 和 \"，未转义 \\n / \\r / \\t 等控制字符，
     * 用户输入含换行时会触发 MySQL "Invalid JSON text" 错误（与 PersonnelImportTask 同类隐患）。
     * ObjectMapper 是线程安全（只读配置后），可在 static 上下文共享。
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

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
    public List<String> getPlatforms() {
        return parseJsonArray(platformsJson);
    }

    public void setPlatforms(List<String> platforms) {
        this.platformsJson = toJsonArray(platforms);
    }

    /**
     * 地区列表。
     */
    public List<String> getRegions() {
        return parseJsonArray(regionsJson);
    }

    public void setRegions(List<String> regions) {
        this.regionsJson = toJsonArray(regions);
    }

    /**
     * 事业部门列表（CO-469 第八轮 P1 审计：补全对称的写入入口，避免调用方直接 setBusinessUnitsJson(String) 绕过 Jackson）。
     */
    public List<String> getBusinessUnits() {
        return parseJsonArray(businessUnitsJson);
    }

    public void setBusinessUnits(List<String> businessUnits) {
        this.businessUnitsJson = toJsonArray(businessUnits);
    }

    /**
     * CO-469 第八轮 P1：用 Jackson 替代手写拼接，正确转义控制字符。
     * 旧实现仅转义 \\ 和 \"，遇到 \\n / \\r / \\t 等会写出非法 JSON，触发 MySQL "Invalid JSON text"。
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            List<String> result = JSON_MAPPER.readValue(json, STRING_LIST_TYPE);
            return result != null ? result : List.of();
        } catch (JsonProcessingException e) {
            log.debug("TenderSourceConfig: 反序列化 JSON 数组失败 (json={}): {}", json, e.getMessage());
            return List.of();
        }
    }

    /**
     * CO-469 第八轮 P1：用 Jackson 替代手写拼接，确保输出符合 RFC 8259 JSON 规范。
     * 异常时降级返回 "[]"，保证不抛 RuntimeException 中断业务流程（与 PersonnelImportTaskRepositoryAdapter.serializeErrorDetails 一致）。
     */
    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        try {
            return JSON_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.warn("TenderSourceConfig: 序列化 JSON 数组失败，降级返回 []: {}", e.getMessage());
            return "[]";
        }
    }
}
