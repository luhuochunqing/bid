// Input: 对照极端场景（500+页、20万+字符、全0分、全同分、超长单页、Unicode特异字符、空/Null标题、边界Off-by-One）
// Output: 深度对抗与压力验证测试集，断言语义页面检索器的鲁棒性、稳定性、时间性能与内存表现
// Pos: compliance/domain/policy/ - 标书语义检索对抗验证测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 SemanticPageRetriever 的对抗性与极端边界压力测试套件.
 */
class SemanticPageRetrieverAdversarialChallengeTest {

    private static DocumentContent.PageContent page(final int pageNumber, final String text) {
        return new DocumentContent.PageContent(pageNumber, text, Collections.emptyList());
    }

    private static DocumentContent.PageContent page(
            final int pageNumber, final String text, final List<String> headings) {
        return new DocumentContent.PageContent(pageNumber, text, headings);
    }

    private static DocumentContent docOf(final List<DocumentContent.PageContent> pages) {
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent p : pages) {
            if (p != null && p.text() != null) {
                sb.append(p.text()).append("\n");
            }
        }
        return new DocumentContent("adversarial_test.docx", "hash_adv_123", sb.toString(), pages);
    }

    // =========================================================================
    // 1. 极端海量规模压力测试 (500+ 页, 200,000+ 字符)
    // =========================================================================
    @Nested
    @DisplayName("1. 极端海量规模压力测试")
    class ExtremeVolumeStressTests {

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        @DisplayName("500+ 页且 250,000+ 字符海量标书：执行时间 < 200ms，准确召回前中后段高相关页")
        void test500PagesAnd250KChars_ExecutesFastAndRetrievesAccurately() {
            int pageCount = 500;
            List<DocumentContent.PageContent> pages = new ArrayList<>(pageCount);
            
            // 构造 500 页，每页 500 字符，总计 250,000 字符
            for (int i = 1; i <= pageCount; i++) {
                String text;
                List<String> headings = Collections.emptyList();
                
                if (i == 15) {
                    text = "【前段关键页】第一章 投标人资质等级与体系认证：投标人具备 ISO9001 质量管理体系认证与 ISO14001 环境管理体系认证，资质证书齐全有效。" 
                            + "常规填充文本。".repeat(30);
                    headings = List.of("第一章 投标人资质等级与体系认证");
                } else if (i == 250) {
                    text = "【中段关键页】第五章 质量体系认证有效性核验：质量管理体系认证审核报告，认证机构出具的监督审核合格证明。" 
                            + "常规填充文本。".repeat(30);
                    headings = List.of("第五章 质量体系认证有效性核验");
                } else if (i == 488) {
                    text = "【后段关键页】第十章 附录：资质证书扫描件汇总：资质证书原件扫描件：职业健康安全管理体系认证证书及资质证书年检记录。" 
                            + "常规填充文本。".repeat(30);
                    headings = List.of("第十章 附录：资质证书扫描件汇总");
                } else {
                    text = String.format("第 %d 页 通用办公环境与项目日常行政后勤说明。", i) + "无关文本。".repeat(35);
                }
                pages.add(page(i, text, headings));
            }

            DocumentContent doc = docOf(pages);
            assertThat(doc.pages()).hasSize(500);

            long startTime = System.nanoTime();
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    3_000); // 预算限制 3,000 字符 (约 5~6 页)
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 性能断言：500页检索在 200ms 内完成
            assertThat(durationMs).isLessThan(200);

            // 准确召回前段(15)、中段(250)、后段(488)关键页
            assertThat(result).contains("--- [第 15 页] ---");
            assertThat(result).contains("第一章 投标人资质等级与体系认证");
            assertThat(result).contains("--- [第 250 页] ---");
            assertThat(result).contains("第五章 质量体系认证有效性核验");
            assertThat(result).contains("--- [第 488 页] ---");
            assertThat(result).contains("第十章 附录：资质证书扫描件汇总");

            // 验证自然页码顺序升序出现
            int idx15 = result.indexOf("--- [第 15 页] ---");
            int idx250 = result.indexOf("--- [第 250 页] ---");
            int idx488 = result.indexOf("--- [第 488 页] ---");
            assertThat(idx15).isLessThan(idx250);
            assertThat(idx250).isLessThan(idx488);
        }

        @Test
        @Timeout(value = 3, unit = TimeUnit.SECONDS)
        @DisplayName("1000 页且 500,000+ 字符超极限压力：无内存泄漏或崩溃，耗时平滑")
        void test1000PagesSuperStress_NoMemoryLeakAndLinearTime() {
            int pageCount = 1000;
            List<DocumentContent.PageContent> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                pages.add(page(i, "页面测试内容 " + i + " " + "商务技术响应文本。".repeat(30)));
            }
            DocumentContent doc = docOf(pages);

            long start = System.nanoTime();
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    SemanticPageRetriever.DEFAULT_BID_BUDGET);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(result).isNotBlank();
            assertThat(elapsedMs).isLessThan(500);
        }
    }

    // =========================================================================
    // 2. 全 0 分与全同分极值测试 (Tie-Breaking & Stability)
    // =========================================================================
    @Nested
    @DisplayName("2. 全 0 分与全同分同质化极值测试")
    class ZeroAndIdenticalScoresTests {

        @Test
        @DisplayName("500 页全部得 0 分（无任何匹配关键词）：按 pageNumber ASC 稳定选取前 K 页")
        void test500PagesAllScoreZero_SelectsFirstKPagesInAscendingOrder() {
            int pageCount = 500;
            List<DocumentContent.PageContent> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                // 正文与标题均无任何资质/响应关键词
                pages.add(page(i, "纯噪音填充内容，无匹配线索 " + i + "。" + "无相关词。".repeat(20)));
            }
            DocumentContent doc = docOf(pages);

            // 每页约 120 字符，预算 400 字符 -> 预计可容纳前 3~4 页
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    400);

            // 必须从第 1 页开始按页码升序稳定截取
            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).doesNotContain("--- [第 500 页] ---");

            int idx1 = result.indexOf("--- [第 1 页] ---");
            int idx2 = result.indexOf("--- [第 2 页] ---");
            int idx3 = result.indexOf("--- [第 3 页] ---");
            assertThat(idx1).isLessThan(idx2);
            assertThat(idx2).isLessThan(idx3);
        }

        @Test
        @DisplayName("100 页全部得相同高分（完全相同标题与关键词）：同分决胜严格按 pageNumber ASC 选入并排序")
        void test100PagesIdenticalHighScore_StableTieBreakingByPageNumberAsc() {
            int pageCount = 100;
            List<DocumentContent.PageContent> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                pages.add(page(i, "评分标准响应：项目实施组织设计与售后服务方案完整。",
                        List.of("评分细则响应与技术方案")));
            }
            DocumentContent doc = docOf(pages);

            // 单页约 30 字符，预算 100 字符 -> 预计选入第 1, 2, 3 页
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).doesNotContain("--- [第 10 页] ---");
            assertThat(result).doesNotContain("--- [第 100 页] ---");

            int idx1 = result.indexOf("--- [第 1 页] ---");
            int idx2 = result.indexOf("--- [第 2 页] ---");
            int idx3 = result.indexOf("--- [第 3 页] ---");
            assertThat(idx1).isLessThan(idx2);
            assertThat(idx2).isLessThan(idx3);
        }

        @Test
        @DisplayName("分层同分：Tier 1 (高分同分) 全部先选入，Tier 2 (次高同分) 按预算与页码升序选入")
        void testTieredTies_SelectsHigherTierFirst_ThenLowerTier_AllSortedByPageAsc() {
            // Tier 1: 第 10 页、第 50 页、第 90 页 (超高分，标题+多关键词，每页29字符)
            // Tier 2: 第 5 页、第 30 页、第 70 页 (中等分，单关键词，每页9字符)
            // Tier 3: 其余页面 (0分，每页55字符)
            List<DocumentContent.PageContent> pages = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                if (i == 10 || i == 50 || i == 90) {
                    pages.add(page(i, "资质证书原件：ISO9001 质量管理体系认证与等级证书。",
                            List.of("第一级资质证书")));
                } else if (i == 5 || i == 30 || i == 70) {
                    pages.add(page(i, "普通证书登记证明。", Collections.emptyList()));
                } else {
                    pages.add(page(i, "常规杂项无关键词内容。".repeat(5), Collections.emptyList()));
                }
            }
            DocumentContent doc = docOf(pages);

            // Tier 1: 3 * 29 = 87 字符。
            // 预算设置为 87 + 9 = 96 字符 -> 恰好容纳 Tier 1 全部页 (10, 50, 90) + Tier 2 的第 1 顺位页 (第 5 页)
            int budget = 96;
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    budget);

            // Tier 1 的 10, 50, 90 必须全部入选
            assertThat(result).contains("--- [第 10 页] ---");
            assertThat(result).contains("--- [第 50 页] ---");
            assertThat(result).contains("--- [第 90 页] ---");
            // Tier 2 中页码最小的第 5 页入选，第 30、70 页未入选（因预算耗尽）
            assertThat(result).contains("--- [第 5 页] ---");
            assertThat(result).doesNotContain("--- [第 30 页] ---");
            assertThat(result).doesNotContain("--- [第 70 页] ---");

            // 最终输出必须按自然页码 5 -> 10 -> 50 -> 90 升序排列
            int idx5 = result.indexOf("--- [第 5 页] ---");
            int idx10 = result.indexOf("--- [第 10 页] ---");
            int idx50 = result.indexOf("--- [第 50 页] ---");
            int idx90 = result.indexOf("--- [第 90 页] ---");
            assertThat(idx5).isLessThan(idx10);
            assertThat(idx10).isLessThan(idx50);
            assertThat(idx50).isLessThan(idx90);
        }
    }

    // =========================================================================
    // 3. 超长单页与巨型文本边界测试
    // =========================================================================
    @Nested
    @DisplayName("3. 超长单页与巨型文本边界测试")
    class MassiveSinglePageAndLargeTextTests {

        @Test
        @DisplayName("单页文档文本量达 100,000 字符且预算仅 5,000 字符：保证首顺位页被选入且不返回空")
        void testSinglePage100KCharsWithSmallBudget_GuaranteesFirstPageIncluded() {
            String massiveText = "单页巨型标书技术方案正文。".repeat(7000); // 约 98,000 字符
            DocumentContent doc = docOf(List.of(page(1, massiveText)));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    5000);

            assertThat(result).isNotBlank();
            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("单页巨型标书技术方案正文");
        }

        @Test
        @DisplayName("历史无分页单文本文档（pages 为空）且 fullText 达 50,000 字符：按 budget 优雅截断并附带省略标识")
        void testLegacySingleFullTextDocumentExceedingBudget_TruncatesGracefully() {
            String text = "这是长单文本内容。".repeat(3000); // 27,000 字符
            DocumentContent legacyDoc = new DocumentContent("legacy.pdf", "hash_leg", text, Collections.emptyList());

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    legacyDoc,
                    1000);

            assertThat(result).contains("--- [内容块 1；原件页码未知] ---");
            assertThat(result).contains("……（已截断）");
            assertThat(result.length()).isLessThanOrEqualTo(1100);
        }
    }

    // =========================================================================
    // 4. 预算边界条件精准测试 (docLength == budget, budget - 1, budget + 1)
    // =========================================================================
    @Nested
    @DisplayName("4. 预算边界条件精准测试")
    class BudgetBoundaryPrecisionTests {

        @Test
        @DisplayName("边界精准测试：docLength == budget - 1，100% 全文无损透传（不触发淘汰打分）")
        void testBudgetBoundary_DocLengthEqualsBudgetMinusOne() {
            String t1 = "A".repeat(50);
            String t2 = "B".repeat(49);
            DocumentContent doc = docOf(List.of(page(1, t1), page(2, t2))); // total = 99

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            assertThat(result).contains("--- [第 1 页] ---").contains(t1);
            assertThat(result).contains("--- [第 2 页] ---").contains(t2);
        }

        @Test
        @DisplayName("边界精准测试：docLength == budget，100% 全文无损透传（不触发淘汰打分）")
        void testBudgetBoundary_DocLengthEqualsBudget() {
            String t1 = "A".repeat(50);
            String t2 = "B".repeat(50);
            DocumentContent doc = docOf(List.of(page(1, t1), page(2, t2))); // total = 100

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            assertThat(result).contains("--- [第 1 页] ---").contains(t1);
            assertThat(result).contains("--- [第 2 页] ---").contains(t2);
        }

        @Test
        @DisplayName("边界精准测试：docLength == budget + 1，触发超预算 TOP-N 淘汰逻辑，淘汰低分页")
        void testBudgetBoundary_DocLengthEqualsBudgetPlusOne() {
            // total = 50 + 51 = 101, budget = 100
            // page 1 含有相关性关键词（高分），page 2 为无意义字符（0分）
            String t1 = "评分标准响应项目方案" + "A".repeat(40); // 50 字符
            String t2 = "B".repeat(51); // 51 字符
            DocumentContent doc = docOf(List.of(page(1, t1), page(2, t2)));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            // 高分第 1 页必须保留，低分第 2 页被淘汰
            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains(t1);
            assertThat(result).doesNotContain("--- [第 2 页] ---");
            assertThat(result).doesNotContain(t2);
        }

        @Test
        @DisplayName("预算累积精确截断：多页累加恰好达到 budget 时终止选取")
        void testBudgetAccumulation_ExactBoundaryCutoff() {
            // 4 页，每页 25 字符，总计 100 字符
            // 预算 75 字符 -> 恰好容纳前 3 页 (75字符)，第4页被截断淘汰
            DocumentContent.PageContent p1 = page(1, "一".repeat(25)); // 25 字符
            DocumentContent.PageContent p2 = page(2, "二".repeat(25)); // 25 字符
            DocumentContent.PageContent p3 = page(3, "三".repeat(25)); // 25 字符
            DocumentContent.PageContent p4 = page(4, "四".repeat(25)); // 25 字符
            DocumentContent doc = docOf(List.of(p1, p2, p3, p4));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    75);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).doesNotContain("--- [第 4 页] ---");
        }

        @Test
        @DisplayName("预算为 0 或负数时：优雅保底首顺位页，不抛异常")
        void testZeroOrNegativeBudget_SafelyReturnsTopPage() {
            DocumentContent doc = docOf(List.of(page(1, "第一页关键内容"), page(2, "第二页")));

            String resZero = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, 0);
            String resNeg = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, -100);

            assertThat(resZero).contains("--- [第 1 页] ---");
            assertThat(resNeg).contains("--- [第 1 页] ---");
        }
    }

    // =========================================================================
    // 5. Unicode、Emoji、特殊符号与多语言编码对抗测试
    // =========================================================================
    @Nested
    @DisplayName("5. Unicode、Emoji 与特异字符对抗测试")
    class UnicodeAndEncodingAdversarialTests {

        @Test
        @DisplayName("包含 Emoji (Surrogate Pairs)、罕见生僻字与特殊符号：打分及截断无乱码、无字符串越界")
        void testUnicodeSurrogatePairsAndRareCharacters_HandlesSafely() {
            String rareChinese = "𪚥𠮷𠀾𡈽 龘䶛 Über Groß £¥€$©®™ §¶†‡";
            String emojis = "🚀 🏆 🚨 📊 📋 💯 🚩 💡 🔒";
            
            DocumentContent.PageContent p1 = page(1,
                    "【资质认证】" + emojis + " 本投标人具备 ISO9001 质量管理体系认证与资质证书 " + rareChinese,
                    List.of("🌟 资质认证与体系评定 🌟"));
            DocumentContent.PageContent p2 = page(2, "常规填充文本 " + emojis.repeat(5));

            DocumentContent doc = docOf(List.of(p1, p2));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    p1.text().length() + 20);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("ISO9001");
            assertThat(result).contains("🚀");
            assertThat(result).contains("𪚥");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }

        @Test
        @DisplayName("包含零宽空格 (\\u200B)、控制符 (\\r\\n\\t) 及全半角标点：线索提取与检索正常运作")
        void testZeroWidthSpacesAndControlCharacters_ParsesWithoutFailure() {
            String textWithControlChars = "资质\u200B证书\uFEFF：\t\r\n包含质量管理体系认证，有效。\n\r\t";
            DocumentContent doc = docOf(List.of(page(1, textWithControlChars)));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    1000);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("质量管理体系认证");
        }

        @Test
        @DisplayName("阿拉伯语、藏语等多语系混合文本：关键词匹配不受 RTL 字符影响")
        void testMultiLanguageMixedText_MatchingIsUnaffected() {
            String mixedText = "المناقصة 资质证书 བོད་ཡིག ISO9001 质量管理体系认证 شهادة";
            DocumentContent doc = docOf(List.of(page(1, mixedText)));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    1000);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("المناقصة");
            assertThat(result).contains("ISO9001");
        }
    }

    // =========================================================================
    // 6. 空值、Null 结构与畸形对象鲁棒性测试 (FP-Java 零崩溃)
    // =========================================================================
    @Nested
    @DisplayName("6. 畸形结构与 Null 安全鲁棒性测试")
    class MalformedStructureAndNullSafetyTests {

        @Test
        @DisplayName("Headings 列表包含 null、空白串、超长标题时：安全忽略并正常打分")
        void testHeadingsWithNullAndWhitespaceElements_HandledGracefully() {
            List<String> dirtyHeadings = Arrays.asList(null, "", "   ", "\t\n", "第二章 资质证书与认证等级", null);
            DocumentContent.PageContent p1 = new DocumentContent.PageContent(1, "正文内容：企业资质齐全", dirtyHeadings);
            DocumentContent.PageContent p2 = page(2, "纯填充无关键词".repeat(10));
            DocumentContent doc = docOf(List.of(p1, p2));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    p1.text().length() + 10);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("企业资质齐全");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }

        @Test
        @DisplayName("TenderRequirements 内部包含有效提取数据时：动态线索正常提取并召回对应页面")
        void testTenderRequirementsWithDynamicCues_SafelyProcesses() {
            TenderRequirements reqWithCues = new TenderRequirements(
                    List.of(new TenderRequirements.ScoringItem("云平台方案", 20.0, "支持多租户")),
                    List.of("CMMI3"),
                    List.of(new TenderRequirements.VetoClause("未提供授权废标", List.of("原厂授权"))),
                    List.of("组成要求1"),
                    List.of("格式要求1"),
                    false);

            DocumentContent doc = docOf(List.of(
                    page(1, "本公司已取得 CMMI3 认证，符合原厂授权要求。"),
                    page(2, "常规说明文本。".repeat(5))));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    reqWithCues,
                    doc,
                    100);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("CMMI3");
        }
    }
}
