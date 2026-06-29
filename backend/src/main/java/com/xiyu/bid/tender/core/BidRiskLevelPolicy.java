// Input: 最高决策人倾向性 + 其他关键人倾向性列表（抽象输入，不依赖实体/DTO）
// Output: 风险等级枚举 HIGH / MEDIUM / LOW
// Pos: 纯核心层（core）- 不依赖 Spring / JPA / 任何外部框架
// 维护声明: AI 风险等级判定规则在此一次性固化；service / controller 层不允许重复实现。
package com.xiyu.bid.tender.core;

import java.util.List;
import java.util.Locale;

/**
 * 投标风险等级判定策略（纯函数 / FP-Java 核心）。
 *
 * <p>蓝图规则：
 * <ul>
 *   <li>规则1：任何关键人（最高决策人或任一其他关键人）TENDENCY=反对 → HIGH</li>
 *   <li>规则2：最高决策人=支持 <b>且</b> 3 个其他关键人=支持 → LOW</li>
 *   <li>规则3：其他场景 → MEDIUM</li>
 * </ul>
 *
 * <p>倾向性值兼容：
 * <ul>
 *   <li>中文：支持 / 中立 / 反对</li>
 *   <li>数字代码（前端真实入库值）：1=支持 / 2=中立 / 3=反对</li>
 *   <li>英文别名：support / neutral / oppose（大小写不敏感）</li>
 *   <li>空白会被 trim，未识别值视为 null（不参与反对/支持判定）</li>
 * </ul>
 *
 * <p>调用约定：
 * <ul>
 *   <li>{@code evaluate(null)} → MEDIUM（AI 失败降级，不报错）</li>
 *   <li>合法输入 → 永不抛出异常，返回 HIGH/MEDIUM/LOW</li>
 * </ul>
 */
public final class BidRiskLevelPolicy {

    private BidRiskLevelPolicy() {
        // 工具类不可实例化
    }

    /** 风险等级枚举（自带中文展示名，避免 controller 层重复 switch）。 */
    public enum RiskLevel {
        HIGH("高"), MEDIUM("中"), LOW("低");

        private final String display;

        RiskLevel(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    /**
     * 风险判定的抽象输入。
     *
     * @param highestDecisionMakerTendency 最高决策人对西陆的倾向性原始值
     * @param otherKeyDecisionMakerTendencies 其他关键决策人倾向性原始值列表（顺序无关）
     */
    public record RiskLevelInput(
        String highestDecisionMakerTendency,
        List<String> otherKeyDecisionMakerTendencies
    ) {}

    /**
     * 判定风险等级。
     *
     * @param input 风险判定输入；为 null 时降级返回 MEDIUM
     * @return 风险等级（永不返回 null）
     */
    public static RiskLevel evaluate(RiskLevelInput input) {
        if (input == null) {
            return RiskLevel.MEDIUM;
        }

        Tendency highest = Tendency.parse(input.highestDecisionMakerTendency());

        // 规则1：任何关键人反对 → HIGH
        if (highest == Tendency.OPPOSE) {
            return RiskLevel.HIGH;
        }
        List<String> others = input.otherKeyDecisionMakerTendencies();
        if (others != null) {
            for (String raw : others) {
                if (Tendency.parse(raw) == Tendency.OPPOSE) {
                    return RiskLevel.HIGH;
                }
            }
        }

        // 规则2：最高决策人支持 + 3 个其他关键人支持 → LOW
        if (highest == Tendency.SUPPORT) {
            int supportedCount = 0;
            if (others != null) {
                for (String raw : others) {
                    if (Tendency.parse(raw) == Tendency.SUPPORT) {
                        supportedCount++;
                    }
                }
            }
            if (supportedCount >= 3) {
                return RiskLevel.LOW;
            }
        }

        // 规则3：其他 → MEDIUM
        return RiskLevel.MEDIUM;
    }

    /** 倾向性归一化内部枚举。 */
    private enum Tendency {
        SUPPORT, NEUTRAL, OPPOSE;

        /**
         * 将原始值归一化为倾向性枚举，未识别返回 null。
         */
        static Tendency parse(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim();
            if (s.isEmpty()) {
                return null;
            }
            String lower = s.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "支持", "1", "support", "supported" -> SUPPORT;
                case "中立", "2", "neutral" -> NEUTRAL;
                case "反对", "3", "oppose", "opposed", "against" -> OPPOSE;
                default -> null;
            };
        }
    }
}
