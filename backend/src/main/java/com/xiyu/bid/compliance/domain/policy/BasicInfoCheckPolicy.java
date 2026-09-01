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
 * 基本信息类质量核查策略 (FP-Java).
 */
public final class BasicInfoCheckPolicy {

    private static final Pattern USCC_PATTERN = Pattern.compile("[0-9A-HJ-NPQRTUWXY]{2}[0-9]{6}[0-9A-HJ-NPQRTUWXY]{10}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})");

    private BasicInfoCheckPolicy() {
    }

    public static QualityCheckItemResult checkCoverInfo(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.COVER_INFO;
        List<DocumentContent.PageContent> pages = bidDoc.pages();
        if (pages.isEmpty()) {
            return QualityCheckItemResult.fail(item, "文档为空，未检测到封面", "请上传包含完整封面的有效投标文件", Collections.emptyList());
        }
        String text = pages.get(0).text();
        boolean hasProject = text.contains("项目") || text.contains("工程") || text.contains("招标");
        boolean hasBidder = text.contains("投标") || text.contains("单位") || text.contains("公司");
        boolean hasDate = text.matches("(?s).*(\\d{4}年|\\d{4}-|\\d{4}/).*");

        List<QualityEvidence> evidences = List.of(QualityEvidence.bid("封面第一页", pages.get(0).pageNumber(), snippet(text, 200)));
        if (hasProject && hasBidder && hasDate) {
            return QualityCheckItemResult.pass(item, "封面要素完整，包含项目名称、投标人信息与编制日期", evidences);
        }
        List<String> missing = new ArrayList<>();
        if (!hasProject) missing.add("项目名称");
        if (!hasBidder) missing.add("投标人全称");
        if (!hasDate) missing.add("日期信息");
        return QualityCheckItemResult.fail(item, "封面缺少必要关键要素: " + String.join("、", missing), "请在封面第一页补全" + String.join("、", missing), evidences);
    }

    public static QualityCheckItemResult checkBidLetterFormat(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.BID_LETTER_FORMAT;
        PageMatch match = findFirstPageContaining(bidDoc, "投标函");
        if (match != null) {
            boolean hasSalutation = match.text().contains("致：") || match.text().contains("致:");
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("投标函章节", match.pageNumber(), snippetAround(match.text(), 200, "投标函")));
            if (hasSalutation) {
                return QualityCheckItemResult.pass(item, "投标函格式规范，包含致招标人称谓与承诺段落", evidences);
            }
            return QualityCheckItemResult.manualReview(item, "已找到投标函，但未检测到标准'致：'称谓，建议复核", "请确认投标函抬头致招标人称谓是否准确", evidences);
        }
        return QualityCheckItemResult.fail(item, "未检索到投标函独立章节", "请在投标文件中添加标准格式的《投标函》", Collections.emptyList());
    }

    public static QualityCheckItemResult checkAuthorizationLetter(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.AUTHORIZATION_LETTER;
        PageMatch match = findFirstPageContaining(bidDoc, "授权委托书", "法定代表人身份证明");
        if (match != null) {
            boolean hasPrincipal = match.text().contains("法定代表人") || match.text().contains("委托人");
            boolean hasAgent = match.text().contains("受托人") || match.text().contains("委托代理人");
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("授权书章节", match.pageNumber(), snippetAround(match.text(), 200, "授权委托书", "法定代表人身份证明")));
            if (hasPrincipal && hasAgent) {
                return QualityCheckItemResult.pass(item, "授权委托书内容完备，委托人与受托人信息齐全", evidences);
            }
            return QualityCheckItemResult.manualReview(item, "检测到授权委托书，但委托人或受托人信息不完整", "请确保法定代表人身份证明与委托代理人权限清晰明确", evidences);
        }
        return QualityCheckItemResult.fail(item, "未检测到法定代表人身份证明或授权委托书", "请补充法定代表人证明书及有效授权委托书", Collections.emptyList());
    }

    public static QualityCheckItemResult checkBusinessLicense(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.BUSINESS_LICENSE;
        PageMatch match = findFirstPageContaining(bidDoc, "营业执照", "统一社会信用代码");
        if (match != null) {
            Matcher m = USCC_PATTERN.matcher(match.text());
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("营业执照部分", match.pageNumber(), snippetAround(match.text(), 200, "营业执照", "统一社会信用代码")));
            if (m.find()) {
                return QualityCheckItemResult.pass(item, "已包含营业执照且识别到有效统一社会信用代码: " + m.group(), evidences);
            }
            return QualityCheckItemResult.pass(item, "已包含营业执照相关证明文件", evidences);
        }
        return QualityCheckItemResult.fail(item, "未检索到营业执照或主体资格证明文件", "请在商务资质分卷中补充营业执照清晰扫描件", Collections.emptyList());
    }

    public static QualityCheckItemResult checkPriceConsistency(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.PRICE_CONSISTENCY;
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text.contains("投标总价") || text.contains("投标报价") || text.contains("大写：") || text.contains("小写：")) {
                ParsedPrice price = parsePricePair(text);
                if (price != null) {
                    List<QualityEvidence> evidences = List.of(QualityEvidence.bid("报价所在页", page.pageNumber(), snippetAround(text, 250, "投标总价", "投标报价", "大写：", "小写：")));
                    return toPriceResult(item, price, evidences);
                }
            }
        }
        // 跨页回退：物理页/虚拟块切开后，大写与小写金额可能分处两页（如投标函与报价表），
        // 逐页未命中时用全文（保留合并后 markdown，与分页前行为等价）做跨行比对
        String fullText = bidDoc.fullText();
        ParsedPrice fullPrice = parsePricePairCrossLine(fullText);
        if (fullPrice != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("报价所在页", null, snippetAround(fullText, 250, "大写：", "小写：", "投标总价", "投标报价")));
            return toPriceResult(item, fullPrice, evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未能在同一段落中识别到结构化的大小写总价对照关系", "建议人工复核投标总报价大写与小写数字", Collections.emptyList());
    }

    /** 报价对一致性裁决：一致 PASS、不一致 FAIL（避免逐页/全文两条路径重复分支）. */
    private static QualityCheckItemResult toPriceResult(
            final BidQualityCheckItem item, final ParsedPrice price, final List<QualityEvidence> evidences) {
        if (price.isConsistent()) {
            return QualityCheckItemResult.pass(item, "报价大写与小写金额完全一致 (大写: " + price.chineseText() + ", 小写: ¥" + price.numericAmount() + ")", evidences);
        }
        return QualityCheckItemResult.fail(item, "报价大写金额与小写金额不一致！大写折算: ¥" + price.convertedFromChinese() + "，小写填写: ¥" + price.numericAmount(), "请立刻核对并修正投标函或开标一览表中的大小写总价", evidences);
    }

    public static QualityCheckItemResult checkCurrencyUnit(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.CURRENCY_UNIT;
        PageMatch match = findFirstPageContaining(bidDoc, "人民币", "RMB", "￥", "元", "万元");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "计价货币单位规范，符合人民币法定计价标准", List.of(QualityEvidence.bid("报价计价单位", match.pageNumber(), snippetAround(match.text(), 150, "人民币", "RMB", "￥", "元", "万元"))));
        }
        return QualityCheckItemResult.manualReview(item, "未识别到明确的计价货币单位", "请确认报价表中明确标明货币单位（如：人民币/元）", Collections.emptyList());
    }

    public static QualityCheckItemResult checkScheduleResponse(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.SCHEDULE_RESPONSE;
        PageMatch match = findFirstPageContaining(bidDoc, "工期", "交货期", "日历天");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "已做出明确的工期或交货期承诺响应", List.of(QualityEvidence.bid("工期响应章节", match.pageNumber(), snippetAround(match.text(), 200, "工期", "交货期", "日历天"))));
        }
        return QualityCheckItemResult.fail(item, "未检测到明确的工期或交货期承诺条款", "请在投标函中补充工期/交货期承诺", Collections.emptyList());
    }

    public static QualityCheckItemResult checkSignatureSeal(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.SIGNATURE_SEAL;
        PageMatch match = findFirstPageContaining(bidDoc, "（盖章）", "(盖章)", "盖单位公章", "法定代表人（签字）", "签字或盖章");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "已检测到签章与落款占位，符合签署规范", List.of(QualityEvidence.bid("签章落款页", match.pageNumber(), snippetAround(match.text(), 200, "（盖章）", "(盖章)", "盖单位公章", "法定代表人（签字）", "签字或盖章"))));
        }
        return QualityCheckItemResult.manualReview(item, "未识别到标准签章文本占位或印章标记", "请确保投标函、授权委托书及承诺书均已加盖公章与法定代表人签字", Collections.emptyList());
    }

    public static QualityCheckItemResult checkContactInfo(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.CONTACT_INFO;
        PageMatch match = findFirstPageContaining(bidDoc, "联系人", "电话", "邮箱", "地址");
        if (match != null) {
            boolean hasPhone = PHONE_PATTERN.matcher(match.text()).find();
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("联系人信息", match.pageNumber(), snippetAround(match.text(), 200, "联系人", "电话", "邮箱", "地址")));
            if (hasPhone) {
                return QualityCheckItemResult.pass(item, "联系人、联系电话及通讯地址信息清晰完整", evidences);
            }
            return QualityCheckItemResult.manualReview(item, "包含联系人信息但未能识别到有效电话号码", "请核对联系人电话号码格式", evidences);
        }
        return QualityCheckItemResult.fail(item, "未检索到投标人联系人及联系方式", "请在基本信息或投标函中补充联系人、手机号与邮箱", Collections.emptyList());
    }

    record PageMatch(int pageNumber, String text) {}

    static PageMatch findFirstPageContaining(final DocumentContent bidDoc, final String... keywords) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            for (String kw : keywords) {
                if (page.text().contains(kw)) {
                    return new PageMatch(page.pageNumber(), page.text());
                }
            }
        }
        return null;
    }

    static String snippet(final String text, final int maxChars) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars) + "...";
    }

    /**
     * 围绕关键词命中位置摘录证据上下文.
     *
     * <p>分页缺失时整篇文档退化为单页，页首摘录会恒定指向封面文本，证据失去定位意义；
     * 本方法取最早命中的关键词位置为中心截取窗口，保证为原文子串（压缩空白后截断至 maxChars），
     * 未命中任何关键词时回退 {@link #snippet(String, int)} 页首行为。
     */
    static String snippetAround(final String text, final int maxChars, final String... keywords) {
        if (text == null) return "";
        int bestIdx = -1;
        int bestLen = 0;
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            int idx = text.indexOf(kw);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
                bestLen = kw.length();
            }
        }
        if (bestIdx < 0) {
            return snippet(text, maxChars);
        }
        int before = Math.max(0, (maxChars - bestLen) / 2);
        int start = Math.max(0, bestIdx - before);
        int end = Math.min(text.length(), start + maxChars * 2);
        return snippet(text.substring(start, end), maxChars);
    }

    record ParsedPrice(String chineseText, BigDecimal numericAmount, BigDecimal convertedFromChinese) {
        public boolean isConsistent() {
            return numericAmount != null && convertedFromChinese != null && numericAmount.compareTo(convertedFromChinese) == 0;
        }
    }

    static ParsedPrice parsePricePair(final String text) {
        Pattern p = Pattern.compile("(?:大写[：:]\\s*(?:人民币)?([零壹贰叁肆伍陆柒捌玖拾佰仟万亿元角分整正\\s]+)).*?(?:小写[：:]\\s*[¥￥]?([0-9,]+(?:\\.[0-9]{1,2})?))");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String cn = m.group(1).replaceAll("[\\s整正]", "").trim();
            String numStr = m.group(2).replace(",", "").trim();
            if (numStr.matches("^[0-9]+(\\.[0-9]{1,2})?$")) {
                BigDecimal num = new BigDecimal(numStr);
                BigDecimal converted = ChineseAmountConverter.convertChineseToNumber(cn);
                return new ParsedPrice(cn, num, converted);
            }
        }
        return null;
    }

    /** 跨行大写与小写之间最大距离（字符）：限制无界跨行匹配的误配面. */
    private static final int CROSS_LINE_PAIR_WINDOW = 300;

    /**
     * 跨行价格对匹配（仅全文回退使用）：{@code .} 默认不跨行，物理页/虚拟块切开后
     * 大写与小写之间必然隔着换行，逐页与单行正则都无法命中；此版本启用 DOTALL
     * 并以 {@link #CROSS_LINE_PAIR_WINDOW} 距离窗口约束，控制远距误配.
     */
    static ParsedPrice parsePricePairCrossLine(final String text) {
        Pattern p = Pattern.compile(
                "(?:大写[：:]\\s*(?:人民币)?([零壹贰叁肆伍陆柒捌玖拾佰仟万亿元角分整正\\s]+))"
                        + ".{0," + CROSS_LINE_PAIR_WINDOW + "}?(?:小写[：:]\\s*[¥￥]?([0-9,]+(?:\\.[0-9]{1,2})?))",
                Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String cn = m.group(1).replaceAll("[\\s整正]", "").trim();
            String numStr = m.group(2).replace(",", "").trim();
            if (numStr.matches("^[0-9]+(\\.[0-9]{1,2})?$")) {
                BigDecimal num = new BigDecimal(numStr);
                BigDecimal converted = ChineseAmountConverter.convertChineseToNumber(cn);
                return new ParsedPrice(cn, num, converted);
            }
        }
        return null;
    }
}
