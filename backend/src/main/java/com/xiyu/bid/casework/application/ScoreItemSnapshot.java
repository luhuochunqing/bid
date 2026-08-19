package com.xiyu.bid.casework.application;

/**
 * 案例沉淀评分项快照（源自 score_item，经监听器映射引入）。
 *
 * <p>用轻量值对象承接 {@code score_item} 所需字段，避免 casework 直接依赖 scoreparse 实体，
 * 保持跨模块边界清晰。字段语义与沉淀下游一一对应：
 * <ul>
 *   <li>{@code id}       评分项主键，用于 AI 切片配对回填</li>
 *   <li>{@code title}    评分项名称（score_item.dim），对应案例的评分点标题</li>
 *   <li>{@code ruleText} 评分规则原文（score_item.detail），对应案例的要求原文</li>
 *   <li>{@code category} 评分类别（score_item.dim 中文关键词），经 {@link CaseAiMatcher#extractCategory} 归类</li>
 * </ul>
 *
 * @param id       评分项主键
 * @param title    评分项名称
 * @param ruleText 评分规则原文
 * @param category 评分类别原始文本
 */
public record ScoreItemSnapshot(Long id, String title, String ruleText, String category) {
}