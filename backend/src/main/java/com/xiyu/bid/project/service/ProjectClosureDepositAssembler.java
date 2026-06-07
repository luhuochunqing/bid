// Input: Fee 表 / ProjectInitiationDetails 保证金派生 + GatePolicy 适配
// Output: 结项服务所需的保证金快照和状态解析（数据访问 + 映射，不含业务决策）
// Pos: project/service/ - 应用服务辅助层
package com.xiyu.bid.project.service;

import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.DepositReturnStatus;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.DepositSnapshot;
import com.xiyu.bid.project.dto.ClosureSubmitRequest;
import com.xiyu.bid.project.entity.ProjectClosure;
import com.xiyu.bid.project.entity.ProjectDepositSnapshot;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public final class ProjectClosureDepositAssembler {

    /** feeRepository. */
    private final FeeRepository feeRepository;
    /** initiationRepository. */
    private final ProjectInitiationDetailsRepository initiationRepository;

    /**
     * 构建保证金快照.
     *
     * @param projectId       项目ID
     * @param existingClosure 已存在的结项（可选）
     * @return 保证金快照
     */
    public ProjectDepositSnapshot buildSnapshot(
            final Long projectId,
            final Optional<ProjectClosure> existingClosure) {
        // 如果已有结项记录且包含保证金退回数据，优先使用已保存的数据
        if (existingClosure.isPresent()) {
            ProjectClosure closure = existingClosure.get();
            if (closure.getDepositReturnStatus() != null
                    && !closure.getDepositReturnStatus().isBlank()
                    && !"NA".equals(closure.getDepositReturnStatus())) {
                return buildSnapshotFromClosure(projectId, closure);
            }
        }
        List<Fee> allBonds = feeRepository.findByProjectId(projectId)
                .stream().filter(f -> f.getFeeType() == Fee.FeeType.BID_BOND
                        && f.getStatus() != Fee.Status.CANCELLED).toList();
        if (allBonds.isEmpty()) {
            ProjectInitiationDetails init = initiationRepository
                    .findByProjectId(projectId).orElse(null);
            if (init != null && "YES".equals(init.getNeedDeposit())) {
                BigDecimal amount = init.getDepositAmount() != null
                        ? init.getDepositAmount() : BigDecimal.ZERO;
                return new ProjectDepositSnapshot(
                        projectId, true, amount,
                        DepositReturnStatus.NOT_RETURNED, null, null);
            }
            return new ProjectDepositSnapshot(projectId, false,
                    BigDecimal.ZERO, DepositReturnStatus.NA, null, null);
        }
        BigDecimal totalAmount = allBonds.stream().map(Fee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Fee> returnedBonds = allBonds.stream()
                .filter(f -> f.getStatus() == Fee.Status.RETURNED).toList();
        if (returnedBonds.size() == allBonds.size()) {
            Fee latest = returnedBonds.stream()
                    .max((a, b) -> a.getReturnDate() != null
                            && b.getReturnDate() != null
                    ? a.getReturnDate().compareTo(b.getReturnDate()) : 0)
                    .orElse(returnedBonds.get(0));
            Long evidenceId = existingClosure
                    .map(ProjectClosure::getDepositReturnEvidenceId)
                    .orElse(null);
            return new ProjectDepositSnapshot(projectId, true, totalAmount,
                    DepositReturnStatus.FULLY_RETURNED,
                    latest.getReturnDate(), evidenceId);
        }
        return new ProjectDepositSnapshot(projectId, true, totalAmount,
                DepositReturnStatus.NOT_RETURNED, null, null);
    }

    /** 从已保存的结项记录构建保证金快照 */
    private ProjectDepositSnapshot buildSnapshotFromClosure(Long projectId, ProjectClosure closure) {
        DepositReturnStatus status;
        try {
            status = DepositReturnStatus.valueOf(closure.getDepositReturnStatus());
        } catch (IllegalArgumentException e) {
            status = DepositReturnStatus.NOT_RETURNED;
        }
        BigDecimal amount = initiationRepository.findByProjectId(projectId)
                .map(ProjectInitiationDetails::getDepositAmount)
                .orElse(BigDecimal.ZERO);
        return new ProjectDepositSnapshot(projectId, amount.compareTo(BigDecimal.ZERO) > 0,
                amount, status, closure.getDepositReturnDate(),
                closure.getDepositReturnEvidenceId());
    }

    /**
     * 将业务快照映射为验证策略使用的快照格式.
     *
     * @param snap    业务层快照
     * @param closure 结项数据
     * @return 策略验证快照
     */
    public DepositSnapshot mapToGateSnapshot(
            final ProjectDepositSnapshot snap,
            final ProjectClosure closure) {
        if (closure == null || closure.getDepositReturnStatus() == null) {
            return new DepositSnapshot(snap.hasDeposit(), snap.returnStatus(),
                    snap.returnDate(), snap.evidenceDocId(), null, null);
        }
        DepositReturnStatus status;
        try {
            status = DepositReturnStatus
                    .valueOf(closure.getDepositReturnStatus());
        } catch (IllegalArgumentException e) {
            status = snap.returnStatus();
        }
        return new DepositSnapshot(snap.hasDeposit(), status,
                closure.getDepositReturnDate() != null
                        ? closure.getDepositReturnDate()
                        : snap.returnDate(),
                closure.getDepositReturnEvidenceId() != null
                        ? closure.getDepositReturnEvidenceId()
                        : snap.evidenceDocId(),
                closure.getTransferAmount(), closure.getReturnedAmount());
    }

    /**
     * 解析请求状态.
     *
     * @param req  提交的结项请求
     * @param snap 当前项目保证金快照
     * @return 解析后的状态信息
     */
    public DepositStatusInfo resolveStatus(
            final ClosureSubmitRequest req,
            final ProjectDepositSnapshot snap) {
        if (!snap.hasDeposit()) {
            return new DepositStatusInfo(DepositReturnStatus.NA, null,
                    null, null, null);
        }
        String statusStr = req.getDepositReturnStatus();
        if (statusStr == null || statusStr.isBlank()) {
            return new DepositStatusInfo(DepositReturnStatus.NOT_RETURNED,
                    null, null, null, null);
        }
        DepositReturnStatus status;
        try {
            status = DepositReturnStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return new DepositStatusInfo(DepositReturnStatus.NOT_RETURNED,
                    null, null, null, null);
        }
        return switch (status) {
            case FULLY_RETURNED -> new DepositStatusInfo(status,
                    req.getDepositReturnDate() != null
                            ? req.getDepositReturnDate()
                            : snap.returnDate(),
                    req.getDepositReturnEvidenceId() != null
                            ? req.getDepositReturnEvidenceId()
                            : snap.evidenceDocId(),
                    null, null);
            case TRANSFERRED_TO_FEE -> new DepositStatusInfo(status, null,
                    req.getDepositReturnEvidenceId(),
                    req.getTransferAmount(), null);
            case PARTIAL_RETURN_PARTIAL_TRANSFER -> new DepositStatusInfo(
                    status, null, req.getDepositReturnEvidenceId(),
                    req.getTransferAmount(), req.getReturnedAmount());
            default -> new DepositStatusInfo(status, null, null, null, null);
        };
    }

    /**
     * 获取指定项目的保证金缴纳方式名称.
     *
     * @param projectId 项目ID
     * @return 缴纳方式名称，例如：电汇、保险/保函
     */
    public String getPaymentMethod(final Long projectId) {
        try {
            return initiationRepository.findByProjectId(projectId)
                    .map(ProjectInitiationDetails::getDepositPaymentMethod)
                    .map(m -> switch (m) {
                        case "WIRE" -> "电汇";
                        case "GUARANTEE" -> "保险/保函";
                        default -> m;
                    })
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("getPaymentMethod failed for {}: {}",
                    projectId, e.getMessage());
            return null;
        }
    }

    /**
     * 保证金状态信息记录.
     *
     * @param status         状态
     * @param returnDate     退回日期
     * @param evidenceDocId  凭证文档 ID
     * @param transferAmount 转出金额
     * @param returnedAmount 退回金额
     */
    public record DepositStatusInfo(DepositReturnStatus status,
                                    LocalDateTime returnDate,
                                    Long evidenceDocId,
                                    BigDecimal transferAmount,
                                    BigDecimal returnedAmount) {
    }
}
