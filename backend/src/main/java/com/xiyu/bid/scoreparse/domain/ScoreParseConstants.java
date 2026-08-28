// Input: 无（模块级常量单一出处）
// Output: scoreparse 全模块共享的数值常量（权重阈值/裁决语料上限/遥测告警阈）
// Pos: scoreparse/domain — 纯常量类，无框架依赖（xcode-review B-3：消 MAX_SINGLE_WEIGHT 双处定义）
// 维护声明: 维护者按项目SOP；修改任一常量时确认 ScoreMergeResultGuardTest / RunSummaryAssemblerTest /
//   GoldenCasesReplayTest 的边界断言语义仍成立（三处测试锚定同一组值）
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * scoreparse 模块共享常量（spec 046/047/055/057 数值口径唯一出处）。
 */
public final class ScoreParseConstants {

    /** 单项权重有效上界：>100 视为误提金额/年份等噪声（PostProcessor 权重过滤 + Guard 高置信判定共用） */
    public static final BigDecimal MAX_SINGLE_WEIGHT = new BigDecimal("100");

    /** 权重超载判定上限（spec 046 FR-002；WeightSumCheck 容差 +0.5） */
    public static final BigDecimal OVERLOAD_LIMIT = new BigDecimal("100.5");

    /** 裁决上下文（评分章节拼接）字符上界（spec 057 FR-03，扩容支持复杂多标包章节完整性） */
    public static final int SECTION_TEXT_LIMIT = 24000;

    /** 对齐率地板：低于此值说明模型改写/概括严重（对齐器未命中）（spec 057 FR-06） */
    public static final double ALIGNED_RATIO_FLOOR = 0.5;

    /** 幻觉丢弃率天花板：高于此值说明归并裁决质量劣化（spec 057 FR-06） */
    public static final double HALLUCINATION_RATIO_CEIL = 0.3;

    private ScoreParseConstants() {
    }
}
