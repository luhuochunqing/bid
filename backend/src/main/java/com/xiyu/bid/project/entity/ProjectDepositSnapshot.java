// Input: 保证金读模型（由 Fee 表派生）
// Output: 项目结项预览所需的保证金快照（不可变 record）
// Pos: project/entity/ - 派生读模型，非 JPA 实体
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.entity;

import com.xiyu.bid.project.core.ProjectClosureGatePolicy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目保证金快照（派生自 fees 表 BID_BOND 记录）。
 * <p>非 JPA 实体；仅用于结项闸门快照视图。
 *
 * @param projectId       项目 ID
 * @param hasDeposit      是否存在保证金（fees 表 BID_BOND 是否有非 CANCELLED 行）
 * @param depositAmount   保证金总额（汇总 BID_BOND）
 * @param returnStatus    退回状态（NA/NOT_RETURNED/RETURNED）
 * @param returnDate      最近一次退回时间（仅 RETURNED 有意义）
 * @param evidenceDocId   退回凭证文档 ID（来源 project_closure.deposit_return_evidence_id）
 */
public record ProjectDepositSnapshot(
        Long projectId,
        boolean hasDeposit,
        BigDecimal depositAmount,
        ProjectClosureGatePolicy.DepositReturnStatus returnStatus,
        LocalDateTime returnDate,
        Long evidenceDocId) {

    public ProjectClosureGatePolicy.DepositSnapshot toGateInput() {
        return new ProjectClosureGatePolicy.DepositSnapshot(
                hasDeposit, returnStatus, returnDate, evidenceDocId, null, null);
    }
}
