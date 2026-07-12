// Input: DepositSnapshot + ClosureInput + task states
// Output: Decision (Allow | Deny{reasons}) -- PRD §3.6 结项闸门策略（保证金 + 任务完成）
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PRD §3.6 / 蓝图 §3.3.1.6 项目结项闸门策略（纯规则，无 Spring/JPA）。
 *
 * <p>核心规则：
 * <ul>
 *   <li>当项目存在保证金（hasDeposit=true）且状态为 NOT_RETURNED，结项被拒绝。</li>
 *   <li>FULLY_RETURNED 需退回日期 + 退回凭证。</li>
 *   <li>TRANSFERRED_TO_FEE 需转服务费金额 + 证明文件。</li>
 *   <li>PARTIAL_RETURN_PARTIAL_TRANSFER 需退回金额 + 转服务费金额 + 证明文件。</li>
 *   <li>CO-573：TRANSFERRED_TO_FEE 时转服务费金额必须等于保证金金额；PARTIAL_RETURN_PARTIAL_TRANSFER 时退回金额+转服务费金额必须等于保证金金额（depositAmount 非 null 时生效）。</li>
 *   <li>当无保证金（hasDeposit=false）时，NA 状态跳过所有校验。</li>
 *   <li>所有未完成的任务（非 COMPLETED 状态）阻止结项。</li>
 *   <li>所有错误以 reasons 列表返回，便于前端整体提示。</li>
 * </ul>
 */
public final class ProjectClosureGatePolicy {

    private ProjectClosureGatePolicy() {
    }

    /**
     * @deprecated 请使用 {@link #decide(ClosureGateInputs)}，支持任务完成校验。
     *             保留用于向后兼容（不含任务校验）。
     */
    @Deprecated
    public static Decision decide(DepositSnapshot snapshot, ClosureInput input) {
        return decideDepositOnly(snapshot, input, null);
    }

    public static Decision decide(ClosureGateInputs inputs) {
        Objects.requireNonNull(inputs, "inputs 不能为空");
        List<String> reasons = new ArrayList<>();

        Decision depositResult = decideDepositOnly(inputs.depositSnapshot(), inputs.closureInput(), inputs.depositAmount());
        if (!depositResult.allowed()) {
            reasons.addAll(((Decision.Deny) depositResult).reasons());
        }

        Decision taskResult = decideTaskCompletion(inputs.taskStates());
        if (!taskResult.allowed()) {
            reasons.addAll(((Decision.Deny) taskResult).reasons());
        }

        return reasons.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(reasons));
    }

    private static Decision decideDepositOnly(DepositSnapshot snapshot, ClosureInput input, BigDecimal depositAmount) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        Objects.requireNonNull(input, "input 不能为空");
        List<String> reasons = new ArrayList<>();

        if (snapshot.hasDeposit()) {
            if (snapshot.returnStatus() == DepositReturnStatus.NA) {
                reasons.add("保证金状态异常：存在保证金但状态为 NA");
            } else if (snapshot.returnStatus() == DepositReturnStatus.NOT_RETURNED) {
                reasons.add("保证金未退回");
            } else if (snapshot.returnStatus() == DepositReturnStatus.FULLY_RETURNED) {
                if (snapshot.returnDate() == null) {
                    reasons.add("缺少保证金退回日期");
                }
                if (snapshot.evidenceDocId() == null || snapshot.evidenceDocId() <= 0L) {
                    reasons.add("缺少保证金退回凭证");
                }
            } else if (snapshot.returnStatus() == DepositReturnStatus.TRANSFERRED_TO_FEE) {
                if (snapshot.transferAmount() == null || snapshot.transferAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    reasons.add("缺少转平台服务费金额");
                }
                if (snapshot.evidenceDocId() == null || snapshot.evidenceDocId() <= 0L) {
                    reasons.add("缺少转服务费证明文件");
                }
                // CO-573: 转服务费金额必须等于保证金金额（depositAmount 非 null 时校验）
                if (depositAmount != null
                        && snapshot.transferAmount() != null
                        && snapshot.transferAmount().compareTo(BigDecimal.ZERO) > 0
                        && snapshot.transferAmount().compareTo(depositAmount) != 0) {
                    reasons.add("转服务费金额必须等于保证金金额");
                }
            } else if (snapshot.returnStatus() == DepositReturnStatus.PARTIAL_RETURN_PARTIAL_TRANSFER) {
                if (snapshot.returnedAmount() == null || snapshot.returnedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    reasons.add("缺少退回金额");
                }
                if (snapshot.transferAmount() == null || snapshot.transferAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    reasons.add("缺少转平台服务费金额");
                }
                if (snapshot.evidenceDocId() == null || snapshot.evidenceDocId() <= 0L) {
                    reasons.add("缺少证明文件");
                }
                // CO-573: 退回金额 + 转服务费金额必须等于保证金金额（depositAmount 非 null 时校验）
                if (depositAmount != null
                        && snapshot.returnedAmount() != null && snapshot.returnedAmount().compareTo(BigDecimal.ZERO) > 0
                        && snapshot.transferAmount() != null && snapshot.transferAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal sum = snapshot.returnedAmount().add(snapshot.transferAmount());
                    if (sum.compareTo(depositAmount) != 0) {
                        reasons.add("退回金额与转服务费金额之和必须等于保证金金额");
                    }
                }
            }
        }

        return reasons.isEmpty()
                ? Decision.ALLOW
                : new Decision.Deny(Collections.unmodifiableList(reasons));
    }

    private static Decision decideTaskCompletion(List<AllTasksCompletedPolicy.TaskState> taskStates) {
        AllTasksCompletedPolicy.Decision decision = AllTasksCompletedPolicy.decide(taskStates);
        if (decision.allowed()) {
            return Decision.ALLOW;
        }
        int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) decision).incompleteCount();
        String reason = incomplete < 0
                ? "存在未完成的任务"
                : "存在 " + incomplete + " 项未完成的任务";
        return new Decision.Deny(Collections.singletonList(reason));
    }

    /** 保证金退回状态（蓝图 §3.3.1.6）。 */
    public enum DepositReturnStatus {
        NOT_RETURNED,
        FULLY_RETURNED,
        TRANSFERRED_TO_FEE,
        PARTIAL_RETURN_PARTIAL_TRANSFER,
        NA
    }

    /**
     * 保证金快照（只读视图）。
     * @param hasDeposit     项目是否存在保证金
     * @param returnStatus   退回状态
     * @param returnDate     退回时间（FULLY_RETURNED 时必填）
     * @param evidenceDocId  退回凭证文档 ID（FULLY_RETURNED/TRANSFERRED_TO_FEE/PARTIAL_RETURN 时必填）
     * @param transferAmount 转平台服务费金额（TRANSFERRED_TO_FEE/PARTIAL_RETURN 时必填）
     * @param returnedAmount 实际退回金额（PARTIAL_RETURN_PARTIAL_TRANSFER 时必填）
     */
    public record DepositSnapshot(
            boolean hasDeposit,
            DepositReturnStatus returnStatus,
            LocalDateTime returnDate,
            Long evidenceDocId,
            BigDecimal transferAmount,
            BigDecimal returnedAmount) {

        public static DepositSnapshot none() {
            return new DepositSnapshot(false, DepositReturnStatus.NA, null, null, null, null);
        }

        public static DepositSnapshot notReturned() {
            return new DepositSnapshot(true, DepositReturnStatus.NOT_RETURNED, null, null, null, null);
        }

        public static DepositSnapshot returned(LocalDateTime when, Long docId) {
            return new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, when, docId, null, null);
        }
    }

    /** 结项提交输入（占位：未来扩展归档/备注校验）。 */
    public record ClosureInput(String archiveLocation, String notes) {
        public static final ClosureInput EMPTY = new ClosureInput(null, null);
    }

    /**
     * 结项闸门完整输入。
     * @param depositSnapshot  保证金快照
     * @param closureInput     结项提交输入
     * @param taskStates       任务状态列表（来自 AllTasksCompletedPolicy.TaskState）
     * @param depositAmount    保证金金额（CO-573：用于校验退回/转服务费金额等值，null 时跳过等值校验）
     */
    public record ClosureGateInputs(
            DepositSnapshot depositSnapshot,
            ClosureInput closureInput,
            List<AllTasksCompletedPolicy.TaskState> taskStates,
            BigDecimal depositAmount) {
    }

    /** Sealed Decision: Allow | Deny{reasons}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(List<String> reasons) implements Decision {
            public String reasonText() {
                return String.join("；", reasons);
            }
        }
    }
}
