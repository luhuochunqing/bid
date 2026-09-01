// Input: 无分页符的长文本（docx/markitdown 输出无物理页边界的真实场景）
// Output: 断言虚拟分页纯函数（标题切块、段落累积切块、超长单段硬切、短文本不切、空文本兜底）
// Pos: compliance/domain/ - DocumentContent 虚拟分页单元测试（分页丢失根因修复）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentContent 虚拟分页测试（无 \\f 分页符长文本按标题/段落切块）")
class DocumentContentVirtualPaginationTest {

    @Nested
    @DisplayName("短文本与空文本")
    class ShortAndEmptyTextTests {

        @Test
        @DisplayName("短文本（<= 阈值）不切块，返回单页且保留原文")
        void shortText_returnsSinglePageWithOriginalText() {
            String text = "投标函正文内容。".repeat(50); // 350 字符

            List<DocumentContent.PageContent> pages = DocumentContent.virtualPages(text);

            assertThat(pages).hasSize(1);
            assertThat(pages.get(0).pageNumber()).isEqualTo(1);
            assertThat(pages.get(0).text()).isEqualTo(text);
        }

        @Test
        @DisplayName("空文本返回空列表")
        void blankText_returnsEmptyList() {
            assertThat(DocumentContent.virtualPages(null)).isEmpty();
            assertThat(DocumentContent.virtualPages("")).isEmpty();
            assertThat(DocumentContent.virtualPages("   \n  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("标题切块")
    class HeadingSplitTests {

        @Test
        @DisplayName("超阈值长文本含 markdown 标题：按标题切块，页码连续，每块以标题开头")
        void longTextWithHeadings_splitsByHeadings() {
            String chapterBody = "本章详细阐述技术方案与实施方案的落地细节，覆盖组织架构、人员配备与质量保障措施。".repeat(40);
            String text = "# 第一章 投标函\n" + chapterBody
                    + "\n# 第二章 技术方案\n" + chapterBody
                    + "\n# 第三章 售后服务承诺\n" + chapterBody;

            List<DocumentContent.PageContent> pages = DocumentContent.virtualPages(text);

            assertThat(pages.size()).isGreaterThanOrEqualTo(3);
            assertThat(pages.get(0).text()).startsWith("# 第一章 投标函");
            assertThat(pages.get(1).text()).startsWith("# 第二章 技术方案");
            assertThat(pages.get(pages.size() - 1).text()).startsWith("# 第三章 售后服务承诺");
            assertThat(pages)
                    .extracting(DocumentContent.PageContent::pageNumber)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, pages.size())
                            .boxed().toList());
        }

        @Test
        @DisplayName("首页内容在首个标题之前（如封面）：作为独立块保留，不丢失")
        void textBeforeFirstHeading_preservedAsFirstBlock() {
            String cover = "张家口银行 XX 项目投标文件 投标人：西域智慧供应链。".repeat(60);
            String text = cover + "\n# 第一章 投标函\n" + "正文内容。".repeat(800);

            List<DocumentContent.PageContent> pages = DocumentContent.virtualPages(text);

            assertThat(pages.size()).isGreaterThanOrEqualTo(2);
            assertThat(pages.get(0).text()).contains("张家口银行");
            assertThat(pages.get(0).text()).doesNotContain("# 第一章");
            assertThat(pages.get(1).text()).startsWith("# 第一章 投标函");
        }
    }

    @Nested
    @DisplayName("段落累积切块")
    class ParagraphChunkTests {

        @Test
        @DisplayName("超阈值无标题长文：按段落边界累积切块，单页不超上限，页码连续")
        void longTextWithoutHeadings_splitsByParagraphBoundaries() {
            String paragraph = "投标人对招标文件中的商务条款与技术服务要求作出完全响应，无任何偏离项，特此确认。";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 120; i++) {
                sb.append(paragraph).append("\n\n");
            }
            String text = sb.toString();

            List<DocumentContent.PageContent> pages = DocumentContent.virtualPages(text);

            assertThat(pages.size()).isGreaterThanOrEqualTo(2);
            for (DocumentContent.PageContent p : pages) {
                assertThat(p.text().length())
                        .as("单块长度不得超上限（段落边界切块）")
                        .isLessThanOrEqualTo(6_100);
            }
            assertThat(pages.get(0).pageNumber()).isEqualTo(1);
            assertThat(pages.get(pages.size() - 1).pageNumber()).isEqualTo(pages.size());
        }

        @Test
        @DisplayName("无换行的超长单段：硬切兜底，不丢失字符，页码连续")
        void singleHugeParagraph_hardSplitWithoutLoss() {
            String text = "甲".repeat(20_000);

            List<DocumentContent.PageContent> pages = DocumentContent.virtualPages(text);

            assertThat(pages.size()).isGreaterThanOrEqualTo(3);
            int totalLength = pages.stream().mapToInt(p -> p.text().length()).sum();
            assertThat(totalLength).isEqualTo(20_000);
            assertThat(pages.get(0).pageNumber()).isEqualTo(1);
            assertThat(pages.get(pages.size() - 1).pageNumber()).isEqualTo(pages.size());
        }
    }
}
