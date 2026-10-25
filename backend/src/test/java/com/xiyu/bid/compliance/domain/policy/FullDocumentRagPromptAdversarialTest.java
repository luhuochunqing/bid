// Input: 50 页超长标书合成测试数据（证据置于第 20、35、45、50 页）
// Output: 对抗性验证全标书 RAG 检索（7 项语义条款准确定位远端页面、包含 --- [第 X 页] --- 页码标识、彻底杜绝 6000 字符硬截断）
// Pos: compliance/domain/policy/ - 全标书 RAG Prompt 对抗性验证测试（M1 挑战者验收套件）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标书质量核查 50 页全标书语义 RAG 提示词生成的对抗性验证测试套件.
 */
public class FullDocumentRagPromptAdversarialTest {

    private static final String BASE_FILLER_SENTENCE = "本页为标书常规格式化通用介绍与行政办公支持说明，展示项目常规组织管理与后勤保障体系内容。"
            + "不涉及特定资格资质、偏离承诺、评分细则或澄清答疑等关键条款。\n";

    private static final String FILLER_TEXT_LONG = BASE_FILLER_SENTENCE.repeat(18); // ~1400-1500 chars
    private static final String FILLER_TEXT_SHORT = BASE_FILLER_SENTENCE.repeat(6); // ~500 chars

    private static DocumentContent synthetic50PageDocOverBudget;
    private static DocumentContent synthetic50PageDocWithinBudget;
    private static DocumentContent synthetic50PageDocNoHeadings;
    private static DocumentContent synthetic50PageTenderDoc;
    private static TenderRequirements sampleRequirements;

    @BeforeAll
    static void setUpFixtures() {
        sampleRequirements = new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("技术方案与实施组织", 35.0, "方案完整")),
                List.of("ISO9001 质量管理体系认证"),
                List.of(new TenderRequirements.VetoClause("未提供制造商原厂授权书者废标", List.of("原厂授权书"))),
                List.of(), List.of(), false);

        // 1. 构建超预算 50 页文档（每页 ~1400 字符，总字符量 ~70,000 > 60,000 预算）
        List<DocumentContent.PageContent> overBudgetPages = new ArrayList<>(50);
        for (int p = 1; p <= 50; p++) {
            overBudgetPages.add(createPage(p, true));
        }
        synthetic50PageDocOverBudget = new DocumentContent(
                "synthetic_50p_over_budget.docx", "hash_over_50", "", overBudgetPages);

        // 2. 构建预算内 50 页文档（每页 ~500 字符，总字符量 ~26,000 <= 60,000 预算）
        List<DocumentContent.PageContent> withinBudgetPages = new ArrayList<>(50);
        for (int p = 1; p <= 50; p++) {
            withinBudgetPages.add(createPage(p, false));
        }
        synthetic50PageDocWithinBudget = new DocumentContent(
                "synthetic_50p_within_budget.docx", "hash_within_50", "", withinBudgetPages);

        // 3. 构建无标题 50 页超预算文档（仅靠正文关键词打分）
        List<DocumentContent.PageContent> noHeadingsPages = new ArrayList<>(50);
        for (DocumentContent.PageContent p : overBudgetPages) {
            noHeadingsPages.add(new DocumentContent.PageContent(p.pageNumber(), p.text(), Collections.emptyList()));
        }
        synthetic50PageDocNoHeadings = new DocumentContent(
                "synthetic_50p_no_headings.docx", "hash_no_headings_50", "", noHeadingsPages);

        // 4. 构建 50 页招标文件
        synthetic50PageTenderDoc = new DocumentContent(
                "synthetic_50p_tender.docx", "hash_tender_50", "", overBudgetPages);
    }

    private static DocumentContent.PageContent createPage(final int pageNumber, final boolean longFiller) {
        String filler = longFiller ? FILLER_TEXT_LONG : FILLER_TEXT_SHORT;
        return switch (pageNumber) {
            case 20 -> new DocumentContent.PageContent(20,
                    "【第二十章 企业资质证书与原厂授权】\n"
                            + "投标人依法取得 ISO9001 质量管理体系认证证书、环境管理体系认证，证书均在国家认监委有效期内；"
                            + "同时提供核心产品制造商出具的品牌原厂授权书与授权声明，具备独立法人营业执照，完全满足资格审查一票否决实质性要求。\n"
                            + filler,
                    List.of("第二十章 企业资质证书与原厂授权"));
            case 35 -> new DocumentContent.PageContent(35,
                    "【第三十五章 投标人须知前附表与偏离表响应】\n"
                            + "投标人须知前附表关键条款响应表：对项目概况、标段划分、投标有效期90天、质保期3年、工期要求逐项完全响应无偏离。"
                            + "商务偏离表与技术偏离表：付款方式及支付条款、交货期供货周期、售后服务承诺全部实质性响应，声明无负偏离。\n"
                            + filler,
                    List.of("第三十五章 投标人须知前附表与偏离表响应"));
            case 45 -> new DocumentContent.PageContent(45,
                    "【第四十五章 资格性评审业绩与技术方案评分细则响应】\n"
                            + "资格性评审条款证明：提供近三年类似业绩合同与财务审计报告、资产负债表、纳税及社保证明；项目经理具备高级职称与信誉声明。"
                            + "技术与商务评分点响应：技术方案与项目实施组织设计完整周详，质量保证措施、售后服务体系方案、进度计划及应急预案详尽，响应评分细则。\n"
                            + filler,
                    List.of("第四十五章 资格性评审业绩与技术方案评分细则响应"));
            case 50 -> new DocumentContent.PageContent(50,
                    "【第五十章 澄清文件与答疑纪要响应说明】\n"
                            + "澄清与答疑文件响应：关于本项目第1号澄清文件、答疑纪要及补遗书的响应说明，已收悉全部澄清公告与变更通知，并出具正式确认函完全响应。\n"
                            + filler,
                    List.of("第五十章 澄清文件与答疑纪要响应说明"));
            default -> new DocumentContent.PageContent(pageNumber,
                    "第 " + pageNumber + " 页常规文档内容。\n" + filler,
                    List.of("第 " + pageNumber + " 章节"));
        };
    }

    // =========================================================================
    // 1. 7 项语义条款远端页面检索与页码标签断言
    // =========================================================================

    public static Stream<Arguments> provideSevenSemanticItemsAndExpectedPages() {
        return Stream.of(
                Arguments.of(BidQualityCheckItem.QUALIFICATION_MATCH, 20, "ISO9001 质量管理体系认证"),
                Arguments.of(BidQualityCheckItem.QUALIFICATION_VETO, 20, "原厂授权书"),
                Arguments.of(BidQualityCheckItem.FRONT_TABLE_RESPONSE, 35, "投标人须知前附表关键条款响应表"),
                Arguments.of(BidQualityCheckItem.RESPONSIVENESS_REVIEW, 35, "商务偏离表与技术偏离表"),
                Arguments.of(BidQualityCheckItem.QUALIFICATION_REVIEW, 45, "近三年类似业绩合同与财务审计报告"),
                Arguments.of(BidQualityCheckItem.SCORING_RESPONSE, 45, "技术方案与项目实施组织设计"),
                Arguments.of(BidQualityCheckItem.CLARIFICATION_RESPONSE, 50, "澄清与答疑文件响应")
        );
    }

    @ParameterizedTest(name = "[{index}] {0} 应成功命中第 {1} 页远端证据")
    @MethodSource("provideSevenSemanticItemsAndExpectedPages")
    @DisplayName("LlmAdjudicator.buildPrompt 成功召回远端目标页并包含 --- [第 X 页] --- 标识")
    void testBuildPrompt_RetrievesDistantEvidencePage_And_ContainsPageTag(
            final BidQualityCheckItem item, final int expectedPage, final String expectedSnippet) {
        String prompt = LlmAdjudicator.buildPrompt(
                item,
                synthetic50PageDocOverBudget,
                DocumentContent.empty(),
                sampleRequirements,
                List.of());

        // 1. 验证包含了目标远端页面的页码标签 --- [第 X 页] ---
        String expectedPageTag = "--- [第 " + expectedPage + " 页] ---";
        assertThat(prompt)
                .as("Check item %s must include tag %s in prompt", item.getCode(), expectedPageTag)
                .contains(expectedPageTag);

        // 2. 验证包含了目标远端页面的关键证据文本
        assertThat(prompt)
                .as("Check item %s must include evidence text '%s'", item.getCode(), expectedSnippet)
                .contains(expectedSnippet);

        // 3. 验证提示词固定骨架完整
        assertThat(prompt).contains("你是投标文件质量核查专家");
        assertThat(prompt).contains("【招标要求（LLM 结构化提取）】");
        assertThat(prompt).contains("【企业知识库真实记录】");
        assertThat(prompt).contains("【招标文件相关内容】");
        assertThat(prompt).contains("【投标文件相关内容】");
    }

    // =========================================================================
    // 2. 杜绝 6000 字符硬截断验证
    // =========================================================================

    @Test
    @DisplayName("50页标书总字符量达 70,000+ 字符，第 20/35/45/50 页物理偏移远超 6000 字符，均未被截断丢弃")
    void testLegacy6000CharTruncation_IsCompletelyEliminated() {
        // 计算第 20、35、45、50 页在原始文档中的字符物理偏移量
        int charOffsetPage20 = calculateCharOffset(synthetic50PageDocOverBudget, 20);
        int charOffsetPage35 = calculateCharOffset(synthetic50PageDocOverBudget, 35);
        int charOffsetPage45 = calculateCharOffset(synthetic50PageDocOverBudget, 45);
        int charOffsetPage50 = calculateCharOffset(synthetic50PageDocOverBudget, 50);

        // 严格断言：所有目标页面的物理偏移均远大于旧版的 6000 字符上限
        assertThat(charOffsetPage20).isGreaterThan(20_000);
        assertThat(charOffsetPage35).isGreaterThan(40_000);
        assertThat(charOffsetPage45).isGreaterThan(55_000);
        assertThat(charOffsetPage50).isGreaterThan(65_000);

        // 验证 CLARIFICATION_RESPONSE 的第 50 页（处于 ~67,000 字符末尾位置）能被成功生成进 Prompt
        String prompt50 = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.CLARIFICATION_RESPONSE,
                synthetic50PageDocOverBudget,
                DocumentContent.empty(),
                sampleRequirements,
                List.of());

        assertThat(prompt50).contains("--- [第 50 页] ---");
        assertThat(prompt50).contains("澄清与答疑文件响应");
    }

    @Test
    @DisplayName("预算内（26,000 字符）50 页文档 100% 全文无损透传，包含全部 50 个页码标签")
    void testWithinBudget50PageDoc_FullPassThroughWithoutAnyLoss() {
        String prompt = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                synthetic50PageDocWithinBudget,
                DocumentContent.empty(),
                sampleRequirements,
                List.of());

        // 验证全部 50 个页码标签均完整存在
        for (int p = 1; p <= 50; p++) {
            assertThat(prompt).contains("--- [第 " + p + " 页] ---");
        }

        // 验证页码呈现严格自然递增顺序
        int lastIndex = -1;
        for (int p = 1; p <= 50; p++) {
            int currentIndex = prompt.indexOf("--- [第 " + p + " 页] ---");
            assertThat(currentIndex).isGreaterThan(lastIndex);
            lastIndex = currentIndex;
        }
    }

    // =========================================================================
    // 3. 对抗性边界与复杂场景测试
    // =========================================================================

    @Test
    @DisplayName("无 headings 标题元数据场景：纯正文关键词打分仍能准确命中第 20、35、45、50 页")
    void testNoHeadings50PageDoc_ScoresOnBodyTextAndRetrievesDistantPages() {
        String prompt20 = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                synthetic50PageDocNoHeadings, DocumentContent.empty(), sampleRequirements, List.of());
        assertThat(prompt20).contains("--- [第 20 页] ---").contains("ISO9001 质量管理体系认证");

        String prompt35 = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                synthetic50PageDocNoHeadings, DocumentContent.empty(), sampleRequirements, List.of());
        assertThat(prompt35).contains("--- [第 35 页] ---").contains("商务偏离表与技术偏离表");

        String prompt45 = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.SCORING_RESPONSE,
                synthetic50PageDocNoHeadings, DocumentContent.empty(), sampleRequirements, List.of());
        assertThat(prompt45).contains("--- [第 45 页] ---").contains("技术方案与项目实施组织设计");

        String prompt50 = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.CLARIFICATION_RESPONSE,
                synthetic50PageDocNoHeadings, DocumentContent.empty(), sampleRequirements, List.of());
        assertThat(prompt50).contains("--- [第 50 页] ---").contains("澄清与答疑文件响应");
    }

    @Test
    @DisplayName("招标文件与投标文件双 50 页场景：buildPrompt 分别按各自预算独立检索并组装")
    void testBothBidAndTender50Pages_RetrievesBothContextsIndependently() {
        String prompt = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.SCORING_RESPONSE,
                synthetic50PageDocOverBudget,
                synthetic50PageTenderDoc,
                sampleRequirements,
                List.of());

        // 验证【招标文件相关内容】与【投标文件相关内容】均成功召回第 45 页
        int tenderSectionIdx = prompt.indexOf("【招标文件相关内容】");
        int bidSectionIdx = prompt.indexOf("【投标文件相关内容】");
        assertThat(tenderSectionIdx).isLessThan(bidSectionIdx);

        String tenderPart = prompt.substring(tenderSectionIdx, bidSectionIdx);
        String bidPart = prompt.substring(bidSectionIdx);

        assertThat(tenderPart).contains("--- [第 45 页] ---").contains("技术与商务评分点响应");
        assertThat(bidPart).contains("--- [第 45 页] ---").contains("技术与商务评分点响应");
    }

    @Test
    @DisplayName("端到端裁决集成：50页超长标书在 LlmAdjudicator.adjudicate 中完成全部 7 项裁决，无崩溃且证据链完整")
    void testAdjudicate_EndToEnd50PageDocument_SucceedsWithAllItems() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> {
            assertThat(prompt).contains("--- [第");
            return LlmVerdict.success(new LlmVerdict("PASS", "", "全标书分析通过", ""));
        });

        List<Integer> progressSteps = new ArrayList<>();
        Map<String, QualityCheckItemResult> results = adjudicator.adjudicate(
                synthetic50PageDocOverBudget,
                synthetic50PageTenderDoc,
                sampleRequirements,
                List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "7", "资质证书: ISO9001", true)),
                Set.of(),
                (progress, step) -> progressSteps.add(progress));

        assertThat(results).hasSize(7);
        assertThat(results.values()).allSatisfy(r -> {
            if (r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())) {
                assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(r.conclusion()).contains("尚需人工确认");
            } else {
                assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
                assertThat(r.conclusion()).contains("全标书分析通过");
            }
        });
        assertThat(progressSteps).contains(100);
    }

    private static int calculateCharOffset(final DocumentContent doc, final int targetPage) {
        int offset = 0;
        for (DocumentContent.PageContent page : doc.pages()) {
            if (page.pageNumber() == targetPage) {
                return offset;
            }
            offset += page.text() != null ? page.text().length() : 0;
        }
        return offset;
    }
}
