// Input: 招标文件提取的工期、质保期、投标有效期及付款条款要求
// Output: 商务条款结构化契约 Record（纯核心模型）
// Pos: compliance/domain/policy/ - 商务条款要求契约模型（FP-Java 纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * 招标商务条款要求契约（工期、质保期、投标有效期等）.
 */
public record BusinessTermsRequirement(
        Boolean required,
        Integer maxScheduleDays,
        String scheduleRawText,
        Integer minWarrantyMonths,
        String warrantyRawText,
        Integer bidValidityDays,
        String bidValidityRawText,
        String paymentTerms,
        String performanceBondRatio,
        String description) {

    public static BusinessTermsRequirement standard() {
        return new BusinessTermsRequirement(
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "须完全响应招标文件规定的工期、质保期及相关商务条款");
    }

    public static BusinessTermsRequirement notRequired(final String description) {
        return new BusinessTermsRequirement(
                false, null, null, null, null, null, null, null, null, description);
    }

    public static BusinessTermsRequirement fuse(final BusinessTermsRequirement a, final BusinessTermsRequirement b) {
        if (a == null) return b;
        if (b == null) return a;
        Boolean req = a.required() != null ? a.required() : b.required();
        Integer sched = a.maxScheduleDays() != null ? a.maxScheduleDays() : b.maxScheduleDays();
        String schedRaw = a.scheduleRawText() != null && !a.scheduleRawText().isBlank()
                ? a.scheduleRawText() : b.scheduleRawText();
        Integer war = a.minWarrantyMonths() != null ? a.minWarrantyMonths() : b.minWarrantyMonths();
        String warRaw = a.warrantyRawText() != null && !a.warrantyRawText().isBlank()
                ? a.warrantyRawText() : b.warrantyRawText();
        Integer val = a.bidValidityDays() != null ? a.bidValidityDays() : b.bidValidityDays();
        String valRaw = a.bidValidityRawText() != null && !a.bidValidityRawText().isBlank()
                ? a.bidValidityRawText() : b.bidValidityRawText();
        String pay = a.paymentTerms() != null && !a.paymentTerms().isBlank()
                ? a.paymentTerms() : b.paymentTerms();
        String bond = a.performanceBondRatio() != null && !a.performanceBondRatio().isBlank()
                ? a.performanceBondRatio() : b.performanceBondRatio();
        String desc = a.description() != null && !a.description().isBlank()
                ? a.description() : b.description();
        return new BusinessTermsRequirement(req, sched, schedRaw, war, warRaw, val, valRaw, pay, bond, desc);
    }

    public static BusinessTermsRequirement fuseWithDeterministic(
            final BusinessTermsRequirement llmReq, final BusinessTermsRequirement detReq) {
        if (detReq == null) return llmReq;
        if (llmReq == null) return detReq;
        // 确定性提取数值优先（抗大模型幻觉与数值微调）
        Integer sched = detReq.maxScheduleDays() != null ? detReq.maxScheduleDays() : llmReq.maxScheduleDays();
        String schedRaw = detReq.scheduleRawText() != null && !detReq.scheduleRawText().isBlank()
                ? detReq.scheduleRawText() : llmReq.scheduleRawText();
        Integer war = detReq.minWarrantyMonths() != null ? detReq.minWarrantyMonths() : llmReq.minWarrantyMonths();
        String warRaw = detReq.warrantyRawText() != null && !detReq.warrantyRawText().isBlank()
                ? detReq.warrantyRawText() : llmReq.warrantyRawText();
        Integer val = detReq.bidValidityDays() != null ? detReq.bidValidityDays() : llmReq.bidValidityDays();
        String valRaw = detReq.bidValidityRawText() != null && !detReq.bidValidityRawText().isBlank()
                ? detReq.bidValidityRawText() : llmReq.bidValidityRawText();
        Boolean req = detReq.required() != null ? detReq.required() : llmReq.required();
        String pay = detReq.paymentTerms() != null && !detReq.paymentTerms().isBlank()
                ? detReq.paymentTerms() : llmReq.paymentTerms();
        String bond = detReq.performanceBondRatio() != null && !detReq.performanceBondRatio().isBlank()
                ? detReq.performanceBondRatio() : llmReq.performanceBondRatio();
        String desc = detReq.description() != null && !detReq.description().isBlank()
                ? detReq.description() : llmReq.description();
        return new BusinessTermsRequirement(req, sched, schedRaw, war, warRaw, val, valRaw, pay, bond, desc);
    }
}
