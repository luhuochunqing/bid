// Input: 多份解析后的投标文件 DocumentContent 列表
// Output: 验证 merge 纯函数——页码跨册连续重编号、全文拼接、文件名顿号连接、聚合哈希确定性
// Pos: compliance/domain/ - 多册投标文件合并纯函数单测（多文件质量核查）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多文件质量核查：Loader 边界将 N 份投标文件合并为单个 DocumentContent，
 * 25 项引擎/证据链/报告链路零改动。merge 必须是纯函数（FP-Java）.
 */
class DocumentContentMergeTest {

    private DocumentContent doc(String fileName, String fileHash, String text, DocumentContent.PageContent... pages) {
        return new DocumentContent(fileName, fileHash, text, List.of(pages));
    }

    @Test
    @DisplayName("空列表合并为空文档")
    void merge_emptyList_returnsEmpty() {
        DocumentContent merged = DocumentContent.merge(List.of());
        assertThat(merged.fullText()).isEmpty();
        assertThat(merged.pages()).isEmpty();
        assertThat(merged.fileName()).isEmpty();
    }

    @Test
    @DisplayName("单份合并原样返回（保持既有单文件缓存哈希兼容）")
    void merge_singleDocument_returnsSameInstance() {
        DocumentContent single = doc("价格册.pdf", "hash-a", "价格正文",
                new DocumentContent.PageContent(1, "价格正文", List.of()));
        assertThat(DocumentContent.merge(List.of(single))).isSameAs(single);
    }

    @Test
    @DisplayName("多份合并：页码跨册连续重编号，标题行保留")
    void merge_multipleDocuments_pagesRenumberedContinuously() {
        DocumentContent price = doc("价格册.pdf", "hash-a", "价格正文",
                new DocumentContent.PageContent(1, "价格第1页", List.of("第一章")),
                new DocumentContent.PageContent(2, "价格第2页", List.of()));
        DocumentContent business = doc("商务册.pdf", "hash-b", "商务正文",
                new DocumentContent.PageContent(1, "商务第1页", List.of("商务章")));

        DocumentContent merged = DocumentContent.merge(List.of(price, business));

        assertThat(merged.pages()).hasSize(3);
        assertThat(merged.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(merged.pages().get(0).text()).isEqualTo("价格第1页");
        assertThat(merged.pages().get(0).headings()).containsExactly("第一章");
        assertThat(merged.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(merged.pages().get(1).text()).isEqualTo("价格第2页");
        assertThat(merged.pages().get(2).pageNumber()).isEqualTo(3);
        assertThat(merged.pages().get(2).text()).isEqualTo("商务第1页");
        assertThat(merged.pages().get(2).headings()).containsExactly("商务章");
    }

    @Test
    @DisplayName("多份合并：全文按册序拼接且互不粘连")
    void merge_multipleDocuments_fullTextJoined() {
        DocumentContent a = doc("a.pdf", "hash-a", "价格册正文");
        DocumentContent b = doc("b.pdf", "hash-b", "商务册正文");

        DocumentContent merged = DocumentContent.merge(List.of(a, b));

        assertThat(merged.fullText()).contains("价格册正文", "商务册正文");
        // 不粘连：两段正文之间必须有分隔，避免跨册文本拼成一处误触正则
        assertThat(merged.fullText()).doesNotContain("价格册正文商务册正文");
    }

    @Test
    @DisplayName("多份合并：文件名按册序顿号连接")
    void merge_multipleDocuments_fileNamesJoined() {
        DocumentContent a = doc("价格册.pdf", "hash-a", "A");
        DocumentContent b = doc("商务册.pdf", "hash-b", "B");

        assertThat(DocumentContent.merge(List.of(a, b)).fileName())
                .isEqualTo("价格册.pdf、商务册.pdf");
    }

    @Test
    @DisplayName("聚合哈希确定性：同序合并哈希一致，异序哈希不同，且不等于任一单册哈希")
    void merge_hashDeterministicAndOrderSensitive() {
        DocumentContent a = doc("价格册.pdf", "hash-a", "A");
        DocumentContent b = doc("商务册.pdf", "hash-b", "B");

        DocumentContent ab = DocumentContent.merge(List.of(a, b));
        DocumentContent abAgain = DocumentContent.merge(List.of(a, b));
        DocumentContent ba = DocumentContent.merge(List.of(b, a));

        assertThat(ab.fileHash()).isEqualTo(abAgain.fileHash());
        assertThat(ab.fileHash()).isNotEqualTo(ba.fileHash());
        assertThat(ab.fileHash()).isNotEqualTo("hash-a").isNotEqualTo("hash-b");
        assertThat(ab.fileHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("合并文件名超 255 截断为前段+「等N册」，总长不超限")
    void merge_longFileNames_cappedWithCountSuffix() {
        String longName = "很长的文件名".repeat(50) + ".pdf"; // 304 字符，拼接后必超 255
        DocumentContent a = doc(longName, "hash-a", "A");
        DocumentContent b = doc("商务册.pdf", "hash-b", "B");

        String mergedName = DocumentContent.merge(List.of(a, b)).fileName();

        assertThat(mergedName.length()).isLessThanOrEqualTo(255);
        assertThat(mergedName).endsWith("等2册");
    }

    @Test
    @DisplayName("capMergedFileName 按码点边界截断，不产生未配对代理项")
    void capMergedFileName_surrogateSafe() {
        // 首字符占 1 个 code unit，使 255 处截断正好落在代理对中间，触发收尾裁剪
        String joined = "A" + "📄".repeat(200);

        String capped = DocumentContent.capMergedFileName(joined, 2);

        assertThat(capped.length()).isLessThanOrEqualTo(255);
        // codePoints() 会把合法代理对合并为增补码点；若出现 D800-DFFF 区间即存在未配对代理项
        capped.codePoints().forEach(cp -> {
            boolean unpairedSurrogate = cp >= 0xD800 && cp <= 0xDFFF;
            assertThat(unpairedSurrogate).as("码点 %s 为未配对代理项", cp).isFalse();
        });
    }

    @Test
    @DisplayName("空白文件名的册不参与文件名拼接（正文与页码照常合并）")
    void merge_blankFileNames_filteredFromJoin() {
        DocumentContent a = doc("", "hash-a", "A正文",
                new DocumentContent.PageContent(1, "A第1页", List.of()));
        DocumentContent b = doc("商务册.pdf", "hash-b", "B正文",
                new DocumentContent.PageContent(1, "B第1页", List.of()));

        DocumentContent merged = DocumentContent.merge(List.of(a, b));

        assertThat(merged.fileName()).isEqualTo("商务册.pdf");
        assertThat(merged.pages()).hasSize(2);
        assertThat(merged.fullText()).contains("A正文", "B正文");
    }

    @Test
    @DisplayName("capMergedFileName null 入参返回空串")
    void capMergedFileName_nullReturnsEmpty() {
        assertThat(DocumentContent.capMergedFileName(null, 2)).isEmpty();
    }

    @Test
    @DisplayName("displayFileName：null/空白名兜底 bid_document.pdf，非空名原样返回")
    void displayFileName_fallbackContract() {
        assertThat(DocumentContent.displayFileName(null)).isEqualTo("bid_document.pdf");
        assertThat(DocumentContent.displayFileName("   ")).isEqualTo("bid_document.pdf");
        assertThat(DocumentContent.displayFileName("价格册.pdf")).isEqualTo("价格册.pdf");
    }

    @Test
    @DisplayName("某册哈希为空时不阻断聚合（sidecar 个别场景无哈希）")
    void merge_blankPerFileHash_stillAggregates() {
        DocumentContent a = doc("价格册.pdf", "", "A");
        DocumentContent b = doc("商务册.pdf", "hash-b", "B");

        DocumentContent merged = DocumentContent.merge(List.of(a, b));

        assertThat(merged.fileHash()).hasSize(64);
    }
}
