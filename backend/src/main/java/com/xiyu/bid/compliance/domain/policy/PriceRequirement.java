// Input: 招标文件提取的最高限价、报价方式、开标一览表及分项报价要求
// Output: 报价要求结构化契约 Record（纯核心模型）
// Pos: compliance/domain/policy/ - 报价与限价要求契约模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.math.BigDecimal;

/**
 * 报价要求与最高投标限价契约.
 */
public record PriceRequirement(
        Boolean required,
        BigDecimal maxPrice,
        String maxPriceRawText,
        String pricingMethod,
        Boolean requireItemizedList,
        Boolean requireBidOpeningTable,
        String description) {

    public static PriceRequirement standard() {
        return new PriceRequirement(
                true,
                null,
                null,
                "总价报价",
                true,
                true,
                "须提供开标一览表及分项报价表，报价不得超过招标文件规定的最高限价");
    }

    public static PriceRequirement notRequired(final String description) {
        return new PriceRequirement(false, null, null, null, false, false, description);
    }

    public static PriceRequirement fuse(final PriceRequirement a, final PriceRequirement b) {
        if (a == null) return b;
        if (b == null) return a;
        Boolean req = a.required() != null ? a.required() : b.required();
        BigDecimal max = a.maxPrice() != null ? a.maxPrice() : b.maxPrice();
        String rawText = a.maxPriceRawText() != null && !a.maxPriceRawText().isBlank() ? a.maxPriceRawText() : b.maxPriceRawText();
        String method = a.pricingMethod() != null && !a.pricingMethod().isBlank() ? a.pricingMethod() : b.pricingMethod();
        Boolean itemized = Boolean.TRUE.equals(a.requireItemizedList()) || Boolean.TRUE.equals(b.requireItemizedList());
        Boolean table = Boolean.TRUE.equals(a.requireBidOpeningTable()) || Boolean.TRUE.equals(b.requireBidOpeningTable());
        String desc = a.description() != null && !a.description().isBlank() ? a.description() : b.description();
        return new PriceRequirement(req, max, rawText, method, itemized, table, desc);
    }

    public static PriceRequirement fuseWithDeterministic(final PriceRequirement llmReq, final PriceRequirement detReq) {
        if (detReq == null) return llmReq;
        if (llmReq == null) return detReq;
        // 确定性提取的最高限价数值具备更高精度与可信度
        BigDecimal max = detReq.maxPrice() != null ? detReq.maxPrice() : llmReq.maxPrice();
        String rawText = detReq.maxPriceRawText() != null && !detReq.maxPriceRawText().isBlank()
                ? detReq.maxPriceRawText() : llmReq.maxPriceRawText();
        Boolean req = detReq.required() != null ? detReq.required() : llmReq.required();
        String method = detReq.pricingMethod() != null && !detReq.pricingMethod().isBlank()
                ? detReq.pricingMethod() : llmReq.pricingMethod();
        Boolean itemized = Boolean.TRUE.equals(detReq.requireItemizedList()) || Boolean.TRUE.equals(llmReq.requireItemizedList());
        Boolean table = Boolean.TRUE.equals(detReq.requireBidOpeningTable()) || Boolean.TRUE.equals(llmReq.requireBidOpeningTable());
        String desc = detReq.description() != null && !detReq.description().isBlank() ? detReq.description() : llmReq.description();
        return new PriceRequirement(req, max, rawText, method, itemized, table, desc);
    }
}
