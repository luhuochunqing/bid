// Input: BusinessQualification + remainingDays + level + detailUrl
// Output: CO-532 QualificationReviewAlertMessage 模板单测
// Pos: test/java/.../application/view - 消息模板单测
// 维护声明: 覆盖正常构建、null 入参、空名兜底、链接构建.
package com.xiyu.bid.businessqualification.application.view;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualificationReviewAlertMessageTest {

    @Test
    @DisplayName("from - 正常构建：标题与正文包含审核提醒字段")
    void from_NormalBuild_ShouldContainReviewFields() {
        BusinessQualification q = sample("测试证书 XYZ", LocalDate.of(2026, 10, 5));
        QualificationReviewAlertMessage msg = QualificationReviewAlertMessage.from(q, 30L, "甲级", null);

        assertThat(msg.title()).contains("【证书审核提醒】").contains("测试证书 XYZ").contains("30 天");
        assertThat(msg.body())
                .contains("① 证书名称：测试证书 XYZ")
                .contains("② 证书号：CN-2024-001")
                .contains("③ 等级：甲级")
                .contains("④ 认证机构：国家计量局")
                .contains("⑤ 代理机构：中兴代理")
                .contains("⑥ 代理机构联系人：13800000000")
                .contains("⑦ 审核提醒日期：2026-10-05")
                .contains("⑧ 剩余天数：30 天")
                .contains("⑨ 跳转详情：/knowledge/qualification?id=1");
    }

    @Test
    @DisplayName("from - qualification 为 null：抛 IllegalArgumentException")
    void from_NullQualification_ShouldThrow() {
        assertThatThrownBy(() -> QualificationReviewAlertMessage.from(null, 30L, "甲级", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("qualification must not be null");
    }

    @Test
    @DisplayName("from - 证书名为空：兜底为 (未命名证书)")
    void from_BlankName_ShouldFallback() {
        BusinessQualification q = sample("", LocalDate.of(2026, 10, 5));
        QualificationReviewAlertMessage msg = QualificationReviewAlertMessage.from(q, 30L, null, null);
        assertThat(msg.title()).contains("(未命名证书)");
        assertThat(msg.body()).contains("③ 等级：—");
    }

    @Test
    @DisplayName("buildDefaultLink - id 非 null：返回带 id 的链接")
    void buildDefaultLink_WithId_ShouldReturnIdLink() {
        assertThat(QualificationReviewAlertMessage.buildDefaultLink(42L))
                .isEqualTo("/knowledge/qualification?id=42");
    }

    @Test
    @DisplayName("buildDefaultLink - id 为 null：返回通用链接")
    void buildDefaultLink_NullId_ShouldReturnGenericLink() {
        assertThat(QualificationReviewAlertMessage.buildDefaultLink(null))
                .isEqualTo("/knowledge/qualification");
    }

    private BusinessQualification sample(String name, LocalDate reviewDate) {
        QualificationSubject subject = QualificationSubject.of(QualificationSubjectType.COMPANY, "测试公司");
        ValidityPeriod validity = new ValidityPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1));
        ReminderPolicy policy = new ReminderPolicy(true, 30, null);
        return BusinessQualification.create(
                1L, name, "甲级", subject, QualificationCategory.OTHER,
                "CN-2024-001", "国家计量局", "中兴代理", "13800000000",
                "测试范围", reviewDate, "持有人",
                validity, policy,
                null, null, null, List.of()
        );
    }
}
