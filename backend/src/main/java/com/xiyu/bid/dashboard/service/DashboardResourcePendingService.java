package com.xiyu.bid.dashboard.service;

import com.xiyu.bid.dashboard.dto.ResourcePendingApprovalDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.service.AccountBorrowApplicationMapper;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.service.CaBorrowApplicationNameEnricher;
import com.xiyu.bid.resources.service.CaBorrowPermissionChecker;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作台角色化改造：资源待审批聚合 Service（spec.md §3 模块4）。
 * 合并账户借用申请和 CA 借用申请，按当前用户角色返回待审批列表。
 * - 管理员（GLOBAL_ACCESS_ROLES）：查全部待审批申请
 * - 保管员：查自己作为 custodian/approver 的待审批申请
 * - 其他角色：返回空列表（无审批权限）
 * 合并后按 createdAt 倒序，取前 4 条。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardResourcePendingService {

    private final AccountBorrowApplicationRepository accountBorrowRepository;
    private final AccountBorrowApplicationMapper accountBorrowMapper;
    private final CaBorrowApplicationRepository caBorrowRepository;
    private final CaBorrowApplicationNameEnricher caNameEnricher;
    private final CurrentUserLookupService currentUserLookupService;
    private final EffectiveRoleResolver effectiveRoleResolver;

    @Transactional(readOnly = true)
    public List<ResourcePendingApprovalDTO> getPendingApprovals(UserDetails userDetails) {
        User currentUser = currentUserLookupService.requireUser(userDetails);
        String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
        // fail-closed：OSS 缓存未命中时 roleCode 为 null，返回空列表
        if (roleCode == null) {
            log.warn("Resource pending approvals: role code resolution failed for user {} (OSS cache miss)",
                    currentUser.getUsername());
            return List.of();
        }

        boolean isPrivileged = CaBorrowPermissionChecker.isPrivilegedRole(roleCode);

        // 账户借用申请：管理员查全部，保管员查自己 custodian 的
        List<AccountBorrowApplication> accountApps = isPrivileged
                ? accountBorrowRepository.findByStatusOrderByCreatedAtDesc(
                        AccountBorrowApplication.BorrowStatus.PENDING_APPROVAL)
                : accountBorrowRepository.findByCustodianIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), AccountBorrowApplication.BorrowStatus.PENDING_APPROVAL);
        List<BorrowApplicationDTO> accountDtos = accountBorrowMapper.toDTOList(accountApps);

        // CA 借用申请：管理员查全部，保管员查自己 approver 的
        List<CaBorrowApplicationEntity> caApps = isPrivileged
                ? caBorrowRepository.findByStatusOrderByCreatedAtDesc(
                        CaBorrowApplicationEntity.BorrowStatus.PENDING_APPROVAL.name())
                : caBorrowRepository.findByApproverIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), CaBorrowApplicationEntity.BorrowStatus.PENDING_APPROVAL.name());
        List<CaBorrowApplicationDTO> caDtos = caNameEnricher.enrich(caApps);

        // 合并 + 按 createdAt 倒序 + 取前 4 条
        return Stream.concat(
                accountDtos.stream().map(this::toAccountDTO),
                caDtos.stream().map(this::toCaDTO)
        )
        .sorted(Comparator.comparing(ResourcePendingApprovalDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(4)
        .toList();
    }

    private ResourcePendingApprovalDTO toAccountDTO(BorrowApplicationDTO dto) {
        return ResourcePendingApprovalDTO.builder()
                .applicationType("ACCOUNT")
                .applicationId(dto.getId())
                .resourceLabel(dto.getAccountName())
                .applicantId(dto.getApplicantId())
                .applicantName(dto.getApplicantName())
                .purpose(dto.getPurpose())
                .projectId(dto.getProjectId())
                .projectName(dto.getProjectName())
                .createdAt(dto.getCreatedAt())
                .build();
    }

    private ResourcePendingApprovalDTO toCaDTO(CaBorrowApplicationDTO dto) {
        return ResourcePendingApprovalDTO.builder()
                .applicationType("CA")
                .applicationId(dto.getId())
                .resourceLabel(dto.getCaName())
                .applicantId(dto.getApplicantId())
                .applicantName(dto.getApplicantName())
                .purpose(dto.getPurpose())
                .projectId(dto.getProjectId())
                .projectName(dto.getProjectName())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
