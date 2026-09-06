// Input: 标书文档 + TenderRequirements（含财务要求）
// Output: 验证资格评审反假拦截（财务造假、纳税/社保/信用占位符拦截）与反短路纪律
// Pos: compliance/domain/policy/ - 资格性评审纯核心核验测试（FP-Java 无外部依赖）
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
 * 资格性评审条款反假与反短路核验策略测试.
 */
class QualificationReviewPolicyTest {

    @Test
    @DisplayName("财务硬造假拦截：标书存在财务占位模板但无真实三表与审计报告，直接 FAIL")
    void checkFirstStage_financialPlaceholderWithoutRealReport_returnsFail() {
        String p1 = "财务报告部分\n【此处插入经审计的财务报表】\n会计师事务所：______";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash1", p1);
        FinancialRequirement finReq = FinancialRequirement.standard();
        TenderRequirements req = TenderRequirements.empty().withFinancialRequirement(finReq);

        Optional<QualityCheckItemResult> result = QualificationReviewPolicy.checkFirstStage(bidDoc, req);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("财务审计报告占位模板");
    }

    @Test
    @DisplayName("资格文件反假拦截：标书在纳税/社保/信用等资格证明中存在未替换占位模板，直接 FAIL")
    void checkFirstStage_qualificationProofPlaceholder_returnsFail() {
        String p1 = "资格性审查证明材料\n一、依法缴纳税收和社会保障资金凭证\n【此处粘贴近三个月税务局完税证明及社保缴纳凭据】\n二、信用中国查询记录\n【此处贴图】";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash2", p1);
        TenderRequirements req = TenderRequirements.empty();

        Optional<QualityCheckItemResult> result = QualificationReviewPolicy.checkFirstStage(bidDoc, req);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("占位模板或空白留白");
    }

    @Test
    @DisplayName("反短路纪律：财务报表完备且无造假，绝对不得判定 PASS，必须返回 empty 移交 LLM 穿透全部资格核验")
    void checkFirstStage_financialComplete_returnsEmptyForLlmToReviewAll() {
        String p1 = """
                财务状况报告
                经审计，某某会计师事务所出具标准无保留意见审计报告。
                资产负债表（2023年度）：资产总计 100,000,000 元，负债总计 40,000,000 元。
                利润表（2023年度）：营业收入 80,000,000 元，净利润 8,000,000 元。
                现金流量表（2023年度）：经营活动现金流量净额 10,000,000 元。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash3", p1);
        FinancialRequirement finReq = FinancialRequirement.standard();
        TenderRequirements req = TenderRequirements.empty().withFinancialRequirement(finReq);

        Optional<QualityCheckItemResult> result = QualificationReviewPolicy.checkFirstStage(bidDoc, req);

        // 准则 8 硬约束：财务完备不能直接 PASS，必须返回 empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空文档或无内容输入安全防御")
    void checkFirstStage_emptyDoc_returnsEmpty() {
        assertThat(QualificationReviewPolicy.checkFirstStage(null, TenderRequirements.empty())).isEmpty();
        assertThat(QualificationReviewPolicy.checkFirstStage(DocumentContent.empty(), null)).isEmpty();
    }
}
