// Input: 单条完整资格条件及原文行/单元格（支持清洗 HTML 标签与表格多行）
// Output: 子句级证书要求及分类、匹配、证据共用的识别结果
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
    private static final Pattern SUBJECT_AFFILIATE = Pattern.compile("[（(][^）)]*(?:母子公司|集团公司|体系内|关联公司)[^）)]*[）)]");
    private static final Pattern OBLIGATION = Pattern.compile("^(?:(?:投标人|供应商|申请人|企业|公司|我方)\\s*)?(?:应当|必须|须|应|需)?\\s*(?:具有|具备|持有|取得|通过|提供)");

    private CertificateRequirementPolicy() { }

    /** 分类及消费共用识别；完整证书子句不受旧证书名称长度上限裁剪。 */
    static boolean isCertificateReference(final String text) {
        String clean = QualificationConditionExtractor.stripHtml(text);
        return clean != null && !clean.isBlank()
                && !clean.matches(".*(?:授权文件|授权书|证明资料|股权关系|出资关系).*")
                && (CERTIFICATE.matcher(clean).find() || DeterministicQualificationExtractor.hasSpecificCertificateKeyword(clean));
    }

    static List<QualificationCondition.CertificateRequirement> classify(final String text, final String sourceQuote) {
        List<QualificationCondition.CertificateRequirement> result = new ArrayList<>();
        String cleanText = QualificationConditionExtractor.stripHtml(text);
        String cleanQuote = QualificationConditionExtractor.stripHtml(sourceQuote);
        Target defaultTarget = (cleanText.matches(".*(?:投标人|供应商|申请人|企业|公司|我方).*?")
                || (cleanQuote != null && cleanQuote.matches(".*(?:投标人|供应商|申请人|企业|公司).*?"))) ? Target.BIDDER : Target.UNKNOWN;
        for (String part : cleanText.split("[，,；;。\\r\\n]+|(?:且|并|同时)(?=(?:投标人|供应商)?(?:必须|应当|应|须|需)?(?:具有|具备|持有|取得|通过|提供))")) {
            String clause = part.trim();
            if (!isCertificateReference(clause)) continue;
            var role = QualificationClauseRolePolicy.assess(clause);
            if (role.role() == QualificationClauseRolePolicy.Role.PENALTY_FACT
                    || role.role() == QualificationClauseRolePolicy.Role.PENALTY_PROOF) continue;
            clause = role.certificateClause();
            String strippedClause = SUBJECT_AFFILIATE.matcher(clause).replaceAll("").trim();
            Applicability applicability = role.role() == QualificationClauseRolePolicy.Role.UNKNOWN ? Applicability.UNKNOWN
                    : OPTIONAL.matcher(strippedClause).find() ? Applicability.OPTIONAL
                    : strippedClause.matches(".*(?:或|或者|任选|二选一|至少一项).*")
                        || strippedClause.matches(".*(?:如|若|不|无|除非|仅限).*?") ? Applicability.UNKNOWN
                    : OBLIGATION.matcher(strippedClause).find() ? Applicability.MANDATORY : Applicability.UNKNOWN;
            Target target = strippedClause.matches(".*(?:供货产品|所投产品|投标产品|产品应|产品须|货物).*?") ? Target.PRODUCT
                    : strippedClause.matches(".*(?:人员|项目经理|工程师|员工).*?") ? Target.PERSON
                    : strippedClause.matches(".*(?:投标人|供应商|企业|公司|我方).*?") ? Target.BIDDER : defaultTarget;
            result.add(new QualificationCondition.CertificateRequirement(clause, target, applicability,
                    applicability == Applicability.UNKNOWN ? cleanText : clause,
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
