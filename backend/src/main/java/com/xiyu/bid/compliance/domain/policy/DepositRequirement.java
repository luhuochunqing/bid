// Input: 招标文件中提取的保证金形式、金额与免收标识
// Output: 投标保证金结构化要求 Record（纯核心模型）
// Pos: compliance/domain/policy/ - 投标保证金要求契约模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * 投标保证金要求契约.
 * required 为 false 表示明确免收/不涉及；true 为需缴纳；null 为未明确.
 */
public record DepositRequirement(
        Boolean required,
        String amount,
        String forms,
        String description) {

    public static DepositRequirement notRequired(final String description) {
        return new DepositRequirement(false, null, null, description);
    }

    public static DepositRequirement required(final String amount, final String forms, final String description) {
        return new DepositRequirement(true, amount, forms, description);
    }

    /**
     * 交叉校验与融合两条保证金要求提取结果（硬事实优先，杜绝偏向免收放大幻觉）.
     */
    public static DepositRequirement fuse(final DepositRequirement a, final DepositRequirement b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        // 1. 若一方具备明确金额（硬事实），优先以具备金额的要求为准
        boolean aHasAmount = a.amount() != null && !a.amount().isBlank();
        boolean bHasAmount = b.amount() != null && !b.amount().isBlank();
        if (aHasAmount && !bHasAmount) {
            return a;
        }
        if (bHasAmount && !aHasAmount) {
            return b;
        }

        // 2. 若双方结论一致，保留更详细的描述与形式
        if (Boolean.TRUE.equals(a.required()) && Boolean.TRUE.equals(b.required())) {
            String forms = a.forms() != null ? a.forms() : b.forms();
            String amount = a.amount() != null ? a.amount() : b.amount();
            return new DepositRequirement(true, amount, forms, a.description() != null ? a.description() : b.description());
        }
        if (Boolean.FALSE.equals(a.required()) && Boolean.FALSE.equals(b.required())) {
            return new DepositRequirement(false, null, null, a.description() != null ? a.description() : b.description());
        }

        // 3. 冲突防御：一方声称需缴纳（但无明确金额），另一方声称免收
        // 绝不无脑偏向免收！保持审慎合规原则，保留要求缴纳并附带冲突说明
        return new DepositRequirement(true, null, null, "提取结果存在分歧（条款与前附表表述不一致），按审慎合规原则提示缴纳并建议人工复核");
    }

    /**
     * 双轨融合：以确定性提取结果校准 LLM 提取结果.
     */
    public static DepositRequirement fuseWithDeterministic(final DepositRequirement llmReq, final DepositRequirement detReq) {
        if (detReq == null) {
            return llmReq;
        }
        if (llmReq == null) {
            return detReq;
        }
        boolean detHasAmount = detReq.amount() != null && !detReq.amount().isBlank();
        boolean llmHasAmount = llmReq.amount() != null && !llmReq.amount().isBlank();
        if (detHasAmount && llmHasAmount) {
            // 双有数：以确定性原文金额为准，防止大模型改写原文金额
            String forms = llmReq.forms() != null ? llmReq.forms() : detReq.forms();
            String desc = llmReq.description() != null ? llmReq.description() : detReq.description();
            return new DepositRequirement(true, detReq.amount(), forms, desc);
        }
        return fuse(llmReq, detReq);
    }
}
