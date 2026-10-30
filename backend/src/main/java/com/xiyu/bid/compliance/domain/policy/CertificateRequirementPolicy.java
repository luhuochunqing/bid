// Input: 单条完整资格条件及原文行/单元格
// Output: 子句级证书要求、作用对象、适用性及判断依据
// Pos: compliance/domain/policy/ - 资格条件内的证书子项纯核心
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.xiyu.bid.compliance.domain.policy.QualificationCondition.Applicability;
import static com.xiyu.bid.compliance.domain.policy.QualificationCondition.Target;

public final class CertificateRequirementPolicy {
    private static final Pattern CERTIFICATE = Pattern.compile("(?i)ISO\\s*[-:]?\\s*\\d{3,5}|许可证|认证|资质证书|资格证书|备案证明|等级保护");
    private static final Pattern OPTIONAL = Pattern.compile("[（(](?:如有|若有)(?:则)?提供[）)]|^(?:如有|若有)(?:该)?(?:证书|ISO\\s*\\d+)|^(?:无需|不要求|不必|不需要)");
    private static final Pattern OBLIGATION = Pattern.compile("^(?:(?:投标人|供应商|申请人|企业|公司|我方)\\s*)?(?:应当|必须|须|应|需)?\\s*(?:具有|具备|持有|取得|通过|提供)");

    private CertificateRequirementPolicy() { }

    static List<QualificationCondition.CertificateRequirement> classify(final String text, final String sourceQuote) {
        List<QualificationCondition.CertificateRequirement> result = new ArrayList<>();
        for (String part : text.split("[，,；;。\\r\\n]+")) {
            String clause = part.trim();
            if (!CERTIFICATE.matcher(clause).find() && !DeterministicQualificationExtractor.isSpecificCertificate(clause)) continue;
            Applicability applicability = OPTIONAL.matcher(clause).find() ? Applicability.OPTIONAL
                    : text.matches("(?s).*(?:或|任选|二选一|至少一项).*?")
                        || clause.matches(".*(?:如|若|不|无|除非|仅限).*?") ? Applicability.UNKNOWN
                    : OBLIGATION.matcher(clause).find() ? Applicability.MANDATORY : Applicability.UNKNOWN;
            Target target = clause.matches(".*(?:供货产品|所投产品|投标产品|产品应|产品须|货物).*?") ? Target.PRODUCT
                    : clause.matches(".*(?:人员|项目经理|工程师|员工).*?") ? Target.PERSON
                    : clause.matches(".*(?:投标人|供应商|企业|公司|我方).*?") ? Target.BIDDER : Target.UNKNOWN;
            result.add(new QualificationCondition.CertificateRequirement(clause, target, applicability,
                    applicability == Applicability.UNKNOWN ? text : clause,
                    cellIndex(sourceQuote, clause)));
        }
        return List.copyOf(result);
    }

    private static Integer cellIndex(final String row, final String clause) {
        if (!row.contains("|")) return null;
        String[] cells = row.replaceAll("^\\s*\\||\\|\\s*$", "").split("\\|", -1);
        Integer match = null;
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i].isBlank() && cells[i].trim().contains(clause)) {
                if (match != null) return null;
                match = i + 1;
            }
        }
        return match;
    }
}
