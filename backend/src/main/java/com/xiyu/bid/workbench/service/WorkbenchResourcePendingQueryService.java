package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.service.AccountBorrowApplicationMapper;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.service.CaBorrowApplicationNameEnricher;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.workbench.dto.ResourcePendingApprovalDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作台角色化改造：资源待审批聚合 Service（spec.md §3 模块4）。
 * 合并账户借用申请和 CA 借用申请，按当前用户角色返回待审批列表。
 *
 * 角色分支：
 * - 管理员（GLOBAL_ACCESS_ROLES）：查全部待审批申请
 * - 保管员：查自己作为 custodian/approver 的待审批申请
 * - 其他角色：返回空列表（无审批权限）
 *
 * 改进点（相对 DashboardResourcePendingService）：
 * 1. P0-2.3：使用 RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(canonicalCode(roleCode))
 *    替代 CaBorrowPermissionChecker.isPrivilegedRole，消除跨包抽象泄漏
 * 2. P0-4.1：使用 Pageable 数据库层面分页，每类各取前 MAX_ITEMS 条
 *    替代全量加载后内存 limit 4 的性能问题
 * 3. P0-2.2：迁移到 workbench 包，路径前缀 /api/workbench 与既有 WorkbenchDeadlineController 对齐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkbenchResourcePendingQueryService {

    private final AccountBorrowApplicationRepository accountBorrowRepository;
    private final AccountBorrowApplicationMapper accountBorrowMapper;
    private final CaBorrowApplicationRepository caBorrowRepository;
    private final CaBorrowApplicationNameEnricher caNameEnricher;
    private final CurrentUserLookupService currentUserLookupService;
    private final EffectiveRoleResolver effectiveRoleResolver;

    /** 卡片最大显示条数（与 workbench-rebuild-core.js MAX_CARD_ITEMS 一致） */
    static final int MAX_ITEMS = 4;

    @Transactional(readOnly = true)
    public List<ResourcePendingApprovalDTO> getPendingApprovals(UserDetails userDetails) {
        User currentUser = currentUserLookupService.requireUser(userDetails);
        String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
        // fail-closed：OSS 缓存未命中时 roleCode 为 null，返回空列表避免误放行
        if (roleCode == null) {
            log.warn("Resource pending approvals: role code resolution failed for user {} (OSS cache miss)",
                    currentUser.getUsername());
            return List.of();
        }

        // P0-2.3：使用 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 替代 CaBorrowPermissionChecker
        String canonicalRole = RoleProfileCatalog.canonicalCode(roleCode);
        boolean isPrivileged = RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(canonicalRole);

        // P0-4.1：数据库层面分页，每类各取前 MAX_ITEMS 条避免全量加载
        // 跨表无法合并分页，但单表分页足以避免加载全量数据
        Pageable pageable = PageRequest.of(0, MAX_ITEMS);

        // 账户借用申请：管理员查全部，保管员查自己 custodian 的
        List<AccountBorrowApplication> accountApps = isPrivileged
                ? accountBorrowRepository.findByStatusOrderByCreatedAtDesc(
                        AccountBorrowApplication.BorrowStatus.PENDING_APPROVAL, pageable)
                : accountBorrowRepository.findByCustodianIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), AccountBorrowApplication.BorrowStatus.PENDING_APPROVAL, pageable);
        List<BorrowApplicationDTO> accountDtos = accountBorrowMapper.toDTOList(accountApps);

        // CA 借用申请：管理员查全部，保管员查自己 approver 的
        List<CaBorrowApplicationEntity> caApps = isPrivileged
                ? caBorrowRepository.findByStatusOrderByCreatedAtDesc(
                        CaBorrowApplicationEntity.BorrowStatus.PENDING_APPROVAL.name(), pageable)
                : caBorrowRepository.findByApproverIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), CaBorrowApplicationEntity.BorrowStatus.PENDING_APPROVAL.name(), pageable);
        List<CaBorrowApplicationDTO> caDtos = caNameEnricher.enrich(caApps);

        // 合并 + 按 createdAt 倒序 + 取前 MAX_ITEMS 条
        return Stream.concat(
                accountDtos.stream().map(this::toAccountDTO),
                caDtos.stream().map(this::toCaDTO)
        )
        .sorted(Comparator.comparing(ResourcePendingApprovalDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(MAX_ITEMS)
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
