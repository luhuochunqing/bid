package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserEnabledDetector - OSS 用户在职状态判定")
class UserEnabledDetectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("employeeStatus=3 应判定为在职")
    void isEnabled_employeeStatus3_returnsTrue() {
        JsonNode node = parseJson("{\"employeeStatus\": 3}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("employeeStatus=8 应判定为离职")
    void isEnabled_employeeStatus8_returnsFalse() {
        JsonNode node = parseJson("{\"employeeStatus\": 8}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("employeeStatus=1 应判定为待入职（未启用）")
    void isEnabled_employeeStatus1_returnsFalse() {
        JsonNode node = parseJson("{\"employeeStatus\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("employeeStatus=0 应 fallback 到其他条件")
    void isEnabled_employeeStatus0_fallsBack() {
        JsonNode node = parseJson("{\"employeeStatus\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("del=1 应判定为已删除")
    void isEnabled_del1_returnsFalse() {
        JsonNode node = parseJson("{\"del\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("del=0 不应阻止启用")
    void isEnabled_del0_allowsEnabled() {
        JsonNode node = parseJson("{\"del\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("activationState=1 应判定为已激活")
    void isEnabled_activationState1_returnsTrue() {
        JsonNode node = parseJson("{\"activationState\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("activationState=0 应判定为未激活")
    void isEnabled_activationState0_returnsFalse() {
        JsonNode node = parseJson("{\"activationState\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("status=1 应判定为在职")
    void isEnabled_status1_returnsTrue() {
        JsonNode node = parseJson("{\"status\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("status=0 应判定为离职")
    void isEnabled_status0_returnsFalse() {
        JsonNode node = parseJson("{\"status\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("status 字符串 \"1\" 应判定为在职（OSS 实测返回字符串形态）")
    void isEnabled_statusString1_returnsTrue() {
        JsonNode node = parseJson("{\"status\": \"1\"}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("status 字符串 \"0\" 应判定为离职")
    void isEnabled_statusString0_returnsFalse() {
        JsonNode node = parseJson("{\"status\": \"0\"}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("status 字符串 \"1\" 优先级高于 employeeStatus=8（字符串形态下 status 仍为最高优先级）")
    void isEnabled_statusString1_overridesEmployeeStatus8() {
        JsonNode node = parseJson("{\"employeeStatus\": 8, \"status\": \"1\"}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("del 字符串 \"1\" 应判定为已删除（del 也兼容字符串形态）")
    void isEnabled_delString1_returnsFalse() {
        JsonNode node = parseJson("{\"del\": \"1\", \"status\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("enabled=true 应直接返回 true")
    void isEnabled_enabledTrue_returnsTrue() {
        JsonNode node = parseJson("{\"enabled\": true}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("enabled=false 应直接返回 false")
    void isEnabled_enabledFalse_returnsFalse() {
        JsonNode node = parseJson("{\"enabled\": false}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("disabled=true 应返回 false")
    void isEnabled_disabledTrue_returnsFalse() {
        JsonNode node = parseJson("{\"disabled\": true}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("disabled=false 应返回 true")
    void isEnabled_disabledFalse_returnsTrue() {
        JsonNode node = parseJson("{\"disabled\": false}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("所有字段均缺失时应兜底返回 true")
    void isEnabled_emptyNode_returnsTrue() {
        JsonNode node = parseJson("{}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("status=0 优先级高于 employeeStatus=3（status 最高优先级）")
    void isEnabled_status0_overridesEmployeeStatus3() {
        JsonNode node = parseJson("{\"employeeStatus\": 3, \"status\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("status=1 优先级高于 employeeStatus=8（status 最高优先级）")
    void isEnabled_status1_overridesEmployeeStatus8() {
        JsonNode node = parseJson("{\"employeeStatus\": 8, \"status\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("del=1 优先级高于 status=1（del 仍为最高优先级）")
    void isEnabled_del1_overridesStatus1() {
        JsonNode node = parseJson("{\"del\": 1, \"status\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("del=1 优先级高于 status=0")
    void isEnabled_del1_overridesStatus0() {
        JsonNode node = parseJson("{\"del\": 1, \"status\": 0}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    @Test
    @DisplayName("status 字段缺失时 fallback 到 employeeStatus")
    void isEnabled_statusMissing_fallsBackToEmployeeStatus() {
        JsonNode node = parseJson("{\"employeeStatus\": 3}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("status 字段缺失时 fallback 到 activationState")
    void isEnabled_statusMissing_fallsBackToActivationState() {
        JsonNode node = parseJson("{\"activationState\": 1}");
        assertThat(UserEnabledDetector.isEnabled(node)).isTrue();
    }

    @Test
    @DisplayName("del=1 优先级高于 employeeStatus=3")
    void isEnabled_del1_overridesEmployeeStatus3() {
        JsonNode node = parseJson("{\"del\": 1, \"employeeStatus\": 3}");
        assertThat(UserEnabledDetector.isEnabled(node)).isFalse();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}