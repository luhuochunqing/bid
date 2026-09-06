package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * FinancialStatementAccuracyCalculator 单元测试.
 */
class FinancialStatementAccuracyCalculatorTest {

    // ===== 资产负债表平衡等式校验 =====

    @Test
    void balanceSheet_balanced_noViolation() {
        String text = """
                资产负债表
                流动资产：
                资产总计  1000000.00
                负债合计  600000.00
                所有者权益合计  400000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(text, null, null);
        assertThat(result.hasViolations()).isFalse();
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void balanceSheet_imbalanced_returnViolation() {
        // 资产总计 1000，负债 600 + 权益 300 = 900，差额 100
        String text = """
                资产负债表
                资产总计  1000000.00
                负债合计  600000.00
                所有者权益合计  300000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(text, null, null);
        assertThat(result.hasViolations()).isTrue();
        assertThat(result.violations().get(0)).contains("资产负债表不平衡");
        assertThat(result.violations().get(0)).contains("差额");
    }

    @Test
    void balanceSheet_withinTolerance_noViolation() {
        // 差额 0.50 元，在容差 ±1 元内
        String text = """
                资产负债表
                资产总计  1000000.00
                负债合计  600000.00
                所有者权益合计  399999.50
                """;
        var result = FinancialStatementAccuracyCalculator.check(text, null, null);
        assertThat(result.hasViolations()).isFalse();
    }

    @Test
    void balanceSheet_partialExtraction_returnWarning() {
        // 只有资产总计，缺负债和权益
        String text = """
                资产负债表
                资产总计  1000000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(text, null, null);
        assertThat(result.hasViolations()).isFalse();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings().get(0)).contains("数字提取不完整");
    }

    // ===== 利润表勾稽校验 =====

    @Test
    void profitStatement_operatingProfitExceedsGrossProfit_violation() {
        // 营业利润 > 营业收入 - 营业成本（不合理）
        String text = """
                利润表
                营业收入  1000000.00
                营业成本  800000.00
                营业利润  250000.00
                净利润  200000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, text, null);
        assertThat(result.hasViolations()).isTrue();
        assertThat(result.violations().get(0)).contains("利润表勾稽异常");
    }

    @Test
    void profitStatement_netProfitExceedsRevenue_violation() {
        // 净利润 > 营业收入（不合理）
        String text = """
                利润表
                营业收入  500000.00
                营业成本  100000.00
                净利润  600000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, text, null);
        assertThat(result.hasViolations()).isTrue();
        assertThat(result.violations().get(0)).contains("利润表合理性异常");
    }

    @Test
    void profitStatement_normalData_noViolation() {
        String text = """
                利润表
                营业收入  1000000.00
                营业成本  700000.00
                营业利润  200000.00
                净利润  150000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, text, null);
        assertThat(result.hasViolations()).isFalse();
    }

    // ===== 现金流量表合理性校验 =====

    @Test
    void cashFlow_negativeEndingBalance_violation() {
        String text = """
                现金流量表
                经营活动产生的现金流量净额  500000.00
                期末现金余额  -10000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, null, text);
        assertThat(result.hasViolations()).isTrue();
        assertThat(result.violations().get(0)).contains("期末现金余额");
        assertThat(result.violations().get(0)).contains("< 0");
    }

    @Test
    void cashFlow_extremelyNegativeOperatingCf_warning() {
        String text = """
                现金流量表
                经营活动产生的现金流量净额  -200000000.00
                期末现金余额  50000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, null, text);
        assertThat(result.hasViolations()).isFalse();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings().get(0)).contains("经营活动现金流量净额");
    }

    @Test
    void cashFlow_normalPositiveBalance_noViolation() {
        String text = """
                现金流量表
                经营活动产生的现金流量净额  500000.00
                期末现金及现金等价物余额  200000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(null, null, text);
        assertThat(result.hasViolations()).isFalse();
        assertThat(result.hasWarnings()).isFalse();
    }

    // ===== 数字提取工具方法 =====

    @Test
    void extractFirst_chineseCommaNumber_parsedCorrectly() {
        // 通过完整三表校验间接验证数字提取：1,234,567.89 = 600,000 + 634,567.89（平衡）
        String balanceText = """
                资产负债表
                资产总计  1,234,567.89
                负债合计  600,000.00
                所有者权益合计  634,567.89
                """;
        var result = FinancialStatementAccuracyCalculator.check(balanceText, null, null);
        // 提取正确则差额为 0，不触发 violation
        assertThat(result.hasViolations()).isFalse();
    }

    @Test
    void extractFirst_noMatch_returnsEmpty() {
        // 无资产负债表关键词，extractFirst 无法匹配，不产生任何 violation 或 warning
        String balanceText = "企业简介 注册资本 100万 成立于2020年";
        var result = FinancialStatementAccuracyCalculator.check(balanceText, null, null);
        assertThat(result.hasViolations()).isFalse();
    }


    // ===== 汇总报告 =====

    @Test
    void summary_noViolationsNoWarnings_passMessage() {
        var result = FinancialStatementAccuracyCalculator.check(null, null, null);
        assertThat(result.summary()).isEqualTo("财务三表平衡勾稽核验通过");
    }

    @Test
    void summary_withViolation_containsFailMark() {
        String text = """
                资产负债表
                资产总计  1000000.00
                负债合计  500000.00
                所有者权益合计  300000.00
                """;
        var result = FinancialStatementAccuracyCalculator.check(text, null, null);
        assertThat(result.summary()).contains("❌");
    }
}
