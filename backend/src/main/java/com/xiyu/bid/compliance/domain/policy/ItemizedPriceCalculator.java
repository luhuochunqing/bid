// Input: 标书分项报价明细内容 (DocumentContent 或文本行)
// Output: 分项报价算术核验与占位符检查结果（FP-Java 纯核心）
// Pos: compliance/domain/policy/ - 分项报价计算与多表交叉校验助手
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分项报价计算与多表交叉校验助手 (FP-Java 纯核心).
 */
public final class ItemizedPriceCalculator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "_{3,}|【(?:请填写|此处填写|待填|待报价).*?】|（待定）|\\b待报价\\b"
    );

    private static final Pattern ROW_PATTERN = Pattern.compile(
            "(?:^|\\|)\\s*([0-9]{1,3})\\s*\\|\\s*([^|\\r\\n]+?)\\s*\\|\\s*(?:[^|\\r\\n]*?\\|)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*(?:[^|\\r\\n]*?\\|)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*\\|\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:\\||$)",
            Pattern.MULTILINE
    );

    private static final Pattern STATED_TOTAL_PATTERN = Pattern.compile(
            "(?:合\\s*计|总\\s*计|分项合计|总报价|小\\s*计)[：:\\s|]{1,10}[¥￥]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    private ItemizedPriceCalculator() {
    }

    public record ItemizedRow(
            int index,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            boolean arithmeticMatch) {
    }

    public record ItemizedParseResult(
            List<ItemizedRow> rows,
            BigDecimal statedTotal,
            BigDecimal calculatedTotal,
            boolean hasUnfilledPlaceholder,
            Integer pageNumber,
            String placeholderSnippet,
            List<String> arithmeticErrors) {

        public boolean hasItemizedData() {
            return rows != null && !rows.isEmpty();
        }

        public boolean isSumConsistent() {
            if (statedTotal == null || calculatedTotal == null) {
                return true;
            }
            return statedTotal.compareTo(calculatedTotal) == 0;
        }
    }

    public static ItemizedParseResult parseAndValidate(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return new ItemizedParseResult(List.of(), null, null, false, null, null, List.of());
        }

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (isItemizedPricePage(text)) {
                ItemizedParseResult result = parsePage(text, page.pageNumber());
                if (result.hasItemizedData() || result.hasUnfilledPlaceholder()) {
                    return result;
                }
            }
        }
        return new ItemizedParseResult(List.of(), null, null, false, null, null, List.of());
    }

    public static boolean isItemizedPricePage(final String text) {
        if (text == null) return false;
        boolean hasKeyword = text.contains("分项报价") || text.contains("报价明细")
                || text.contains("工程量清单") || text.contains("货物清单报价")
                || text.contains("分项明细表");
        boolean hasHeader = (text.contains("单价") && text.contains("合价"))
                || (text.contains("数量") && text.contains("总价"));
        return hasKeyword && hasHeader;
    }

    private static ItemizedParseResult parsePage(final String text, final Integer pageNumber) {
        Matcher phMatcher = PLACEHOLDER_PATTERN.matcher(text);
        boolean hasPh = phMatcher.find();
        String phSnippet = hasPh
                ? BasicInfoCheckPolicy.snippetAround(text, 150, phMatcher.group())
                : null;

        List<ItemizedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BigDecimal runningSum = BigDecimal.ZERO;

        Matcher rowMatcher = ROW_PATTERN.matcher(text);
        while (rowMatcher.find()) {
            Integer idx = parseSafeInt(rowMatcher.group(1));
            String name = rowMatcher.group(2).trim();
            BigDecimal qty = parseSafeDecimal(rowMatcher.group(3));
            BigDecimal unitPrice = parseSafeDecimal(rowMatcher.group(4));
            BigDecimal total = parseSafeDecimal(rowMatcher.group(5));
            if (idx == null || qty == null || unitPrice == null || total == null) {
                continue;
            }

            BigDecimal expectedTotal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            boolean match = expectedTotal.subtract(total).abs().compareTo(new BigDecimal("0.05")) <= 0;
            if (!match) {
                errors.add("第" + idx + "项[" + name + "]算术错误: 数量" + qty + " × 单价" + unitPrice
                        + " = " + expectedTotal + "，但填写为: " + total);
            }
            rows.add(new ItemizedRow(idx, name, qty, unitPrice, total, match));
            runningSum = runningSum.add(total);
        }

        BigDecimal statedTotal = null;
        Matcher totalMatcher = STATED_TOTAL_PATTERN.matcher(text);
        if (totalMatcher.find()) {
            statedTotal = parseSafeDecimal(totalMatcher.group(1));
        }

        BigDecimal calculatedTotal = rows.isEmpty() ? null : runningSum.setScale(2, RoundingMode.HALF_UP);
        if (statedTotal != null && calculatedTotal != null && statedTotal.compareTo(calculatedTotal) != 0) {
            errors.add("分项合价累加总和（¥" + calculatedTotal + "）与表内标明合计（¥" + statedTotal + "）不一致，差额: ¥"
                    + calculatedTotal.subtract(statedTotal).abs());
        }

        return new ItemizedParseResult(rows, statedTotal, calculatedTotal, hasPh, pageNumber, phSnippet, errors);
    }

    private static Integer parseSafeInt(final String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String clean = s.trim();
        if (!clean.matches("^[0-9]{1,9}$")) {
            return null;
        }
        long val = 0;
        for (int i = 0; i < clean.length(); i++) {
            val = val * 10 + (clean.charAt(i) - '0');
        }
        return val <= Integer.MAX_VALUE ? (int) val : null;
    }

    private static BigDecimal parseSafeDecimal(final String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String clean = s.replace(",", "").trim();
        if (!clean.matches("^[0-9]+(\\.[0-9]+)?$")) {
            return null;
        }
        return new BigDecimal(clean);
    }
}
