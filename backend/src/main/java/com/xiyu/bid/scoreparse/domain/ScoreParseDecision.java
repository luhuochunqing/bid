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
 *   <li>{@link #MANUAL_APPROVED}：人工复核确认采纳；覆盖当前生效 score_item。</li>
 *   <li>{@link #REVIEW_REQUIRED}：解析产出事实与证据，但触发复核信号（权重缺口/大类声明不符等）；
 *       只保留 facts/evidence 与任务复核标记，不覆盖当前生效 score_item，等待人工复核审计闭环。</li>
 *   <li>{@link #STRUCTURE_INCOMPLETE}：原文/表格结构损坏，结构化提取失败；保留现有 score_item 不覆盖。</li>
 *   <li>{@link #PARSE_FAILED}：链路异常终止；0 项或后处理失败。</li>
 * </ul>
 *
 * <p>阶段 0 引入目的是为后续 Evidence Pipeline 提供可审计的字段；旧任务字段为 NULL
 * 表示尚未决策（兼容历史）。
 */
public enum ScoreParseDecision {
    AUTO_APPROVED,
    MANUAL_APPROVED,
    REVIEW_REQUIRED,
    STRUCTURE_INCOMPLETE,
    PARSE_FAILED
}
