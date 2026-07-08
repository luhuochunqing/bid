package com.xiyu.bid.notification.core;

/**
 * 文档变更通知 targetUrl 解析策略 — 按 documentCategory 分流到对应项目阶段页。
 *
 * <p>纯核心：无状态、无依赖、无副作用。所有方法静态、参数显式传入。
 * 不依赖 Spring、Repository 或任何 IO；可在单元测试中直接验证。
 *
 * <p>设计参考 {@link TaskNotificationTargetUrlResolver}（任务通知按角色分流）。
 * 本类按文档分类分流，对齐项目生命周期的 6 个阶段子路径：
 * <ul>
 *   <li>{@code TENDER}（招标文件）→ {@code /project/{id}/initiation}</li>
 *   <li>{@code BID}（投标文件）→ {@code /project/{id}/drafting}</li>
 *   <li>{@code OPEN_LIST}（开标一览表）→ {@code /project/{id}/evaluation}</li>
 *   <li>{@code WIN_NOTICE}（中标通知书）→ {@code /project/{id}/result}</li>
 *   <li>{@code DEPOSIT_RECEIPT}（保证金回单）→ {@code /project/{id}/closure}</li>
 *   <li>{@code BID_RESULT_NOTICE}/{@code BID_RESULT_ANALYSIS} → {@code /project/{id}/result}</li>
 *   <li>其他/null/未知 → {@code /project/{id}/drafting}（兜底，最常访问页）</li>
 * </ul>
 *
 * <p>注意：前端项目详情页路由为 {@code /project/:id/:stage}（单一路由 + stage 段），
 * 权威 stage 子路径仅 initiation/drafting/evaluation/result/retrospective/closure 六个，
 * 详见前端 {@code PROJECT_STAGES} 常量。本类映射只使用这 6 个子路径。
 */
public final class DocumentChangeTargetUrlResolver {

    private DocumentChangeTargetUrlResolver() {
    }

    /**
     * 按 documentCategory 解析文档变更通知的 targetUrl。
     *
     * @param projectId       项目 ID（必填，用于构造 URL 路径）
     * @param documentCategory 文档分类（已归一化值，来自 {@code DocumentCategoryNormalizer.normalize}；
     *                         可为 null/空，兜底返回 drafting 页）
     * @return 通知 targetUrl 字符串，格式 {@code /project/{projectId}/{stage}}
     */
    public static String resolveTargetUrl(final Long projectId, final String documentCategory) {
        String stage = resolveStage(documentCategory);
        return "/project/" + projectId + "/" + stage;
    }

    private static String resolveStage(String category) {
        if (category == null || category.isBlank()) {
            return "drafting";
        }
        return switch (category.trim()) {
            case "TENDER" -> "initiation";
            case "BID" -> "drafting";
            case "OPEN_LIST" -> "evaluation";
            case "WIN_NOTICE", "BID_RESULT_NOTICE", "BID_RESULT_ANALYSIS" -> "result";
            case "DEPOSIT_RECEIPT" -> "closure";
            default -> "drafting"; // 兜底：OTHER/RETROSPECTIVE_REPORT(已归一化为 OTHER)/未知分类
        };
    }
}
