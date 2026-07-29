// Input: 各种招标文件候选文本
// Output: 断言 extractPurchaserName 的正则兜底行为
// Pos: biddraftagent/infrastructure/openai — 招标主体正则兜底提取单元测试
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PurchaserNameExtractor")
class PurchaserNameExtractorTest {

    @Test
    @DisplayName("张家口银行真实 case：招标人标签被空格打断仍能提取")
    void shouldExtractWhenKeywordSplitBySpaces() {
        String candidate = "招 标 文 件\n【招标编号：XAZB[2026]019】\n招 标 人：张家口银行股份有限公司\n代理机构：祥安招标代理有限公司";
        assertThat(PurchaserNameExtractor.extractPurchaserName(candidate))
                .isEqualTo("张家口银行股份有限公司");
    }

    @Test
    @DisplayName("标准标签行：招标人：XXX")
    void shouldExtractFromStandardLabelLine() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("招标人：某某市财政局"))
                .isEqualTo("某某市财政局");
    }

    @Test
    @DisplayName("全角冒号：采购单位：XXX")
    void shouldExtractWithFullwidthColon() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("采购单位：某某集团有限公司"))
                .isEqualTo("某某集团有限公司");
    }

    @Test
    @DisplayName("半角冒号：采购人:XXX")
    void shouldExtractWithHalfwidthColon() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("采购人:某某医院集团"))
                .isEqualTo("某某医院集团");
    }

    @Test
    @DisplayName("排除代理机构：代理机构标签不作为招标主体")
    void shouldNotExtractFromAgencyLabel() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("代理机构：祥安招标代理有限公司"))
                .isEmpty();
    }

    @Test
    @DisplayName("排除叙事性行：招标人不予受理")
    void shouldNotExtractFromNarrativeLine() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("提交投标文件的，招标人不予受理。"))
                .isEmpty();
    }

    @Test
    @DisplayName("多标签取多数：出现次数最多的机构名")
    void shouldPickMostFrequentWhenMultipleLabels() {
        String candidate = "招标人：甲方科技有限公司\n招标单位：乙方集团有限公司\n招 标 人：甲方科技有限公司";
        assertThat(PurchaserNameExtractor.extractPurchaserName(candidate))
                .isEqualTo("甲方科技有限公司");
    }

    @Test
    @DisplayName("代理机构与招标人共存：只取招标人")
    void shouldPreferPurchaserOverAgencyWhenCoexist() {
        String candidate = "招 标 人：张家口银行股份有限公司\n代理机构：祥安招标代理有限公司\n招标代理：祥安招标代理有限公司";
        assertThat(PurchaserNameExtractor.extractPurchaserName(candidate))
                .isEqualTo("张家口银行股份有限公司");
    }

    @Test
    @DisplayName("null / 空文本 → 空字符串")
    void shouldReturnEmptyForNullOrBlank() {
        assertThat(PurchaserNameExtractor.extractPurchaserName(null)).isEmpty();
        assertThat(PurchaserNameExtractor.extractPurchaserName("")).isEmpty();
        assertThat(PurchaserNameExtractor.extractPurchaserName("   ")).isEmpty();
    }

    @Test
    @DisplayName("无标签行 → 空字符串")
    void shouldReturnEmptyWhenNoLabelLine() {
        assertThat(PurchaserNameExtractor.extractPurchaserName("这是一段没有招标主体标签的纯文本"))
                .isEmpty();
    }
}
