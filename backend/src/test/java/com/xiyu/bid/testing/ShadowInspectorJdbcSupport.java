package com.xiyu.bid.testing;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

final class ShadowInspectorJdbcSupport {
    private static final int AUDIT_POLL_INTERVAL_MS = 50;
    private static final int AUDIT_POLL_MAX_ATTEMPTS = 100;

    private ShadowInspectorJdbcSupport() {}

    static <T> T queryDatabase(
            JdbcTemplate jdbcTemplate,
            String tableName,
            Long entityId,
            Function<Map<String, Object>, T> extractor) {

        ShadowInspectorSchemaRegistry.validateTable(tableName);
        String sql = String.format("SELECT * FROM %s WHERE id = ?", tableName);
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, entityId);
        return extractor.apply(result);
    }

    static Integer waitForAuditCount(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        Integer count = 0;
        for (int i = 0; i < AUDIT_POLL_MAX_ATTEMPTS; i++) {
            count = jdbcTemplate.queryForObject(sql, Integer.class, args);
            if (count != null && count > 0) {
                return count;
            }
            try {
                Thread.sleep(AUDIT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return count;
    }

    static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("Unsupported timestamp type: " + value.getClass().getName());
    }
}
