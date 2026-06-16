// Input: List of customer info EAV rows (evaluation_id, role_key, info_key, value, value_type)
// Output: ValidationResult (fixed 13 roles × 14 info columns, no row mutations, value_type matching)
// Pos: 纯核心层（core）- 不依赖 Spring / JPA / 任何外部框架
// 维护声明: 客户信息矩阵的固定结构规则在此一次性校验；service 层不允许重复校验或绕过此策略。
package com.xiyu.bid.tender.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标讯评估客户信息段固定表格校验策略（纯函数）。
 *
 * <p>规则：
 * <ul>
 *   <li>role_key 必须属于预定义的 14 个固定角色 → INVALID_ROLE</li>
 *   <li>info_key 必须属于预定义的 14 个固定信息维度 → INVALID_INFO_KEY</li>
 *   <li>value_type 必须与预期类型一致（TEXT / DROPDOWN / SWITCH / ENUM14 / ENUM7 / DROPDOWN6）→ INVALID_VALUE_TYPE</li>
 *   <li>不允许增删预定义之外的行-row（即不允许提交未被认可的 role_key）→ INVALID_ROLE</li>
 *   <li>必填的 info_key 值不能为空 → REQUIRED</li>
 * </ul>
 *
 * <p>FR-004 定义的 14 个固定角色：
 * <ol>
 *   <li>PROJECT_HIGHEST_DECISION_MAKER — 项目最高决策人</li>
 *   <li>MATERIALS_COMPANY_CHAIRMAN — 物资公司董事长</li>
 *   <li>MATERIALS_COMPANY_ELECTRONICS_LEADER — 物资公司分管电商领导</li>
 *   <li>ELECTRONICS_COMPANY_CHAIRMAN — 电商公司董事长</li>
 *   <li>ELECTRONICS_COMPANY_GENERAL_MANAGER — 电商公司总经理</li>
 *   <li>ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER — 电商公司副总经理</li>
 *   <li>ELECTRONICS_COMPANY_OPERATIONS_LEADER — 电商公司运营负责人</li>
 *   <li>BID_DOCUMENT_PREPARER — 招标文件制作人</li>
 *   <li>OTHER_KEY_DECISION_MAKER_1 — 其他关键决策人 1</li>
 *   <li>OTHER_KEY_DECISION_MAKER_2 — 其他关键决策人 2</li>
 *   <li>OTHER_KEY_DECISION_MAKER_3 — 其他关键决策人 3</li>
 *   <li>EXPERT_1 — 专家 1</li>
 *   <li>EXPERT_2 — 专家 2</li>
 *   <li>EXPERT_3 — 专家 3</li>
 * </ol>
 *
 * <p>FR-005 定义的 14 个固定信息维度及其预期值类型：
 * <table>
 *   <tr><th>info_key</th><th>中文名</th><th>类型</th></tr>
 *   <tr><td>NAME</td><td>姓名</td><td>TEXT</td></tr>
 *   <tr><td>POSITION</td><td>职位</td><td>ENUM14（14选项枚举）</td></tr>
 *   <tr><td>XIYU_CONTACT</td><td>西域项目负责人</td><td>TEXT</td></tr>
 *   <tr><td>CONTACT_METHOD</td><td>触达方式</td><td>ENUM7（7选项枚举）</td></tr>
 *   <tr><td>EVALUATION_BASIS</td><td>倾向性评估依据</td><td>TEXT</td></tr>
 *   <tr><td>INFO_TENDENCY_BASIS</td><td>倾向性评估依据（新增）</td><td>TEXT</td></tr>
 *   <tr><td>INFO_CLEAR_WINNER_BID</td><td>是否给出明确中标信息</td><td>SWITCH</td></tr>
 *   <tr><td>INFO_WIN_RATE_IMPACT</td><td>对中标影响率</td><td>DROPDOWN6</td></tr>
 *   <tr><td>CONTACTED</td><td>是否触达</td><td>DROPDOWN（是/否）</td></tr>
 *   <tr><td>GUIDED_BID</td><td>是否向此人引导标书</td><td>DROPDOWN（是/否）</td></tr>
 *   <tr><td>CAN_GET_KEY_INFO</td><td>是否可获取关键信息</td><td>DROPDOWN（是/否）</td></tr>
 *   <tr><td>CAN_REMOVE_ADVERSE</td><td>是否可删除不利项</td><td>DROPDOWN（是/否）</td></tr>
 *   <tr><td>CAN_SYNC_EVAL</td><td>是否可同步评标信息</td><td>DROPDOWN（是/否）</td></tr>
 *   <tr><td>TENDENCY</td><td>对我司的倾向性</td><td>DROPDOWN（支持/中立/反对）</td></tr>
 * </table>
 *
 * <p>调用约定：
 * <ul>
 *   <li>{@code validate(null)} → 返回含 NullPointerException 语义的 ValidationResult</li>
 *   <li>合法输入 → 返回 {@code ValidationResult} 含 0..N 条 FieldError，永不抛出业务错误</li>
 * </ul>
 */
public final class TenderEvaluationCustomerInfoPolicy {

    private static final String CODE_INVALID_ROLE = "INVALID_ROLE";
    private static final String CODE_INVALID_INFO_KEY = "INVALID_INFO_KEY";
    private static final String CODE_INVALID_VALUE_TYPE = "INVALID_VALUE_TYPE";
    private static final String CODE_REQUIRED = "REQUIRED";

    /** 14 个固定角色键。 */
    public static final Set<String> VALID_ROLE_KEYS = Set.of(
        "PROJECT_HIGHEST_DECISION_MAKER",
        "MATERIALS_COMPANY_CHAIRMAN",
        "MATERIALS_COMPANY_ELECTRONICS_LEADER",
        "ELECTRONICS_COMPANY_CHAIRMAN",
        "ELECTRONICS_COMPANY_GENERAL_MANAGER",
        "ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER",
        "ELECTRONICS_COMPANY_OPERATIONS_LEADER",
        "BID_DOCUMENT_PREPARER",
        "OTHER_KEY_DECISION_MAKER_1",
        "OTHER_KEY_DECISION_MAKER_2",
        "OTHER_KEY_DECISION_MAKER_3",
        "EXPERT_1",
        "EXPERT_2",
        "EXPERT_3"
    );

    /** 角色键 → 中文显示名。 */
    public static final Map<String, String> ROLE_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("PROJECT_HIGHEST_DECISION_MAKER", "项目最高决策人"),
        Map.entry("MATERIALS_COMPANY_CHAIRMAN", "物资公司董事长"),
        Map.entry("MATERIALS_COMPANY_ELECTRONICS_LEADER", "物资公司分管电商领导"),
        Map.entry("ELECTRONICS_COMPANY_CHAIRMAN", "电商公司董事长"),
        Map.entry("ELECTRONICS_COMPANY_GENERAL_MANAGER", "电商公司总经理"),
        Map.entry("ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER", "电商公司副总经理"),
        Map.entry("ELECTRONICS_COMPANY_OPERATIONS_LEADER", "电商公司运营负责人"),
        Map.entry("BID_DOCUMENT_PREPARER", "招标文件制作人"),
        Map.entry("OTHER_KEY_DECISION_MAKER_1", "其他关键决策人1"),
        Map.entry("OTHER_KEY_DECISION_MAKER_2", "其他关键决策人2"),
        Map.entry("OTHER_KEY_DECISION_MAKER_3", "其他关键决策人3"),
        Map.entry("EXPERT_1", "专家1"),
        Map.entry("EXPERT_2", "专家2"),
        Map.entry("EXPERT_3", "专家3")
    );

    /** 14 个固定信息维度键（2026-06-16 删除 ROLE_NAME/HIGH_LEVEL_EXCHANGE/KEY_TARGET）。 */
    public static final Set<String> VALID_INFO_KEYS = Set.of(
        "NAME",
        "POSITION",
        "XIYU_CONTACT",
        "CONTACT_METHOD",
        "EVALUATION_BASIS",
        "INFO_TENDENCY_BASIS",
        "INFO_CLEAR_WINNER_BID",
        "INFO_WIN_RATE_IMPACT",
        "CONTACTED",
        "GUIDED_BID",
        "CAN_GET_KEY_INFO",
        "CAN_REMOVE_ADVERSE",
        "CAN_SYNC_EVAL",
        "TENDENCY"
    );

    /** 信息维度键 → 预期值类型。 */
    public static final Map<String, String> INFO_KEY_VALUE_TYPES = Map.ofEntries(
        Map.entry("NAME", "TEXT"),
        Map.entry("POSITION", "ENUM14"),
        Map.entry("XIYU_CONTACT", "TEXT"),
        Map.entry("CONTACT_METHOD", "ENUM7"),
        Map.entry("EVALUATION_BASIS", "TEXT"),
        Map.entry("INFO_TENDENCY_BASIS", "TEXT"),
        Map.entry("INFO_CLEAR_WINNER_BID", "SWITCH"),
        Map.entry("INFO_WIN_RATE_IMPACT", "DROPDOWN6"),
        Map.entry("CONTACTED", "DROPDOWN"),
        Map.entry("GUIDED_BID", "DROPDOWN"),
        Map.entry("CAN_GET_KEY_INFO", "DROPDOWN"),
        Map.entry("CAN_REMOVE_ADVERSE", "DROPDOWN"),
        Map.entry("CAN_SYNC_EVAL", "DROPDOWN"),
        Map.entry("TENDENCY", "DROPDOWN")
    );

    /** 信息维度键 → 中文显示名。 */
    public static final Map<String, String> INFO_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("NAME", "姓名"),
        Map.entry("POSITION", "职位"),
        Map.entry("XIYU_CONTACT", "西域项目负责人"),
        Map.entry("CONTACT_METHOD", "触达方式"),
        Map.entry("EVALUATION_BASIS", "倾向性评估依据"),
        Map.entry("INFO_TENDENCY_BASIS", "倾向性评估依据"),
        Map.entry("INFO_CLEAR_WINNER_BID", "是否给出明确中标信息"),
        Map.entry("INFO_WIN_RATE_IMPACT", "对中标影响率"),
        Map.entry("CONTACTED", "是否触达"),
        Map.entry("GUIDED_BID", "是否向此人引导标书"),
        Map.entry("CAN_GET_KEY_INFO", "是否可获取关键信息"),
        Map.entry("CAN_REMOVE_ADVERSE", "是否可删除不利项"),
        Map.entry("CAN_SYNC_EVAL", "是否可同步评标信息"),
        Map.entry("TENDENCY", "对我司的倾向性")
    );

    /** 必填的信息维度键（触达方式必填）。 */
    public static final Set<String> REQUIRED_INFO_KEYS = Set.of(
        "CONTACT_METHOD"
    );

    private TenderEvaluationCustomerInfoPolicy() {
        // 工具类不可实例化
    }

    /**
     * 客户信息 EAV 行记录。
     */
    public record CustomerInfoRow(
        String roleKey,
        String infoKey,
        String value,
        String valueType
    ) {}

    /**
     * 校验客户信息段 EAV 行集合。
     * <p>检查每行的 role_key / info_key / value_type 合法性。
     *
     * @param rows 客户信息行集合；为 null 时返回 {@link #valid()}
     * @return 校验结果
     */
    public static ValidationResult validate(List<CustomerInfoRow> rows) {
        if (rows == null) {
            return ValidationResult.valid();
        }

        List<FieldError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            CustomerInfoRow row = rows.get(i);
            String prefix = "customerInfos[" + i + "]";

            // role_key 合法性
            if (row.roleKey == null || !VALID_ROLE_KEYS.contains(row.roleKey)) {
                errors.add(new FieldError(
                    prefix + ".roleKey",
                    CODE_INVALID_ROLE,
                    "角色键 '" + row.roleKey + "' 不在 14 个固定角色列表中"));
                continue;
            }

            // info_key 合法性
            if (row.infoKey == null || !VALID_INFO_KEYS.contains(row.infoKey)) {
                errors.add(new FieldError(
                    prefix + ".infoKey",
                    CODE_INVALID_INFO_KEY,
                    "信息维度键 '" + row.infoKey + "' 不在 14 个固定信息维度列表中"));
                continue;
            }

            // value_type 合法性
            String expectedType = INFO_KEY_VALUE_TYPES.get(row.infoKey);
            if (expectedType != null && row.valueType != null
                    && !expectedType.equals(row.valueType)) {
                errors.add(new FieldError(
                    prefix + ".valueType",
                    CODE_INVALID_VALUE_TYPE,
                    "信息维度 '" + INFO_DISPLAY_NAMES.getOrDefault(row.infoKey, row.infoKey)
                        + "' 的预期值类型为 " + expectedType + "，实际为 " + row.valueType));
            }

            // 必填字段
            if (REQUIRED_INFO_KEYS.contains(row.infoKey)
                    && (row.value == null || row.value.isBlank())) {
                errors.add(new FieldError(
                    prefix + ".value",
                    CODE_REQUIRED,
                    INFO_DISPLAY_NAMES.getOrDefault(row.infoKey, row.infoKey) + " 不能为空"));
            }
        }

        return new ValidationResult(errors);
    }

    /**
     * 校验 role_key 是否为合法的固定角色。
     */
    public static boolean isValidRoleKey(String roleKey) {
        return roleKey != null && VALID_ROLE_KEYS.contains(roleKey);
    }

    /**
     * 校验 info_key 是否为合法的固定信息维度。
     */
    public static boolean isValidInfoKey(String infoKey) {
        return infoKey != null && VALID_INFO_KEYS.contains(infoKey);
    }
}
