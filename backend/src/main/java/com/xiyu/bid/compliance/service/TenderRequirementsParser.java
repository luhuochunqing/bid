// Input: LLM 结构化提取的 JSON 响应字符串（contracts/tender-requirements-extraction.md §2，容忍 markdown 代码围栏与前后解释文字）
// Output: TenderRequirements 招标要求模型；JSON 非法或响应为空 → missingSentinel，不抛异常
// Pos: compliance/service/ - 招标要求 JSON 解析适配器（spec 049 US2，契约 §2/§3）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.policy.AuthorizationRequirement;
import com.xiyu.bid.compliance.domain.policy.BidLetterRequirement;
import com.xiyu.bid.compliance.domain.policy.BusinessLicenseRequirement;
import com.xiyu.bid.compliance.domain.policy.CaCertificateRequirement;
import com.xiyu.bid.compliance.domain.policy.DepositRequirement;
import com.xiyu.bid.compliance.domain.policy.FinancialRequirement;
import com.xiyu.bid.compliance.domain.policy.IdAuthLetterRequirement;
import com.xiyu.bid.compliance.domain.policy.PriceRequirement;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 招标要求 LLM JSON 响应解析适配器（spec 049 第二步）.
 *
 * <p>JSON 解析属于边界 IO 行为（Jackson 抛 checked 异常），故置于 service 层
 * 而非纯核心 record（FP-Java：纯核心禁止以异常驱动业务流）；解析失败一律降级
 * {@link TenderRequirements#missingSentinel()}，不抛异常、不阻断任务.
 *
 * <p>容错机制：剥离 markdown 代码围栏（```json）与前后解释文字，通过首个配平花括号
 * 提取 JSON 对象子串，避免 LLM 自然语言交互引发的虚假反序列化失败与降级.
 */
public final class TenderRequirementsParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TenderRequirementsParser() {
    }

    /**
     * 解析 LLM JSON 响应（契约 §2）：五个顶层键缺失按空数组兜底；
     * 容忍 markdown 代码围栏与前后解释文字；
     * JSON 非法或响应为空 → {@link TenderRequirements#missingSentinel()}，不抛异常.
     *
     * @param llmJson LLM 返回的 JSON 字符串
     * @return 招标要求模型或哨兵值
     */
    public static TenderRequirements parse(final String llmJson) {
        if (llmJson == null || llmJson.isBlank()) {
            return TenderRequirements.missingSentinel();
        }
        String json = LlmVerdictParser.extractJsonObject(llmJson);
        if (json == null) {
            return TenderRequirements.missingSentinel();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return TenderRequirements.missingSentinel();
            }
            List<TenderRequirements.ScoringItem> scoringItems = new ArrayList<>();
            for (JsonNode node : arrayAt(root, "scoringItems")) {
                String name = textAt(node, "name");
                if (name.isBlank()) {
                    continue;
                }
                Double maxScore = node.path("maxScore").isNumber() ? node.path("maxScore").asDouble() : null;
                scoringItems.add(new TenderRequirements.ScoringItem(name, maxScore, textAt(node, "criteria")));
            }
            List<TenderRequirements.VetoClause> vetoClauses = new ArrayList<>();
            for (JsonNode node : arrayAt(root, "vetoClauses")) {
                String clause = textAt(node, "clause");
                if (clause.isBlank()) {
                    continue;
                }
                vetoClauses.add(new TenderRequirements.VetoClause(clause, stringListAt(node, "keywords")));
            }
            DepositRequirement depositRequirement = parseDepositRequirement(root.path("depositRequirement"));
            BidLetterRequirement bidLetterRequirement = parseBidLetterRequirement(root.path("bidLetterRequirement"));
            AuthorizationRequirement authorizationRequirement = parseAuthorizationRequirement(root.path("authorizationRequirement"));
            BusinessLicenseRequirement businessLicenseRequirement = parseBusinessLicenseRequirement(root.path("businessLicenseRequirement"));
            IdAuthLetterRequirement idAuthLetterRequirement = parseIdAuthLetterRequirement(root.path("idAuthLetterRequirement"));
            FinancialRequirement financialRequirement = parseFinancialRequirement(root.path("financialRequirement"));
            PriceRequirement priceRequirement = parsePriceRequirement(root.path("priceRequirement"));
            CaCertificateRequirement caCertificateRequirement = parseCaCertificateRequirement(root.path("caCertificateRequirement"));
            return new TenderRequirements(
                    scoringItems,
                    stringListAt(root, "mandatoryQualifications"),
                    vetoClauses,
                    stringListAt(root, "compositionRequirements"),
                    stringListAt(root, "formatRequirements"),
                    depositRequirement,
                    bidLetterRequirement,
                    authorizationRequirement,
                    businessLicenseRequirement,
                    idAuthLetterRequirement,
                    financialRequirement,
                    priceRequirement,
                    caCertificateRequirement,
                    false);
        } catch (JsonProcessingException e) {
            return TenderRequirements.missingSentinel();
        }
    }

    private static IdAuthLetterRequirement parseIdAuthLetterRequirement(final JsonNode node) {
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

    private static BusinessLicenseRequirement parseBusinessLicenseRequirement(final JsonNode node) {
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

    private static AuthorizationRequirement parseAuthorizationRequirement(final JsonNode node) {
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

    private static BidLetterRequirement parseBidLetterRequirement(final JsonNode node) {
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

    private static DepositRequirement parseDepositRequirement(final JsonNode node) {
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

    private static FinancialRequirement parseFinancialRequirement(final JsonNode node) {
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

    private static PriceRequirement parsePriceRequirement(final JsonNode node) {
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

    private static CaCertificateRequirement parseCaCertificateRequirement(final JsonNode node) {
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

    private static Iterable<JsonNode> arrayAt(final JsonNode root, final String key) {
        JsonNode node = root.path(key);
        return node.isArray() ? node : List.of();
    }

    private static List<String> stringListAt(final JsonNode root, final String key) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayAt(root, key)) {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().trim());
            }
        }
        return values;
    }

    private static String textAt(final JsonNode node, final String key) {
        JsonNode value = node.path(key);
        return value.isTextual() ? value.asText().trim() : "";
    }
}
