// Input: 标书文档 + 招标文件 + TenderRequirements + 知识库证据
// Output: 验证一票否决与资格前置反假（占位符拦截、违规分包拦截、合规承诺放行、目录点线防穿透）
// Pos: compliance/domain/policy/ - 一票否决纯核心核验测试（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一票否决与资格前置核验策略测试.
 */
class QualificationVetoPolicyTest {

    @Test
    @DisplayName("反假拦截：标书在一票否决或资格承诺中包含占位符未填，直接 FAIL")
    void checkFirstStage_placeholderUnfilled_returnsFail() {
        String p1 = "一票否决条款合规声明\n我单位郑重承诺：无行贿犯罪记录，失信被执行人核查结果为【请填写核查情况】。";
        String p2 = "承诺人签字：______  日期：2026年___月___日";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash1", p1 + "\f" + p2);
        TenderRequirements req = TenderRequirements.empty();

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("占位模板或空白留白");
    }

    @Test
    @DisplayName("违规拦截：标书自述实质性违规分包/转包，直接 FAIL")
    void checkFirstStage_subcontractingViolation_returnsFail() {
        String p1 = "项目实施方案\n我方中标后，拟将主体工程分包给第三方专业公司实施。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash2", p1);
        TenderRequirements req = TenderRequirements.empty();

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("分包");
    }

    @Test
    @DisplayName("正向合规承诺放行：标书承诺不转包不分包，绝不误判为违规，返回 empty 移交 LLM")
    void checkFirstStage_positiveComplianceCommitment_returnsEmpty() {
        String p1 = "合规履约承诺书\n我方承诺在本项目中绝不转包，不进行违法分包，严格遵守招投标法律法规。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash3", p1);
        TenderRequirements req = TenderRequirements.empty();

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("目录欺骗防穿透：硬性资质仅在目录点线页出现，正文无真实材料且知识库无，直接 FAIL")
    void checkFirstStage_catalogDeceptionOnly_returnsFail() {
        String p1 = "目录 TABLE OF CONTENTS\n1. 投标函......1\n2. 增值电信业务经营许可证......15\n3. 报价表......20";
        String p2 = "投标函正文\n我方响应本次招标，愿意按合同约定履约。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash4", p1 + "\f" + p2);
        TenderRequirements req = TenderRequirements.empty().withMergedQualifications(List.of("增值电信业务经营许可证"));

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("增值电信业务经营许可证");
    }

    @Test
    @DisplayName("正文有真实资质证明且无违规：返回 empty 留交 LLM 深度语义裁决")
    void checkFirstStage_validContent_returnsEmptyForLlm() {
        String p1 = "资质证明文件\n我方依法取得增值电信业务经营许可证，证书编号为 B2-20230001，业务范围包含信息服务业务。";
        String p2 = "承诺无转包，无违法分包。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash5", p1 + "\f" + p2);
        TenderRequirements req = TenderRequirements.empty().withMergedQualifications(List.of("增值电信业务经营许可证"));

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("长句与评分细则防护：非规范资质证书要求（如超长文本或非证书描述）不得作为硬性资质在一票否决中直接判定 FAIL")
    void checkFirstStage_overlongOrScoringItem_doesNotFailAsQualification() {
        String p1 = "投标函正文\n我方完全满足招标文件要求。承诺无转包，无违法分包。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash6", p1);
        String longText = "8.应答人应在提交应答文件时签署并提供框架采购供货履约承诺函，格式见采购文件第五章应答文件格式。9.最终用户紧急需求应急响应专项方案。";
        TenderRequirements req = TenderRequirements.empty().withMergedQualifications(List.of(longText));

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空值防御：mandatoryQualifications 包含 null 元素时不抛 NPE 并安全跳过")
    void checkFirstStage_nullQualificationInRequirements_doesNotThrowNpe() {
        String p1 = "投标函正文\n我方完全满足招标文件要求。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash7", p1);
        java.util.List<String> listWithNull = new java.util.ArrayList<>();
        listWithNull.add(null);
        listWithNull.add("   ");
        TenderRequirements req = new TenderRequirements(List.of(), listWithNull, List.of(), List.of(), List.of(), false);

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, null, req, List.of());

        assertThat(result).isEmpty();
    }
}
