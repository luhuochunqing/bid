// Input: BidResultType + RetrospectiveInput (字段集合)
// Output: Decision (Allow | Deny{missing}) -- PRD §3.3.1.5 必填字段矩阵
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PRD §3.3.1.5 复盘必填字段策略。
 *
 * <p>必填映射：
 * <ul>
 *   <li>WON: meetingTime, meetingFormat, meetingParticipants, winFactors,
 *            processHighlights, postWinImprovements, reportFileIds</li>
 *   <li>LOST: meetingTime, meetingFormat, meetingParticipants, lossReasonFlags,
 *             processProblems, postLossMeasures, reportFileIds</li>
 * </ul>
 *
 * <p>会议信息为中标/未中标共有必填；复盘报告必填。
 * 流标/弃标不进入复盘流程，不在本策略覆盖范围内。
 */
public final class RetrospectiveFieldPolicy {

    private RetrospectiveFieldPolicy() {
    }

    public static Decision validate(BidResultType resultType, RetrospectiveInput input) {
        if (resultType == null) {
            return new Decision.Deny(List.of("resultType"));
        }
        Objects.requireNonNull(input, "input 不能为空");
        List<String> missing = new ArrayList<>();

        // 会议信息：中标/未中标均必填
        requireField("meetingTime", input.meetingTime(), missing);
        requireField("meetingFormat", input.meetingFormat(), missing);
        requireField("meetingParticipants", input.meetingParticipants(), missing);

        switch (resultType) {
            case WON -> {
                requireField("winFactors", input.winFactors(), missing);
                requireField("processHighlights", input.processHighlights(), missing);
                requireField("postWinImprovements", input.postWinImprovements(), missing);
                requireField("reportFileIds", input.reportFileIds(), missing);
            }
            case LOST -> {
                requireField("lossReasonFlags", input.lossReasonFlags(), missing);
                requireField("processProblems", input.processProblems(), missing);
                requireField("postLossMeasures", input.postLossMeasures(), missing);
                requireField("reportFileIds", input.reportFileIds(), missing);
            }
            // FAILED / ABANDONED 不进入复盘
            default -> {
                return new Decision.Deny(List.of("resultType: 流标/弃标无需复盘"));
            }
        }
        return missing.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(missing));
    }

    private static void requireField(String name, String value, List<String> missing) {
        if (value == null || value.trim().isEmpty()) {
            missing.add(name);
        }
    }

    /**
     * 复盘字段输入。所有字段可为 null，由策略按 resultType 决定必填。
     *
     * @param summary              复盘总结/摘要（补充字段，非蓝图必填）
     * @param winFactors           中标优势
     * @param lossReasons          旧丢标原因文本（向后兼容，新数据写入 lossReasonFlags）
     * @param competitorNotes      旧问题分析（向后兼容，新数据写入 processProblems）
     * @param improvementActions   旧改进措施（向后兼容，新数据写入 postWinImprovements/postLossMeasures）
     * @param meetingTime          复盘会时间（必填）
     * @param meetingFormat        会议形式—ONLINE/OFFLINE（必填）
     * @param meetingParticipants  会议参与人（必填）
     * @param lossReasonFlags      丢标原因多选标记—逗号分隔枚举值（未中标必填）
     * @param processHighlights    流程亮点（中标必填）
     * @param postWinImprovements  中标后续改进建议（中标必填）
     * @param processProblems      流程存在问题（未中标必填）
     * @param postLossMeasures     具体改进措施（未中标必填）
     * @param reportFileIds        复盘报告附件ID（WON/LOST 均必填）
     */
    public record RetrospectiveInput(
            String summary,
            String winFactors,
            String lossReasons,
            String competitorNotes,
            String improvementActions,
            String meetingTime,
            String meetingFormat,
            String meetingParticipants,
            String lossReasonFlags,
            String processHighlights,
            String postWinImprovements,
            String processProblems,
            String postLossMeasures,
            String reportFileIds) {
    }

    /** Sealed Decision: Allow | Deny{missing}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(List<String> missing) implements Decision {
            public String reason() {
                return "缺少必填字段：" + String.join(",", missing);
            }
        }
    }
}
