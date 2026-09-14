// Input: 四知识库 Repository mock（资质/品牌授权/人员/业绩）+ 招标硬性要求关键词
// Output: 断言 KnowledgeMatch 结构化真实匹配、空库零固定文案、单库异常 → 依赖项 MANUAL 降级
// Pos: compliance/service/ - 四知识库真实查询测试（spec 049 US3 / T031，research.md R4）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationScope;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.domain.policy.BidDocumentQualityEngine;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.PerformanceEvidencePort;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.repository.QualificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseRealQueryTest {

    private QualificationRepository qualificationRepository;
    private PersonnelRepository personnelRepository;
    private BrandAuthorizationRepository brandAuthorizationRepository;
    private PerformanceEvidencePort performanceEvidencePort;
    private KnowledgeBaseOnDemandLoader loader;

    @BeforeEach
    void setUp() {
        qualificationRepository = mock(QualificationRepository.class);
        personnelRepository = mock(PersonnelRepository.class);
        brandAuthorizationRepository = mock(BrandAuthorizationRepository.class);
        performanceEvidencePort = mock(PerformanceEvidencePort.class);
        loader = new KnowledgeBaseOnDemandLoader(
                qualificationRepository, personnelRepository,
                brandAuthorizationRepository, performanceEvidencePort);
    }

    @Test
    @DisplayName("四库各一条真实记录命中：输出结构化 KnowledgeMatch（source/recordId/content/matched）")
    void testQueryKnowledgeBases_FourLibrariesRealMatch_OutputsStructuredMatches() {
        when(qualificationRepository.findAll()).thenReturn(List.of(
                Qualification.builder().id(7L).name("ISO27001信息安全认证").level("甲级")
                        .expiryDate(LocalDate.of(2027, 3, 31)).build()));
        when(brandAuthorizationRepository.findAll()).thenReturn(List.of(
                new BrandAuthorization(3L, "华为", "华为技术有限公司", AuthorizationScope.NATIONAL,
                        "全产品线", LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1),
                        AuthorizationStatus.ACTIVE, null, null, null, null)));
        Certificate pmpCert = new Certificate(21L, "PMP项目管理专业人士认证", "CERT-001",
                CertificateType.PMP, null, LocalDate.of(2028, 6, 30), null, null, false, null);
        when(personnelRepository.findAll(any())).thenReturn(List.of(
                new Personnel(11L, "张三", "XY001", null, "交付部", null, null, null,
                        null, null, null, PersonnelStatus.ACTIVE, null, null,
                        List.of(pmpCert), List.of(), null, null)));
        when(performanceEvidencePort.findPerformanceMatches(anyInt())).thenReturn(List.of(
                new KnowledgeMatch(KnowledgeMatch.SOURCE_PERFORMANCE, "31",
                        "业绩: 某银行数智化平台项目（签约主体: 西域数智，签约日期: 2025-05-20）", true)));

        KnowledgeQueryOutcome outcome = loader.queryKnowledgeBases(List.of("ISO27001", "PMP"));

        assertThat(outcome.failedSources()).isEmpty();
        Set<String> sources = outcome.matches().stream()
                .map(KnowledgeMatch::knowledgeSource).collect(Collectors.toSet());
        assertThat(sources).containsExactlyInAnyOrder(
                "qualification", "brand-authorization", "personnel", "performance");
        for (KnowledgeMatch match : outcome.matches()) {
            assertThat(match.recordId()).isNotBlank();
            assertThat(match.content()).isNotBlank();
            assertThat(match.matched()).isTrue();
        }
        // content 必须来自 DB 记录字段拼接（可回答"来自哪里"）
        assertThat(outcome.matches()).anyMatch(m -> m.content().contains("ISO27001信息安全认证"));
        assertThat(outcome.matches()).anyMatch(m -> m.content().contains("华为") && m.content().contains("2027"));
        assertThat(outcome.matches()).anyMatch(m -> m.content().contains("张三") && m.content().contains("PMP"));
        assertThat(outcome.matches()).anyMatch(m -> m.content().contains("某银行数智化平台项目"));
    }

    @Test
    @DisplayName("大模型提取长句要求匹配：投标人须具备有效的ISO27001信息安全管理体系认证证书")
    void testQueryKnowledgeBases_LongSentenceExtractedByLlm_MatchesShortDbName() {
        when(qualificationRepository.findAll()).thenReturn(List.of(
                Qualification.builder().id(11L).name("ISO27001信息安全认证")
                        .level("甲级").expiryDate(LocalDate.of(2028, 1, 1)).build()));
        when(brandAuthorizationRepository.findAll()).thenReturn(Collections.emptyList());
        when(personnelRepository.findAll(any())).thenReturn(Collections.emptyList());
        when(performanceEvidencePort.findPerformanceMatches(anyInt())).thenReturn(Collections.emptyList());

        String llmExtractedRequirement = "投标人须具备有效的质量与信息安全管理体系认证（ISO27001）证书且在有效期内";
        KnowledgeQueryOutcome outcome = loader.queryKnowledgeBases(List.of(llmExtractedRequirement));

        assertThat(outcome.failedSources()).isEmpty();
        assertThat(outcome.matches()).hasSize(1);
        KnowledgeMatch match = outcome.matches().get(0);
        assertThat(match.knowledgeSource()).isEqualTo("qualification");
        assertThat(match.recordId()).isEqualTo("11");
        assertThat(match.content()).contains("ISO27001信息安全认证");
    }

    @Test
    @DisplayName("空库：matches 为空且证据链零固定文案残留")
    void testQueryKnowledgeBases_EmptyLibraries_NoFixedTextEvidences() {
        when(qualificationRepository.findAll()).thenReturn(Collections.emptyList());
        when(brandAuthorizationRepository.findAll()).thenReturn(Collections.emptyList());
        when(personnelRepository.findAll(any())).thenReturn(Collections.emptyList());
        when(performanceEvidencePort.findPerformanceMatches(anyInt())).thenReturn(Collections.emptyList());

        KnowledgeQueryOutcome outcome = loader.queryKnowledgeBases(List.of("ISO27001"));

        assertThat(outcome.matches()).isEmpty();
        assertThat(outcome.failedSources()).isEmpty();
        List<QualityEvidence> evidences = outcome.matches().stream()
                .map(QualityEvidence::fromKnowledgeMatch).toList();
        assertThat(evidences).isEmpty();
    }

    @Test
    @DisplayName("单库查询异常：记录失败源，依赖该库的检查项置 MANUAL + [EVIDENCE_INSUFFICIENT]，独立项照常")
    void testQueryKnowledgeBases_QualificationQueryFails_DependentItemsManual() {
        when(qualificationRepository.findAll()).thenThrow(new RuntimeException("DB timeout"));
        when(brandAuthorizationRepository.findAll()).thenReturn(Collections.emptyList());
        when(personnelRepository.findAll(any())).thenReturn(Collections.emptyList());
        when(performanceEvidencePort.findPerformanceMatches(anyInt())).thenReturn(Collections.emptyList());

        KnowledgeQueryOutcome outcome = loader.queryKnowledgeBases(List.of("ISO27001"));

        assertThat(outcome.failedSources()).containsExactly("qualification");

        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", "投标函 资质证书");
        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标要求"),
                outcome.matches(), TenderRequirements.empty(), outcome.failedSources());

        assertThat(results).hasSize(25);
        for (String code : List.of(
                BidQualityCheckItem.QUALIFICATION_MATCH.getCode(),
                BidQualityCheckItem.QUALIFICATION_VETO.getCode(),
                BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())) {
            QualityCheckItemResult item = results.stream()
                    .filter(r -> r.itemCode().equals(code)).findFirst().orElseThrow();
            assertThat(item.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(item.conclusion()).contains("[EVIDENCE_INSUFFICIENT]");
        }
        // 独立项不受单库失败影响
        QualityCheckItemResult respItem = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst().orElseThrow();
        assertThat(respItem.conclusion()).doesNotContain("[EVIDENCE_INSUFFICIENT]");
    }

    @Test
    @DisplayName("仓储 Bean 缺失（无参降级）：四库均标记失败，查询不抛异常")
    void testQueryKnowledgeBases_RepositoriesAbsent_AllSourcesFailed() {
        KnowledgeQueryOutcome outcome =
                new KnowledgeBaseOnDemandLoader().queryKnowledgeBases(List.of("ISO27001"));

        assertThat(outcome.matches()).isEmpty();
        assertThat(outcome.failedSources()).containsExactlyInAnyOrder(
                "qualification", "brand-authorization", "personnel", "performance");
    }

    @Test
    @DisplayName("优先真实业务企业资质库 BusinessQualificationRepository：加载非注销资质并成功匹配")
    void testQueryKnowledgeBases_PrefersBusinessQualificationRepository() {
        BusinessQualificationRepository bqRepo = mock(BusinessQualificationRepository.class);
        BusinessQualification bq = BusinessQualification.create(
                46L, "中华人民共和国增值电信业务经营许可证", "国家级",
                null, null, "B2-20200001", "工信部", null, null,
                "信息服务业务", null, "西域",
                new ValidityPeriod(LocalDate.of(2025, 3, 28), LocalDate.of(2030, 3, 28)),
                null, "http://file", null, null, List.of()
        );
        when(bqRepo.findAll(any(QualificationListCriteria.class))).thenReturn(List.of(bq));

        KnowledgeBaseOnDemandLoader customLoader = new KnowledgeBaseOnDemandLoader(
                qualificationRepository, bqRepo, personnelRepository,
                brandAuthorizationRepository, performanceEvidencePort);

        KnowledgeQueryOutcome outcome = customLoader.queryKnowledgeBases(
                List.of("增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）"));

        assertThat(outcome.failedSources()).isEmpty();
        assertThat(outcome.matches()).anyMatch(m ->
                m.knowledgeSource().equals("qualification")
                        && m.recordId().equals("46")
                        && m.content().contains("中华人民共和国增值电信业务经营许可证")
                        && m.content().contains("2030-03-28"));

        List<Qualification> all = customLoader.findAllQualifications();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getId()).isEqualTo(46L);
        assertThat(all.getFirst().getName()).isEqualTo("中华人民共和国增值电信业务经营许可证");
        assertThat(all.getFirst().getExpiryDate()).isEqualTo(LocalDate.of(2030, 3, 28));
    }

    @Test
    @DisplayName("CA证书库对接：传入交易平台关键词，通过 CaCertificateEvidencePort 真实查询并输出 ca-certificate 匹配证据")
    void testQueryKnowledgeBases_WithCaCertificateEvidencePort() {
        com.xiyu.bid.compliance.domain.policy.CaCertificateEvidencePort caPort =
                mock(com.xiyu.bid.compliance.domain.policy.CaCertificateEvidencePort.class);
        when(caPort.findCaMatches("国网电商", 20)).thenReturn(List.of(
                new KnowledgeMatch(KnowledgeMatch.SOURCE_CA_CERTIFICATE, "101",
                        "CA证书: 国家电网新一代电子商务平台（实体CA/公章，有效，有效期至 2027-12-31，在库，保管人: 李保管）", true)));

        KnowledgeBaseOnDemandLoader customLoader = new KnowledgeBaseOnDemandLoader(
                qualificationRepository, null, personnelRepository,
                brandAuthorizationRepository, performanceEvidencePort, caPort);

        KnowledgeQueryOutcome outcome = customLoader.queryKnowledgeBases(List.of(), "国网电商");

        assertThat(outcome.failedSources()).isEmpty();
        assertThat(outcome.matches()).anyMatch(m ->
                m.knowledgeSource().equals("ca-certificate")
                        && m.recordId().equals("101")
                        && m.content().contains("国家电网")
                        && m.content().contains("李保管"));
    }
}
