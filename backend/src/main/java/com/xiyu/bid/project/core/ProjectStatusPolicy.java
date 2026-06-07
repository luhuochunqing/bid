// Input: stage + bidResultStatus + initiationSubmitted
// Output: Project.Status — computed 8-value project status
// Pos: project/core/ — pure rule, no Spring/JPA
package com.xiyu.bid.project.core;

import com.xiyu.bid.entity.Project;

import java.util.Objects;

/**
 * 投标项目状态计算策略。产品蓝图 V1.1 §4.3。
 *
 * <p>根据项目当前阶段 ({@link ProjectStage})、投标结果、立项提交状态
 * 推导出 8 值 {@link Project.Status}。
 *
 * <p>规则：
 * <ul>
 *   <li>CLOSED + result → WON / LOST / FAILED / ABANDONED</li>
 *   <li>INITIATED + 未提交立项 → PENDING_INITIATION</li>
 *   <li>INITIATED + 已提交立项 → INITIATED</li>
 *   <li>DRAFTING → BIDDING</li>
 *   <li>EVALUATING / RESULT_PENDING → EVALUATING</li>
 *   <li>RETROSPECTIVE → 根据 result 决定 WON/LOST（如有），否则 BIDDING</li>
 * </ul>
 */
public final class ProjectStatusPolicy {

    private ProjectStatusPolicy() {}

    /**
     * 计算项目状态。
     *
     * @param stage              项目阶段（非空）
     * @param bidResult          投标结果（WON/LOST/FAILED/ABANDONED，可为 null）
     * @param initiationSubmitted 立项是否已提交
     * @return 计算后的 Project.Status
     */
    public static Project.Status compute(ProjectStage stage, String bidResult, boolean initiationSubmitted) {
        Objects.requireNonNull(stage, "stage 不能为空");

        // CLOSED stage — terminal states based on bid result
        if (stage == ProjectStage.CLOSED) {
            if (bidResult != null) {
                return switch (bidResult.toUpperCase()) {
                    case "WON" -> Project.Status.WON;
                    case "LOST" -> Project.Status.LOST;
                    case "FAILED" -> Project.Status.FAILED;
                    case "ABANDONED" -> Project.Status.ABANDONED;
                    default -> Project.Status.INITIATED;
                };
            }
            return Project.Status.INITIATED;
        }

        // RETROSPECTIVE — has result but not yet closed
        if (stage == ProjectStage.RETROSPECTIVE) {
            if (bidResult != null) {
                return switch (bidResult.toUpperCase()) {
                    case "WON" -> Project.Status.WON;
                    case "LOST" -> Project.Status.LOST;
                    case "FAILED" -> Project.Status.FAILED;
                    case "ABANDONED" -> Project.Status.ABANDONED;
                    default -> Project.Status.BIDDING;
                };
            }
            return Project.Status.BIDDING;
        }

        // Stage-based mapping
        return switch (stage) {
            case INITIATED -> initiationSubmitted ? Project.Status.INITIATED : Project.Status.PENDING_INITIATION;
            case DRAFTING -> Project.Status.BIDDING;
            case EVALUATING, RESULT_PENDING -> Project.Status.EVALUATING;
            default -> Project.Status.INITIATED;
        };
    }
}
