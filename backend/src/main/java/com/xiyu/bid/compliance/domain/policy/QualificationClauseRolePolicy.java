// Input: 完整资格子句及证书词命中
// Output: 持证义务、处罚事实、处罚证明或不确定角色与可检索原文
// Pos: compliance/domain/policy/ - 资格条件对象语义纯核心
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Pattern;

final class QualificationClauseRolePolicy {
    enum Role { CERTIFICATE, PENALTY_PROOF, PENALTY_FACT, UNKNOWN }
    record Assessment(Role role, String certificateClause) { }

    private static final Pattern PENALTY = Pattern.compile("吊销|暂扣|撤销|注销");
    private static final Pattern CERTIFICATE = Pattern.compile("(?i)ISO\\s*[-:]?\\s*\\d{3,5}|许可证|认证|资质证书|资格证书|备案证明|等级保护");
    private static final Pattern DOCUMENT = Pattern.compile("承诺书|声明书|证明材料|承诺函|说明书|情况说明|说明函|声明|承诺|说明");
    private static final Pattern DIRECT_ACTION = Pattern.compile("(?<!不)(?<!未)(?<!无)(?<!非)(?<!没)(?:持有|取得|具有|拥有|具备|提供|提交|出具|附带|附)(?!下列|以下|上述|资格条件|相关条件)");
    private static final Pattern STATEMENT_HEAD = Pattern.compile("(?:提供|提交|出具)[^，,；;。\\r\\n]{0,100}$");
    private static final Pattern JOIN = Pattern.compile("(?:并作|并提供|同时提供|以及|及相关|同时|并|及|和|与|、|或)");

    private QualificationClauseRolePolicy() { }

    static Assessment assess(final String clause) {
        var penalty = PENALTY.matcher(clause);
        if (!penalty.find()) return new Assessment(Role.CERTIFICATE, clause);
        var certificate = CERTIFICATE.matcher(clause);
        if (!certificate.find()) return new Assessment(Role.UNKNOWN, clause);
        var document = DOCUMENT.matcher(clause);
        int docStart = document.find() ? document.start() : -1;
        if (docStart >= 0) {
            int split = independentJoin(clause, certificate.end(), docStart);
            if (split >= 0) return new Assessment(Role.CERTIFICATE, clause.substring(0, split).trim());
            if (docStart > certificate.end() && STATEMENT_HEAD.matcher(clause.substring(0, docStart)).find())
                return new Assessment(Role.PENALTY_PROOF, "");
        }
        var action = DIRECT_ACTION.matcher(clause);
        if (action.find() && action.start() < certificate.start()) {
            String between = clause.substring(action.end(), certificate.start());
            if (!between.matches("(?s).*(?:下列|以下|上述)(?:资格)?条件.*"))
                return new Assessment(Role.CERTIFICATE, clause);
        }
        String modifier = penalty.end() <= certificate.start() ? clause.substring(penalty.end(), certificate.start()) : "";
        if (modifier.contains("的") && !modifier.matches("(?s).*的(?:情况|情形|企业|记录).*"))
            return new Assessment(Role.UNKNOWN, clause);
        return new Assessment(Role.PENALTY_FACT, "");
    }

    private static int independentJoin(final String text, final int from, final int documentStart) {
        if (documentStart <= from) return -1;
        var join = JOIN.matcher(text.substring(from, documentStart));
        return join.find() ? from + join.start() : -1;
    }
}
