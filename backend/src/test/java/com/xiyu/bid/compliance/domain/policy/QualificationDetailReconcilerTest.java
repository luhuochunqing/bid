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

    @Test
    void ambiguousDuplicateCannotEraseSeparatelyProvenMandatoryExpiry() {
        String clause = "投标人须具有ISO9001认证";
        var known = new QualificationCondition(clause, new QualificationCondition.Source("t.pdf", "h", 1, clause), null);
        var unknown = new QualificationCondition(clause, new QualificationCondition.Source("t.pdf", "h", 2, clause),
                List.of(new QualificationCondition.CertificateRequirement(clause, QualificationCondition.Target.BIDDER,
                        QualificationCondition.Applicability.UNKNOWN, "章节范围待确认", null)));
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(known, unknown));
        var certificate = com.xiyu.bid.entity.Qualification.builder().name("ISO9001认证")
                .expiryDate(java.time.LocalDate.of(2020, 1, 1)).build();
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("b.pdf", "b", "ISO9001认证证明");
        var details = QualificationMatchPolicy.buildDetails(req.certificateRequirements(), List.of(certificate), bid,
                java.time.LocalDate.of(2026, 10, 31));
        assertThat(QualificationDetailReconciler.reconcile(createRawPassResult(), details, req, bid).status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void explicitAttentionReferenceWithoutLocationRemainsManual() {
        var detail = new QualificationDetail("ISO9001认证", "ISO9001认证", "ATTENTION", null, "", 10,
                "引用位置待核实", QualificationDetail.Finding.MATCHED_REFERENCE);
        var result = QualificationDetailReconciler.reconcile(createRawPassResult(), List.of(detail));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("尚未可靠定位");
    }

    @Test
    void certificateRequirementsWithUnexpectedEmptyDetailsCannotPass() {
        var req = TenderRequirements.empty().withMergedQualifications(List.of("投标人须具有ISO9001认证"));
        var result = QualificationDetailReconciler.reconcile(createRawPassResult(), List.of(), req,
                com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "h", "投标正文"));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ISO9001认证", "CMMI三级", "ITSS二级", "CCRC信息安全服务资质"})
    void longCertificateClauseProducesLookupMissInsteadOfDisappearing(String certificate) {
        String clause = "投标人须具备" + certificate + "并应在投标文件中提供对应原件扫描副本以供逐项核验".repeat(6);
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "h", "投标正文");
        var tender = com.xiyu.bid.compliance.domain.DocumentContent.fromText("tender.pdf", "t", "资格要求\n" + clause);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var details = QualificationMatchPolicy.buildDetails(req.certificateRequirements(), List.of(), bid,
                java.time.LocalDate.of(2026, 10, 31));
        assertThat(details).singleElement().satisfies(d -> {
            assertThat(d.requirement()).isEqualTo(clause);
            assertThat(d.finding()).isEqualTo(QualificationDetail.Finding.LOOKUP_MISS);
        });
        var evidence = EvidenceAssembler.semanticEvidences(BidQualityCheckItem.QUALIFICATION_MATCH, bid, tender, req, List.of());
        assertThat(evidence).anySatisfy(e -> {
            assertThat(e.sourceType()).isEqualTo("TENDER");
            assertThat(e.pageNumber()).isEqualTo(1);
        });
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "通过", "")));
        var raw = BidDocumentQualityEngine.evaluateAll(bid, tender, List.of(), req, java.util.Set.of(), llm, (p, s) -> { })
                .stream().filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_MATCH.getCode())).findFirst().orElseThrow();
        assertThat(QualificationDetailReconciler.reconcile(raw, details, req, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void gradeDefectLabelWithoutActualGradeConflictCannotCreateFailure() {
        var detail = new QualificationDetail("ISO9001认证", "ISO9001认证", "UNMATCHED", null, "", null,
                "等级不足", QualificationDetail.Finding.GRADE_INSUFFICIENT);
        var result = QualificationDetailReconciler.reconcile(createRawPassResult(), List.of(detail));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details().get(0).finding()).isEqualTo(QualificationDetail.Finding.UNKNOWN);
    }

    @Test
    void fourArgumentReconciliationDoesNotReplaceAnotherCheckItem() {
        var other = new QualityCheckItemResult("OTHER_ITEM", QualityCheckCategory.BASIC_INFO, "其他项", "说明",
                QualityCheckStatus.PASS, "HIGH", 1, "原结论", "", List.of());
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "h", "资质证书\n【此处上传ISO9001证书】");
        var otherDetail = new QualificationDetail("ISO9001认证", "ISO9001认证", "UNMATCHED", null, "", null,
                "外部项原明细", QualificationDetail.Finding.GRADE_INSUFFICIENT);
        var result = QualificationDetailReconciler.reconcile(other, List.of(otherDetail), TenderRequirements.empty(), bid);
        assertThat(result.itemCode()).isEqualTo("OTHER_ITEM");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.details()).containsExactly(otherDetail);
    }

    @Test
    void knownMandatoryLookupMissWithStructuredSourceRemainsManual() {
        String clause = "投标人须具有ISO9001认证";
        var tender = new com.xiyu.bid.compliance.domain.DocumentContent("tender.pdf", "tender-hash", "投标人资格要求\n" + clause,
                List.of(new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(7, "投标人资格要求\n" + clause, List.of())));
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "bid-hash", "投标函正文及扫描附件");
        var details = QualificationMatchPolicy.buildDetails(req.certificateRequirements(), List.of(), bid,
                java.time.LocalDate.of(2026, 10, 30));
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH, "人工核验附件", "核验", List.of());
        var result = QualificationDetailReconciler.reconcile(raw, details, req, bid);
        assertThat(req.hasUnresolvedCertificateRequirements()).isFalse();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details().get(0).status()).isEqualTo("ATTENTION");
        assertThat(result.conclusion()).doesNotContain("资质未匹配", "废标");
    }

    @Test
    void unsupportedModelFailureForUnknownRequirementNeedsReview() {
        var raw = QualityCheckItemResult.fail(BidQualityCheckItem.QUALIFICATION_MATCH, "未提供ISO9001证书", "补充", List.of());
        var req = TenderRequirements.empty().withMergedQualifications(List.of("ISO9001认证"));
        var result = QualificationDetailReconciler.reconcile(raw, List.of(), req);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unknownConditionDoesNotEraseAnotherKnownExpiredCertificate() {
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH, "待复核", "核验", List.of());
        var req = TenderRequirements.empty().withMergedQualifications(List.of("投标人须具有ISO9001认证", "ISO14001认证"));
        var known = new QualificationDetail("投标人须具有ISO9001认证", "ISO9001认证", "EXPIRED", 5, "证书原文", -10, "已过期");
        var unknown = new QualificationDetail("ISO14001认证", "", "UNMATCHED", null, "", null, "未检索到");
        var result = QualificationDetailReconciler.reconcile(raw, List.of(known, unknown), req);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.details().get(0).status()).isEqualTo("EXPIRED");
        assertThat(result.details().get(1).status()).isEqualTo("ATTENTION");
    }

    @Test
    void libraryMatchWithoutBidProofIsReferenceOnly() {
        var certificate = new com.xiyu.bid.entity.Qualification();
        certificate.setName("ISO9001质量管理体系认证");
        certificate.setExpiryDate(java.time.LocalDate.of(2028, 1, 1));
        var details = QualificationMatchPolicy.buildDetails(List.of("ISO9001认证"), List.of(certificate),
                com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "hash", "投标函正文"),
                java.time.LocalDate.of(2026, 10, 30));
        assertThat(details.get(0).status()).isEqualTo("ATTENTION");
        var result = QualificationDetailReconciler.reconcile(createRawPassResult(), details);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).doesNotContain("即将到期", "临期");
    }

    @Test
    void unknownRequirementScopeCannotBecomeFailureAfterKnowledgeLookup() {
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                "条件作用对象尚未确认", "请核验", List.of());
        var detail = new QualificationDetail("ISO9001认证", "", "UNMATCHED", null, "", null, "未检索到");
        var req = TenderRequirements.empty().withMergedQualifications(List.of("ISO9001认证"));
        var result = QualificationDetailReconciler.reconcile(raw, List.of(detail), req);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details().get(0).status()).isEqualTo("ATTENTION");
        assertThat(result.details().get(0).remark()).contains("UNKNOWN", "UNMATCHED");
    }

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
    @DisplayName("纯检索未匹配不能证明缺失，转人工确认")
    void testReconcile_HasUnmatchedCert_RequiresReview() {
        QualityCheckItemResult raw = createRawPassResult();
        List<QualificationDetail> details = List.of(
                new QualificationDetail("银行基本账户开户许可证（...）", "", "UNMATCHED", null, "", null,
                        "企业资质库及投标文件中均未检索到对应资质证明材料，存在偏离或废标风险"),
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "MATCHED", 1, "许可证", 500, "已匹配")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(reconciled.details().get(0).status()).isEqualTo("ATTENTION");
        assertThat(reconciled.details().get(0).finding()).isEqualTo(QualificationDetail.Finding.LOOKUP_MISS);
        assertThat(reconciled.conclusion()).doesNotContain("废标风险", "资质未匹配");
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
        assertThat(reconciled.details().get(1).bidPageNumber()).isEqualTo(3);
        assertThat(reconciled.details().get(1).bidQuote()).isEqualTo("银行开户信息");
        assertThat(reconciled.details().get(1).finding()).isEqualTo(QualificationDetail.Finding.EVIDENCE_INCOMPLETE);
    }

    @Test
    @DisplayName("仅因证书临期触发需关注时：文案精准提示证书临期与换证，且彻底消除与「资质匹配通过」的自相矛盾")
    void testReconcile_ExpiringSoonAttention_AccurateConclusionAndNoContradiction() {
        QualityCheckItemResult raw = QualityCheckItemResult.pass(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                "投标文件提供了完整且有效的资质证明材料，完全满足招标文件的硬性资格要求，资质匹配通过。",
                Collections.emptyList());

        List<QualificationDetail> details = List.of(
                new QualificationDetail("ISO9001", "ISO9001质量管理体系认证证书", "MATCHED", 236, "证书", 300, "已匹配"),
                new QualificationDetail("ISO14001", "ISO14001环境管理体系认证证书", "MATCHED", 288, "证书", 300, "已匹配"),
                new QualificationDetail("ISO45001", "ISO45001职业健康管理认证证书", "ATTENTION", 290, "证书", 11,
                        "证书即将到期（剩余 11 天，至 2026-11-05），建议复核")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        // 精准归因为证书临期，不能误报为“标书证明材料待核实”
        assertThat(reconciled.conclusion()).contains("证书临期");
        assertThat(reconciled.conclusion()).contains("ISO45001职业健康管理认证证书");
        assertThat(reconciled.conclusion()).doesNotContain("请人工复核标书中资质证明材料的有效性");
        // 消除“资质匹配通过”矛盾句
        assertThat(reconciled.conclusion()).doesNotContain("资质匹配通过");
        assertThat(reconciled.conclusion()).contains("建议人工复核确认");
        // 建议操作针对性提示换证续期
        assertThat(reconciled.suggestion()).contains("换证续期");
    }

    @Test
    @DisplayName("降级为 FAIL 时：原始通过结论中的「资质匹配通过」同样被清洗消除")
    void testReconcile_FailStatus_CleansesPassPhrases() {
        QualityCheckItemResult raw = QualityCheckItemResult.pass(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                "投标文件提供了完整且有效的资质证明材料，完全满足招标文件的硬性资格要求，资质匹配通过。",
                Collections.emptyList());

        List<QualificationDetail> details = List.of(
                new QualificationDetail("增值电信业务许可证", "增值电信业务经营许可证", "EXPIRED", 1, "许可证", -5, "证书已过期")
        );

        QualityCheckItemResult reconciled = QualificationDetailReconciler.reconcile(raw, details);

        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(reconciled.conclusion()).doesNotContain("资质匹配通过");
        assertThat(reconciled.conclusion()).doesNotContain("完全满足招标文件的硬性资格要求");
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
        var bid = new com.xiyu.bid.compliance.domain.DocumentContent("bid.pdf", "proof-hash", "资质证书\n【此处上传ISO14001证书】",
                List.of(new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(288, "资质证书\n【此处上传ISO14001证书】", List.of())));
        var req = TenderRequirements.empty().withMergedQualifications(List.of("投标人须具有ISO14001认证"));
        var result = QualificationDetailReconciler.reconcile(raw, details, req, bid);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anySatisfy(e -> {
            assertThat(e.pageNumber()).isEqualTo(288);
            assertThat(e.quote()).contains("【此处上传ISO14001证书】");
        });
    }

    @Test
    void separateCertificateLevelDoesNotLoseOriginalNameLocation() {
        String requirement = "投标人须具备电力工程施工总承包一级资质";
        var certificate = com.xiyu.bid.entity.Qualification.builder().name("电力工程施工总承包")
                .level("一级").expiryDate(java.time.LocalDate.of(2028, 1, 1)).build();
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "h",
                "资质证书\n电力工程施工总承包\n资质等级：一级");
        var detail = QualificationMatchPolicy.buildDetails(List.of(requirement), List.of(certificate), bid,
                java.time.LocalDate.of(2026, 10, 30)).get(0);
        assertThat(detail.finding()).isEqualTo(QualificationDetail.Finding.MATCHED_REFERENCE);
        assertThat(detail.bidPageNumber()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void gradeDefectFromActualComparisonStillFails(boolean separateLevel) {
        String requirement = "投标人须具备电力工程施工总承包一级资质";
        var cert = com.xiyu.bid.entity.Qualification.builder().name(separateLevel ? "电力工程施工总承包" : "电力工程施工总承包三级")
                .level(separateLevel ? "三级" : null)
                .expiryDate(java.time.LocalDate.of(2028, 1, 1)).build();
        var bid = com.xiyu.bid.compliance.domain.DocumentContent.fromText("bid.pdf", "h", "资质证书\n电力工程施工总承包三级");
        var details = QualificationMatchPolicy.buildDetails(List.of(requirement), List.of(cert), bid, java.time.LocalDate.of(2026,10,30));
        assertThat(details.get(0).finding()).isEqualTo(QualificationDetail.Finding.GRADE_INSUFFICIENT);
        var req = TenderRequirements.empty().withMergedQualifications(List.of(requirement));
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH, "待确认", "核验", List.of());
        assertThat(QualificationDetailReconciler.reconcile(raw, details, req, bid).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void legacyJsonRemarkCannotInventGradeDefect() throws Exception {
        var detail = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"requirement":"ISO9001认证","matchedCertName":"","status":"UNMATCHED","remark":"等级不足，未匹配"}
                """, QualificationDetail.class);
        assertThat(detail.finding()).isEqualTo(QualificationDetail.Finding.LOOKUP_MISS);
        assertThat(QualificationDetailReconciler.reconcile(createRawPassResult(), List.of(detail)).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void legacyExpiredLabelWithoutDateFactCannotCreateFailure() throws Exception {
        var detail = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"requirement":"ISO9001认证","matchedCertName":"ISO9001认证","status":"EXPIRED","remark":"已过期"}
                """, QualificationDetail.class);
        var result = QualificationDetailReconciler.reconcile(createRawPassResult(), List.of(detail));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details().get(0).status()).isEqualTo("ATTENTION");
    }

}
