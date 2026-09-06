// Input: 招标文件全文文本
// Output: 财务状况证明与财务审计报告结构化要求 FinancialRequirement（纯规则提取）
// Pos: compliance/domain/policy/ - 招标文件财务状况确定性规则提取器（FP-Java）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件财务状况与审计报告要求纯核心确定性提取器.
 */
public final class DeterministicFinancialExtractor {

    private static final Pattern YEAR_PATTERN = Pattern.compile("20[12][0-9]年?");
    private static final Pattern RELATIVE_YEAR_PATTERN = Pattern.compile("(近[一二三两123]年|前[一二三两123]年|上一年度|上年度)");

    private DeterministicFinancialExtractor() {
    }

    /**
     * 从招标文件提取结构化财务要求.
     */
    public static FinancialRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        // 1. 豁免/免除判定
        if (isFinancialNotRequired(tenderText)) {
            return FinancialRequirement.notRequired("招标文件明确无需或免除提供财务状况证明/审计报告");
        }

        // 2. 是否提及财务状况或财务审计
        if (!hasFinancialKeywords(tenderText)) {
            return null;
        }

        // 3. 扫描提取年份要求与三表要素
        LinkedHashSet<String> years = new LinkedHashSet<>();
        LinkedHashSet<String> statements = new LinkedHashSet<>();
        boolean auditRequired = false;
        boolean cpaSealRequired = false;
        String bestDescription = null;

        String[] lines = tenderText.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 4) {
                continue;
            }
            if (!hasLineFinancialKeyword(trimmed)) {
                continue;
            }

            // 识别审计报告与事务所印章要求
            if (trimmed.contains("审计报告") || trimmed.contains("经审计") || trimmed.contains("审计机构")
                    || trimmed.contains("会计师事务所") || trimmed.contains("注册会计师")) {
                auditRequired = true;
                cpaSealRequired = true;
            }

            // 提取具体年份
            Matcher ym = YEAR_PATTERN.matcher(trimmed);
            while (ym.find()) {
                String y = ym.group().replace("年", "");
                years.add(y);
            }

            // 提取相对年份说明
            Matcher rym = RELATIVE_YEAR_PATTERN.matcher(trimmed);
            while (rym.find()) {
                years.add(rym.group());
            }

            // 提取三表要素
            if (trimmed.contains("资产负债表")) {
                statements.add("资产负债表");
            }
            if (trimmed.contains("利润表") || trimmed.contains("损益表")) {
                statements.add("利润表");
            }
            if (trimmed.contains("现金流量表")) {
                statements.add("现金流量表");
            }
            if (trimmed.contains("财务报表附注") || trimmed.contains("报表附注")) {
                statements.add("财务报表附注");
            }

            if (bestDescription == null || trimmed.length() > bestDescription.length()) {
                if (trimmed.length() <= 120) {
                    bestDescription = trimmed;
                }
            }
        }

        if (statements.isEmpty()) {
            statements.add("资产负债表");
            statements.add("利润表");
            statements.add("现金流量表");
        }

        StringBuilder desc = new StringBuilder("须提供");
        if (auditRequired) {
            desc.append("经会计师事务所审计的财务审计报告");
        } else {
            desc.append("健全的财务会计报表");
        }
        if (!years.isEmpty()) {
            desc.append("（涵盖 ").append(String.join("、", years)).append("）");
        }
        desc.append("，包含").append(String.join("、", statements));
        if (cpaSealRequired) {
            desc.append("并附会计师事务所印章");
        }

        return new FinancialRequirement(
                true,
                auditRequired,
                List.copyOf(years),
                List.copyOf(statements),
                cpaSealRequired,
                desc.toString());
    }

    public static boolean isFinancialNotRequired(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("无需提供财务状况") || text.contains("无需提供财务审计报告")
                || text.contains("免于提供财务审计报告") || text.contains("不要求提供财务")
                || text.contains("免于提供财务状况") || text.contains("无需财务状况证明")
                || text.contains("免除财务状况证明") || text.contains("免于提供财务报表");
    }

    private static boolean hasFinancialKeywords(final String text) {
        return text.contains("财务状况") || text.contains("财务审计报告")
                || text.contains("财务报表") || text.contains("审计报告")
                || text.contains("资产负债表") || text.contains("利润表")
                || text.contains("现金流量表") || text.contains("健全的财务会计制度")
                || text.contains("财务报告") || text.contains("会计师事务所");
    }

    private static boolean hasLineFinancialKeyword(final String line) {
        return line.contains("财务") || line.contains("审计")
                || line.contains("资产负债") || line.contains("利润")
                || line.contains("现金流量") || line.contains("损益");
    }
}
