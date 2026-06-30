// Input: 立项客户信息表行列表（CustomerInfoRow）
// Output: 风险评估结果（风险等级 + 评估说明）
// Pos: project/core/ - 纯核心层（不依赖 Spring / JPA）
// 维护声明: 立项 AI 风险评估规则在此一次性固化；service / controller 层不允许重复实现。
// 规则来源：蓝图 §4 立项阶段「AI 风险评估」— 基于客户信息表倾向性判定
package com.xiyu.bid.project.core;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.tender.core.BidRiskLevelPolicy;
import com.xiyu.bid.tender.core.BidRiskLevelPolicy.RiskLevel;
import com.xiyu.bid.tender.core.BidRiskLevelPolicy.RiskLevelInput;

import java.util.ArrayList;
import java.util.List;

/**
 * 立项 AI 风险评估策略（纯函数 / FP-Java 核心）。
 *
 * <p>蓝图规则（与 {@link BidRiskLevelPolicy} 一致）：
 * <ul>
 *   <li>规则1：任何关键人（最高决策人或任一其他关键人）倾向性=反对 → HIGH</li>
 *   <li>规则2：最高决策人=支持 <b>且</b> 3 个其他关键人=支持 → LOW</li>
 *   <li>规则3：其他场景 → MEDIUM</li>
 * </ul>
 *
 * <p>角色识别（兼容两种存储形态）：
 * <ul>
 *   <li>最高决策人：{@code position == "1"}（数字代码）<b>或</b> {@code role} 含"最高决策人"</li>
 *   <li>其他关键决策人：{@code position} ∈ {@code ["9","10","11"]} <b>或</b> {@code role} 含"其他关键决策人"</li>
 * </ul>
 *
 * <p>倾向性值兼容：数字代码（1=支持/2=中立/3=反对）、中文（支持/中立/反对）、英文别名。
 *
 * <p>调用约定：
 * <ul>
 *   <li>{@code evaluate(null)} / 空列表 → MEDIUM（降级，不报错）</li>
 *   <li>合法输入 → 永不抛出异常</li>
 * </ul>
 */
public final class InitiationRiskAssessmentPolicy {

    /** 最高决策人的 position 数字代码（对应 POSITION_OPTIONS.value='1'）。 */
    private static final String HIGHEST_DECISION_MAKER_POSITION = "1";

    /** 其他关键决策人 1/2/3 的 position 数字代码（对应 POSITION_OPTIONS.value='9'/'10'/'11'）。 */
    private static final List<String> OTHER_KEY_DECISION_MAKER_POSITIONS = List.of("9", "10", "11");

    private InitiationRiskAssessmentPolicy() {
        // 工具类不可实例化
    }

    /**
     * 评估立项风险等级。
     *
     * @param rows 客户信息表行列表；为 null 或空时降级返回 MEDIUM
     * @return 风险评估结果（永不返回 null）
     */
    public static Result evaluate(List<CustomerInfoRow> rows) {
        RiskLevelInput input = extractInput(rows);
        RiskLevel level = BidRiskLevelPolicy.evaluate(input);
        String notes = buildNotes(level, input);
        return new Result(level, notes);
    }

    /**
     * 从客户信息表行列表提取风险判定输入。
     * 同时兼容 position 数字代码和 role 中文标签两种识别方式。
     */
    static RiskLevelInput extractInput(List<CustomerInfoRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new RiskLevelInput(null, List.of());
        }
        String highest = null;
        List<String> others = new ArrayList<>();
        for (CustomerInfoRow row : rows) {
            if (row == null) {
                continue;
            }
            if (isHighestDecisionMaker(row)) {
                highest = row.getPreference();
            } else if (isOtherKeyDecisionMaker(row)) {
                others.add(row.getPreference());
            }
        }
        return new RiskLevelInput(highest, others);
    }

    private static boolean isHighestDecisionMaker(CustomerInfoRow row) {
        if (HIGHEST_DECISION_MAKER_POSITION.equals(row.getPosition())) {
            return true;
        }
        return row.getRole() != null && row.getRole().contains("最高决策人");
    }

    private static boolean isOtherKeyDecisionMaker(CustomerInfoRow row) {
        if (row.getPosition() != null && OTHER_KEY_DECISION_MAKER_POSITIONS.contains(row.getPosition())) {
            return true;
        }
        return row.getRole() != null && row.getRole().contains("其他关键决策人");
    }

    /**
     * 根据风险等级和输入生成评估说明文字。
     * 说明文字会区分具体触发场景，便于业务人员理解。
     */
    private static String buildNotes(RiskLevel level, RiskLevelInput input) {
        return switch (level) {
            case HIGH -> buildHighNotes(input);
            case LOW -> "最高决策人及3位以上其他关键决策人支持，项目风险较低";
            case MEDIUM -> "关键决策人倾向性未达低风险标准，项目风险中等";
        };
    }

    /**
     * 高风险说明：区分是最高决策人反对还是其他关键人反对。
     */
    private static String buildHighNotes(RiskLevelInput input) {
        boolean highestOppose = TendencyUtil.isOppose(input.highestDecisionMakerTendency());
        boolean otherOppose = input.otherKeyDecisionMakerTendencies() != null
                && input.otherKeyDecisionMakerTendencies().stream().anyMatch(TendencyUtil::isOppose);
        if (highestOppose && otherOppose) {
            return "最高决策人及其他关键决策人均存在反对，项目风险较高";
        }
        if (highestOppose) {
            return "最高决策人反对，项目风险较高";
        }
        if (otherOppose) {
            return "存在其他关键决策人反对，项目风险较高";
        }
        return "存在关键决策人反对，项目风险较高";
    }

    /** 倾向性判断辅助（复用 BidRiskLevelPolicy 的归一化逻辑）。 */
    private static final class TendencyUtil {
        private TendencyUtil() {}

        static boolean isOppose(String raw) {
            return BidRiskLevelPolicy.evaluate(
                    new RiskLevelInput(raw, List.of())) == RiskLevel.HIGH;
        }
    }

    /**
     * 风险评估结果。
     *
     * @param riskLevel 风险等级（HIGH/MEDIUM/LOW）
     * @param notes 评估说明文字（用于前端展示和 aiRiskAssessmentNotes 持久化）
     */
    public record Result(RiskLevel riskLevel, String notes) {}
}
