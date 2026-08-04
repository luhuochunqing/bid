package com.xiyu.bid.performance.controller.dto;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExportTaskResponse} 单元测试。
 *
 * @since CO-602 PR 设计评估修复（D1-2）
 */
class ExportTaskResponseTest {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void from_完整实体_所有字段正确映射() {
        PerformanceExportTaskEntity entity = PerformanceExportTaskEntity.builder()
                .id(100L)
                .status(PerformanceExportTaskEntity.ExportStatus.COMPLETED)
                .totalCount(50)
                .downloadUrl("/api/download/100")
                .expiresAt(LocalDateTime.of(2026, 8, 4, 12, 0, 0))
                .createdAt(LocalDateTime.of(2026, 8, 4, 11, 30, 0))
                .completedAt(LocalDateTime.of(2026, 8, 4, 11, 35, 0))
                .failureReason("")
                .resultSummary("{\"wordBytes\":1024,\"elapsedMs\":30000}")
                .build();

        ExportTaskResponse resp = ExportTaskResponse.from(entity, this::parseJson, DT_FMT);

        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(resp.totalCount()).isEqualTo(50);
        assertThat(resp.downloadUrl()).isEqualTo("/api/download/100");
        assertThat(resp.expiresAt()).isEqualTo("2026-08-04 12:00:00");
        assertThat(resp.createdAt()).isEqualTo("2026-08-04 11:30:00");
        assertThat(resp.completedAt()).isEqualTo("2026-08-04 11:35:00");
        assertThat(resp.failureReason()).isEqualTo("");
        assertThat(resp.resultSummary()).containsEntry("wordBytes", 1024);
    }

    @Test
    void from_null字段_使用默认值() {
        PerformanceExportTaskEntity entity = PerformanceExportTaskEntity.builder()
                .id(1L)
                .status(PerformanceExportTaskEntity.ExportStatus.PENDING)
                .totalCount(null)
                .downloadUrl(null)
                .expiresAt(null)
                .createdAt(LocalDateTime.of(2026, 8, 4, 10, 0, 0))
                .completedAt(null)
                .failureReason(null)
                .resultSummary(null)
                .build();

        ExportTaskResponse resp = ExportTaskResponse.from(entity, this::parseJson, DT_FMT);

        assertThat(resp.totalCount()).isEqualTo(0);
        assertThat(resp.downloadUrl()).isEqualTo("");
        assertThat(resp.expiresAt()).isNull();
        assertThat(resp.completedAt()).isNull();
        assertThat(resp.failureReason()).isEqualTo("");
        assertThat(resp.resultSummary()).isEmpty();
    }

    @Test
    void from_FAILED状态_包含failureReason() {
        PerformanceExportTaskEntity entity = PerformanceExportTaskEntity.builder()
                .id(2L)
                .status(PerformanceExportTaskEntity.ExportStatus.FAILED)
                .totalCount(0)
                .createdAt(LocalDateTime.of(2026, 8, 4, 10, 0, 0))
                .completedAt(LocalDateTime.of(2026, 8, 4, 10, 1, 0))
                .failureReason("记录数超过上限")
                .build();

        ExportTaskResponse resp = ExportTaskResponse.from(entity, this::parseJson, DT_FMT);

        assertThat(resp.status()).isEqualTo("FAILED");
        assertThat(resp.failureReason()).isEqualTo("记录数超过上限");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        // 简化解析：测试场景下使用固定数据
        if (json.contains("wordBytes")) {
            return Map.of("wordBytes", 1024, "elapsedMs", 30000);
        }
        return Map.of();
    }
}
