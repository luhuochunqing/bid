// Input: 各种状态的资质比对明细与原始裁决结果
// Output: 验证 QualificationDetailReconciler 状态校准与关键词提取纯核心逻辑
// Pos: compliance/domain/policy/ - 资质比对状态校准器单元测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckCategory;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationDetailReconcilerTest {

    private QualityCheckItemResult createRawPassResult() {
        return QualityCheckItemResult.pass(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                "投标文件提供了完整且有效的资质证明材料，完全满足招标文件的硬性资格要求。",
                Collections.emptyList());
    }

    @Test
    @DisplayName("全量 MATCHED 时保持原 PASS 状态")
    void testReconcile_AllMatched_MaintainsPassStatus() {
        QualityCheckItemResult raw = createRawPassResult();
        List<QualificationDetail> details = List.of(
                new QualificationDetail("ISO9001认证", "ISO9001质量管理体系认证", "MATCHED", 28, "证书复印件", 300, "已匹配有效证书"),
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "MATCHED", 1, "许可证复印件", 500, "已匹配有效证书")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(reconciled.details()).hasSize(2);
        assertThat(reconciled.conclusion()).isEqualTo(raw.conclusion());
    }

    @Test
    @DisplayName("存在 EXPIRED 过期证书时一票否决为 FAIL")
    void testReconcile_HasExpiredCert_ForcesFailStatus() {
        QualityCheckItemResult raw = createRawPassResult();
        List<QualificationDetail> details = List.of(
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "MATCHED", 1, "许可证", 500, "已匹配"),
                new QualificationDetail("安全生产许可证", "建筑施工安全生产许可证", "EXPIRED", 15, "安全生产许可证", -10, "证书已过期，存在废标风险")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(reconciled.conclusion()).contains("【资质已过期】");
        assertThat(reconciled.conclusion()).contains("建筑施工安全生产许可证");
        assertThat(reconciled.suggestion()).contains("更新或替换已过期");
        assertThat(reconciled.details()).hasSize(2);
    }

    @Test
    @DisplayName("存在 UNMATCHED 未匹配项时绝不放行 PASS，强制降级为 FAIL")
    void testReconcile_HasUnmatchedCert_ForcesFailStatus() {
        QualityCheckItemResult raw = createRawPassResult();
        List<QualificationDetail> details = List.of(
                new QualificationDetail("银行基本账户开户许可证（...）", "", "UNMATCHED", null, "", null,
                        "企业资质库及投标文件中均未检索到对应资质证明材料，存在偏离或废标风险"),
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "MATCHED", 1, "许可证", 500, "已匹配")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(reconciled.conclusion()).contains("【资质未匹配】");
        assertThat(reconciled.conclusion()).contains("银行基本账户开户许可证");
        assertThat(reconciled.suggestion()).contains("补充对应资质证明材料");
    }

    @Test
    @DisplayName("存在 ATTENTION 需关注项且无硬性失败时，PASS 降级为 MANUAL_REVIEW")
    void testReconcile_OnlyAttention_DowngradesToManualReview() {
        QualityCheckItemResult raw = createRawPassResult();
        List<QualificationDetail> details = List.of(
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "MATCHED", 1, "许可证", 500, "已匹配"),
                new QualificationDetail("银行基本存款账户开户证明", "", "ATTENTION", 3, "银行开户信息", null,
                        "资质库未维护该证书，但在标书第 3 页检索到证明材料，请人工核查有效性")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(reconciled.conclusion()).contains(ManualReasons.MANUAL_REVIEW_REQUIRED);
        assertThat(reconciled.conclusion()).contains("标书第3页");
        assertThat(reconciled.suggestion()).contains("人工复核标书中资质证明材料与资质有效期");
    }

    @Test
    @DisplayName("空值与非 QUALIFICATION_MATCH 项守卫测试")
    void testReconcile_Guards() {
        assertThat(QualificationDetailReconciler.reconcile(null, List.of())).isNull();

        QualityCheckItemResult raw = createRawPassResult();
        assertThat(QualificationDetailReconciler.reconcile(raw, null)).isSameAs(raw);
        assertThat(QualificationDetailReconciler.reconcile(raw, Collections.emptyList())).isSameAs(raw);

        // 非 QUALIFICATION_MATCH 项只附加 details 不做状态改写
        QualityCheckItemResult otherItem = new QualityCheckItemResult(
                "OTHER_ITEM", QualityCheckCategory.BASIC_INFO, "其他项", "说明",
                QualityCheckStatus.PASS, "HIGH", 1, "结论", "建议", Collections.emptyList());
        List<QualificationDetail> details = List.of(
                new QualificationDetail("资质", "", "UNMATCHED", null, "", null, "未匹配")
        );
        QualityCheckItemResult otherReconciled = QualificationDetailReconciler.reconcile(otherItem, details);
        assertThat(otherReconciled.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(otherReconciled.details()).hasSize(1);
    }

    @Test
    @DisplayName("buildBidSearchCues：长句与括号注释正确提取核心证照与关键名词")
    void testBuildBidSearchCues_ExtractsCoreCues() {
        String longReq = "银行基本账户开户许可证（取消开户许可证的须提供基本存款账户银行备案资料，包括单位名称、开户银行、账号、法定代表人或单位负责人等信息）";
        List<String> cues = QualificationDetailReconciler.buildBidSearchCues(longReq);

        assertThat(cues).contains(longReq);
        assertThat(cues).contains("银行基本账户开户许可证");
        assertThat(cues).contains("基本存款账户");
        assertThat(cues).contains("开户许可证");
        assertThat(cues).contains("开户证明");

        String commaReq = "增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）及在线数据处理与交易业务";
        List<String> commaCues = QualificationDetailReconciler.buildBidSearchCues(commaReq);
        assertThat(commaCues).contains("增值电信业务经营许可证");
    }

    @Test
    @DisplayName("资质汇总称缺证但明细已匹配时不得保留矛盾的确定性失败")
    void reconcile_missingCertificateClaimContradictedByDetails_requiresManualReview() {
        QualityCheckItemResult raw = QualityCheckItemResult.fail(BidQualityCheckItem.QUALIFICATION_MATCH,
                "未在企业资质库匹配到ISO14001证书", "请补证", List.of());
        List<QualificationDetail> details = List.of(new QualificationDetail("ISO14001", "ISO14001环境管理体系认证", "ATTENTION",
                288, "ISO14001认证证书", 11, "已匹配但即将到期"));
        QualityCheckItemResult result = QualificationDetailReconciler.reconcile(raw, details);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).doesNotContain("未在企业资质库匹配到");
    }

    @Test
    @DisplayName("真实证明章节占位失败不因库中证书有效而被放宽")
    void reconcile_hardPlaceholderEvidence_preservesFailure() {
        QualityCheckItemResult raw = QualityCheckItemResult.fail(BidQualityCheckItem.QUALIFICATION_MATCH,
                "资质证明章节仍有未填写占位符", "请补全", List.of(
                        com.xiyu.bid.compliance.domain.QualityEvidence.bid("资质证书证明材料占位留白", 288, "【此处上传ISO14001证书】")));
        List<QualificationDetail> details = List.of(new QualificationDetail("ISO14001", "ISO14001环境管理体系认证", "ATTENTION",
                288, "【此处上传ISO14001证书】", 11, "已匹配但即将到期"));
        assertThat(QualificationDetailReconciler.reconcile(raw, details).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

}
