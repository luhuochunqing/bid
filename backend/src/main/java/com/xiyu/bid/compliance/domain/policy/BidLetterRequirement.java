// Input: 招标文件中提取的投标函/报价函格式、抬头称谓与有效期
// Output: 投标函结构化要求 Record（纯核心模型）
// Pos: compliance/domain/policy/ - 投标函格式要求契约模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * 投标函/报价函/响应函格式与内容要求契约.
 */
public record BidLetterRequirement(
        String letterType,
        String recipientName,
        Integer validityDays,
        String description) {

    public static BidLetterRequirement fuse(final BidLetterRequirement a, final BidLetterRequirement b) {
        if (a == null) return b;
        if (b == null) return a;
        String type = a.letterType() != null && !a.letterType().isBlank() ? a.letterType() : b.letterType();
        String recipient = a.recipientName() != null && !a.recipientName().isBlank() ? a.recipientName() : b.recipientName();
        Integer days = a.validityDays() != null ? a.validityDays() : b.validityDays();
        String desc = a.description() != null && !a.description().isBlank() ? a.description() : b.description();
        return new BidLetterRequirement(type, recipient, days, desc);
    }

    public static BidLetterRequirement fuseWithDeterministic(final BidLetterRequirement llmReq, final BidLetterRequirement detReq) {
        if (detReq == null) return llmReq;
        if (llmReq == null) return detReq;
        Integer days = detReq.validityDays() != null ? detReq.validityDays() : llmReq.validityDays();
        String type = detReq.letterType() != null && !detReq.letterType().isBlank() ? detReq.letterType() : llmReq.letterType();
        String recipient = detReq.recipientName() != null && !detReq.recipientName().isBlank() ? detReq.recipientName() : llmReq.recipientName();
        String desc = llmReq.description() != null && !llmReq.description().isBlank() ? llmReq.description() : detReq.description();
        return new BidLetterRequirement(type, recipient, days, desc);
    }
}
