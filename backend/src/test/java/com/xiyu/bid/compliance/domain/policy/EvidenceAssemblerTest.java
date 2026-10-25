// Input: 各种场景的标书内容、招标要求与多知识库证据
// Output: 验证 EvidenceAssembler 正文页定位（跳过目录页）与知识库白名单源过滤
// Pos: compliance/domain/policy/ - 证据装配策略单元测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceAssemblerTest {

    @Test
    @DisplayName("QUALIFICATION_MATCH 组装证据：跳过目录页，直接命中正文页证据")
    void qualificationMatch_skipsCatalogPage() {
        String text = "目 录\n(一) 基本情况表....... 236\n(二) ISO认证体系....... 288\n(三) 近年\n第二章 资质证明材料正文\n我司已取得有效的 ISO9001质量管理体系、ISO14001环境管理体系与ISO45001体系证书。";
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "proj-1", text, List.of(
                new DocumentContent.PageContent(4, "目 录\n(一) 基本情况表....... 236\n(二) ISO认证体系....... 288\n(三) 近年", List.of()),
                new DocumentContent.PageContent(236, "第二章 资质证明材料正文\n我司已取得有效的 ISO9001质量管理体系、ISO14001环境管理体系与ISO45001体系证书。", List.of())
        ));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "proj-1", "资格要求：具备ISO9001、ISO14001、ISO45001认证。");
        TenderRequirements requirements = new TenderRequirements(
                List.of(),
                List.of("具备ISO质量管理体系认证（如ISO9001）、环境管理体系认证（ISO14001）、职业健康安全管理体系认证（ISO45001）等"),
                List.of(), List.of(), List.of(), false);

        List<QualityEvidence> evidences = EvidenceAssembler.semanticEvidences(
                BidQualityCheckItem.QUALIFICATION_MATCH, bidDoc, tenderDoc, requirements, List.of());

        List<QualityEvidence> bidEvidences = evidences.stream()
                .filter(e -> "BID".equals(e.sourceType()) && e.pageNumber() != null)
                .toList();

        assertThat(bidEvidences).isNotEmpty();
        assertThat(bidEvidences).noneMatch(e -> e.pageNumber() == 4);
        assertThat(bidEvidences).anyMatch(e -> e.pageNumber() == 236);
    }

    @Test
    @DisplayName("QUALIFICATION_MATCH 组装证据：严格过滤知识库证据，仅保留资质库（不混入业绩库等杂音）")
    void qualificationMatch_filtersKnowledgeSources_onlyQualification() {
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "proj-1", "ISO9001 证书正文");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "proj-1", "ISO9001 认证要求");
        TenderRequirements requirements = new TenderRequirements(
                List.of(), List.of("ISO9001质量管理体系认证"), List.of(), List.of(), List.of(), false);

        List<QualityEvidence> kbEvidences = List.of(
                QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "101", "ISO9001质量体系认证证书", true)),
                QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(KnowledgeMatch.SOURCE_PERFORMANCE, "202", "某央企物资采购历史业绩合同", true)),
                new QualityEvidence("KNOWLEDGE_BASE", "未知来源", null, "摘要", "999", 1.0, null, true),
                QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(KnowledgeMatch.SOURCE_CA_CERTIFICATE, "303", "上海CA数字证书", true))
        );

        List<QualityEvidence> evidences = EvidenceAssembler.semanticEvidences(
                BidQualityCheckItem.QUALIFICATION_MATCH, bidDoc, tenderDoc, requirements, kbEvidences);

        List<QualityEvidence> attachedKb = evidences.stream()
                .filter(e -> "KNOWLEDGE_BASE".equals(e.sourceType()))
                .toList();

        assertThat(attachedKb).extracting(QualityEvidence::knowledgeSource)
                .containsExactly(KnowledgeMatch.SOURCE_QUALIFICATION);
    }

    @Test
    @DisplayName("CA_CERTIFICATE 组装证据：仅保留 CA 证书库证据")
    void caCertificate_filtersKnowledgeSources_onlyCaCertificate() {
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "proj-1", "CA数字证书");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "proj-1", "要求CA数字证书");
        TenderRequirements requirements = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(), false);

        List<QualityEvidence> kbEvidences = List.of(
                QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "101", "ISO9001证书", true)),
                QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(KnowledgeMatch.SOURCE_CA_CERTIFICATE, "303", "上海CA数字证书", true))
        );

        List<QualityEvidence> evidences = EvidenceAssembler.semanticEvidences(
                BidQualityCheckItem.CA_CERTIFICATE, bidDoc, tenderDoc, requirements, kbEvidences);

        List<QualityEvidence> attachedKb = evidences.stream()
                .filter(e -> "KNOWLEDGE_BASE".equals(e.sourceType()))
                .toList();

        assertThat(attachedKb).extracting(QualityEvidence::knowledgeSource)
                .containsExactly(KnowledgeMatch.SOURCE_CA_CERTIFICATE);
    }
}
