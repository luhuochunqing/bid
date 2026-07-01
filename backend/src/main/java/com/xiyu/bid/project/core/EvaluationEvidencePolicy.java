// Input: 是否已上传评标文件
// Output: Decision(allowed/reason) - 评标推进结果确认阶段的纯核心闸门
// Pos: project/core/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

/**
 * CO-461 评标文件必填校验闸门：推进到结果确认阶段前，校验评标文件是否已上传。
 *
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按返回结果映射 HTTP 状态码（拒绝→422）。</p>
 *
 * <p>供 {@code ProjectEvaluationService.advanceToResultPending}（推进到结果确认阶段）使用。
 * 业务语义：评标文件是评标阶段的核心交付物，推进阶段前必须上传。</p>
 */
public final class EvaluationEvidencePolicy {

    private EvaluationEvidencePolicy() {
    }

    /**
     * 校验评标文件是否已上传。
     *
     * @param hasEvidence 是否存在评标文件（linkedEntityType=EVALUATION）
     * @return 允许或拒绝决定 + 拒绝原因
     */
    public static Decision checkEvidenceUploaded(boolean hasEvidence) {
        if (!hasEvidence) {
            return Decision.deny("请上传评标文件");
        }
        return Decision.permit();
    }

    /**
     * 闸门决策结果。
     */
    public record Decision(boolean allowed, String reason) {

        public static Decision permit() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
