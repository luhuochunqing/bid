// Input: 招标文件抽取或规则提取的法定代表人身份证明与授权委托书要求
// Output: 授权委托书结构化要求契约 record（是否必填/是否允许法人证明替代/模板要求）
// Pos: compliance/domain/policy/ - 授权委托书要求领域契约
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * 授权委托书与法定代表人证明结构化要求契约（纯核心 record）.
 *
 * <p>required 为 false 时表示明确免除/不涉及（如自然人直接投标）；
 * allowLegalRepresentativeOnly 为 true 时表示法定代表人亲自投标只需提供身份证明，不要求授权委托书.
 */
public record AuthorizationRequirement(
        Boolean required,
        Boolean allowLegalRepresentativeOnly,
        String templateTitle,
        String description) {

    public static AuthorizationRequirement standard() {
        return new AuthorizationRequirement(true, true, "法定代表人身份证明及授权委托书", "需提供法定代表人身份证明及有效授权委托书");
    }

    public static AuthorizationRequirement legalRepresentativeOnlyAllowed(final String description) {
        return new AuthorizationRequirement(true, true, "法定代表人身份证明", description);
    }

    /**
     * 交叉校验与融合两条授权委托书要求提取结果（硬事实优先）.
     */
    public static AuthorizationRequirement fuse(final AuthorizationRequirement a, final AuthorizationRequirement b) {
        if (a == null) return b;
        if (b == null) return a;
        Boolean req = a.required() != null ? a.required() : b.required();
        Boolean allowLegalRep = a.allowLegalRepresentativeOnly() != null ? a.allowLegalRepresentativeOnly() : b.allowLegalRepresentativeOnly();
        String title = a.templateTitle() != null && !a.templateTitle().isBlank() ? a.templateTitle() : b.templateTitle();
        String desc = a.description() != null && !a.description().isBlank() ? a.description() : b.description();
        return new AuthorizationRequirement(req, allowLegalRep, title, desc);
    }

    /**
     * 双轨融合：以确定性提取结果校准 LLM 提取结果（硬事实优先）.
     */
    public static AuthorizationRequirement fuseWithDeterministic(final AuthorizationRequirement llmReq, final AuthorizationRequirement detReq) {
        if (detReq == null) return llmReq;
        if (llmReq == null) return detReq;
        Boolean req = detReq.required() != null ? detReq.required() : llmReq.required();
        Boolean allowLegalRep = detReq.allowLegalRepresentativeOnly() != null ? detReq.allowLegalRepresentativeOnly() : llmReq.allowLegalRepresentativeOnly();
        String title = detReq.templateTitle() != null && !detReq.templateTitle().isBlank() ? detReq.templateTitle() : llmReq.templateTitle();
        String desc = llmReq.description() != null && !llmReq.description().isBlank() ? llmReq.description() : detReq.description();
        return new AuthorizationRequirement(req, allowLegalRep, title, desc);
    }
}
