package com.xiyu.bid.formengine.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-601 US2：项目三 scope schema key 冲突纯函数校验（契约 §5）。
 *
 * <p>语义边界（contracts/project-custom-fields-api.md §5 L71）：
 * <ul>
 *   <li>hybrid scope（project.initiation / project.detail）：预置字段由业务页 fallback 硬编码渲染，
 *       schema 不应含预置 key —— 命中即拒绝；</li>
 *   <li>project.basic（纯 schema 渲染，V140 种子含 8 个预置字段）：预置字段合法存在，
 *       仅校验 key 重复（新增撞预置 key 必然产生重复 key，已被重复校验覆盖）——避免老 schema 重保存被误杀；</li>
 *   <li>非项目 scope：不校验，直接放行。</li>
 * </ul>
 */
class CustomFieldsSchemaPolicyTest {

    private static Map<String, Object> field(String key) {
        return Map.of("key", key, "label", key + "-label", "type", "text");
    }

    @Test
    void nonProjectScope_shouldSkipValidation() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "tender.entry", List.of(field("name"), field("name")));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void hybridScope_presetKeyCollision_shouldReject() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.initiation", List.of(field("projectName")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("projectName"));
    }

    @Test
    void hybridScope_detailPresetKeyCollision_shouldReject() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.detail", List.of(field("description"), field("customNote")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("description"));
    }

    @Test
    void customKeyDuplicated_shouldReject() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.initiation", List.of(field("customA"), field("customA")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("customA"));
    }

    @Test
    void basicScope_presetFieldsPresent_shouldPass() {
        // V140 种子形态：8 个预置字段全部在 schema（纯 schema 渲染依赖），不得误杀
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.basic",
                List.of(field("name"), field("customer"), field("budget"), field("industry"),
                        field("region"), field("platform"), field("deadline"), field("manager"),
                        field("competitors"), field("budgetLevel")));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void basicScope_duplicatedKey_shouldReject() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.basic", List.of(field("name"), field("name")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("name"));
    }

    @Test
    void hybridScope_pureCustomFields_shouldPass() {
        ValidationResult result = CustomFieldsSchemaPolicy.validate(
                "project.initiation", List.of(field("customA"), field("customB")));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void emptyOrNullFields_shouldPass() {
        assertThat(CustomFieldsSchemaPolicy.validate("project.initiation", List.of()).valid()).isTrue();
        assertThat(CustomFieldsSchemaPolicy.validate("project.detail", null).valid()).isTrue();
    }
}
