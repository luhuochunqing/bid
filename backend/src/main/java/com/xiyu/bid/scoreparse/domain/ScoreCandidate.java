package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * 评分项候选池记录（spec 041 FR-001 四路召回的中间产物）。
 * <p>召回一（关键词/规则）、召回二（文档结构）、召回三（评分规则语义）、召回四（LLM 全文语义）
 * 各自产出候选，经 {@link ScoreItemMergePolicy} 合并去重后进入结构化提取。
 * <p>纯核心 record：无框架依赖，可独立单测（Constitution FP-Java）。
 *
 * @param code           评分项编号（原文提取，如 A1/B2；缺失时为空串）
 * @param dim            评分项名称
 * @param detail         详细要素（完整原文，禁止摘要）
 * @param weight         权重绝对分值（缺失时为 null，由结构化提取补齐）
 * @param scoreTypeGuess 客观/主观初判（OBJECTIVE/SUBJECTIVE；最终以分类策略为准）
 * @param contextNote    评分规则上下文（注/说明/备注）
 * @param sourceText     原文依据
 * @param location       页码/位置
 * @param semanticPattern 语义命中模式说明（召回三/四的命中理由，供审计追溯）
 */
public record ScoreCandidate(
        String code,
        String dim,
        String detail,
        BigDecimal weight,
        String scoreTypeGuess,
        String contextNote,
        String sourceText,
        String location,
        String semanticPattern
) {
}
