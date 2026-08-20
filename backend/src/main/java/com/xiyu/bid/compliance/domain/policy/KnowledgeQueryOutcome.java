// Input: 四知识库查询产出的匹配记录列表 + 查询失败的库来源集合
// Output: 不可变查询结果载体（供引擎按失败源对依赖项 MANUAL 降级）
// Pos: compliance/domain/policy/ - 四库查询结果聚合（spec 049 US3 / T037，PRD §5.1 单库失败不阻断）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.List;
import java.util.Set;

/**
 * 四知识库查询结果聚合（spec 049 第三步）.
 *
 * <p>matches 为全部真实匹配记录；failedSources 为查询超时/异常的知识库来源集合，
 * 引擎对依赖这些库的检查项置 MANUAL + [EVIDENCE_INSUFFICIENT]，不阻断其他项
 * （PRD §5.1，对齐 Constitution VII 降级精神）.
 *
 * @param matches 四库真实匹配记录
 * @param failedSources 查询失败的知识库来源标识集合
 */
public record KnowledgeQueryOutcome(
        List<KnowledgeMatch> matches,
        Set<String> failedSources) {

    public KnowledgeQueryOutcome {
        matches = matches == null ? List.of() : List.copyOf(matches);
        failedSources = failedSources == null ? Set.of() : Set.copyOf(failedSources);
    }

    /**
     * 空结果（无任何匹配、无失败源）.
     *
     * @return 空查询结果
     */
    public static KnowledgeQueryOutcome empty() {
        return new KnowledgeQueryOutcome(List.of(), Set.of());
    }
}
