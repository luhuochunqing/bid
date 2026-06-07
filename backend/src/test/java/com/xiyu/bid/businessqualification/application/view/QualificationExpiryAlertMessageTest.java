// Input: 蓝图 §4.1.3.8 消息模板（标题 + 9 项正文）
// Output: QualificationExpiryAlertMessage 纯函数单测
// Pos: test/java/.../businessqualification/application/view - 模板单测
// 维护声明: 模板文案严格对齐蓝图，不引入文案外行为.
package com.xiyu.bid.businessqualification.application.view;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
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

class QualificationExpiryAlertMessageTest {

    @Test
    @DisplayName("标题应包含证书名 + 剩余天数 + 【资质到期提醒】前缀")
    void title_ShouldFollowBlueprintFormat() {
        LocalDate today = LocalDate.of(2026, 6, 7);
        BusinessQualification q = sample(today, 30);
        QualificationExpiryAlertMessage msg =
                QualificationExpiryAlertMessage.from(q, 30, "甲级", "/x/qual?id=1");

        assertThat(msg.title()).isEqualTo("【资质到期提醒】《测试证书 ABC》还有 30 天到期");
    }

    @Test
    @DisplayName("正文必须包含 9 项字段（①-⑨）")
    void body_ShouldContainAllNineFields() {
        LocalDate today = LocalDate.of(2026, 6, 7);
        BusinessQualification q = sample(today, 7);
        QualificationExpiryAlertMessage msg =
                QualificationExpiryAlertMessage.from(q, 7, "乙级", "/x/qual?id=1");

        assertThat(msg.body())
                .contains("① 证书名称：测试证书 ABC")
                .contains("② 证书号：CN-2024-001")
                .contains("③ 等级：乙级")
                .contains("④ 认证机构：国家计量局")
                .contains("⑤ 代理机构：中兴代理")
                .contains("⑥ 代理联系方式：13800000000")
                .contains("⑦ 有效期至：2026-06-14")
                .contains("⑧ 剩余天数：7 天")
                .contains("⑨ 跳转详情：/x/qual?id=1");
    }

    @Test
    @DisplayName("缺字段时使用占位符「—」")
    void body_MissingOptionalFields_ShouldUsePlaceholder() {
        BusinessQualification q = BusinessQualification.create(
                1L, "无名", QualificationSubject.of(QualificationSubjectType.COMPANY, "测试公司"),
                QualificationCategory.OTHER, "", null, null, null, null, null, null,
                new ValidityPeriod(null, LocalDate.of(2026, 7, 7)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.AVAILABLE,
                null, null, null, null, null, null, List.of()
        );
        QualificationExpiryAlertMessage msg =
                QualificationExpiryAlertMessage.from(q, 30, null, null);

        assertThat(msg.body())
                .contains("② 证书号：—")
                .contains("③ 等级：—")
                .contains("④ 认证机构：—")
                .contains("⑤ 代理机构：—")
                .contains("⑥ 代理联系方式：—");
        // 无 detailUrl 时使用默认 link
        assertThat(msg.body()).contains("⑨ 跳转详情：/knowledge/qualification?id=1");
    }

    @Test
    @DisplayName("qualification 为空时拒绝构造（避免 NPE 隐式扩散）")
    void from_NullQualification_ShouldThrow() {
        assertThatThrownBy(() -> QualificationExpiryAlertMessage.from(null, 30, "甲级", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("默认 link 拼接正确：/knowledge/qualification?id={id}")
    void buildDefaultLink_ShouldJoinId() {
        assertThat(QualificationExpiryAlertMessage.buildDefaultLink(42L))
                .isEqualTo("/knowledge/qualification?id=42");
    }

    @Test
    @DisplayName("默认 link null id：返回基础路径")
    void buildDefaultLink_NullId_ShouldReturnBasePath() {
        assertThat(QualificationExpiryAlertMessage.buildDefaultLink(null))
                .isEqualTo("/knowledge/qualification");
    }

    private BusinessQualification sample(LocalDate today, long remainingDays) {
        return BusinessQualification.create(
                1L, "测试证书 ABC",
                QualificationSubject.of(QualificationSubjectType.COMPANY, "测试公司"),
                QualificationCategory.OTHER, "CN-2024-001", "国家计量局",
                "中兴代理", "13800000000", null, null, "测试持有人",
                new ValidityPeriod(LocalDate.of(2024, 1, 1), today.plusDays(remainingDays)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.AVAILABLE,
                null, null, null, null, null, null, List.of()
        );
    }
}
