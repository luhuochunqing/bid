// Input: 招标文件提取的财务状况、审计报告、三表及年份要求
// Output: 财务状况结构化要求 Record（纯核心模型）
// Pos: compliance/domain/policy/ - 财务状况与审计报告要求契约模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 财务状况证明与财务审计报告要求契约.
 */
public record FinancialRequirement(
        Boolean required,
        Boolean auditReportRequired,
        List<String> requiredYears,
        List<String> requiredStatements,
        Boolean requireCpaSeal,
        String description) {

    public FinancialRequirement {
        requiredYears = requiredYears == null ? List.of() : List.copyOf(requiredYears);
        requiredStatements = requiredStatements == null ? List.of() : List.copyOf(requiredStatements);
    }

    public static FinancialRequirement standard() {
        return new FinancialRequirement(
                true,
                true,
                List.of(),
                List.of("资产负债表", "利润表", "现金流量表"),
                true,
                "须提供经会计师事务所审计的财务会计报告（包含资产负债表、利润表、现金流量表及事务所印章）");
    }

    public static FinancialRequirement notRequired(final String description) {
        return new FinancialRequirement(false, false, List.of(), List.of(), false, description);
    }

    public static FinancialRequirement fuse(final FinancialRequirement a, final FinancialRequirement b) {
        if (a == null) return b;
        if (b == null) return a;
        Boolean req = resolveRequired(a.required(), b.required());
        Boolean audit = Boolean.TRUE.equals(a.auditReportRequired()) || Boolean.TRUE.equals(b.auditReportRequired());
        Boolean cpa = Boolean.TRUE.equals(a.requireCpaSeal()) || Boolean.TRUE.equals(b.requireCpaSeal());

        LinkedHashSet<String> years = new LinkedHashSet<>(a.requiredYears());
        years.addAll(b.requiredYears());

        LinkedHashSet<String> stmts = new LinkedHashSet<>(a.requiredStatements());
        stmts.addAll(b.requiredStatements());

        String desc = pickDescription(req, a, b);
        return new FinancialRequirement(req, audit, List.copyOf(years), List.copyOf(stmts), cpa, desc);
    }

    /** 确定性字段来自同一原文义务范围；未知不是豁免，禁止LLM无源扩张字段。 */
    public static FinancialRequirement fuseWithDeterministic(final FinancialRequirement llmReq, final FinancialRequirement detReq) {
        return detReq != null ? detReq : llmReq;
    }

    /** 免/要冲突时按审慎合规保留必须提供，与保证金 fuse 对齐. */
    static Boolean resolveRequired(final Boolean a, final Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) {
            return true;
        }
        if (a != null) {
            return a;
        }
        return b;
    }

    static String pickDescription(final Boolean required, final FinancialRequirement a, final FinancialRequirement b) {
        if (Boolean.TRUE.equals(required)) {
            if (Boolean.TRUE.equals(a.required()) && a.description() != null && !a.description().isBlank()) {
                return a.description();
            }
            if (Boolean.TRUE.equals(b.required()) && b.description() != null && !b.description().isBlank()) {
                return b.description();
            }
        }
        if (a.description() != null && !a.description().isBlank()) {
            return a.description();
        }
        return b.description();
    }
}
