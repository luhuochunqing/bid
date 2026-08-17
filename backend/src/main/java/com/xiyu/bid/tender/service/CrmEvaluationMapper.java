// Input: CRM 商机 VO + 对接人 VO 列表
// Output: 评估表 basic DTO + customerInfos DTO 列表
// Pos: Service 层内映射器（Spring Bean），依赖注入 ObjectMapper 处理 gapFile JSON 解析
// 维护声明: 仅做 VO → DTO 映射，不调 CRM、不查 DB。
//          映射规则与前端 useCrmOpportunitySelector.js 完全一致。
package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static java.util.Map.entry;

/**
 * CO-526: CRM VO → 评估表 DTO 纯映射器。
 * <p>从前端 {@code useCrmOpportunitySelector.js} 的映射逻辑移植而来，
 * 保证后端 submit 时拉取的 CRM 数据与前端关联商机时回填的数据格式一致。
 */
@Component
@Slf4j
@RequiredArgsConstructor
class CrmEvaluationMapper {

    private final ObjectMapper objectMapper;

    /**
     * CRM 对接人 position（数字字符串 1~14）→ 评估表 roleKey。
     * <p><b>SYNC:</b> 必须与前端 {@code src/views/Bidding/detail/components/customerInfoMatrixConfig.js}
     * 的 {@code CRM_POSITION_TO_ROLE} 保持一致。任一方调整时必须同步修改另一方。
     */
    private static final Map<String, String> CRM_POSITION_TO_ROLE = Map.ofEntries(
            entry("1", "PROJECT_HIGHEST_DECISION_MAKER"),
            entry("2", "MATERIALS_COMPANY_CHAIRMAN"),
            entry("3", "MATERIALS_COMPANY_ELECTRONICS_LEADER"),
            entry("4", "ELECTRONICS_COMPANY_CHAIRMAN"),
            entry("5", "ELECTRONICS_COMPANY_GENERAL_MANAGER"),
            entry("6", "ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER"),
            entry("7", "ELECTRONICS_COMPANY_OPERATIONS_LEADER"),
            entry("8", "BID_DOCUMENT_PREPARER"),
            entry("9", "OTHER_KEY_DECISION_MAKER_1"),
            entry("10", "OTHER_KEY_DECISION_MAKER_2"),
            entry("11", "OTHER_KEY_DECISION_MAKER_3"),
            entry("12", "EXPERT_1"),
            entry("13", "EXPERT_2"),
            entry("14", "EXPERT_3"));

    /**
     * 将 CRM 商机 VO 映射为评估表 basic DTO。
     * <p>映射规则与前端 useCrmOpportunitySelector.js confirmLink() 完全一致。
     */
    EvaluationBasicDTO mapChanceToBasic(CustomerChanceVO chance) {
        return new EvaluationBasicDTO(
                chance.planSupplierCount() != null ? chance.planSupplierCount().intValue() : null,
                chance.ecommerceMroAmount(),
                normalizeToEmpty(chance.bidDocumentDisadvantage()),
                normalizeToEmpty(chance.riskPrediction()),
                chance.backupPlan() != null ? (chance.backupPlan() ? "是" : "否") : "",
                normalizeToEmpty(chance.managerUnderstandProcess()),
                normalizeToEmpty(chance.remark()),
                // 2026-08-17 需求 3：CRM 商机的 projectGap 文本不再带入（GAP 文本下线），附件仍走 parseGapFiles
                chance.customerRevenue(),
                parseGapFiles(chance.gapFile()));
    }

    /**
     * 将 CRM 对接人列表映射为评估表客户信息 EAV 行列表。
     * <p>每个对接人产生 14 行（对应 14 个信息维度），roleKey 由 position 映射，
     * position 不在字典范围时落到 EXTERNAL_ROLE_N（与前端逻辑一致）。
     * <p><b>重复 position 处理（CO-526 Sentry Bug XIYU-X 修复 + 生产反馈）：</b>
     * 同一个 position 的多个对接人：第一个保留标准 roleKey，后续重复者落到 EXTERNAL_ROLE_N
     * 但 POSITION 字段仍记录原始 position，既避免 tender_evaluation_customer_info 表的
     * {@code uk_eval_role_info (evaluation_id, role_key, info_key)} 唯一约束冲突，又保留全部联系人数据。
     * null position 的对接人各自落到 EXTERNAL_ROLE_N。
     */
    List<EvaluationCustomerInfoDTO> mapContactsToCustomerInfos(List<ContactPersonInfoVO> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return List.of();
        }
        List<EvaluationCustomerInfoDTO> rows = new ArrayList<>();
        int externalRoleSeq = 0;
        java.util.Set<String> seenPositions = new java.util.HashSet<>();
        for (ContactPersonInfoVO c : contacts) {
            String position = c.position();
            String roleKey = CRM_POSITION_TO_ROLE.get(position);
            boolean isKnownPosition = roleKey != null;
            // 已知的 14 个 position 字典内重复 → 后续落到 EXTERNAL_ROLE_N，但保留 POSITION 字段
            if (isKnownPosition && !seenPositions.add(position)) {
                roleKey = "EXTERNAL_ROLE_" + (++externalRoleSeq);
                log.info("CO-526 fix: duplicate contact position={} name={} mapped to {} (first one kept standard role)",
                        position, c.name(), roleKey);
            }
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "NAME",
                    c.name() != null ? c.name() : "", "TEXT"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CONTACT_INFO",
                    resolveContactInfo(c), "TEXT"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "POSITION",
                    isKnownPosition ? position : null, "ENUM14"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "XIYU_CONTACT",
                    c.ehsyProjectManager() != null ? c.ehsyProjectManager() : "", "TEXT"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CONTACT_METHOD",
                    c.contactMethod() != null ? c.contactMethod() : "", "ENUM7"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "INFO_TENDENCY_BASIS",
                    c.preferenceBasis() != null ? c.preferenceBasis() : "", "TEXT"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CONTACTED",
                    boolToYesNo(c.contacted()), "DROPDOWN"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "GUIDED_BID",
                    boolToYesNo(c.guidedBidDocument()), "DROPDOWN"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CAN_GET_KEY_INFO",
                    boolToYesNo(c.getKeyInfo()), "DROPDOWN"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CAN_REMOVE_ADVERSE",
                    boolToYesNo(c.deleteDisadvantage()), "DROPDOWN"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "CAN_SYNC_EVAL",
                    boolToYesNo(c.syncInfo()), "DROPDOWN"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "TENDENCY",
                    normalizeToNull(c.preferenceLevel()), "DROPDOWN"));
            // SWITCH 字段只有 true/false 两种语义，空值不存在，必须始终保留一行
            rows.add(new EvaluationCustomerInfoDTO(roleKey, "INFO_CLEAR_WINNER_BID",
                    String.valueOf(c.guaranteeWin() != null && c.guaranteeWin()), "SWITCH"));
            addIfHasValue(rows, new EvaluationCustomerInfoDTO(roleKey, "INFO_WIN_RATE_IMPACT",
                    normalizeToNull(c.impactRate()), "DROPDOWN6"));
        }
        return rows;
    }

    private String resolveContactInfo(ContactPersonInfoVO c) {
        if (c.phone() != null && !c.phone().isBlank()) return c.phone();
        if (c.email() != null && !c.email().isBlank()) return c.email();
        return "";
    }

    private String boolToYesNo(Boolean value) {
        return value != null ? (value ? "是" : "否") : null;
    }

    /**
     * 与前端 {@code buildApiPayload} 过滤逻辑对齐：值为 null 或空白字符串时不生成 EAV 行。
     * 避免 CRM 空字段（如 contactMethod）被映射成空串后触发后端必填校验。
     */
    private void addIfHasValue(List<EvaluationCustomerInfoDTO> rows, EvaluationCustomerInfoDTO row) {
        if (row.value() == null || row.value().isBlank()) {
            return;
        }
        rows.add(row);
    }

    /**
     * 空白字符串归一为 null（与前端 {@code value || null} 语义对齐）。
     * 用于 TENDENCY / INFO_WIN_RATE_IMPACT 等 DROPDOWN 字段，空串与 null 语义等价。
     */
    private String normalizeToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * null 归一为空串（与前端 {@code value || ''} 语义对齐）。
     * 用于 basic 字符串字段，避免 null vs 空串在下游 equals 比较时出现差异。
     */
    private String normalizeToEmpty(String value) {
        return value != null ? value : "";
    }

    /**
     * 解析 CRM 商机 gapFile 字段为 GAP 附件引用列表。
     * <p><b>SYNC:</b> 解析逻辑与前端 {@code useCrmOpportunitySelector.js parseGapFiles()} 完全一致，
     * 任一方调整时必须同步修改另一方。
     * <p>gapFile 可能是：JSON 数组字符串、JSON 对象字符串（单文件）、或单个 URL 字符串。
     */
    private List<EvaluationBasicDTO.GapFileRef> parseGapFiles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isArray()) {
                List<EvaluationBasicDTO.GapFileRef> files = new ArrayList<>();
                for (JsonNode item : node) {
                    EvaluationBasicDTO.GapFileRef ref = parseGapFileNode(item);
                    if (ref != null && ref.fileUrl() != null && !ref.fileUrl().isBlank()) {
                        files.add(ref);
                    }
                }
                return files;
            }
            if (node.isObject()) {
                EvaluationBasicDTO.GapFileRef ref = parseGapFileNode(node);
                return (ref != null && ref.fileUrl() != null && !ref.fileUrl().isBlank())
                        ? List.of(ref) : List.of();
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse gapFile as JSON, treating as single URL: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to parse gapFile as JSON, treating as single URL: {}", e.getMessage());
        }
        // 非合法 JSON → 按单个 URL 兜底
        return List.of(new EvaluationBasicDTO.GapFileRef("GAP附件", trimmed));
    }

    private EvaluationBasicDTO.GapFileRef parseGapFileNode(JsonNode node) {
        if (node == null) return null;
        String name = textOrDefault(node, "name", node.path("fileName").asText(""), "GAP附件");
        String url = textOrDefault(node, "url", node.path("fileUrl").asText(""), "");
        return new EvaluationBasicDTO.GapFileRef(name, url);
    }

    private String textOrDefault(JsonNode node, String primaryField, String fallback, String defaultValue) {
        String val = node.path(primaryField).asText("");
        if (val.isEmpty()) val = fallback;
        return val.isEmpty() ? defaultValue : val;
    }
}
