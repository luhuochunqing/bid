// Input: fees repository, DTOs, audit service, and project access scope service
// Output: Fee business service operations guarded by project data permissions
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.fees.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.fees.dto.FeeCreateRequest;
import com.xiyu.bid.fees.dto.FeeDTO;
import com.xiyu.bid.fees.dto.FeeStatisticsDTO;
import com.xiyu.bid.fees.dto.FeeUpdateRequest;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 费用服务
 * 处理费用相关的业务逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeService {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final FeeRepository feeRepository;
    private final IAuditLogService auditLogService;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Auditable(action = "CREATE", entityType = "Fee", description = "Create new fee")
    @Transactional
    public FeeDTO createFee(FeeCreateRequest request) {
        log.info("Creating fee for project: {}", request.getProjectId());

        // Validate input
        FeeRequestValidator.validateCreateRequest(request);
        projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());

        Fee fee = Fee.builder()
                .projectId(request.getProjectId())
                .feeType(request.getFeeType())
                .amount(request.getAmount())
                .feeDate(request.getFeeDate())
                .status(Fee.Status.PENDING)
                .remarks(request.getRemarks())
                .build();

        Fee savedFee = feeRepository.save(fee);
        log.info("Created fee with id: {}", savedFee.getId());

        return FeeMapper.toDTO(savedFee);
    }

    @Transactional(readOnly = true)
    public FeeDTO getFeeById(Long id) {
        log.debug("Fetching fee by id: {}", id);
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);
        return FeeMapper.toDTO(fee);
    }

    @Transactional(readOnly = true)
    public Page<FeeDTO> getAllFees(Pageable pageable) {
        log.debug("Fetching all fees with pagination");
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        Page<Fee> fees = allowedProjectIds.isEmpty()
                ? findAllFeesForEmptyScope(pageable)
                : feeRepository.findByProjectIdIn(allowedProjectIds, pageable);
        return fees
                .map(FeeMapper::toDTO);
    }

    /**
     * 根据项目ID获取费用列表
     */
    @Transactional(readOnly = true)
    public List<FeeDTO> getFeesByProjectId(Long projectId) {
        log.debug("Fetching fees for project: {}", projectId);
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return feeRepository.findByProjectId(projectId).stream()
                .map(FeeMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据状态获取费用列表
     */
    @Transactional(readOnly = true)
    public List<FeeDTO> getFeesByStatus(FeeDTO.Status status) {
        Fee.Status entityStatus = Fee.Status.valueOf(status.name());
        log.debug("Fetching fees with status: {}", entityStatus);
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        List<Fee> fees = allowedProjectIds.isEmpty()
                ? findFeesByStatusForEmptyScope(entityStatus)
                : feeRepository.findByStatusAndProjectIdIn(entityStatus, allowedProjectIds);
        return fees.stream()
                .map(FeeMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 更新费用
     */
    @Auditable(action = "UPDATE", entityType = "Fee", description = "Update fee")
    @Transactional
    public FeeDTO updateFee(Long id, FeeUpdateRequest request) {
        log.info("Updating fee with id: {}", id);

        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);

        // Only allow updates to pending or cancelled fees
        if (fee.getStatus() == Fee.Status.PAID || fee.getStatus() == Fee.Status.RETURNED) {
            throw new IllegalStateException("Cannot update fee with status: " + fee.getStatus());
        }

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            fee.setAmount(request.getAmount());
        }

        if (request.getFeeDate() != null) {
            fee.setFeeDate(request.getFeeDate());
        }

        if (request.getRemarks() != null) {
            fee.setRemarks(request.getRemarks());
        }

        Fee updatedFee = feeRepository.save(fee);
        log.info("Updated fee with id: {}", updatedFee.getId());

        return FeeMapper.toDTO(updatedFee);
    }

    /**
     * 删除费用
     */
    @Auditable(action = "DELETE", entityType = "Fee", description = "Delete fee")
    @Transactional
    public void deleteFee(Long id) {
        log.info("Deleting fee with id: {}", id);

        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);

        // Only allow deletion of pending or cancelled fees
        if (fee.getStatus() == Fee.Status.PAID || fee.getStatus() == Fee.Status.RETURNED) {
            throw new IllegalStateException("Cannot delete fee with status: " + fee.getStatus());
        }

        feeRepository.deleteById(id);
        log.info("Deleted fee with id: {}", id);
    }

    /**
     * 标记费用为已支付
     */
    @Auditable(action = "PAY", entityType = "Fee", description = "Mark fee as paid")
    @Transactional
    public FeeDTO markAsPaid(Long id, String paidBy) {
        log.info("Marking fee {} as paid by: {}", id, paidBy);

        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);

        if (fee.getStatus() != Fee.Status.PENDING) {
            throw new IllegalStateException("Only pending fees can be marked as paid. Current status: " + fee.getStatus());
        }

        fee.setStatus(Fee.Status.PAID);
        fee.setPaymentDate(LocalDateTime.now());
        fee.setPaidBy(paidBy);

        Fee updatedFee = feeRepository.save(fee);
        log.info("Marked fee {} as paid", id);

        return FeeMapper.toDTO(updatedFee);
    }

    /**
     * 标记费用为已退还
     */
    @Auditable(action = "RETURN", entityType = "Fee", description = "Mark fee as returned")
    @Transactional
    public FeeDTO markAsReturned(Long id, String returnTo) {
        log.info("Marking fee {} as returned to: {}", id, returnTo);

        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);

        if (fee.getStatus() != Fee.Status.PAID) {
            throw new IllegalStateException("Only paid fees can be marked as returned. Current status: " + fee.getStatus());
        }

        fee.setStatus(Fee.Status.RETURNED);
        fee.setReturnDate(LocalDateTime.now());
        fee.setReturnTo(returnTo);

        Fee updatedFee = feeRepository.save(fee);
        log.info("Marked fee {} as returned", id);

        return FeeMapper.toDTO(updatedFee);
    }

    /**
     * 取消费用
     */
    @Auditable(action = "CANCEL", entityType = "Fee", description = "Cancel fee")
    @Transactional
    public FeeDTO cancelFee(Long id) {
        log.info("Cancelling fee with id: {}", id);

        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee not found with id: " + id));
        assertCanAccessFeeProject(fee);

        if (fee.getStatus() != Fee.Status.PENDING) {
            throw new IllegalStateException("Only pending fees can be cancelled. Current status: " + fee.getStatus());
        }

        fee.setStatus(Fee.Status.CANCELLED);

        Fee updatedFee = feeRepository.save(fee);
        log.info("Cancelled fee with id: {}", id);

        return FeeMapper.toDTO(updatedFee);
    }

    /**
     * 获取费用统计
     */
    @Transactional(readOnly = true)
    public FeeStatisticsDTO getStatistics(Long projectId) {
        log.debug("Fetching fee statistics for project: {}", projectId);
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        BigDecimal totalPending = feeRepository.sumAmountByProjectIdAndStatus(projectId, Fee.Status.PENDING);
        BigDecimal totalPaid = feeRepository.sumAmountByProjectIdAndStatus(projectId, Fee.Status.PAID);
        BigDecimal totalReturned = feeRepository.sumAmountByProjectIdAndStatus(projectId, Fee.Status.RETURNED);
        BigDecimal totalCancelled = feeRepository.sumAmountByProjectIdAndStatus(projectId, Fee.Status.CANCELLED);

        return FeeStatisticsFactory.create(projectId, totalPending, totalPaid, totalReturned, totalCancelled);
    }

    private void assertCanAccessFeeProject(Fee fee) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(fee.getProjectId());
    }

    private Page<Fee> findAllFeesForEmptyScope(Pageable pageable) {
        if (currentUserHasAdminAccess()) {
            return feeRepository.findAll(pageable);
        }
        return Page.empty(pageable);
    }

    private List<Fee> findFeesByStatusForEmptyScope(Fee.Status status) {
        if (currentUserHasAdminAccess()) {
            return feeRepository.findByStatus(status);
        }
        return List.of();
    }

    private boolean currentUserHasAdminAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
