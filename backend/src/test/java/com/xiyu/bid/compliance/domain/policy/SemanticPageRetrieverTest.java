// Input: 检查项 + 招标要求 + 分页文档 + 字符预算
// Output: 断言语义页面检索逻辑（预算内全文透传、超预算 TOP-N 打分重排、标题/关键词加权、动态招标要求线索、空文档兜底、无标题兜底）
// Pos: compliance/domain/policy/ - 标书语义检索单元测试（spec 049 R1 / T001-T005）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticPageRetrieverTest {

    private static DocumentContent.PageContent page(final int pageNumber, final String text) {
        return new DocumentContent.PageContent(pageNumber, text, Collections.emptyList());
    }

    private static DocumentContent.PageContent page(
            final int pageNumber, final String text, final List<String> headings) {
        return new DocumentContent.PageContent(pageNumber, text, headings);
    }

    private static DocumentContent docOf(final DocumentContent.PageContent... pages) {
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent p : pages) {
            if (p.text() != null) {
                sb.append(p.text()).append("\n");
            }
        }
        return new DocumentContent("test_doc.docx", "hash_123", sb.toString(), List.of(pages));
    }

    // =========================================================================
    // 1. 全文无损透传测试（文档总字符量 <= 预算）
    // =========================================================================
    @Nested
    @DisplayName("1. 预算内全文无损透传测试")
    class PassThroughTests {

        @Test
        @DisplayName("多页文档在预算内：100% 保留全部页面，按自然页码排序，并包含 --- [第 X 页] --- 标识")
        void testRetrieveContext_WhenWithinBudget_PreservesAllPagesAndPageTags() {
            DocumentContent doc = docOf(
                    page(1, "投标文件封面与基本信息"),
                    page(2, "法定代表人授权委托书与营业执照"),
                    page(3, "投标函与开标一览表报价"));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.COVER_INFO,
                    TenderRequirements.empty(),
                    doc,
                    10_000);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("投标文件封面与基本信息");
            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains("法定代表人授权委托书与营业执照");
            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).contains("投标函与开标一览表报价");

            // 验证页码顺序升序出现
            int idxPage1 = result.indexOf("--- [第 1 页] ---");
            int idxPage2 = result.indexOf("--- [第 2 页] ---");
            int idxPage3 = result.indexOf("--- [第 3 页] ---");
            assertThat(idxPage1).isLessThan(idxPage2);
            assertThat(idxPage2).isLessThan(idxPage3);
        }

        @Test
        @DisplayName("单页文档在预算内：正常格式化单页标识与正文")
        void testRetrieveContext_WhenSinglePageWithinBudget_FormatsWithPageTag() {
            DocumentContent doc = docOf(page(1, "单页投标文件正文内容"));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    5_000);

            assertThat(result).isEqualTo("--- [第 1 页] ---\n单页投标文件正文内容");
        }

        @Test
        @DisplayName("临界值测试：文档总长度恰好等于预算，所有页面全部保留")
        void testRetrieveContext_WhenDocLengthEqualsBudget_RetainsAllPages() {
            String text1 = "一".repeat(50);
            String text2 = "二".repeat(50);
            DocumentContent doc = docOf(page(1, text1), page(2, text2));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains(text1);
            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains(text2);
        }

        @Test
        @DisplayName("便捷方法测试：retrieveBidContext 与 retrieveTenderContext 分别使用 60,000 与 30,000 默认预算")
        void testRetrieveBidContext_And_retrieveTenderContext_UseDefaultBudgets() {
            DocumentContent doc = docOf(page(1, "标书内容"));

            String bidResult = SemanticPageRetriever.retrieveBidContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc);
            String tenderResult = SemanticPageRetriever.retrieveTenderContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc);

            assertThat(bidResult).contains("--- [第 1 页] ---").contains("标书内容");
            assertThat(tenderResult).contains("--- [第 1 页] ---").contains("标书内容");
            assertThat(SemanticPageRetriever.DEFAULT_BID_BUDGET).isEqualTo(60_000);
            assertThat(SemanticPageRetriever.DEFAULT_TENDER_BUDGET).isEqualTo(30_000);
        }
    }

    // =========================================================================
    // 2. 超出预算 TOP-N 检索与自然页码重排测试
    // =========================================================================
    @Nested
    @DisplayName("2. 超出预算 TOP-N 检索与重排测试")
    class TopNRetrievalAndOrderingTests {

        @Test
        @DisplayName("超预算：挑选高相关性页面，剔除无关页面，且最终结果严格按自然页码升序排列")
        void testRetrieveContext_WhenExceedsBudget_SelectsTopScoringPagesAndReordersChronologically() {
            // 构造 6 页文档，总长度 > 1500 字符，设置预算为 200 字符
            DocumentContent.PageContent page1 = page(1, "第1页 封面与目录，介绍项目概况与投标单位名称。".repeat(15));
            DocumentContent.PageContent page2 = page(2, "第2页 投标保证声明，无特别业务条款。".repeat(15));
            DocumentContent.PageContent page3 = page(3, "第3页 通用办公环境说明，日常后勤支持。".repeat(15));
            DocumentContent.PageContent page4 = page(4,
                    "第4页 评分标准响应：详细阐述项目实施组织设计与技术方案架构，配备专业实施团队。第四章 评分项响应与技术方案",
                    List.of("第四章 评分项响应与技术方案"));
            DocumentContent.PageContent page5 = page(5, "第5页 通用资质说明，普通营业证件。".repeat(15));
            DocumentContent.PageContent page6 = page(6,
                    "第6页 评分细则响应：售后服务体系方案、进度计划及应急预案措施完整周详。第六章 售后服务方案与评分细则",
                    List.of("第六章 售后服务方案与评分细则"));

            DocumentContent doc = docOf(page1, page2, page3, page4, page5, page6);

            // 针对 SCORING_RESPONSE 检索，预算限制为 200 字符
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    200);

            // 验证命中高分页面（第 4 页与第 6 页），且排除了无关页面（第 1、2、3、5 页）
            assertThat(result).contains("--- [第 4 页] ---");
            assertThat(result).contains("第四章 评分项响应与技术方案");
            assertThat(result).contains("--- [第 6 页] ---");
            assertThat(result).contains("第六章 售后服务方案与评分细则");
            assertThat(result).doesNotContain("--- [第 1 页] ---");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
            assertThat(result).doesNotContain("--- [第 3 页] ---");
            assertThat(result).doesNotContain("--- [第 5 页] ---");

            // 核心断言：输出中第 4 页必须出现在第 6 页前面（自然页码升序）
            int idx4 = result.indexOf("--- [第 4 页] ---");
            int idx6 = result.indexOf("--- [第 6 页] ---");
            assertThat(idx4).isLessThan(idx6);
        }

        @Test
        @DisplayName("全标书覆盖验证：前段、中段、后段相关页面均可被准确定位并按升序呈现")
        void testRetrieveContext_LongDocFrontMidBack_SelectsRelevantDistributedPages() {
            DocumentContent.PageContent frontPage = page(2,
                    "投标人须知前附表响应表：前附表关键条款响应，投标有效期90天，质保期3年，无偏离。",
                    List.of("前附表关键条款响应"));
            DocumentContent.PageContent midPage = page(25,
                    "商务偏离表与技术偏离表：商务偏离表实质性响应招标文件要求，全部无负偏离。",
                    List.of("商务偏离表"));
            DocumentContent.PageContent backPage = page(80,
                    "澄清文件与答疑纪要响应说明：澄清响应确认函已完整响应澄清公告要求，特此确认。",
                    List.of("澄清响应确认函"));
            DocumentContent.PageContent fillerPage1 = page(10, "普通技术参数介绍。".repeat(50));
            DocumentContent.PageContent fillerPage2 = page(50, "常规管理流程制度。".repeat(50));

            DocumentContent doc = docOf(frontPage, fillerPage1, midPage, fillerPage2, backPage);

            // 针对 CLARIFICATION_RESPONSE 检索，后段第 80 页得分最高
            String clarResult = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.CLARIFICATION_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    200);

            assertThat(clarResult).contains("--- [第 80 页] ---");
            assertThat(clarResult).contains("澄清响应确认函");

            // 针对 RESPONSIVENESS_REVIEW 检索，中段第 25 页得分最高
            String respResult = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    TenderRequirements.empty(),
                    doc,
                    200);

            assertThat(respResult).contains("--- [第 25 页] ---");
            assertThat(respResult).contains("商务偏离表");

            // 针对 FRONT_TABLE_RESPONSE 检索，前段第 2 页得分最高
            String frontResult = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    200);

            assertThat(frontResult).contains("--- [第 2 页] ---");
            assertThat(frontResult).contains("前附表关键条款响应");
        }

        @Test
        @DisplayName("同分页面按自然页码升序稳定排序")
        void testRetrieveContext_WhenTiedScores_ResolvesByAscendingPageNumber() {
            DocumentContent.PageContent p3 = page(3, "资质证书内容：ISO9001 质量管理体系认证".repeat(5));
            DocumentContent.PageContent p7 = page(7, "资质证书内容：ISO9001 质量管理体系认证".repeat(5));
            DocumentContent doc = docOf(p3, p7);

            // 预算仅允许 1 页 (~150 字符)，同分情况下优先选择页码更小的第 3 页
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    150);

            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).doesNotContain("--- [第 7 页] ---");
        }
    }

    // =========================================================================
    // 3. 页面打分精细化与加权规则测试
    // =========================================================================
    @Nested
    @DisplayName("3. 页面打分精细化与加权规则测试")
    class ScoringAlgorithmTests {

        @Test
        @DisplayName("标题匹配 (+50) 权重远高于正文普通匹配 (+10)")
        void testScoring_HeadingMatch_ReceivesHighPriorityBoost() {
            // Page A: 标题命中 "评分细则"，正文普通
            DocumentContent.PageContent pageA = page(1,
                    "本页阐述实施计划内容。",
                    List.of("第三章 评分细则与评分标准"));

            // Page B: 标题无命中，正文仅出现一次 "评分细则"
            DocumentContent.PageContent pageB = page(2,
                    "这里提到评分细则，正文无其他线索。".repeat(5),
                    Collections.emptyList());

            DocumentContent doc = docOf(pageA, pageB);

            // 预算只允许选择 1 页
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    pageA.text().length() + 10);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }

        @Test
        @DisplayName("多关键词覆盖度 (+10/词) 与高频出现 (+2/次) 累积高分")
        void testScoring_KeywordDiversityAndFrequency_BoostsRichPages() {
            // Page 1: 命中多个不同关键词（资质、证书、认证、质量管理体系、有效期）
            DocumentContent.PageContent richPage = page(1,
                    "本投标人具备资质证书，通过ISO质量管理体系认证，证书在有效期内，认证机构权威。");

            // Page 2: 仅命中一个关键词一次
            DocumentContent.PageContent sparsePage = page(2,
                    "本节介绍办公环境，包含普通资质。".repeat(5));

            DocumentContent doc = docOf(richPage, sparsePage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    TenderRequirements.empty(),
                    doc,
                    richPage.text().length() + 10);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }

        @Test
        @DisplayName("动态招标要求：mandatoryQualifications 动态注入关键词并优先召回")
        void testScoring_DynamicTenderRequirements_MandatoryQualificationsBoost() {
            TenderRequirements reqs = new TenderRequirements(
                    List.of(),
                    List.of("CMMI5", "信息安全服务资质一级"),
                    List.of(),
                    List.of(),
                    List.of(),
                    false);

            DocumentContent.PageContent targetPage = page(5,
                    "我公司已获得 CMMI5 软件能力成熟度模型集成五级认证证书与信息安全服务资质一级证书。");
            DocumentContent.PageContent genericPage = page(6,
                    "通用营业执照与基础登记证件。".repeat(5));

            DocumentContent doc = docOf(genericPage, targetPage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    reqs,
                    doc,
                    targetPage.text().length() + 10);

            assertThat(result).contains("--- [第 5 页] ---");
            assertThat(result).contains("CMMI5");
            assertThat(result).doesNotContain("--- [第 6 页] ---");
        }

        @Test
        @DisplayName("动态招标要求：scoringItems 动态注入评分项名称与标准并优先召回")
        void testScoring_DynamicTenderRequirements_ScoringItemsBoost() {
            TenderRequirements reqs = new TenderRequirements(
                    List.of(new TenderRequirements.ScoringItem("云原生高可用架构设计", 25.0, "提供同城双活方案")),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false);

            DocumentContent.PageContent targetPage = page(12,
                    "技术方案重点：云原生高可用架构设计方案，详细论述同城双活容灾与切换机制。");
            DocumentContent.PageContent otherPage = page(13,
                    "项目组织机构及人员岗位职责分配表。".repeat(5));

            DocumentContent doc = docOf(otherPage, targetPage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    reqs,
                    doc,
                    targetPage.text().length() + 10);

            assertThat(result).contains("--- [第 12 页] ---");
            assertThat(result).contains("云原生高可用架构设计");
            assertThat(result).doesNotContain("--- [第 13 页] ---");
        }

        @Test
        @DisplayName("动态招标要求：vetoClauses 动态注入否决条款与关键词并优先召回")
        void testScoring_DynamicTenderRequirements_VetoClausesBoost() {
            TenderRequirements reqs = new TenderRequirements(
                    List.of(),
                    List.of(),
                    List.of(new TenderRequirements.VetoClause("未提供原厂授权书者废标", List.of("原厂授权书", "制造商授权"))),
                    List.of(),
                    List.of(),
                    false);

            DocumentContent.PageContent targetPage = page(8,
                    "设备制造商原厂授权书：本公司已取得服务器厂商出具的正规原厂授权书。");
            DocumentContent.PageContent otherPage = page(9,
                    "产品常规出厂合格证说明。".repeat(5));

            DocumentContent doc = docOf(otherPage, targetPage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_VETO,
                    reqs,
                    doc,
                    targetPage.text().length() + 10);

            assertThat(result).contains("--- [第 8 页] ---");
            assertThat(result).contains("原厂授权书");
            assertThat(result).doesNotContain("--- [第 9 页] ---");
        }

        @Test
        @DisplayName("动态招标要求：formatRequirements 与 compositionRequirements 动态增强前附表响应召回")
        void testScoring_DynamicTenderRequirements_FormatAndCompositionBoost() {
            TenderRequirements reqs = new TenderRequirements(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("资格审查申请书必须包含近三年财务审计报告"),
                    List.of("投标人须知前附表格式不可篡改"),
                    false);

            DocumentContent.PageContent frontTablePage = page(3,
                    "投标人须知前附表格式响应：严格遵循模板要求填写各项关键条款。");
            DocumentContent.PageContent randomPage = page(4,
                    "办公设备及通用打印机型号清单。".repeat(5));

            DocumentContent doc = docOf(randomPage, frontTablePage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                    reqs,
                    doc,
                    frontTablePage.text().length() + 10);

            assertThat(result).contains("--- [第 3 页] ---");
            assertThat(result).contains("投标人须知前附表");
            assertThat(result).doesNotContain("--- [第 4 页] ---");
        }

        @Test
        @DisplayName("澄清响应项：静态线索准确召回答疑文件与补遗确认函页面")
        void testScoring_ClarificationResponse_CuesBoost() {
            DocumentContent.PageContent clarPage = page(15,
                    "关于本项目第1号澄清文件的响应说明与确认函：已收悉答疑纪要并据此调整方案。");
            DocumentContent.PageContent genericPage = page(16,
                    "常规保密承诺与廉洁协议。".repeat(5));

            DocumentContent doc = docOf(genericPage, clarPage);

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.CLARIFICATION_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    clarPage.text().length() + 10);

            assertThat(result).contains("--- [第 15 页] ---");
            assertThat(result).contains("澄清文件");
            assertThat(result).doesNotContain("--- [第 16 页] ---");
        }
    }

    // =========================================================================
    // 4. 全部 7 项语义检查项全覆盖参数化测试
    // =========================================================================
    @Nested
    @DisplayName("4. 7 项语义检查项全覆盖测试")
    class AllSemanticItemsTests {

        @ParameterizedTest
        @EnumSource(value = BidQualityCheckItem.class, names = {
                "QUALIFICATION_MATCH",
                "QUALIFICATION_VETO",
                "FRONT_TABLE_RESPONSE",
                "RESPONSIVENESS_REVIEW",
                "QUALIFICATION_REVIEW",
                "SCORING_RESPONSE",
                "CLARIFICATION_RESPONSE"
        })
        @DisplayName("7项语义检查项均能正常提取线索并检索相关页面")
        void testScoring_AllSevenSemanticItems_ExtractValidCuesAndPrioritizeRelevantPages(
                final BidQualityCheckItem item) {
            DocumentContent.PageContent matchPage = page(1,
                    "重点响应内容：" + item.getTitle() + " 相关要求已全面落实，具备相应证明资料。");
            DocumentContent.PageContent noisePage = page(2,
                    "无关填充文本，不包含任何业务关键词。".repeat(10));

            DocumentContent doc = docOf(noisePage, matchPage);

            String result = SemanticPageRetriever.retrieveContext(
                    item,
                    TenderRequirements.empty(),
                    doc,
                    matchPage.text().length() + 20);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains(item.getTitle());
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }
    }

    // =========================================================================
    // 5. 异常、空值与边界降级测试 (FP-Java 零异常流)
    // =========================================================================
    @Nested
    @DisplayName("5. 边界与空值降级测试")
    class EdgeAndFallbackTests {

        @Test
        @DisplayName("文档为 null 或 empty 时返回 '（无内容）'")
        void testRetrieveContext_WhenDocNullOrEmpty_ReturnsNoContent() {
            assertThat(SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, TenderRequirements.empty(), null, 1000))
                    .isEqualTo("（无内容）");

            assertThat(SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, TenderRequirements.empty(), DocumentContent.empty(), 1000))
                    .isEqualTo("（无内容）");
        }

        @Test
        @DisplayName("文档 pages 为空但 fullText 有文本时：降级单页格式化输出")
        void testRetrieveContext_WhenPagesEmptyButFullTextPresent_FormatsAsSinglePage() {
            DocumentContent legacyDoc = new DocumentContent("test.pdf", "h1", "这是全文单文本内容", List.of());

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, TenderRequirements.empty(), legacyDoc, 1000);

            assertThat(result).isEqualTo("--- [内容块 1；原件页码未知] ---\n这是全文单文本内容");
        }

        @Test
        @DisplayName("所有页面正文均为空白字符时：返回 '（无内容）'")
        void testRetrieveContext_WhenAllPagesBlankText_ReturnsNoContent() {
            DocumentContent doc = docOf(page(1, "   \n\t  "), page(2, ""));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, 1000);

            assertThat(result).isEqualTo("（无内容）");
        }

        @Test
        @DisplayName("TenderRequirements 为 null 或 missingSentinel 哨兵时：安全降级使用静态线索，不抛异常")
        void testRetrieveContext_WhenTenderRequirementsNullOrMissingSentinel_ExecutesSafely() {
            DocumentContent doc = docOf(page(1, "企业具备 ISO9001 认证证书"));

            String resNull = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, null, doc, 1000);
            String resSentinel = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH, TenderRequirements.missingSentinel(), doc, 1000);

            assertThat(resNull).contains("--- [第 1 页] ---").contains("ISO9001");
            assertThat(resSentinel).contains("--- [第 1 页] ---").contains("ISO9001");
        }

        @Test
        @DisplayName("BidQualityCheckItem 为 null 时：安全执行，不抛异常")
        void testRetrieveContext_WhenCheckItemNull_ExecutesSafely() {
            DocumentContent doc = docOf(page(1, "普通正文"));

            String result = SemanticPageRetriever.retrieveContext(
                    null, TenderRequirements.empty(), doc, 1000);

            assertThat(result).contains("--- [第 1 页] ---").contains("普通正文");
        }

        @Test
        @DisplayName("字符预算极小（如 5 字符）：仍优雅截取至少第 1 顺位页，不抛出索引越界异常")
        void testRetrieveContext_WhenCharBudgetVerySmall_HandlesGracefully() {
            DocumentContent doc = docOf(
                    page(1, "第一页文本长长长长长长长长长"),
                    page(2, "第二页文本长长长长长长长长长"));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, 5);

            assertThat(result).isNotBlank();
            assertThat(result).contains("--- [第 1 页] ---");
        }
    }

    // =========================================================================
    // 6. 无标题多页文档降级测试
    // =========================================================================
    @Nested
    @DisplayName("6. 无标题多页文档检索测试")
    class NoHeadingsFallbackTests {

        @Test
        @DisplayName("所有页面均无 headings 标题元数据时，纯正文关键词打分仍能准确区分并召回")
        void testRetrieveContext_WhenNoHeadings_ScoresPurelyOnTextAndRanksAccurately() {
            // 所有页面的 headings 均为空集合，总长度 > 400 字符，预算 100 字符
            DocumentContent.PageContent p1 = page(1, "第1页 商务条款通用说明，报价币种为人民币。".repeat(10));
            DocumentContent.PageContent p2 = page(2, "第2页 技术方案实施组织设计：详细进度计划与交付里程碑。");
            DocumentContent.PageContent p3 = page(3, "第3页 杂项声明。".repeat(20));

            DocumentContent doc = docOf(p1, p2, p3);

            // 针对 SCORING_RESPONSE 检索，预算限制为 100 字符
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    TenderRequirements.empty(),
                    doc,
                    100);

            assertThat(result).contains("--- [第 2 页] ---");
            assertThat(result).contains("技术方案实施组织设计");
            assertThat(result).doesNotContain("--- [第 1 页] ---");
            assertThat(result).doesNotContain("--- [第 3 页] ---");
        }
    }

    // =========================================================================
    // 7. 单页超预算硬截断测试（分页丢失场景：整篇文档被解析为 1 页）
    // =========================================================================
    @Nested
    @DisplayName("7. 单页超预算硬截断测试")
    class SinglePageOverBudgetTruncationTests {

        @Test
        @DisplayName("单页文档超出预算：仅保留预算内前缀并标注截断，不再全量透传")
        void testRetrieveContext_SinglePageOverBudget_TruncatesWithMarker() {
            String hugeText = "投标文件正文内容甲乙丙丁。".repeat(2000); // 12000 字符
            DocumentContent doc = docOf(page(1, hugeText));

            int budget = 5_000;
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, budget);

            // 输出总量必须受控在预算附近（页头开销 + 截断标注），不允许 12000 字符全量塞入
            assertThat(result.length())
                    .as("输出长度应受预算约束")
                    .isLessThanOrEqualTo(budget + 200);
            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains("……（已截断）");
        }

        @Test
        @DisplayName("单页文档预算内：行为不变，全文无损透传（回归保护）")
        void testRetrieveContext_SinglePageWithinBudget_PassThrough() {
            String text = "投标总价承诺：人民币壹佰万元整，工期 60 日历天。";
            DocumentContent doc = docOf(page(1, text));

            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, 60_000);

            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).contains(text);
            assertThat(result).doesNotContain("……（已截断）");
        }

        @Test
        @DisplayName("多页超预算：最高分页被选中时同样受预算约束，不因首页选中而突破预算")
        void testRetrieveContext_OversizedTopPage_TruncatedToBudget() {
            String hugeMatched = "技术方案实施方案。".repeat(3000); // 27000 字符，高相关
            DocumentContent doc = docOf(
                    page(1, hugeMatched),
                    page(2, "无关填充文本。"));

            int budget = 6_000;
            String result = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.SCORING_RESPONSE, TenderRequirements.empty(), doc, budget);

            assertThat(result.length()).isLessThanOrEqualTo(budget + 200);
            assertThat(result).contains("--- [第 1 页] ---");
            assertThat(result).doesNotContain("--- [第 2 页] ---");
        }
    }
}
