// Input: LLM 结构化提取的 JSON 响应字符串（contracts/tender-requirements-extraction.md §2，容忍 markdown 代码围栏与前后解释文字）
// Output: TenderRequirements 招标要求模型；JSON 非法、截断或响应为空 → missingSentinel，不抛异常
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
import com.xiyu.bid.compliance.domain.policy.FrontTableRequirement;
import com.xiyu.bid.compliance.domain.policy.IdAuthLetterRequirement;
import com.xiyu.bid.compliance.domain.policy.PriceRequirement;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;

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
     * 解析 LLM JSON 响应（契约 §2）：各顶层键缺失按空数组/null 兜底；
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
        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return TenderRequirements.missingSentinel();
        }
        if (root == null || !root.isObject()) {
            return TenderRequirements.missingSentinel();
        }
        List<TenderRequirements.ScoringItem> scoringItems = new ArrayList<>();
        for (JsonNode node : RequirementSectionParser.arrayAt(root, "scoringItems")) {
            String name = RequirementSectionParser.textAt(node, "name");
            if (name.isBlank()) {
                continue;
            }
            Double maxScore = node.path("maxScore").isNumber() ? node.path("maxScore").asDouble() : null;
            scoringItems.add(new TenderRequirements.ScoringItem(name, maxScore, RequirementSectionParser.textAt(node, "criteria")));
        }
        List<TenderRequirements.VetoClause> vetoClauses = new ArrayList<>();
        for (JsonNode node : RequirementSectionParser.arrayAt(root, "vetoClauses")) {
            String clause = RequirementSectionParser.textAt(node, "clause");
            if (clause.isBlank()) {
                continue;
            }
            vetoClauses.add(new TenderRequirements.VetoClause(clause, RequirementSectionParser.stringListAt(node, "keywords")));
        }
        DepositRequirement depositRequirement = RequirementSectionParser.parseDepositRequirement(root.path("depositRequirement"));
        BidLetterRequirement bidLetterRequirement = RequirementSectionParser.parseBidLetterRequirement(root.path("bidLetterRequirement"));
        AuthorizationRequirement authorizationRequirement = RequirementSectionParser.parseAuthorizationRequirement(root.path("authorizationRequirement"));
        BusinessLicenseRequirement businessLicenseRequirement = RequirementSectionParser.parseBusinessLicenseRequirement(root.path("businessLicenseRequirement"));
        IdAuthLetterRequirement idAuthLetterRequirement = RequirementSectionParser.parseIdAuthLetterRequirement(root.path("idAuthLetterRequirement"));
        FinancialRequirement financialRequirement = RequirementSectionParser.parseFinancialRequirement(root.path("financialRequirement"));
        PriceRequirement priceRequirement = RequirementSectionParser.parsePriceRequirement(root.path("priceRequirement"));
        CaCertificateRequirement caCertificateRequirement = RequirementSectionParser.parseCaCertificateRequirement(root.path("caCertificateRequirement"));
        FrontTableRequirement frontTableRequirement = RequirementSectionParser.parseFrontTableRequirement(root.path("frontTableRequirement"));
        return new TenderRequirements(
                scoringItems,
                RequirementSectionParser.stringListAt(root, "mandatoryQualifications"),
                vetoClauses,
                RequirementSectionParser.stringListAt(root, "compositionRequirements"),
                RequirementSectionParser.stringListAt(root, "formatRequirements"),
                depositRequirement,
                bidLetterRequirement,
                authorizationRequirement,
                businessLicenseRequirement,
                idAuthLetterRequirement,
                financialRequirement,
                priceRequirement,
                caCertificateRequirement,
                frontTableRequirement,
                false);
    }

}
