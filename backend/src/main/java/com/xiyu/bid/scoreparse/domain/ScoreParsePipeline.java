// Input: 无（pipeline 版本号常量，单一真相源）
// Output: 解析 pipeline 标识常量，给任务字段、run_summary 共享
// Pos: scoreparse/domain — 纯常量类，无框架依赖
// 维护声明: 维护者按项目SOP；新增版本号必须经 Spec Kit 评审，禁止调用方硬编码相同字符串

package com.xiyu.bid.scoreparse.domain;

/**
 * 评分解析 pipeline 版本常量（spec 059 Phase 0）。
 *
 * <p>写入 {@code score_parse_task.pipeline_version} 与 run_summary 的 {@code pipelineVersion}；
 * 旧版本字符串保留以兼容历史任务，禁止删改。
 */
public final class ScoreParsePipeline {

    /** Phase 0 默认：正则 + 结构化抽取 + LLM + 全局归并 + 归一化（历史 P1-P5 pipeline）。 */
    public static final String LEGACY_FALLBACK = "LEGACY_FALLBACK";

    /** Phase 1+ Evidence Pipeline 版本（占位；实际启用在 Phase 5 双写阶段）。 */
    public static final String EVIDENCE_PIPELINE_V1 = "EVIDENCE_PIPELINE_V1";

    private ScoreParsePipeline() {
    }
}
