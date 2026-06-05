// Input: Fee 表 / ProjectInitiationDetails 保证金派生 + GatePolicy 适配
// Output: 结项服务所需的保证金快照和状态解析（数据访问 + 映射，不含业务决策）
// Pos: project/service/ - 应用服务辅助层
package com.xiyu.bid.project.service;

import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy;
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
public class ProjectClosureDepositAssembler {

    private final FeeRepository feeRepository;
    private final ProjectInitiationDetailsRepository initiationRepository;

    public ProjectDepositSnapshot buildSnapshot(Long projectId, Optional<ProjectClosure> existingClosure) {
        List<Fee> allBonds = feeRepository.findByProjectId(projectId)
                .stream().filter(f -> f.getFeeType() == Fee.FeeType.BID_BOND
                        && f.getStatus() != Fee.Status.CANCELLED).toList();
        if (allBonds.isEmpty()) {
            return new ProjectDepositSnapshot(projectId, false, BigDecimal.ZERO, DepositReturnStatus.NA, null, null);
        }
        BigDecimal totalAmount = allBonds.stream().map(Fee::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Fee> returnedBonds = allBonds.stream().filter(f -> f.getStatus() == Fee.Status.RETURNED).toList();
        if (returnedBonds.size() == allBonds.size()) {
            Fee latest = returnedBonds.stream().max((a, b) -> a.getReturnDate() != null && b.getReturnDate() != null
                    ? a.getReturnDate().compareTo(b.getReturnDate()) : 0).orElse(returnedBonds.get(0));
            Long evidenceId = existingClosure.map(ProjectClosure::getDepositReturnEvidenceId).orElse(null);
            return new ProjectDepositSnapshot(projectId, true, totalAmount,
                    DepositReturnStatus.FULLY_RETURNED, latest.getReturnDate(), evidenceId);
        }
        return new ProjectDepositSnapshot(projectId, true, totalAmount, DepositReturnStatus.NOT_RETURNED, null, null);
    }

    public DepositSnapshot mapToGateSnapshot(ProjectDepositSnapshot snap, ProjectClosure closure) {
        if (closure == null || closure.getDepositReturnStatus() == null) {
            return new DepositSnapshot(snap.hasDeposit(), snap.returnStatus(),
                    snap.returnDate(), snap.evidenceDocId(), null, null);
        }
        DepositReturnStatus status;
        try {
            status = DepositReturnStatus.valueOf(closure.getDepositReturnStatus());
        } catch (IllegalArgumentException e) {
            status = snap.returnStatus();
        }
        return new DepositSnapshot(snap.hasDeposit(), status,
                closure.getDepositReturnDate() != null ? closure.getDepositReturnDate() : snap.returnDate(),
                closure.getDepositReturnEvidenceId() != null ? closure.getDepositReturnEvidenceId() : snap.evidenceDocId(),
                closure.getTransferAmount(), closure.getReturnedAmount());
    }

    public DepositStatusInfo resolveStatus(ClosureSubmitRequest req, ProjectDepositSnapshot snap) {
        if (!snap.hasDeposit()) {
            return new DepositStatusInfo(DepositReturnStatus.NA, null, null, null, null);
        }
        String statusStr = req.getDepositReturnStatus();
        if (statusStr == null || statusStr.isBlank()) {
            return new DepositStatusInfo(DepositReturnStatus.NOT_RETURNED, null, null, null, null);
        }
        DepositReturnStatus status;
        try {
            status = DepositReturnStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return new DepositStatusInfo(DepositReturnStatus.NOT_RETURNED, null, null, null, null);
        }
        return switch (status) {
            case FULLY_RETURNED -> new DepositStatusInfo(status,
                    req.getDepositReturnDate() != null ? req.getDepositReturnDate() : snap.returnDate(),
                    req.getDepositReturnEvidenceId() != null ? req.getDepositReturnEvidenceId() : snap.evidenceDocId(),
                    null, null);
            case TRANSFERRED_TO_FEE -> new DepositStatusInfo(status, null,
                    req.getDepositReturnEvidenceId(), req.getTransferAmount(), null);
            case PARTIAL_RETURN_PARTIAL_TRANSFER -> new DepositStatusInfo(status, null,
                    req.getDepositReturnEvidenceId(), req.getTransferAmount(), req.getReturnedAmount());
            default -> new DepositStatusInfo(status, null, null, null, null);
        };
    }

    public String getPaymentMethod(Long projectId) {
        try {
            return initiationRepository.findByProjectId(projectId)
                    .map(ProjectInitiationDetails::getDepositPaymentMethod).orElse(null);
        } catch (Exception e) {
            log.warn("getPaymentMethod failed for {}: {}", projectId, e.getMessage());
            return null;
        }
    }

    public record DepositStatusInfo(DepositReturnStatus status, LocalDateTime returnDate,
                                     Long evidenceDocId, BigDecimal transferAmount, BigDecimal returnedAmount) {
    }
}
