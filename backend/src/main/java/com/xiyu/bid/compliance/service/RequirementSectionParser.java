// Input: Jackson JsonNode
// Output: 各类专属需求契约对象（保证金、投标函、授权书、执照、身份证明、财务、报价、CA、前附表等）
// Pos: compliance/service/ - 招标要求子项 JSON 解析器（spec 049 拆分产物，服务 TenderRequirementsParser）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.compliance.domain.policy.AuthorizationRequirement;
import com.xiyu.bid.compliance.domain.policy.BidLetterRequirement;
import com.xiyu.bid.compliance.domain.policy.BusinessLicenseRequirement;
import com.xiyu.bid.compliance.domain.policy.CaCertificateRequirement;
import com.xiyu.bid.compliance.domain.policy.DepositRequirement;
import com.xiyu.bid.compliance.domain.policy.FinancialRequirement;
import com.xiyu.bid.compliance.domain.policy.FrontTableRequirement;
import com.xiyu.bid.compliance.domain.policy.IdAuthLetterRequirement;
import com.xiyu.bid.compliance.domain.policy.PriceRequirement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招标要求子项 JSON 解析器.
 */
final class RequirementSectionParser {

    private RequirementSectionParser() {
    }

    static DepositRequirement parseDepositRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Boolean required = node.has("required") && !node.get("required").isNull()
                ? node.get("required").asBoolean()
                : null;
        String amount = textAt(node, "amount");
        String forms = textAt(node, "forms");
        String description = textAt(node, "description");
        return new DepositRequirement(
                required,
                amount.isBlank() ? null : amount,
                forms.isBlank() ? null : forms,
                description.isBlank() ? null : description
        );
    }

    static BidLetterRequirement parseBidLetterRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String letterType = textAt(node, "letterType");
        String recipientName = textAt(node, "recipientName");
        Integer validityDays = node.path("validityDays").isNumber() ? node.path("validityDays").asInt() : null;
        String description = textAt(node, "description");
        if (letterType.isBlank() && recipientName.isBlank() && validityDays == null && description.isBlank()) {
            return null;
        }
        return new BidLetterRequirement(
                letterType.isBlank() ? null : letterType,
                recipientName.isBlank() ? null : recipientName,
                validityDays,
                description.isBlank() ? null : description
        );
    }

    static AuthorizationRequirement parseAuthorizationRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Boolean required = node.has("required") && !node.get("required").isNull()
                ? node.get("required").asBoolean()
                : null;
        Boolean allowLegalRepOnly = node.has("allowLegalRepresentativeOnly") && !node.get("allowLegalRepresentativeOnly").isNull()
                ? node.get("allowLegalRepresentativeOnly").asBoolean()
                : null;
        String templateTitle = textAt(node, "templateTitle");
        String description = textAt(node, "description");
        if (required == null && allowLegalRepOnly == null && templateTitle.isBlank() && description.isBlank()) {
            return null;
        }
        return new AuthorizationRequirement(
                required,
                allowLegalRepOnly,
                templateTitle.isBlank() ? null : templateTitle,
                description.isBlank() ? null : description
        );
    }

    static BusinessLicenseRequirement parseBusinessLicenseRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        boolean required = !node.has("required") || node.get("required").isNull() || node.get("required").asBoolean();
        List<String> allowCertificates = stringListAt(node, "allowCertificates");
        String requireOriginalOrCopy = textAt(node, "requireOriginalOrCopy");
        String description = textAt(node, "description");
        if (allowCertificates.isEmpty() && requireOriginalOrCopy.isBlank() && description.isBlank()) {
            return null;
        }
        return new BusinessLicenseRequirement(
                required,
                allowCertificates,
                requireOriginalOrCopy.isBlank() ? null : requireOriginalOrCopy,
                description.isBlank() ? null : description
        );
    }

    static IdAuthLetterRequirement parseIdAuthLetterRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        boolean required = !node.has("required") || node.get("required").isNull() || node.get("required").asBoolean();
        boolean requireFrontAndBack = node.path("requireFrontAndBack").asBoolean(true);
        boolean requireSocialSec = node.path("requireSocialSecurity").asBoolean(false);
        String requireOriginalOrCopy = textAt(node, "requireOriginalOrCopy");
        String description = textAt(node, "description");
        if (!node.has("requireFrontAndBack") && !node.has("requireSocialSecurity")
                && requireOriginalOrCopy.isBlank() && description.isBlank()) {
            return null;
        }
        return new IdAuthLetterRequirement(
                required,
                requireFrontAndBack,
                requireSocialSec,
                requireOriginalOrCopy.isBlank() ? null : requireOriginalOrCopy,
                description.isBlank() ? null : description
        );
    }

    static FinancialRequirement parseFinancialRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Boolean required = node.has("required") && !node.get("required").isNull() ? node.get("required").asBoolean() : null;
        Boolean auditReportRequired = node.has("auditReportRequired") && !node.get("auditReportRequired").isNull() ? node.get("auditReportRequired").asBoolean() : null;
        Boolean requireCpaSeal = node.has("requireCpaSeal") && !node.get("requireCpaSeal").isNull() ? node.get("requireCpaSeal").asBoolean() : null;
        List<String> requiredYears = stringListAt(node, "requiredYears");
        List<String> requiredStatements = stringListAt(node, "requiredStatements");
        String description = textAt(node, "description");
        if (required == null && auditReportRequired == null && requireCpaSeal == null
                && requiredYears.isEmpty() && requiredStatements.isEmpty() && description.isBlank()) {
            return null;
        }
        return new FinancialRequirement(
                required,
                auditReportRequired,
                requiredYears,
                requiredStatements,
                requireCpaSeal,
                description.isBlank() ? null : description
        );
    }

    static PriceRequirement parsePriceRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Boolean required = node.has("required") && !node.get("required").isNull() ? node.get("required").asBoolean() : null;
        BigDecimal maxPrice = (node.has("maxPrice") && node.get("maxPrice").isNumber()) ? BigDecimal.valueOf(node.get("maxPrice").asDouble()) : null;
        String maxPriceRawText = textAt(node, "maxPriceRawText");
        String pricingMethod = textAt(node, "pricingMethod");
        Boolean requireItemizedList = node.has("requireItemizedList") && !node.get("requireItemizedList").isNull() ? node.get("requireItemizedList").asBoolean() : null;
        Boolean requireBidOpeningTable = node.has("requireBidOpeningTable") && !node.get("requireBidOpeningTable").isNull() ? node.get("requireBidOpeningTable").asBoolean() : null;
        String description = textAt(node, "description");
        if (required == null && maxPrice == null && maxPriceRawText.isBlank() && pricingMethod.isBlank()
                && requireItemizedList == null && requireBidOpeningTable == null && description.isBlank()) {
            return null;
        }
        return new PriceRequirement(
                required,
                maxPrice,
                maxPriceRawText.isBlank() ? null : maxPriceRawText,
                pricingMethod.isBlank() ? null : pricingMethod,
                requireItemizedList,
                requireBidOpeningTable,
                description.isBlank() ? null : description
        );
    }

    static CaCertificateRequirement parseCaCertificateRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) return null;
        Boolean required = node.has("required") && !node.get("required").isNull() ? node.get("required").asBoolean() : null;
        String platform = textAt(node, "platformName");
        Boolean copy = node.has("requireCertificateCopy") && !node.get("requireCertificateCopy").isNull() ? node.get("requireCertificateCopy").asBoolean() : null;
        Boolean seal = node.has("requireElectronicSeal") && !node.get("requireElectronicSeal").isNull() ? node.get("requireElectronicSeal").asBoolean() : null;
        String desc = textAt(node, "description");
        if (required == null && platform.isBlank() && copy == null && seal == null && desc.isBlank()) return null;
        return new CaCertificateRequirement(
                required == null || required, platform.isBlank() ? null : platform,
                Boolean.TRUE.equals(copy), Boolean.TRUE.equals(seal), desc.isBlank() ? null : desc
        );
    }

    static FrontTableRequirement parseFrontTableRequirement(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Boolean required = node.has("required") && !node.get("required").isNull() ? node.get("required").asBoolean() : null;
        String projectName = textAt(node, "projectName");
        String tenderProjectNo = textAt(node, "tenderProjectNo");
        String deliveryPeriod = textAt(node, "deliveryPeriod");
        String warrantyPeriod = textAt(node, "warrantyPeriod");
        String validityPeriod = textAt(node, "validityPeriod");
        String deliveryLocation = textAt(node, "deliveryLocation");
        String paymentTerms = textAt(node, "paymentTerms");
        String qualityStandard = textAt(node, "qualityStandard");
        String description = textAt(node, "description");

        Map<String, String> keyClauses = new LinkedHashMap<>();
        JsonNode clausesNode = node.path("keyClauses");
        if (clausesNode.isObject()) {
            clausesNode.fieldNames().forEachRemaining(key -> {
                String val = textAt(clausesNode, key);
                if (!val.isBlank()) {
                    keyClauses.put(key, val);
                }
            });
        }

        boolean hasAny = required != null || !projectName.isBlank() || !tenderProjectNo.isBlank()
                || !deliveryPeriod.isBlank() || !warrantyPeriod.isBlank() || !validityPeriod.isBlank()
                || !deliveryLocation.isBlank() || !paymentTerms.isBlank() || !qualityStandard.isBlank()
                || !description.isBlank() || !keyClauses.isEmpty();
        if (!hasAny) {
            return null;
        }

        return new FrontTableRequirement(
                required,
                projectName.isBlank() ? null : projectName,
                tenderProjectNo.isBlank() ? null : tenderProjectNo,
                deliveryPeriod.isBlank() ? null : deliveryPeriod,
                warrantyPeriod.isBlank() ? null : warrantyPeriod,
                validityPeriod.isBlank() ? null : validityPeriod,
                deliveryLocation.isBlank() ? null : deliveryLocation,
                paymentTerms.isBlank() ? null : paymentTerms,
                qualityStandard.isBlank() ? null : qualityStandard,
                keyClauses,
                description.isBlank() ? null : description
        );
    }

    static Iterable<JsonNode> arrayAt(final JsonNode root, final String key) {
        JsonNode node = root.path(key);
        return node.isArray() ? node : List.of();
    }

    static List<String> stringListAt(final JsonNode root, final String key) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayAt(root, key)) {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().trim());
            }
        }
        return values;
    }

    static String textAt(final JsonNode node, final String key) {
        JsonNode value = node.path(key);
        return value.isTextual() ? value.asText().trim() : "";
    }
}
