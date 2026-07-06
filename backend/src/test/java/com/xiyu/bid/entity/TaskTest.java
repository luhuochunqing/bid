// Input: Task entity @PrePersist/@PreUpdate lifecycle callback logic
// Output: TaskTest — unit tests for normalizeExtendedFieldsJson null/blank normalization
// Pos: Test/Entity lifecycle hooks — data quality defense
package com.xiyu.bid.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task entity lifecycle hooks tests.
 * <p>
 * Covers Sentry XIYU-P regression defense: empty strings in extended_fields_json
 * cause MySQL JSON_EXTRACT to throw "Invalid JSON text: The document is empty".
 * Task.normalizeExtendedFieldsJson() normalizes blank strings to NULL at write time.
 */
@DisplayName("Task entity lifecycle hooks")
class TaskTest {

    @Nested
    @DisplayName("normalizeExtendedFieldsJson — 写端空字符串防御")
    class NormalizeExtendedFieldsJson {

        @Test
        @DisplayName("空字符串应被规范化为 NULL")
        void emptyString_normalizedToNull() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("")
                    .build();

            // Trigger @PrePersist lifecycle callback
            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("空白字符串（仅含空格）应被规范化为 NULL")
        void blankString_normalizedToNull() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("   ")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("有效 JSON 字符串应保持不变")
        void validJson_preserved() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("{\"depositAmount\":5000}")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isEqualTo("{\"depositAmount\":5000}");
        }

        @Test
        @DisplayName("NULL 值应保持不变")
        void nullValue_preserved() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson(null)
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("空白 JSON 对象 '{}' 应保持不变（不是空字符串）")
        void emptyJsonObject_preserved() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("{}")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isEqualTo("{}");
        }

        @Test
        @DisplayName("空白 JSON 数组 '[]' 应保持不变")
        void emptyJsonArray_preserved() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("[]")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isEqualTo("[]");
        }

        @Test
        @DisplayName("含制表符的空白字符串应被规范化为 NULL")
        void whitespaceWithTabs_normalizedToNull() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("\t\t")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("含换行符的空白字符串应被规范化为 NULL")
        void whitespaceWithNewlines_normalizedToNull() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("\n\n")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("混合空白字符应被规范化为 NULL")
        void mixedWhitespace_normalizedToNull() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("  \t\n  ")
                    .build();

            task.onCreate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }

        @Test
        @DisplayName("@PreUpdate 也应触发规范化")
        void onUpdate_triggersNormalization() {
            Task task = Task.builder()
                    .projectId(1L)
                    .title("Test task")
                    .extendedFieldsJson("")
                    .build();

            // Simulate update operation
            task.onUpdate();

            assertThat(task.getExtendedFieldsJson()).isNull();
        }
    }
}