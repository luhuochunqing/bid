// Input: 财务报表页文本（资产负债表、利润表、现金流量表各页 text）
// Output: 三表平衡等式与勾稽关系核验结果（BalanceCheckResult）
// Pos: compliance/domain/policy/ - 财务三表数字提取与平衡勾稽穿透校验助手（FP-Java，纯核心，行预算严格 <= 280）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 财务三表平衡等式与勾稽关系穿透校验助手（纯函数，FP-Java：无 void 业务方法，无 try-catch 流程控制）.
 *
 * <p>核验规则：
 * <ol>
 *   <li>资产负债表平衡：资产总计 == 负债合计 + 所有者权益合计（允许 ±1 元浮动）</li>
 *   <li>利润表勾稽：营业利润不得超过 营业收入 - 营业成本</li>
 *   <li>现金流量表合理性：期末现金余额 >= 0</li>
 * </ol>
 */
public final class FinancialStatementAccuracyCalculator {

    /** 允许的浮动误差（元），用于处理四舍五入差异 */
    static final BigDecimal TOLERANCE = BigDecimal.ONE;

    // ---- 资产负债表科目 ----
    private static final Pattern TOTAL_ASSETS_PATTERN = Pattern.compile(
            "资产(?:总计|合计)[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);
    private static final Pattern TOTAL_LIABILITIES_PATTERN = Pattern.compile(
            "负债(?:合计|总计)[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);
    private static final Pattern TOTAL_EQUITY_PATTERN = Pattern.compile(
            "(?:所有者权益|股东权益)(?:合计|总计)?[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);

    // ---- 利润表科目 ----
    private static final Pattern OPERATING_INCOME_PATTERN = Pattern.compile(
            "营业收入[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);
    private static final Pattern OPERATING_COST_PATTERN = Pattern.compile(
            "营业成本[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);
    private static final Pattern OPERATING_PROFIT_PATTERN = Pattern.compile(
            "营业利润[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);
    private static final Pattern NET_PROFIT_PATTERN = Pattern.compile(
            "净利润[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)", Pattern.MULTILINE);

    // ---- 现金流量表科目 ----
    private static final Pattern ENDING_CASH_PATTERN = Pattern.compile(
            "(?:期末现金(?:及现金等价物)?余额|现金及现金等价物期末余额)[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)",
            Pattern.MULTILINE);
    private static final Pattern OPERATING_CF_PATTERN = Pattern.compile(
            "经营活动产生的现金流量净额[^0-9\\-－\\n]{0,20}([\\-－]?[0-9][0-9,，.]*)",
            Pattern.MULTILINE);

    private FinancialStatementAccuracyCalculator() {
    }

    /**
     * 对三张报表文本执行平衡等式与勾稽关系穿透校验.
     *
     * @param balanceSheetText 资产负债表页文本（null 表示未找到）
     * @param profitText       利润表页文本（null 表示未找到）
     * @param cashFlowText     现金流量表页文本（null 表示未找到）
     * @return BalanceCheckResult 含硬性违规项与软性预警项
     */
    public static BalanceCheckResult check(
            final String balanceSheetText,
            final String profitText,
            final String cashFlowText) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        violations.addAll(balanceSheetViolations(balanceSheetText));
        warnings.addAll(balanceSheetWarnings(balanceSheetText));
        violations.addAll(profitViolations(profitText));
        violations.addAll(cashFlowViolations(cashFlowText));
        warnings.addAll(cashFlowWarnings(cashFlowText));
        return new BalanceCheckResult(violations, warnings);
    }

    // ---- 资产负债表 ----

    private static List<String> balanceSheetViolations(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<BigDecimal> totalAssets = extractFirst(TOTAL_ASSETS_PATTERN, text);
        Optional<BigDecimal> totalLiabilities = extractFirst(TOTAL_LIABILITIES_PATTERN, text);
        Optional<BigDecimal> totalEquity = extractFirst(TOTAL_EQUITY_PATTERN, text);
        if (totalAssets.isEmpty() || totalLiabilities.isEmpty() || totalEquity.isEmpty()) {
            return List.of();
        }
        BigDecimal rightSide = totalLiabilities.get().add(totalEquity.get());
        BigDecimal diff = totalAssets.get().subtract(rightSide).abs().setScale(2, RoundingMode.HALF_UP);
        if (diff.compareTo(TOLERANCE) > 0) {
            return List.of(String.format(
                    "[资产负债表不平衡] 资产总计=%.2f，负债合计+所有者权益=%.2f，差额=%.2f（超出容差 ±%.2f 元）",
                    totalAssets.get(), rightSide, totalAssets.get().subtract(rightSide), TOLERANCE));
        }
        return List.of();
    }

    private static List<String> balanceSheetWarnings(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<BigDecimal> totalAssets = extractFirst(TOTAL_ASSETS_PATTERN, text);
        Optional<BigDecimal> totalLiabilities = extractFirst(TOTAL_LIABILITIES_PATTERN, text);
        Optional<BigDecimal> totalEquity = extractFirst(TOTAL_EQUITY_PATTERN, text);
        // 三值均缺失（附注页等）不发预警；部分缺失则发预警
        boolean anyPresent = totalAssets.isPresent() || totalLiabilities.isPresent() || totalEquity.isPresent();
        boolean allPresent = totalAssets.isPresent() && totalLiabilities.isPresent() && totalEquity.isPresent();
        if (anyPresent && !allPresent) {
            return List.of(String.format(
                    "资产负债表数字提取不完整（资产总计=%s，负债合计=%s，所有者权益=%s），无法完成平衡等式校验",
                    fmtOpt(totalAssets), fmtOpt(totalLiabilities), fmtOpt(totalEquity)));
        }
        return List.of();
    }

    // ---- 利润表 ----

    private static List<String> profitViolations(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<BigDecimal> income = extractFirst(OPERATING_INCOME_PATTERN, text);
        Optional<BigDecimal> cost = extractFirst(OPERATING_COST_PATTERN, text);
        Optional<BigDecimal> profit = extractFirst(OPERATING_PROFIT_PATTERN, text);
        Optional<BigDecimal> netProfit = extractFirst(NET_PROFIT_PATTERN, text);

        List<String> result = new ArrayList<>();
        if (income.isPresent() && cost.isPresent() && profit.isPresent()) {
            BigDecimal grossProfit = income.get().subtract(cost.get());
            if (profit.get().compareTo(grossProfit.add(TOLERANCE)) > 0) {
                result.add(String.format(
                        "[利润表勾稽异常] 营业利润=%.2f 大于 营业收入-营业成本=%.2f，差额=%.2f，存在勾稽错误",
                        profit.get(), grossProfit, profit.get().subtract(grossProfit)));
            }
        }
        if (netProfit.isPresent() && income.isPresent()
                && income.get().compareTo(BigDecimal.ZERO) > 0
                && netProfit.get().compareTo(income.get()) > 0) {
            result.add(String.format(
                    "[利润表合理性异常] 净利润=%.2f 大于营业收入=%.2f，财务数据存疑",
                    netProfit.get(), income.get()));
        }
        return List.copyOf(result);
    }

    // ---- 现金流量表 ----

    private static List<String> cashFlowViolations(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<BigDecimal> endingCash = extractFirst(ENDING_CASH_PATTERN, text);
        if (endingCash.isPresent() && endingCash.get().compareTo(BigDecimal.ZERO) < 0) {
            return List.of(String.format(
                    "[现金流量表异常] 期末现金余额=%.2f < 0，余额不应为负，数据存疑",
                    endingCash.get()));
        }
        return List.of();
    }

    private static List<String> cashFlowWarnings(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<BigDecimal> operatingCf = extractFirst(OPERATING_CF_PATTERN, text);
        if (operatingCf.isPresent()
                && operatingCf.get().compareTo(new BigDecimal("-100000000")) < 0) {
            return List.of(String.format(
                    "[现金流量预警] 经营活动现金流量净额=%.2f（< -1 亿元），请人工核实现金流量表数据",
                    operatingCf.get()));
        }
        return List.of();
    }

    /**
     * 从文本中提取正则匹配的第一个数字（FP-Java：不使用 try-catch 控制业务流，用 Pattern.matches 过滤有效数字）.
     */
    static Optional<BigDecimal> extractFirst(final Pattern pattern, final String text) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String raw = m.group(1).replaceAll("[,，]", "").replace("－", "-");
            if (isValidDecimal(raw)) {
                return Optional.of(new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return Optional.empty();
    }

    /** 校验字符串是否为合法小数，避免 try-catch 捕获 NumberFormatException. */
    private static boolean isValidDecimal(final String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private static String fmtOpt(final Optional<BigDecimal> opt) {
        return opt.map(BigDecimal::toPlainString).orElse("?");
    }

    /**
     * 三表平衡校验结果（record，纯不可变）.
     *
     * @param violations 硬性异常（平衡等式破坏或合理性异常）
     * @param warnings   软性预警（建议人工核验）
     */
    public record BalanceCheckResult(List<String> violations, List<String> warnings) {

        public BalanceCheckResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean hasViolations() {
            return !violations.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /** 生成人类可读的汇总文本. */
        public String summary() {
            if (!hasViolations() && !hasWarnings()) {
                return "财务三表平衡勾稽核验通过";
            }
            StringBuilder sb = new StringBuilder();
            for (String v : violations) {
                sb.append("❌ ").append(v).append("\n");
            }
            for (String w : warnings) {
                sb.append("⚠️ ").append(w).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
