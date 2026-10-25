// Input: 招标文件全文文本
// Output: 同一局部义务范围的财务要求；仅模板/条件豁免时返回未知契约
// Pos: compliance/domain/policy/ - 财务状况确定性提取（FP-Java）
// 维护声明: 义务判定、年份/报表/审计/印章字段必须使用同一条款范围。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 财务要求只来自正向义务条款，否定、条件和格式说明不扩张为硬要求。 */
public final class DeterministicFinancialExtractor {
    private static final Pattern YEAR_PATTERN = Pattern.compile("20[12][0-9]年?");
    private static final Pattern RELATIVE_YEAR_PATTERN = Pattern.compile("近[一二三两123]年|前[一二三两123]年|上一年度|上年度");
    private static final Pattern DEMAND_PATTERN = Pattern.compile("(?:必须|应当|须|需|应)(?:提供|提交|附具|报送|附)|要求提供");
    private static final Pattern EXEMPT_PATTERN = Pattern.compile("(?:不需要|不要求|不需|无需|无须|不必|免于|免除)(?:提供|提交|附具|报送|附|加盖|盖)");
    private static final Pattern CPA_SEAL_PATTERN = Pattern.compile("(?s)(?:会计师事务所|审计机构).{0,15}(?:印章|公章)");

    private DeterministicFinancialExtractor() { }

    public static FinancialRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank() || !hasFinancialKeywords(tenderText)) return null;
        ScopeAnalysis analysis = analyzeScopes(tenderText);
        if (analysis.obligations().isEmpty()) {
            return analysis.unqualifiedExemption()
                    ? FinancialRequirement.notRequired("招标文件明确无需或免除提供财务状况证明/审计报告")
                    : new FinancialRequirement(null, null, List.of(), List.of(), null,
                            "仅定位到财务主题、可选格式或条件条款，未定位到适用于本次投标的强制财务要求，请人工确认");
        }
        String scopedText = String.join("；", analysis.obligations());
        LinkedHashSet<String> years = matches(YEAR_PATTERN, scopedText);
        years = new LinkedHashSet<>(years.stream().map(year -> year.replace("年", "")).toList());
        years.addAll(matches(RELATIVE_YEAR_PATTERN, scopedText));
        List<String> statements = new ArrayList<>();
        if (scopedText.contains("资产负债表")) statements.add("资产负债表");
        if (scopedText.contains("利润表") || scopedText.contains("损益表")) statements.add("利润表");
        if (scopedText.contains("现金流量表")) statements.add("现金流量表");
        if (scopedText.contains("报表附注")) statements.add("财务报表附注");
        boolean auditRequired = scopedText.contains("审计报告") || scopedText.contains("经审计")
                || scopedText.contains("会计师事务所") || scopedText.contains("审计机构");
        return new FinancialRequirement(true, auditRequired, List.copyOf(years), List.copyOf(statements),
                CPA_SEAL_PATTERN.matcher(scopedText).find(), scopedText);
    }

    /** 是否存在无适用对象条件且没有正向冲突的明确财务豁免。 */
    public static boolean isFinancialNotRequired(final String text) {
        if (text == null || text.isBlank()) return false;
        ScopeAnalysis analysis = analyzeScopes(text);
        return analysis.unqualifiedExemption() && analysis.obligations().isEmpty();
    }

    static boolean hasPositiveFinancialDemand(final String text) {
        return text != null && !text.isBlank() && !analyzeScopes(text).obligations().isEmpty();
    }

    private static ScopeAnalysis analyzeScopes(final String text) {
        List<String> obligations = new ArrayList<>();
        boolean unqualifiedExemption = false;
        // 仅拼接财务主题续行，不把下一句的无关义务绑定为财务义务。
        String normalized = text.replaceAll(
                "[\\r\\n]+[ \\t]*(?=财务审计报告|财务会计报表|财务报表|财务报告|审计报告|资产负债表|利润表|现金流量表)", "");
        for (String sentence : normalized.split("[。；;]+")) {
            boolean activeFinancialDemand = false;
            boolean unconditionalContext = true;
            for (String raw : sentence.split("[，,\\r\\n]+")) {
                String clause = raw.trim();
                if (clause.isBlank()) continue;
                Matcher exempt = EXEMPT_PATTERN.matcher(clause);
                if (exempt.find() && isFinancialFieldContinuation(clause)) {
                    String subject = clause.substring(0, exempt.start());
                    if (hasFinancialKeywords(clause) && unconditionalContext && isUnqualifiedSubject(subject)) {
                        unqualifiedExemption = true;
                    }
                    activeFinancialDemand = false;
                    continue;
                }
                if (isOptionalOrFormat(clause)) {
                    activeFinancialDemand = false;
                    unconditionalContext = false;
                    continue;
                }
                Matcher demand = DEMAND_PATTERN.matcher(clause);
                if (demand.find()) {
                    activeFinancialDemand = hasFinancialKeywords(clause);
                    if (activeFinancialDemand) obligations.add(clause.substring(demand.start()));
                } else if (activeFinancialDemand && isFinancialFieldContinuation(clause)) {
                    obligations.add(clause);
                } else {
                    unconditionalContext = unconditionalContext && isUnconditionalContext(clause);
                }
            }
        }
        return new ScopeAnalysis(List.copyOf(obligations), unqualifiedExemption);
    }

    private static boolean isUnqualifiedSubject(final String subject) {
        String plain = subject.trim().replaceFirst("^(?:财务状况要求|财务要求|说明|备注|注)[：:]\\s*", "");
        return plain.isBlank() || plain.matches(
                "(?:(?:所有|全部|全体|各)(?:投标人|供应商)|本(?:项目|次采购|次招标|次投标)|投标人|供应商)(?:均|一律)?");
    }

    private static boolean isUnconditionalContext(final String clause) {
        String plain = clause.trim().replaceAll("[：:]$", "");
        return List.of("前附表", "财务状况要求", "财务要求", "说明", "备注", "注").contains(plain)
                || plain.matches("本(?:项目|次采购|次招标)(?:属于|为|是).+");
    }

    private static boolean isOptionalOrFormat(final String clause) {
        return clause.contains("如有") || clause.contains("若有") || clause.contains("如需")
                || clause.contains("若需") || clause.contains("参考格式") || clause.contains("参考模板");
    }

    private static boolean isFinancialFieldContinuation(final String clause) {
        return hasFinancialKeywords(clause) || clause.contains("会计师事务所") || clause.contains("审计机构");
    }

    private static boolean hasFinancialKeywords(final String text) {
        return text.contains("财务状况") || text.contains("财务审计报告") || text.contains("财务报表")
                || text.contains("财务会计报表") || text.contains("审计报告") || text.contains("资产负债表")
                || text.contains("利润表") || text.contains("损益表") || text.contains("现金流量表") || text.contains("财务报告");
    }

    private static LinkedHashSet<String> matches(final Pattern pattern, final String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) values.add(matcher.group());
        return values;
    }

    private record ScopeAnalysis(List<String> obligations, boolean unqualifiedExemption) { }
}
