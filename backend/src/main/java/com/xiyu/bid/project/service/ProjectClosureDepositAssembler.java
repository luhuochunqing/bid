// Input: Fee 表 / ProjectInitiationDetails 保证金派生 + GatePolicy 适配
// Output: 结项服务所需的保证金快照和状态解析（数据访问 + 映射，不含业务决策）
// Pos: project/service/ - 应用服务辅助层
// 保证金金额：立项 deposit_amount > 0 时用立项金额；否则用未取消 BID_BOND 合计。
// 两条分支共用该金额，避免提交按费用合计放行、保存后预览改按立项金额判不平。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
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
        ProjectInitiationDetails init = initiationRepository
                .findByProjectId(projectId).orElse(null);
        List<Fee> allBonds = activeBonds(projectId);
        BigDecimal amount = resolveDepositAmount(init, allBonds);
        if (!hasDeposit(init, allBonds, amount)) {
            return new ProjectDepositSnapshot(projectId, false,
                    BigDecimal.ZERO, DepositReturnStatus.NA, null, null);
        }
        ProjectClosure closure = existingClosure.orElse(null);
        if (closure != null && hasSavedReturnStatus(closure)) {
            return new ProjectDepositSnapshot(projectId, true, amount,
                    parseStatus(closure.getDepositReturnStatus()),
                    closure.getDepositReturnDate(),
                    closure.getDepositReturnEvidenceId());
        }
        List<Fee> returnedBonds = allBonds.stream()
                .filter(f -> f.getStatus() == Fee.Status.RETURNED).toList();
        if (!allBonds.isEmpty() && returnedBonds.size() == allBonds.size()) {
            Fee latest = returnedBonds.stream()
                    .max((a, b) -> a.getReturnDate() != null
                            && b.getReturnDate() != null
                    ? a.getReturnDate().compareTo(b.getReturnDate()) : 0)
                    .orElse(returnedBonds.get(0));
            Long evidenceId = closure == null ? null
                    : closure.getDepositReturnEvidenceId();
            return new ProjectDepositSnapshot(projectId, true, amount,
                    DepositReturnStatus.FULLY_RETURNED,
                    latest.getReturnDate(), evidenceId);
        }
        return new ProjectDepositSnapshot(projectId, true, amount,
                DepositReturnStatus.NOT_RETURNED, null, null);
    }

    private static BigDecimal resolveDepositAmount(final ProjectInitiationDetails init,
                                                   final List<Fee> bonds) {
        BigDecimal initiationAmount = init == null ? null : init.getDepositAmount();
        if (initiationAmount != null
                && initiationAmount.compareTo(BigDecimal.ZERO) > 0) {
            return initiationAmount;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Fee fee : bonds) {
            if (fee.getAmount() != null) {
                total = total.add(fee.getAmount());
            }
        }
        return total;
    }

    private static boolean hasDeposit(final ProjectInitiationDetails init,
                                      final List<Fee> bonds,
                                      final BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        if (!bonds.isEmpty()) {
            return true;
        }
        return init != null && init.getNeedDeposit() != null
                && "YES".equalsIgnoreCase(init.getNeedDeposit().trim());
    }

    private List<Fee> activeBonds(final Long projectId) {
        List<Fee> found = feeRepository.findByProjectId(projectId);
        if (found == null) {
            return List.of();
        }
        return found.stream()
                .filter(f -> f.getFeeType() == Fee.FeeType.BID_BOND
                        && f.getStatus() != Fee.Status.CANCELLED)
                .toList();
    }

    private static boolean hasSavedReturnStatus(final ProjectClosure closure) {
        String status = closure.getDepositReturnStatus();
        return status != null && !status.isBlank() && !"NA".equals(status);
    }

    private static DepositReturnStatus parseStatus(final String raw) {
        try {
            return DepositReturnStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return DepositReturnStatus.NOT_RETURNED;
        }
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
