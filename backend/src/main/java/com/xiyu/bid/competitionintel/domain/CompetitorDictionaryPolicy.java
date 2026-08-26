// Input: 竞品名原文 / 合并源与目标 ID
// Output: 归一化竞品名 / 合并合法性 Decision
// Pos: competitionintel/domain/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.competitionintel.domain;

/**
 * 竞品字典纯核心策略（specs/055）。
 * <p>名称归一化与合并合法性判定为纯函数，可独立单测；依赖 DB 的判定（target 存在性、
 * 停用态）下沉到 service 层。</p>
 */
public final class CompetitorDictionaryPolicy {

    private CompetitorDictionaryPolicy() {
    }

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/admin/settings/core/DepartmentGraphPolicy.java — 同名 trim 归一化仅 3 行纯函数，跨模块复用将引入 competitionintel→admin.settings 错误依赖方向
    /**
     * 归一化竞品名：去除首尾空白。
     *
     * @return 归一化后的名称；null 或空白字符串返回 null
     */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 合并合法性判定：源与目标均非空且不为同一竞品。
     * <p>目标是否存在、是否停用由 service 层查库后判定。</p>
     */
    public static Decision decideMerge(Long sourceId, Long targetId) {
        if (sourceId == null || targetId == null) {
            return new Decision.Deny("sourceId 与 targetId 均不能为空");
        }
        if (sourceId.equals(targetId)) {
            return new Decision.Deny("不能合并到自身");
        }
        return Decision.ALLOW;
    }

    /** Sealed Decision: Allow | Deny{reason}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {

        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        String reason();

        record Allow() implements Decision {
            @Override
            public String reason() {
                return "";
            }
        }

        record Deny(String reason) implements Decision {
        }
    }
}
