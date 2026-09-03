// Input: 无（枚举类型，单一真相源）
// Output: 评分解析决策枚举，给任务级字段、run_summary 与人工复核一致使用
// Pos: scoreparse/domain — 纯枚举，无框架依赖
// 维护声明: 维护者按项目SOP；值扩展必须经 Spec Kit 评审，禁止在调用方隐式新增

package com.xiyu.bid.scoreparse.domain;

/**
 * 评分解析任务级决策（spec 059 Phase 0）。
 *
 * <ul>
 *   <li>{@link #AUTO_APPROVED}：自动通过；覆盖当前生效 score_item。</li>
 *   <li>{@link #REVIEW_REQUIRED}：解析产出有效项但触发复核信号（权重缺口/大类声明不符等）；
 *       仍落库覆盖 score_item 并在任务上保留复核标记供前端提示，<b>不得静默丢弃整批结果</b>
 *       （历史 all-or-nothing 不落库曾导致成功解析被丢弃，详见 lessons-learned §217）。</li>
 *   <li>{@link #STRUCTURE_INCOMPLETE}：原文/表格结构损坏，结构化提取失败；保留现有 score_item 不覆盖。</li>
 *   <li>{@link #PARSE_FAILED}：链路异常终止；0 项或后处理失败。</li>
 * </ul>
 *
 * <p>阶段 0 引入目的是为后续 Evidence Pipeline 提供可审计的字段；旧任务字段为 NULL
 * 表示尚未决策（兼容历史）。
 */
public enum ScoreParseDecision {
    AUTO_APPROVED,
    REVIEW_REQUIRED,
    STRUCTURE_INCOMPLETE,
    PARSE_FAILED
}
