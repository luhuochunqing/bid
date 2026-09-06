// Input: 各种招标资质等级描述与企业资质证书
// Output: 验证 QualificationGradePolicy 等级提取、层级比较与兼容性判定
// Pos: compliance/domain/policy/ - 资质等级层级策略纯核心测试
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationGradePolicyTest {

    @Test
    @DisplayName("工程施工体系：高等级满足低等级要求（二级及以上，持有特级/一级）")
    void testConstruction_HigherGradeSatisfiesLowerRequirement() {
        String req = "投标人须具备建筑机电安装工程专业承包二级及以上资质";

        // 持有一级，满足
        QualificationGradePolicy.GradeAssessment a1 =
                QualificationGradePolicy.assessGrade(req, "建筑机电安装工程专业承包一级");
        assertThat(a1.compatible()).isTrue();
        assertThat(a1.remark()).contains("满足");

        // 持有特级，满足
        QualificationGradePolicy.GradeAssessment a2 =
                QualificationGradePolicy.assessGrade(req, "建筑机电安装工程专业承包特级");
        assertThat(a2.compatible()).isTrue();
        assertThat(a2.remark()).contains("满足");

        // 持有同级（二级），满足
        QualificationGradePolicy.GradeAssessment a3 =
                QualificationGradePolicy.assessGrade(req, "建筑机电安装工程专业承包二级");
        assertThat(a3.compatible()).isTrue();
    }

    @Test
    @DisplayName("工程施工体系：低等级不满足高等级要求（要求二级及以上，持有三级 -> 等级不足）")
    void testConstruction_LowerGradeFailsRequirement() {
        String req = "投标人须具备建筑机电安装工程专业承包二级及以上资质";
        String cert = "建筑机电安装工程专业承包三级";

        QualificationGradePolicy.GradeAssessment assessment =
                QualificationGradePolicy.assessGrade(req, cert);
        assertThat(assessment.compatible()).isFalse();
        assertThat(assessment.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("工程施工体系：要求一级，持有二级 -> 等级不足")
    void testConstruction_RequiresFirstGradeHoldsSecondGrade_Fails() {
        String req = "投标人须具备电力工程施工总承包一级资质";
        String cert = "电力工程施工总承包二级";

        QualificationGradePolicy.GradeAssessment assessment =
                QualificationGradePolicy.assessGrade(req, cert);
        assertThat(assessment.compatible()).isFalse();
        assertThat(assessment.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("设计/咨询体系：甲级满足乙级及以上要求；乙级不满足甲级要求")
    void testDesignConsulting_Grades() {
        String req1 = "具备工程设计建筑行业乙级及以上资质";
        String cert1 = "工程设计建筑行业甲级资质证书";

        QualificationGradePolicy.GradeAssessment a1 =
                QualificationGradePolicy.assessGrade(req1, cert1);
        assertThat(a1.compatible()).isTrue();

        String req2 = "具备工程设计建筑行业甲级资质";
        String cert2 = "工程设计建筑行业乙级资质证书";

        QualificationGradePolicy.GradeAssessment a2 =
                QualificationGradePolicy.assessGrade(req2, cert2);
        assertThat(a2.compatible()).isFalse();
        assertThat(a2.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("软件成熟度体系：CMMI 3级及以上，持有 CMMI5 满足；持有 CMMI2 等级不足")
    void testCmmi_Grades() {
        String req = "具备CMMI3级及以上资质认证";

        QualificationGradePolicy.GradeAssessment a1 =
                QualificationGradePolicy.assessGrade(req, "CMMI-DEV 5级认证证书");
        assertThat(a1.compatible()).isTrue();

        QualificationGradePolicy.GradeAssessment a2 =
                QualificationGradePolicy.assessGrade(req, "软件能力成熟度模型集成CMMI2级");
        assertThat(a2.compatible()).isFalse();
        assertThat(a2.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("等保体系：等保三级及以上，持有等保三级满足；持有等保二级等级不足")
    void testSecurityClassified_Grades() {
        String req = "系统需具备信息安全等级保护三级及以上备案证明";

        QualificationGradePolicy.GradeAssessment a1 =
                QualificationGradePolicy.assessGrade(req, "信息系统安全等级保护第三级备案证明");
        assertThat(a1.compatible()).isTrue();

        QualificationGradePolicy.GradeAssessment a2 =
                QualificationGradePolicy.assessGrade(req, "信息系统安全等级保护第二级备案证明");
        assertThat(a2.compatible()).isFalse();
        assertThat(a2.remark()).contains("等级不足");
    }

    @Test
    @DisplayName("无等级限定资质：双方均无等级时判定兼容")
    void testUnconstrained_NoGradeRequired() {
        String req = "具备有效的安全生产许可证";
        String cert = "建筑施工企业安全生产许可证";

        QualificationGradePolicy.GradeAssessment assessment =
                QualificationGradePolicy.assessGrade(req, cert);
        assertThat(assessment.compatible()).isTrue();
    }
}
