// Input: 1360 原件真实场景与边界用例（第一刀+第二刀）
// Output: 验证图二资格提取与实证闭环、图三评分留白、响应性差异表、否决警示语、文件组成、保证金不涉及及图片OCR闭环
// Pos: compliance/domain/policy/ - 1360 标书核查解除硬编码阻塞纯核心测试 (FP-Java)
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.service.LlmVerdictParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Quality1360UnblockTest {

    @Test
    void headinglessHtmlRequirementIsCleanedBeforeFusionAndMatching() {
        String raw = "具有EDI和<br>ICP许可证";
        var tender = DocumentContent.fromText("tender.pdf", "hash", raw);
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);

        assertThat(requirements.certificateRequirements()).containsExactly("具有EDI和ICP许可证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "EDI和ICP许可证")).isTrue();
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash",
                "EDI和ICP许可证\n证书编号：B2-20200044\n有效期限：2025年7月4日至2030年3月28日");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()))
                .hasValueSatisfying(result -> assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS));
    }

    @Test
    void headinglessEntitiesAreDecodedOnceAndKeepOriginalEvidence() {
        String raw = "投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", raw));

        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001&#49;认证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void encodedMarkupSurvivesParagraphClassification() {
        var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                "tender.pdf", "hash", "七、投标人资格要求\n投标人须具有ISO&lt;b&gt;9001认证"));
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证");
        assertThat(conditions.getFirst().certificates()).extracting(QualificationCondition.CertificateRequirement::requirementText)
                .containsExactly("投标人须具有ISO<b>9001认证");
    }

    @Test
    void encodedMarkupSurvivesTableAndRequirementsReconstruction() {
        var document = DocumentContent.fromText("tender.pdf", "hash",
                "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | 投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证 |");
        var conditions = QualificationConditionExtractor.extractStructured(document);
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证及A&lt;B认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), document);
        for (int i = 0; i < 3; i++) {
            requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
            assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
            assertThat(requirements.mandatoryQualifications()).doesNotContain("投标人须具有ISO9001认证及A<B认证");
        }
    }

    @Test
    void legacyRequirementsDecodeOnceBeforeReconstruction() {
        var requirements = new TenderRequirements(List.of(), List.of("投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证"),
                List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
    }

    @Test
    void legacyRequirementsKeepRawQuoteAcrossReconstruction() {
        String raw = "投标人须具有ISO&#57;001认证";
        var requirements = new TenderRequirements(List.of(), List.of(raw), List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        var condition = requirements.qualificationConditions().stream()
                .filter(c -> c.text().equals("投标人须具有ISO9001认证")).findFirst().orElseThrow();
        assertThat(condition.source().quote()).isEqualTo(raw);
    }

    @Test
    void vetoHtmlMatchesRetainActualSubstantivePageAndRawQuotes() {
        for (String raw : List.of("EDI和<br>ICP许可证", "ISO&#57;001认证")) {
            String nameOnly = QualificationConditionExtractor.stripHtml(raw);
            String bidText = raw + "\n证书编号：合字B2-20210088\n有效期限：2025年3月15日至2030年3月14日";
            String tenderText = "投标人须具有" + raw;
            var bid = new DocumentContent("bid.pdf", "bid", nameOnly + "\n" + bidText,
                    List.of(new DocumentContent.PageContent(2, nameOnly, List.of()),
                            new DocumentContent.PageContent(8, bidText, List.of())));
            var tender = new DocumentContent("tender.pdf", "tender", tenderText,
                    List.of(new DocumentContent.PageContent(6, tenderText, List.of())));
            var requirements = new TenderRequirements(List.of(), List.of(tenderText), List.of(), List.of(), List.of(), false);
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()).orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
            assertThat(result.evidences()).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("BID");
                assertThat(e.pageNumber()).isEqualTo(8);
                assertThat(e.quote()).contains(raw, "证书编号", "有效期限");
                assertThat(bidText).contains(e.quote());
            }).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("TENDER");
                assertThat(e.pageNumber()).isEqualTo(6);
                assertThat(e.quote()).contains(raw);
                assertThat(tenderText).contains(e.quote());
            });
        }
    }

    @Test
    void tableEvidenceLocatesCleanedBrAndNumericEntities() {
        for (String raw : List.of("投标人须具有EDI和<br>ICP许可证", "投标人须具有ISO&#57;001认证")) {
            var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                    "tender.pdf", "hash", "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | " + raw + " |"));
            var condition = conditions.getFirst();
            assertThat(condition.certificates()).hasSize(1);
            assertThat(condition.certificates().getFirst().cellIndex()).isEqualTo(2);
            assertThat(condition.source().quote()).contains(raw);
        }
    }

    @Test
    void htmlCleaningPreservesQualificationComparisons() {
        String condition = "注册资本<100万元不得投标，营业年限>3年";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo(condition);
        assertThat(QualificationConditionExtractor.extract("七、投标人资格要求\n1. " + condition))
                .containsExactly(condition);
    }

    @Test
    void htmlEntitiesPreserveCertificateIdentity() {
        String condition = "投标人须具有ISO&#57;001认证";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo("投标人须具有ISO9001认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", "七、投标人资格要求\n1. " + condition));
        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001认证");
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO14001认证")).isFalse();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO14001认证")).isFalse();
    }

    @Test
    void structuredRequirementsDoNotSufferDoubleEntityDecodingInMatching() {
        // 验证结构化条件在知识库比对路径上不发生二次解码（如 &amp;#49; -> &#49; 不再被错误吞成 1）
        String rawTender = "七、投标人资格要求\n1. 投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", rawTender));
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(decodedReq).isEqualTo("投标人须具有ISO9001&#49;认证");
        // 结构化匹配入口不进行二次解码，故与不同标准的 ISO90011 证书不匹配
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void htmlCleaningDecodesEntitiesWithoutConsumingComparisons() {
        assertThat(QualificationConditionExtractor.stripHtml(
                "<span title=\"a>b\">注册资本&lt;100万元</span>，营业年限&gt;3年，ISO&#x39;001&amp;认证&nbsp;&quot;甲&quot;&apos;乙&apos;"))
                .isEqualTo("注册资本<100万元，营业年限>3年，ISO9001&认证\u00a0\"甲\"'乙'");
        assertThat(QualificationConditionExtractor.stripHtml("EDI和<BR />ICP；未知&unknown;"))
                .isEqualTo("EDI和ICP；未知&unknown;");
    }

    @Test
    @DisplayName("TOC 目录行与后续章节附件不得污染资格条件提取")
    void tocAndLaterSectionsCannotPolluteQualificationConditions() {
        String tenderText = """
                目录
                七、 投标人资格要求 ....................................................- 4 -
                八、 投标文件的语言 ....................................................- 6 -

                七、 投标人资格要求
                1. 投标人为中华人民共和国境内合法注册的独立法人。
                2. 投标人须具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。

                八、 投标文件的语言
                投标文件应用中文编写。

                十、 投标人应提供文件和资料
                第二章
                投标人资格条件相关证明文件
                投标人主体的营业执照及相关认证等
                第二卷 技术标
                包括投标方案等。
                """;
        List<String> conditions = QualificationConditionExtractor.extract(tenderText);
        assertThat(conditions).hasSize(2);
        assertThat(conditions).allMatch(c -> !c.contains("......") && !c.contains("- 4 -") && !c.contains("第二卷") && !c.contains("相关证明文件"));

        var tenderDoc = DocumentContent.fromText("tender.pdf", "hash", tenderText);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        assertThat(req.hasUnresolvedCertificateRequirements()).isFalse();
    }

    @Test
    @DisplayName("主语含括号关联公司限定（或母子公司/集团公司）时，证书要求仍识别为 BIDDER + MANDATORY")
    void parentConditionSubjectWithAffiliateParentheticalIsMandatoryBidder() {
        String clause = "投标人（或与投标人为母子公司、或同属同一集团公司体系内的公司）须具有成熟的电子商城平台，且具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        var certs = CertificateRequirementPolicy.classify(clause, clause);
        assertThat(certs).hasSize(1);
        assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.BIDDER);
        assertThat(certs.getFirst().applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
    }

    @Test
    @DisplayName("无投标人上下文的独立产品或制造商证书子句，主体保持 UNKNOWN 避免盲目升级为投标人强制要求")
    void certificateClauseWithoutBidderContextRetainsTargetUnknown() {
        String clause = "具有ISO9001质量管理体系认证";
        var certs = CertificateRequirementPolicy.classify(clause, clause);
        assertThat(certs).hasSize(1);
        assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.UNKNOWN);
    }

    @Test
    void certificateCategoryWithoutHolderContextRequiresManualConfirmation() {
        for (String clause : List.of("具有一级建造师资格证书", "具有施工资质证书")) {
            var certs = CertificateRequirementPolicy.classify(clause, clause);
            assertThat(certs).hasSize(1);
            assertThat(certs.getFirst().applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
            assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.UNKNOWN);
            var tender = DocumentContent.fromText("tender.pdf", "hash", "资格要求\n" + clause);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
            var detail = new QualificationDetail(clause, "企业资质证书", "MATCHED", 1, clause, 365, "已匹配有效证书");
            var result = QualificationDetailReconciler.reconcile(
                    QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of()),
                    List.of(detail), req);
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void unrelatedOrEmptyKnowledgeQuotesAreExcludedFromCertificateEvidence() {
        var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有ISO9001认证");
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash", "ISO9001认证证明材料");
        for (String quote : java.util.Arrays.asList(null, "", "   ", "完全无关的营业执照")) {
            var evidence = new QualityEvidence("KNOWLEDGE_BASE", "资质记录", null, quote,
                    "unrelated", 1.0, KnowledgeMatch.SOURCE_QUALIFICATION, true);
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence));
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.orElseThrow().evidences()).noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        }
    }

    @Test
    void genericKnowledgeQuotesCannotIdentifyRequiredCertificate() {
        for (String certificate : List.of("ISO9001认证", "增值电信业务经营许可证")) {
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            for (String quote : List.of("认证", "许可证", "经营许可证", "管理体系认证", "企业资质证书")) {
                var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_QUALIFICATION, "generic", quote, true));
                var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence));
                assertThat(result).isPresent();
                assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(result.orElseThrow().evidences())
                        .as("要求 %s 不得归属泛化引文 %s", certificate, quote)
                        .noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            }
        }
    }

    @Test
    void shortCertificateCodesCannotIdentifyRequiredCertificate() {
        for (String certificate : List.of("ISO9001认证", "ICP许可证", "EDI许可证")) {
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            for (String quote : List.of("ISO", "ISO900", "IC", "ED")) {
                var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_QUALIFICATION, "short", quote, true));
                var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence)).orElseThrow();
                assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(result.evidences()).noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            }
            var exact = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "exact", certificate, true));
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(exact)).orElseThrow().evidences())
                    .anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        }
    }

    @Test
    void certificateEvidenceMustCoverEveryRequiredIdentityCode() {
        for (var pair : List.of(
                List.of("ISO9001质量管理体系认证", "质量管理体系认证"),
                List.of("EDI和ICP许可证", "EDI许可证"),
                List.of("EDI和ICP许可证", "ICP许可证"),
                List.of("中华人民共和国增值电信业务经营许可证（EDI和ICP）",
                        "中华人民共和国增值电信业务经营许可证 国家级"))) {
            String certificate = pair.getFirst();
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            var incomplete = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "incomplete", pair.get(1), true));
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(incomplete)).orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.evidences()).as("要求 %s 不得归属引文 %s", certificate, pair.get(1))
                    .noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            var complete = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "complete", certificate, true));
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(complete))
                    .orElseThrow().evidences()).contains(complete);
        }
    }

    @Test
    void certificateWithoutEnglishCodeRetainsIndependentNameMatch() {
        String certificate = "建筑工程施工总承包资质证书";
        var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "chinese", certificate, true));
        var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence)).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).contains(evidence);
    }

    @Test
    void longFieldFragmentsPreserveParsingAndRejectMalformedValues() {
        String longValue = "核验内容".repeat(4000);
        for (String field : List.of("conclusion", "suggestion")) {
            String prefix = "判定：\"status\":\"PASS\","
                    + (field.equals("suggestion") ? "\"conclusion\":\"完整结论\"," : "")
                    + "\"" + field + "\":\"";
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\""))
                    .isPresent();
            assertThat(LlmVerdictParser.parse(prefix + longValue))
                    .isEmpty();
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\", \"status\":null"))
                    .isEmpty();
        }
    }

    @Test
    void invalidTopLevelVerdictCannotBorrowNestedStatus() {
        for (String response : List.of(
                "{\"status\":null,\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}}",
                "{\"status\":\"UNKNOWN\",\"example\":{\"status\":\"PASS\"}}",
                "{\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}",
                "{\"status\":null,\"conclusion\":\"无法判定\"} {\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("无效顶层裁决不得借用示例或后续对象：%s", response).isEmpty();
        }
    }

    @Test
    void malformedVerdictCannotCombineFieldsAcrossObjects() {
        for (String response : List.of(
                "{\"conclusion\":\"无法判定\",} {\"status\":\"PASS\"}",
                "\"conclusion\":\"无法判定\", 另一对象：\"status\":\"PASS\"",
                "\"status\":null, \"example\":{\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("字段必须来自同一个裁决对象：%s", response).isEmpty();
        }
    }

    @Test
    void fieldFragmentRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":\"未完成",
                    ",\"suggestion\":\"请核查材料\"", ",\"conclusion\":null",
                    ",\"conclusion\":123")) {
                assertThat(LlmVerdictParser.parse(prefix + suffix))
                        .as("字段恢复不得补造结论：%s", prefix + suffix).isEmpty();
            }
            for (String suffix : List.of("", ",")) {
                assertThat(LlmVerdictParser.parse(
                        prefix + ",\"conclusion\":\"完整结论\"" + suffix))
                        .hasValueSatisfying(verdict -> {
                            assertThat(verdict.status()).isEqualTo(status);
                            assertThat(verdict.conclusion()).isEqualTo("完整结论");
                        });
            }
        }
    }

    @Test
    void truncatedVerdictRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "{\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",\"conclusion\":\"", ",\"conclusion\":\"尚未输出完整结论",
                    ",\"conclusion\":\"结论包含转义引号\\\"但仍未结束", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":null")) {
                String response = prefix + suffix;
                assertThat(LlmVerdictParser.parse(response))
                        .as("截断响应不得补造裁决：%s", response).isEmpty();
            }
            var complete = LlmVerdictParser.parse(
                    prefix + ",\"conclusion\":\"已完整输出的结论\"");
            assertThat(complete).hasValueSatisfying(verdict -> {
                assertThat(verdict.status()).isEqualTo(status);
                assertThat(verdict.conclusion()).isEqualTo("已完整输出的结论");
            });
        }
    }

    @Test
    @DisplayName("大模型响应末尾截断或丢失闭合花括号时，解析器自动修复并成功提取裁决结果")
    void truncatedOrMalformedLlmResponseIsHealedSuccessfully() {
        // 场景 1：结论已完整输出，仅建议截断；丢弃未完成建议，保留完整裁决。
        String truncated = """
                {
                  "status": "PASS",
                  "conclusion": "投标文件对各项评分标准响应完整，无占位符留白，符合满分标准。",
                  "suggestion": "建议在后续执行中保持发货时效
                """;
        var parsed1 = LlmVerdictParser.parse(truncated);
        assertThat(parsed1).isPresent();
        assertThat(parsed1.get().status()).isEqualTo("PASS");
        assertThat(parsed1.get().conclusion()).contains("符合满分标准");
        assertThat(parsed1.get().suggestion()).isEmpty();

        // 场景 2：仅丢失最外层花括号
        String missingBrace = """
                {"status": "MANUAL", "conclusion": "需人工核对系统报价", "reasonCode": "LOW_CONFIDENCE"
                """;
        var parsed2 = LlmVerdictParser.parse(missingBrace);
        assertThat(parsed2).isPresent();
        assertThat(parsed2.get().status()).isEqualTo("MANUAL");
        assertThat(parsed2.get().reasonCode()).isEqualTo("LOW_CONFIDENCE");

        // 场景 3：无外层对象的连续字段补齐后仍经 JSON 结构校验
        String brokenJson = "AI判定结果：\"status\": \"PASS\", \"conclusion\": \"响应充分\", 详情见后文...";
        var parsed3 = LlmVerdictParser.parse(brokenJson);
        assertThat(parsed3).isPresent();
        assertThat(parsed3.get().status()).isEqualTo("PASS");
        assertThat(parsed3.get().conclusion()).isEqualTo("响应充分");
    }

    @Test
    @DisplayName("条件性补充文件（如授权文件、股权证明）不被误提取为独立证书要求")
    void authorizationLetterInConditionIsNotExtractedAsCertificateRequirement() {
        String text = "如证书及平台非投标人自身拥有，须补充提供：-具备前述平台和经营许可证的单位出具的针对本项目的“授权文件”，证明投标人被授权合法使用该主网站；-股权关系证明资料。";
        assertThat(CertificateRequirementPolicy.classify(text, text)).isEmpty();
        assertThat(CertificateRequirementPolicy.isCertificateReference(text)).isFalse();
    }

    @Test
    @DisplayName("标书正文已提供证书编号与有效期限实证时，资质匹配与一票否决直接闭环 PASS")
    void substantiveCertificateFactsInBidAllowPassForQualificationMatchAndVeto() {
        String bidPage = """
                四、西域具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）
                | 经营许可证编号 | 合字B2-20200044 |
                | 业务种类 | 在线数据处理与交易处理业务（仅限经营类电子商务）；信息服务业务（仅限互联网信息服务） |
                | 有效期限 | 2025年7月4日至2030年3月28日 |
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", bidPage,
                List.of(new DocumentContent.PageContent(8, bidPage, List.of())));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人（或与投标人为母子公司、或同属同一集团公司体系内的公司）须具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        Optional<QualityCheckItemResult> vetoResult = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(vetoResult).isPresent();
        assertThat(vetoResult.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(SemanticEvidenceBoundary.reconcile(vetoResult.orElseThrow(), bidDoc).status())
                .isEqualTo(QualityCheckStatus.PASS);

        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(
                req.certificateRequirements(), List.of(), bidDoc, LocalDate.of(2026, 11, 7));
        assertThat(details).hasSize(1);
        assertThat(details.getFirst().finding()).isEqualTo(QualificationDetail.Finding.MATCHED_REFERENCE);
        var reconciled = QualificationDetailReconciler.reconcile(
                QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "资质匹配通过", List.of()),
                details, req, bidDoc);
        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("招标文件表格中含 <br> HTML标签（如 EDI和<br>ICP）时，自动清洗标签，一票否决实证 PASS，资质匹配保留主体待确认且备注无英文术语")
    void htmlBrTagInTenderRequirementIsSanitizedAndProducesHumanizedRemark() {
        String bidPage = """
                第二章 投标人资格条件相关证明文件
                四、西域具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）
                证书编号：合字B2-20210088
                有效期限：自2021年3月15日至2026年3月14日
                发证机关：中华人民共和国工业和信息化部
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", bidPage,
                List.of(new DocumentContent.PageContent(2, bidPage, List.of())));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n| 1 | 资格要求 | 具有有效的中华人民共和国增值电信业务经营许可证（EDI和<br>ICP） |");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        // 验证要求文字已清洗掉 <br>
        assertThat(req.certificateRequirements()).hasSize(1);
        assertThat(req.certificateRequirements().getFirst())
                .doesNotContain("<br>")
                .doesNotContain("<")
                .doesNotContain(">");

        // 一票否决闭环 PASS
        Optional<QualityCheckItemResult> vetoResult = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(vetoResult).isPresent();
        assertThat(vetoResult.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);

        // 资质比对详情不含英文术语与 [UNKNOWN]
        var detail = new QualificationDetail(req.certificateRequirements().getFirst(),
                "中华人民共和国增值电信业务经营许可证 国家级", "MATCHED", 2, "第 2 页", 1236,
                "[UNKNOWN] 原检索状态=MATCHED；已匹配企业资质库有效证书「中华人民共和国增值电信业务经营许可证 国家级」（有效期至 2030-03-28）");
        var reconciled = QualificationDetailReconciler.reconcile(
                QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "资质匹配", List.of()),
                List.of(detail), req, bidDoc);

        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(reconciled.details()).hasSize(1);
        String remark = reconciled.details().getFirst().remark();
        assertThat(remark).doesNotContain("[UNKNOWN]");
        assertThat(remark).doesNotContain("MATCHED");
        assertThat(remark).doesNotContain("原检索状态=");
        assertThat(remark).contains("已匹配企业资质库有效证书");
    }

    @Test
    @DisplayName("标书命中证书名称且企业资质库具备匹配证据时，一票否决严格保持人工核查契约且证据链纳入资质库佐证")
    void certificateNameHitWithEnterpriseKbEvidenceRetainsManualReviewAndEvidence() {
        String p1 = "封面\n项目编号：XY-ZB-2026-0888\n营业执照有效期限：长期";
        String p2 = "第二章 投标人资格条件相关证明文件\n依法取得中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bid_hash", p1 + "\n" + p2,
                List.of(
                        new DocumentContent.PageContent(1, p1, List.of()),
                        new DocumentContent.PageContent(2, p2, List.of())
                ));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人须具有中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        var kbEvidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "46", "中华人民共和国增值电信业务经营许可证（EDI和ICP） 国家级", true));

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of(kbEvidence));
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.get().evidences()).anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType())
                && "中华人民共和国增值电信业务经营许可证（EDI和ICP） 国家级".equals(e.quote()));
        assertThat(SemanticEvidenceBoundary.reconcile(result.get(), bidDoc).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("仅出现证书名称且其他页含无关项目编号与营业期限时，一票否决仍保持待确认不串页放行")
    void bareCertificateNameWithoutFactsStaysManualReview() {
        String p1 = "封面\n项目编号：XY-ZB-2026-0888\n营业执照有效期限：长期";
        String p2 = "资质证明文件\n依法取得中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bid_hash", p1 + "\n" + p2,
                List.of(
                        new DocumentContent.PageContent(1, p1, List.of()),
                        new DocumentContent.PageContent(2, p2, List.of())
                ));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人须具有中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.get().conclusion()).contains("标书已检索到证书名称：【")
                .doesNotContain("尚未定位到");
    }

    @Test
    @DisplayName("身份证正反面要素经OCR完整识别后，委托人身份证项可闭环 PASS")
    void completeBothSidesIdFactsPassIdAuthLetterAndBoundary() {
        String idPage = """
                法定代表人身份证明及委托代理人身份证
                姓名：张伟 性别：男 民族：汉 住址：上海市浦东新区某路100号
                公民身份号码：310115198805121234
                中华人民共和国居民身份证 签发机关：上海市公安局浦东分局 有效期限：2020.05.12-2040.05.12
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", idPage,
                List.of(new DocumentContent.PageContent(3, idPage, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(0, "image/png", "身份证", 3, 0, 0, "base64")));
        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(SemanticEvidenceBoundary.reconcile(result, bidDoc).status())
                .isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("技术方案中的延保协议模板样板留白不连坐触发评分响应占位留白")
    void warrantyAgreementTemplateDoesNotShortCircuitScoringResponse() {
        String pageText = """
                售后服务方案
                我司承诺提供 7×24 小时技术支持与售后服务保障。

                附：延保服务协议书
                甲方：西域智慧供应链（上海）股份公司
                乙方：
                西域智慧供应链（上海）股份公司于_____年_____月_____日在（公司）购买________，经双方达成一致，对该产品延长质保期至______年_____月_____日。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", pageText);
        TenderRequirements req = new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("售后服务方案", 10.0, "服务方案完整")),
                List.of(), List.of(), List.of(), List.of(), false);

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(bidDoc, req);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("差异表中填报'无差异'时，响应性评审判定为 PASS")
    void differenceTableWithNoDifferencePassesResponsiveness() {
        String bidText = """
                第一卷 商务标
                附件四：差异表
                项目名称：新兴际华物资采购
                差异情况说明：本投标响应完全响应招标文件，无差异。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash", "投标人须按附件四提供差异表。");

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(bidDoc, tenderDoc);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("无实质性负偏离");
    }

    @Test
    @DisplayName("二级供应商管理中'擅自转包将启动退出并处罚'识别为制度规则引用，不误判违规")
    void supplierPenaltyRuleCitationDoesNotTriggerVetoKeyword() {
        String bidText = "对于二级合作单位，如擅自将订单转包给其他单位，则立即启动退出程序，取消资格并进行处罚。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkVetoKeyword(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("包含目录及商务、技术主要卷册时，文件组成判定为 PASS")
    void documentCompletenessWithTocAndCoreSectionsPasses() {
        String bidText = """
                目 录
                第一卷 商务标 ..................................... 1
                第二卷 技术标 ..................................... 10

                第一卷 商务标
                投标函及商务文件。

                第二卷 技术标
                技术方案及实施细节。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDocumentCompleteness(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("完整");
    }

    @Test
    @DisplayName("招标文件全文未提及投标保证金时，判定为【不涉及】免收通过")
    void tenderWithoutDepositRequirementPassesAsNotApplicable() {
        String tenderText = """
                第一章 招标公告
                新兴际华集团电子商城外部合作电商引入公开招标项目，采购人为新兴际华集团有限公司。
                第二章 投标人须知
                请各投标人按照招标文件第一卷商务标、第二卷技术标、第三卷经济标的格式要求认真编制并按时递交投标文件。
                第三章 评标办法
                本项目采用综合评估法，对商务、技术、价格三部分进行综合评审打分，择优确定中标合作电商。
                第四章 合同主要条款及格式
                双方按框架协议约定履行供货与结算义务。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", "投标说明：我方按要求编制标书。");

        QualityCheckItemResult depositResult = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.conclusion()).contains("【不涉及】");

        QualityCheckItemResult refundResult = SubItemAndAttachmentRules.checkDepositRefund(bidDoc, tenderText);
        assertThat(refundResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(refundResult.conclusion()).contains("【不涉及】");
    }
}
