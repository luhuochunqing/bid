// Input: 投标文件分页内容 (DocumentContent) + 招标报价与最高限价要求 (PriceRequirement)
// Output: 报价一致性、分项算术与最高限价核查结果 (QualityCheckItemResult: PASS / FAIL / MANUAL_REVIEW)
// Pos: compliance/domain/policy/ - 报价一致性与限价核验策略（纯核心 FP-Java，严格 <= 300 行）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书报价一致性、分项算术与最高投标限价核查策略 (FP-Java 纯核心).
 */
public final class PriceConsistencyCheckPolicy {

    private static final Pattern PRICE_PAIR_PATTERN = Pattern.compile(
            "(?:[（\\(]?大写[）\\)]?\\s*[：:]?\\s*(?:人民币)?\\s*([零壹贰叁肆伍陆柒捌玖拾佰仟万亿角分]+元(?:整|正)?))"
                    + ".{0,300}?"
                    + "(?:[（\\(]?小写[）\\)]?\\s*[：:]?\\s*[¥￥]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?))",
            Pattern.DOTALL
    );

    private PriceConsistencyCheckPolicy() {
    }

    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, null);
    }

    public static QualityCheckItemResult check(final DocumentContent bidDoc, final PriceRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.PRICE_CONSISTENCY;
        if (bidDoc == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "投标文件为空，无法核查报价一致性", "请上传包含完整报价的有效投标文件", Collections.emptyList());
        }

        ParsedPricePair pricePair = findBestPricePair(bidDoc);
        if (pricePair == null) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "未能在标书段落中识别到结构化的大写与小写总价对照关系",
                    "建议人工复核投标总报价大写与小写数字",
                    Collections.emptyList());
        }

        List<QualityEvidence> evidences = new ArrayList<>();
        evidences.add(QualityEvidence.bid("报价总价页", pricePair.pageNumber(), pricePair.snippet()));

        // 1. 大写与小写数字一致性校验
        if (!pricePair.isConsistent()) {
            return QualityCheckItemResult.fail(
                    item,
                    "报价大写金额与小写金额不一致！大写折算: ¥" + pricePair.convertedFromChinese() + "，小写填写: ¥" + pricePair.numericAmount(),
                    "请立刻核对并修正投标函或开标一览表中的大小写总价",
                    evidences);
        }

        BigDecimal bidPrice = pricePair.numericAmount();

        // 2. 最高投标限价超限废标一票否决判定
        if (requirement != null && requirement.maxPrice() != null && bidPrice != null) {
            BigDecimal maxPrice = requirement.maxPrice();
            if (bidPrice.compareTo(maxPrice) > 0) {
                return QualityCheckItemResult.fail(
                        item,
                        "投标报价（¥" + bidPrice + "）超过招标文件最高投标限价（¥" + maxPrice + "），存在法定一票否决废标风险！",
                        "依据招投标法实施条例第51条，高于最高投标限价将被否决投标，请立刻核减报价至限价以内",
                        evidences);
            }
        }

        // 3. 开标一览表总价与投标函总价一致性联动校验
        BigDecimal tablePrice = BidOpeningTableCheckPolicy.extractTablePrice(bidDoc);
        if (tablePrice != null && bidPrice != null && tablePrice.compareTo(bidPrice) != 0) {
            return QualityCheckItemResult.fail(
                    item,
                    "开标一览表总报价（¥" + tablePrice + "）与投标函总价（¥" + bidPrice + "）不一致！",
                    "请统一开标一览表与投标函中的总报价，杜绝表函不一的合规隐患",
                    evidences);
        }

        // 4. 分项报价明细表深度算术核验与占位符拦截
        ItemizedPriceCalculator.ItemizedParseResult itemized = ItemizedPriceCalculator.parseAndValidate(bidDoc);
        if (itemized.hasUnfilledPlaceholder()) {
            evidences.add(QualityEvidence.bid("分项报价占位留白", itemized.pageNumber(), itemized.placeholderSnippet()));
            return QualityCheckItemResult.fail(
                    item,
                    "分项报价明细表中检测到未填写的模板占位符或空白留白！",
                    "请在分项报价明细表中补齐所有项目单价、合价与金额明细，严禁保留模板空白",
                    evidences);
        }

        if (!itemized.arithmeticErrors().isEmpty()) {
            evidences.add(QualityEvidence.bid("分项报价算术错误", itemized.pageNumber(), String.join("; ", itemized.arithmeticErrors())));
            return QualityCheckItemResult.fail(
                    item,
                    "分项报价明细表存在算术计算错误！" + String.join("；", itemized.arithmeticErrors()),
                    "请修正分项单价、数量与合价的乘积或累加和，确保算术绝对准确",
                    evidences);
        }

        if (itemized.hasItemizedData() && itemized.calculatedTotal() != null && bidPrice != null) {
            if (itemized.calculatedTotal().compareTo(bidPrice) != 0) {
                evidences.add(QualityEvidence.bid("分项报价累加页", itemized.pageNumber(), "分项累加合计: ¥" + itemized.calculatedTotal()));
                return QualityCheckItemResult.fail(
                        item,
                        "分项报价累加总和（¥" + itemized.calculatedTotal() + "）与投标总报价（¥" + bidPrice + "）不一致！差额: ¥"
                                + itemized.calculatedTotal().subtract(bidPrice).abs(),
                        "请核对并统一分项报价明细表与投标总价，消除表间矛盾",
                        evidences);
            }
            if (itemized.statedTotal() != null && itemized.statedTotal().compareTo(bidPrice) != 0) {
                evidences.add(QualityEvidence.bid("分项标明合计页", itemized.pageNumber(), "分项标明合计: ¥" + itemized.statedTotal()));
                return QualityCheckItemResult.fail(
                        item,
                        "分项报价表标明合计（¥" + itemized.statedTotal() + "）与投标总报价（¥" + bidPrice + "）不一致！",
                        "请统一分项合计金额与投标函总价",
                        evidences);
            }
            evidences.add(QualityEvidence.bid("分项报价明细", itemized.pageNumber(), "分项条目数: " + itemized.rows().size()
                    + "，累加总计: ¥" + itemized.calculatedTotal()));
        }

        // 5. 强事实全部满足，直通 PASS
        String maxPriceNotice = (requirement != null && requirement.maxPrice() != null)
                ? "，且未超过招标文件最高投标限价（限价: ¥" + requirement.maxPrice() + "）"
                : "";
        String itemizedNotice = itemized.hasItemizedData()
                ? "，且分项报价表 " + itemized.rows().size() + " 项算术核验完全正确（分项合计 ¥" + itemized.calculatedTotal() + " 与总报价一致）"
                : "";
        return QualityCheckItemResult.pass(
                item,
                "报价大写与小写金额完全一致 (大写: " + pricePair.chineseText() + ", 小写: ¥" + bidPrice + ")" + maxPriceNotice + itemizedNotice,
                evidences);
    }

    private static ParsedPricePair findBestPricePair(final DocumentContent bidDoc) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (text.contains("大写") && text.contains("小写")) {
                ParsedPricePair pair = matchPrice(text, PRICE_PAIR_PATTERN, page.pageNumber());
                if (pair != null) {
                    return pair;
                }
            }
        }
        String fullText = !bidDoc.fullText().isBlank() ? bidDoc.fullText() : combinePages(bidDoc);
        return matchPrice(fullText, PRICE_PAIR_PATTERN, null);
    }

    private static String combinePages(final DocumentContent bidDoc) {
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            sb.append(page.text()).append("\n");
        }
        return sb.toString();
    }

    private static ParsedPricePair matchPrice(final String text, final Pattern pattern, final Integer pageNumber) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String cn = m.group(1).replaceAll("[\\s整正]", "").trim();
            if (cn.isEmpty()) return null;
            String numStr = m.group(2).replace(",", "").trim();
            if (numStr.matches("^[0-9]+(\\.[0-9]{1,2})?$")) {
                BigDecimal num = new BigDecimal(numStr);
                BigDecimal conv = ChineseAmountConverter.convertChineseToNumber(cn);
                String snip = BasicInfoCheckPolicy.snippetAround(text, 250, "大写：", "小写：", "投标总价", "投标报价");
                return new ParsedPricePair(cn, num, conv, pageNumber, snip);
            }
        }
        return null;
    }

    record ParsedPricePair(
            String chineseText,
            BigDecimal numericAmount,
            BigDecimal convertedFromChinese,
            Integer pageNumber,
            String snippet) {

        public boolean isConsistent() {
            return numericAmount != null && convertedFromChinese != null
                    && numericAmount.compareTo(convertedFromChinese) == 0;
        }
    }
}
