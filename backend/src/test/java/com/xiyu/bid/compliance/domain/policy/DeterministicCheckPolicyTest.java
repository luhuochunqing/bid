package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicCheckPolicyTest {

    @Test
    @DisplayName("大写金额转换算法测试")
    void testConvertChineseToNumber() {
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("壹佰万元"))
                .isEqualByComparingTo(BigDecimal.valueOf(1000000));
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("伍拾万零伍佰元"))
                .isEqualByComparingTo(BigDecimal.valueOf(500500));
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("叁仟捌佰万元整"))
                .isEqualByComparingTo(BigDecimal.valueOf(38000000));
    }

    @Test
    @DisplayName("报价一致性检查：大写与小写一致时断言 PASS")
    void testPriceConsistency_Pass() {
        String page1 = "投标文件封面\n项目：数智化采购平台\n投标人：西域供应链有限公司\n2026年08月18日";
        String page2 = "投标总价：\n大写：人民币壹佰万元整\n小写：¥1000000.00";
        DocumentContent doc = DocumentContent.fromText("test.pdf", "hash123", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("报价一致性检查：大写与小写不一致时断言 FAIL")
    void testPriceConsistency_Fail() {
        String page1 = "投标总价：大写：人民币壹仟万元整 (小写：¥1000000.00)";
        DocumentContent doc = DocumentContent.fromText("test.pdf", "hash123", page1);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("不一致");
        assertThat(result.suggestion()).isNotEmpty();
    }

    @Test
    @DisplayName("封面完整性检查：包含项目、投标人、日期断言 PASS，缺失时 FAIL")
    void testCoverInfo() {
        String completeCover = "【投标文件】\n项目名称：某大型智慧中台采购\n投标人：西域科技有限公司\n日期：2026-08-18";
        DocumentContent passDoc = DocumentContent.fromText("bid.pdf", "hash1", completeCover);
        QualityCheckItemResult passResult = DeterministicCheckPolicy.checkCoverInfo(passDoc);
        assertThat(passResult.status()).isEqualTo(QualityCheckStatus.PASS);

        String incompleteCover = "【投标文件】\n投标人：西域科技有限公司";
        DocumentContent failDoc = DocumentContent.fromText("bid.pdf", "hash2", incompleteCover);
        QualityCheckItemResult failResult = DeterministicCheckPolicy.checkCoverInfo(failDoc);
        assertThat(failResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(failResult.conclusion()).contains("缺少必要关键要素");
    }

    @Test
    @DisplayName("敏感词与废标词扫描：命中敏感词断言 FAIL 并定位物理页码")
    void testVetoKeyword_Hit() {
        String page1 = "第一章 概述\n项目正常实施";
        String page2 = "第二章 合作模式\n本项目严禁转包，但我方承诺部分技术进行违法分包。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkVetoKeyword(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).hasSize(2);
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
        assertThat(result.evidences().get(0).quote()).contains("转包");
    }

    @Test
    @DisplayName("营业执照与主体资格：识别 18 位统一社会信用代码")
    void testBusinessLicense_WithUSCC() {
        String page = "企业资质证明\n营业执照\n统一社会信用代码：91310000775244192P\n西域供应链";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", page);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBusinessLicense(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("91310000775244192P");
    }

    @Test
    @DisplayName("全量确定性规则执行：返回 18 项检查结果")
    void testExecuteAllChecks() {
        String content = "项目名称：采购项目\n投标人：西域科技\n2026年\n投标函\n致：招标人\n法定代表人身份证明\n统一社会信用代码：91310000775244192P\n大写：人民币壹佰万元整 小写：1000000\n工期：30日历天\n（盖章）\n联系人：张三 电话：13800000000\n目录\n第1页\n第2页\n第3页\n承诺函\n身份证\n开标一览表\n投标保证金\nCA数字证书\n退还开户银行";
        DocumentContent doc = DocumentContent.fromText("full.pdf", "hash_full", content);

        List<QualityCheckItemResult> results = DeterministicCheckPolicy.executeChecks(doc, "招标要求");
        assertThat(results).hasSize(18);
        assertThat(results.stream().allMatch(r -> r.status() != null)).isTrue();
    }

    @Test
    @DisplayName("证据定位：单页长文档命中关键词时，quote 取关键词附近上下文而非页首（分页缺失场景）")
    void evidenceQuote_locatesAroundKeyword_onLongSinglePage() {
        String cover = "张家口银行 XX 电商平台采购项目 投标文件 投标人：西域智慧供应链（上海）股份公司 2026年6月21日";
        String filler = "服务承诺与实施方案填充内容，无核查关键词。".repeat(400);
        String license = "第九章 资格证明文件\n营业执照\n统一社会信用代码：91310000775244192P\n营业期限：长期";
        String hugeSinglePage = cover + filler + license;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_1352", hugeSinglePage);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBusinessLicense(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).isNotEmpty();
        String quote = result.evidences().get(0).quote();
        assertThat(quote)
                .as("证据摘录应围绕命中关键词（营业执照），而非整页开头")
                .contains("营业执照");
        assertThat(quote)
                .as("证据摘录不应再是封面页首文本")
                .doesNotContain("张家口银行");
    }

    @Test
    @DisplayName("证据定位：关键词未命中窗口兜底为页首摘录（原 snippet 行为）")
    void evidenceQuote_fallsBackToHead_whenKeywordAbsent() {
        String text = "投标函\n致：张家口银行股份有限公司\n我方承诺……".repeat(200);
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).quote()).isNotBlank();
    }

    @Test
    @DisplayName("开标一览表：文档使用「投标报价表」表述时也能识别（词表扩充）")
    void bidOpeningTable_recognizesBidPriceTable() {
        String page = "二、投标报价表\n投标总价：大写：人民币壹佰万元整 小写：¥1000000.00\n分项报价明细见附录";
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_1352", page);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidOpeningTable(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).quote()).contains("投标报价表");
    }

    @Test
    @DisplayName("报价一致性：大写与小写分处两页时经全文回退仍能比对（跨页报价对）")
    void priceConsistency_crossPagePair_fallsBackToFullText() {
        // 页 1 只有投标函大写总价；页 2 只有报价表小写金额——逐页匹配必然失败
        String page1 = "投标函\n投标总价 大写：人民币壹佰万元整\n特此承诺。";
        String page2 = "开标一览表\n报价总额：¥1000000.00\n小写：1000000.00";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);

        assertThat(result.status())
                .as("跨页大小写对照应经全文回退命中并判定一致")
                .isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("报价一致性：大写与小写距离超窗时不得远距误配，保持转人工")
    void priceConsistency_oversizedCrossLineGap_doesNotMismatch() {
        // 大写与小写之间隔 > 300 字符（跨行窗口外），不得强行配对
        String page1 = "投标总价 大写：人民币壹佰万元整\n" + "无关填充内容，用于拉开大小写距离。".repeat(30);
        String page2 = "另一处出现 小写：1000000.00 与本节无关";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);

        assertThat(result.status())
                .as("超窗的远距大小写对照不应被判为一致")
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
