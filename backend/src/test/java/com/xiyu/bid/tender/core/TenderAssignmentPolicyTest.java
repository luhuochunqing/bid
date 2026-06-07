package com.xiyu.bid.tender.core;

import com.xiyu.bid.crm.domain.AssignmentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenderAssignmentPolicyTest {

    @Test
    @DisplayName("resolve(null) → noMatch()")
    void resolve_NullPurchaserName_ShouldReturnNoMatch() {
        AssignmentResult result = TenderAssignmentPolicy.resolve(null);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("resolve(空白字符串) → noMatch()")
    void resolve_BlankPurchaserName_ShouldReturnNoMatch() {
        AssignmentResult result1 = TenderAssignmentPolicy.resolve("");
        AssignmentResult result2 = TenderAssignmentPolicy.resolve("   ");

        assertThat(result1.isMatched()).isFalse();
        assertThat(result2.isMatched()).isFalse();
    }

    @Test
    @DisplayName("resolve(有效名称) → noMatch()（纯核心不做实际匹配）")
    void resolve_ValidPurchaserName_ShouldReturnNoMatchInPureCore() {
        // 纯核心不做实际查询，调用方负责业务逻辑
        AssignmentResult result = TenderAssignmentPolicy.resolve("上海西域采购中心");

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("AssignmentResult.success - 包含负责人信息")
    void assignmentResult_Success_ShouldContainManagerInfo() {
        AssignmentResult result = AssignmentResult.success(
                "CRM-001", "PM-001", "张三", "DEPT-001", "销售部");

        assertThat(result.isMatched()).isTrue();
        assertThat(result.crmProjectId()).isEqualTo("CRM-001");
        assertThat(result.projectManagerId()).isEqualTo("PM-001");
        assertThat(result.projectManagerName()).isEqualTo("张三");
        assertThat(result.departmentId()).isEqualTo("DEPT-001");
        assertThat(result.departmentName()).isEqualTo("销售部");
    }

    @Test
    @DisplayName("AssignmentResult.noMatch - 所有字段为空")
    void assignmentResult_NoMatch_ShouldHaveNullFields() {
        AssignmentResult result = AssignmentResult.noMatch();

        assertThat(result.isMatched()).isFalse();
        assertThat(result.crmProjectId()).isNull();
        assertThat(result.projectManagerId()).isNull();
        assertThat(result.projectManagerName()).isNull();
        assertThat(result.departmentId()).isNull();
        assertThat(result.departmentName()).isNull();
    }
}
