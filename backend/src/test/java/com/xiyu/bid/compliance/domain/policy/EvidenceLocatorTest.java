// Input: 分页文档 PageContent 列表 + 关键词/章节线索
// Output: 断言命中返回真实 pageNumber + quote（原文子串）、未命中 Optional.empty()、quote 上限截断
// Pos: compliance/domain/policy/ - Evidence 真实定位纯函数测试（spec 049 US4 / T039，data-model.md §3.3）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceLocatorTest {

    private static DocumentContent.PageContent page(final int number, final String text) {
        return new DocumentContent.PageContent(number, text, Collections.emptyList());
    }

    @Test
    @DisplayName("命中：返回关键词所在页的真实 pageNumber，quote 为该页原文子串")
    void testLocate_KeywordOnSecondPage_ReturnsRealPageAndQuote() {
        List<DocumentContent.PageContent> pages = List.of(
                page(1, "投标函与法定代表人身份证明"),
                page(2, "本投标人具备 ISO27001 信息安全认证证书，有效期至2027年。"),
                page(3, "开标一览表与投标保证金说明"));

        Optional<EvidenceHit> hit = EvidenceLocator.locate(pages, List.of("ISO27001"));

        assertThat(hit).isPresent();
        assertThat(hit.get().pageNumber()).isEqualTo(2);
        assertThat(hit.get().quote()).contains("ISO27001");
        assertThat(pages.get(1).text()).contains(hit.get().quote());
    }

    @Test
    @DisplayName("多关键词：返回首个命中关键词的定位结果")
    void testLocate_MultipleKeywords_ReturnsFirstHit() {
        List<DocumentContent.PageContent> pages = List.of(
                page(1, "资质证书分卷"),
                page(4, "前附表关键条款响应"));

        Optional<EvidenceHit> hit = EvidenceLocator.locate(pages, List.of("不存在词", "前附表"));

        assertThat(hit).isPresent();
        assertThat(hit.get().pageNumber()).isEqualTo(4);
        assertThat(hit.get().quote()).contains("前附表");
    }

    @Test
    @DisplayName("未命中：返回 Optional.empty()（禁止返回 pageNumber=1 兜底）")
    void testLocate_NoHit_ReturnsEmpty() {
        List<DocumentContent.PageContent> pages = List.of(
                page(1, "投标函"), page(2, "开标一览表"));

        assertThat(EvidenceLocator.locate(pages, List.of("不存在的关键词"))).isEmpty();
        assertThat(EvidenceLocator.locate(Collections.emptyList(), List.of("任意"))).isEmpty();
        assertThat(EvidenceLocator.locate(pages, Collections.emptyList())).isEmpty();
        assertThat(EvidenceLocator.locate(pages, null)).isEmpty();
        assertThat(EvidenceLocator.locate(null, List.of("任意"))).isEmpty();
    }

    @Test
    @DisplayName("quote 长度上限截断：不超过 120 字符且仍为原文子串")
    void testLocate_LongPage_QuoteTruncatedWithinLimit() {
        String longText = "甲".repeat(200) + "澄清响应确认函" + "乙".repeat(200);
        List<DocumentContent.PageContent> pages = List.of(page(1, longText));

        Optional<EvidenceHit> hit = EvidenceLocator.locate(pages, List.of("澄清响应确认函"));

        assertThat(hit).isPresent();
        assertThat(hit.get().quote().length()).isLessThanOrEqualTo(120);
        assertThat(longText).contains(hit.get().quote());
        assertThat(hit.get().quote()).contains("澄清响应确认函");
    }

    @Test
    @DisplayName("跳过目录页定位：目录页命中时穿透至正文页")
    void testLocateSkippingCatalog_CatalogHit_SkipsToBodyPage() {
        List<DocumentContent.PageContent> pages = List.of(
                page(1, "目 录\n第五章 澄清答疑与补遗文件确认函.......................... 56"),
                page(7, "第五章 澄清答疑确认函正文：我方已收悉第1号澄清文件并盖章确认。"));

        Optional<EvidenceHit> hit = EvidenceLocator.locateSkippingCatalog(pages, List.of("澄清答疑确认函"));

        assertThat(hit).isPresent();
        assertThat(hit.get().pageNumber()).isEqualTo(7);
    }

    @Test
    @DisplayName("跳过目录页定位：仅目录页命中时返回 empty（诚实未定位）")
    void testLocateSkippingCatalog_OnlyCatalogHit_ReturnsEmpty() {
        List<DocumentContent.PageContent> pages = List.of(
                page(1, "目 录\n第五章 澄清答疑与补遗文件确认函.......................... 56"));

        assertThat(EvidenceLocator.locateSkippingCatalog(pages, List.of("澄清答疑确认函"))).isEmpty();
    }
}
