package com.xiyu.bid.compliance;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.policy.LlmAdjudicator;
import com.xiyu.bid.compliance.domain.policy.SemanticPageRetriever;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.fixture.BidDocumentTestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 3 独立对抗性验证与压力测试套件 (Empirical Challenger M3).
 *
 * <p>本套件由 EMPIRICAL CHALLENGER 独立执行，专门压力测试：
 * 1. 60 页 35,000+ 字符标书在全部 7 个语义检查项下的全标书提示词生成、远端页面（偏移量 > 28,000 字符）捕获与页码标签存在性；
 * 2. 60 页 27,000 字符预算内标书在透传时 100% 无单字符丢失与严格升序排列；
 * 3. 彻底杜绝旧版 6000 字符前缀截断；
 * 4. 超预算 (80,000+ 字符) 语义排序与自然页码重排；
 * 5. 极端边界与容错（空页、单页、超大页数性能基准）。
 */
public class Milestone3EmpiricalChallengerTest {

    private static DocumentContent doc35kPlus;
    private static DocumentContent doc27kWithinBudget;
    private static DocumentContent doc81kOverBudget;
    private static DocumentContent tenderDoc;
    private static TenderRequirements tenderReqs;

    @BeforeAll
    static void initAll() {
        doc35kPlus = BidDocumentTestFixtures.createRealistic30kPlusBidDocument();
        doc27kWithinBudget = BidDocumentTestFixtures.createWithinBudgetBidDocument();
        doc81kOverBudget = BidDocumentTestFixtures.createOverBudgetBidDocument();
        tenderDoc = BidDocumentTestFixtures.createStandardTenderDocument();
        tenderReqs = BidDocumentTestFixtures.createStandardTenderRequirements();
    }

    // =========================================================================
    // 1. 60 页 35,000+ 字符标书：7 个语义检查项全量断言与远端偏移量检验
    // =========================================================================
    @Nested
    @DisplayName("1. 60 页 35,000+ 字符标书全量 Prompt 验证（7 个语义项）")
    class Realistic35kPlusDocSevenItemsTests {

        @Test
        @DisplayName("经验证 1.1：35K+ 标书总字符量必须 > 35,000 且页数严格为 60 页")
        void testDoc35kPlus_SizeAndPageCount() {
            int totalChars = calculateTotalDocLength(doc35kPlus);
            assertThat(totalChars)
                    .as("Total characters in realistic bid doc must exceed 35,000")
                    .isGreaterThan(35_000);
            assertThat(doc35kPlus.pages()).hasSize(60);
        }

        @Test
        @DisplayName("经验证 1.2：第 48, 49, 52, 56, 58 页在原始文档中的物理字符偏移量全部 > 28,000")
        void testDistantPagesPhysicalOffsets_AllExceed28k() {
            int offset48 = calculatePageOffset(doc35kPlus, 48);
            int offset49 = calculatePageOffset(doc35kPlus, 49);
            int offset52 = calculatePageOffset(doc35kPlus, 52);
            int offset56 = calculatePageOffset(doc35kPlus, 56);
            int offset58 = calculatePageOffset(doc35kPlus, 58);

            assertThat(offset48).as("Page 48 offset").isGreaterThan(28_000);
            assertThat(offset49).as("Page 49 offset").isGreaterThan(29_000);
            assertThat(offset52).as("Page 52 offset").isGreaterThan(31_000);
            assertThat(offset56).as("Page 56 offset").isGreaterThan(33_000);
            assertThat(offset58).as("Page 58 offset").isGreaterThan(35_000);
        }

        @ParameterizedTest(name = "[{index}] 检查项 {0} 生成的 Prompt 必须包含远端页码标签与文本")
        @EnumSource(value = BidQualityCheckItem.class, names = {
                "QUALIFICATION_MATCH",
                "QUALIFICATION_VETO",
                "FRONT_TABLE_RESPONSE",
                "RESPONSIVENESS_REVIEW",
                "QUALIFICATION_REVIEW",
                "SCORING_RESPONSE",
                "CLARIFICATION_RESPONSE"
        })
        @DisplayName("经验证 1.3：对每一个语义检查项，Prompt 均完整包含偏移量 > 28,000 的远端页面及其标签")
        void testAllSevenSemanticItems_PromptContainsDistantPagesAndTags(final BidQualityCheckItem item) {
            String prompt = LlmAdjudicator.buildPrompt(item, doc35kPlus, tenderDoc, tenderReqs, List.of());

            // 1. 验证包含全部关键远端页码标签 (偏移量 > 28,000 字符的第 48、49、52、56、58 页)
            assertThat(prompt).contains("--- [第 48 页] ---");
            assertThat(prompt).contains("--- [第 49 页] ---");
            assertThat(prompt).contains("--- [第 52 页] ---");
            assertThat(prompt).contains("--- [第 56 页] ---");
            assertThat(prompt).contains("--- [第 58 页] ---");

            // 2. 验证包含远端页面具体正文证据
            assertThat(prompt).contains("ISO9001 质量管理体系认证（甲级）");
            assertThat(prompt).contains("付款方式无负偏离");
            assertThat(prompt).contains("前附表关键条款响应表");
            assertThat(prompt).contains("关于招标文件第 1 号澄清答疑文件的确认与点对点响应函");
            assertThat(prompt).contains("原厂授权书");

            // 3. 验证总 Prompt 长度远超旧版 6000 字符限制
            assertThat(prompt.length())
                    .as("Prompt length must exceed 35,000 characters since entire 35k doc is within 60k budget")
                    .isGreaterThan(35_000);

            // 4. 验证不存在任何 6000 字符截断标记
            assertThat(prompt).doesNotContain("……（已截断）");
        }
    }

    // =========================================================================
    // 2. 27,000 字符预算内文档：100% 全量 60 页无损透传（单字符不丢）
    // =========================================================================
    @Nested
    @DisplayName("2. 27,000 字符预算内标书：100% 全量 60 页零字符损失与严格递增性")
    class WithinBudget27kDocZeroLossTests {

        @Test
        @DisplayName("经验证 2.1：27,000 字符标书在预算内透传时，60 个页面文本 100% 完整无损")
        void testRetrieveContext_WithinBudget_ZeroCharacterLoss() {
            String retrievedContext = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    tenderReqs,
                    doc27kWithinBudget,
                    60_000
            );

            // 1. 逐页验证每个页面的文本均 100% 原样包含在检索结果中（去除首尾空白后完全匹配）
            for (DocumentContent.PageContent page : doc27kWithinBudget.pages()) {
                String pageTag = "--- [第 " + page.pageNumber() + " 页] ---";
                assertThat(retrievedContext)
                        .as("Retrieved context must contain tag for page %d", page.pageNumber())
                        .contains(pageTag);

                String trimmedPageText = page.text().trim();
                assertThat(retrievedContext)
                        .as("Retrieved context must contain exact text for page %d without loss", page.pageNumber())
                        .contains(trimmedPageText);
            }

            // 2. 验证所有 60 个页面的页码标签严格按 1..60 单调递增排列
            int lastPos = -1;
            for (int p = 1; p <= 60; p++) {
                String tag = "--- [第 " + p + " 页] ---";
                int curPos = retrievedContext.indexOf(tag);
                assertThat(curPos)
                        .as("Page tag %s must be present and appear strictly after page %d", tag, p - 1)
                        .isGreaterThan(lastPos);
                lastPos = curPos;
            }
        }
    }

    // =========================================================================
    // 3. 超预算 (80,000+ 字符) 语义检索：受控预算与远端关键词精准命中
    // =========================================================================
    @Nested
    @DisplayName("3. 超预算 (80,000+ 字符) 语义检索对抗测试")
    class OverBudget81kDocRetrievalTests {

        public static Stream<Arguments> provideCheckItemsAndTargetPages() {
            return Stream.of(
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_MATCH, 48, "ISO9001 质量管理体系认证（甲级）"),
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_VETO, 58, "原厂授权书"),
                    Arguments.of(BidQualityCheckItem.FRONT_TABLE_RESPONSE, 52, "前附表关键条款响应表"),
                    Arguments.of(BidQualityCheckItem.RESPONSIVENESS_REVIEW, 49, "付款方式无负偏离"),
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_REVIEW, 16, "企业同类项目业绩一览表"),
                    Arguments.of(BidQualityCheckItem.SCORING_RESPONSE, 25, "AI 智能分析引擎与微服务高可用架构设计"),
                    Arguments.of(BidQualityCheckItem.CLARIFICATION_RESPONSE, 56, "关于招标文件第 1 号澄清答疑文件的确认与点对点响应函")
            );
        }

        @ParameterizedTest(name = "[{index}] 检查项 {0} 在超预算下必须精准召回第 {1} 页")
        @MethodSource("provideCheckItemsAndTargetPages")
        @DisplayName("经验证 3.1：超预算 81,000+ 字符标书在 7 项检查中均能 TOP-N 准确命中各自目标页")
        void testRetrieveContext_OverBudget_HitsTargetPagesPrecisely(
                final BidQualityCheckItem item, final int targetPage, final String targetSnippet) {
            String context = SemanticPageRetriever.retrieveContext(
                    item, tenderReqs, doc81kOverBudget, 60_000);

            // 1. 验证目标页码标签与核心文本成功入选
            assertThat(context).contains("--- [第 " + targetPage + " 页] ---");
            assertThat(context).contains(targetSnippet);

            // 2. 验证总字符量受到 60,000 字符预算控制（包含页码标签头部）
            assertThat(context.length()).isLessThanOrEqualTo(60_000 + 2000);

            // 3. 验证返回的页面按自然页码递增排列
            List<Integer> pages = extractPageNumbers(context);
            assertThat(pages).isNotEmpty();
            for (int i = 1; i < pages.size(); i++) {
                assertThat(pages.get(i)).isGreaterThan(pages.get(i - 1));
            }
        }
    }

    // =========================================================================
    // 4. 边界条件与极端压力测试
    // =========================================================================
    @Nested
    @DisplayName("4. 边界条件与鲁棒性压力测试")
    class EdgeCasesAndRobustnessTests {

        @Test
        @DisplayName("经验证 4.1：500 页大型标书检索性能与内存稳定性（毫秒级完成，纯函数不可变）")
        void testExtreme500PagesPerformance() {
            List<DocumentContent.PageContent> largePages = new ArrayList<>(500);
            for (int i = 1; i <= 500; i++) {
                largePages.add(new DocumentContent.PageContent(
                        i, "第 " + i + " 页内容：" + "填充文本 ".repeat(50), List.of("第 " + i + " 章节")));
            }
            DocumentContent largeDoc = new DocumentContent("500p.docx", "hash_500", "", largePages);

            long start = System.currentTimeMillis();
            String context = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, tenderReqs, largeDoc, 60_000);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(context).isNotBlank();
            assertThat(elapsed).as("500 pages retrieval should execute in < 200ms").isLessThan(200);
        }

        @Test
        @DisplayName("经验证 4.2：空页面、空白字符页、无标题页混合文档鲁棒性")
        void testEmptyAndBlankPagesRobustness() {
            List<DocumentContent.PageContent> weirdPages = List.of(
                    new DocumentContent.PageContent(1, null, List.of()),
                    new DocumentContent.PageContent(2, "   \n\t  ", List.of()),
                    new DocumentContent.PageContent(3, "有效第 3 页包含 ISO9001 质量认证", null),
                    new DocumentContent.PageContent(4, "", List.of("第四章 资质说明"))
            );
            DocumentContent weirdDoc = new DocumentContent("weird.docx", "hash_w", "", weirdPages);

            String context = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, tenderReqs, weirdDoc, 60_000);

            assertThat(context).contains("--- [第 3 页] ---");
            assertThat(context).contains("有效第 3 页包含 ISO9001 质量认证");
            assertThat(context).doesNotContain("--- [第 1 页] ---");
            assertThat(context).doesNotContain("--- [第 2 页] ---");
        }
    }

    // =========================================================================
    // 辅助计算
    // =========================================================================

    private static int calculateTotalDocLength(final DocumentContent doc) {
        int len = 0;
        for (DocumentContent.PageContent p : doc.pages()) {
            if (p.text() != null) {
                len += p.text().length();
            }
        }
        return len;
    }

    private static int calculatePageOffset(final DocumentContent doc, final int targetPage) {
        int offset = 0;
        for (DocumentContent.PageContent p : doc.pages()) {
            if (p.pageNumber() == targetPage) {
                return offset;
            }
            offset += p.text() != null ? p.text().length() : 0;
        }
        return offset;
    }

    private static List<Integer> extractPageNumbers(final String context) {
        List<Integer> list = new ArrayList<>();
        String marker = "--- [第 ";
        int idx = 0;
        while ((idx = context.indexOf(marker, idx)) != -1) {
            int endIdx = context.indexOf(" 页] ---", idx);
            if (endIdx != -1) {
                String numStr = context.substring(idx + marker.length(), endIdx).trim();
                list.add(Integer.parseInt(numStr));
                idx = endIdx;
            } else {
                idx += marker.length();
            }
        }
        return list;
    }
}
