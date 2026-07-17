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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作台角色化改造 BE-4：WorkbenchResourcePendingQueryService 聚合接口测试。
 * 覆盖 spec.md §3 模块4 的管理员/保管员分支 + fail-closed + limit 4 + 数据库分页。
 *
 * 改进点（相对 DashboardResourcePendingServiceTest）：
 * 1. 测试 WorkbenchResourcePendingQueryService（迁移自 DashboardResourcePendingService）
 * 2. 验证 P0-4.1：使用 Pageable 数据库分页（每类前 4 条）
 * 3. 验证 P0-2.3：使用 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 替代 CaBorrowPermissionChecker
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkbenchResourcePendingQueryServiceTest {

    @Mock private AccountBorrowApplicationRepository accountBorrowRepository;
    @Mock private AccountBorrowApplicationMapper accountBorrowMapper;
    @Mock private CaBorrowApplicationRepository caBorrowRepository;
    @Mock private CaBorrowApplicationNameEnricher caNameEnricher;
    @Mock private CurrentUserLookupService currentUserLookupService;
    @Mock private EffectiveRoleResolver effectiveRoleResolver;
    @Mock private UserDetails userDetails;

    private WorkbenchResourcePendingQueryService service;
    private final User currentUser = mock(User.class);

    @BeforeEach
    void setUp() {
        service = new WorkbenchResourcePendingQueryService(
                accountBorrowRepository,
                accountBorrowMapper,
                caBorrowRepository,
                caNameEnricher,
                currentUserLookupService,
                effectiveRoleResolver
        );
        when(currentUserLookupService.requireUser(userDetails)).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(1L);
        when(currentUser.getUsername()).thenReturn("testuser");
    }

    @Test
    void adminRole_returnsMergedAccountAndCaApprovalsSortedByCreatedAtDesc() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        // 管理员走 findByStatusOrderByCreatedAtDesc(status, pageable) 分支
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 15, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 16, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 7, 14, 10, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 7, 17, 10, 0);

        List<AccountBorrowApplication> accountApps = List.of(new AccountBorrowApplication());
        when(accountBorrowRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class))).thenReturn(accountApps);
        when(accountBorrowMapper.toDTOList(accountApps)).thenReturn(List.of(
                BorrowApplicationDTO.builder().id(10L).accountName("账号A").applicantId(2L)
                        .applicantName("张三").purpose("投标用").projectId(100L).projectName("项目A")
                        .createdAt(t1).build(),
                BorrowApplicationDTO.builder().id(11L).accountName("账号B").applicantId(3L)
                        .applicantName("李四").purpose("备用").projectId(101L).projectName("项目B")
                        .createdAt(t3).build()
        ));

        List<CaBorrowApplicationEntity> caApps = List.of(new CaBorrowApplicationEntity());
        when(caBorrowRepository.findByStatusOrderByCreatedAtDesc(any(String.class), any(Pageable.class))).thenReturn(caApps);
        when(caNameEnricher.enrich(caApps)).thenReturn(List.of(
                CaBorrowApplicationDTO.builder().id(20L).caName("CA甲").applicantId(2L)
                        .applicantName("张三").purpose("签章").projectId(100L).projectName("项目A")
                        .createdAt(t2).build(),
                CaBorrowApplicationDTO.builder().id(21L).caName("CA乙").applicantId(3L)
                        .applicantName("李四").purpose("加密").projectId(101L).projectName("项目B")
                        .createdAt(t4).build()
        ));

        List<ResourcePendingApprovalDTO> result = service.getPendingApprovals(userDetails);

        assertThat(result).hasSize(4);
        // 按 createdAt 倒序：t4 > t2 > t1 > t3
        assertThat(result.get(0).getApplicationId()).isEqualTo(21L);  // t4
        assertThat(result.get(1).getApplicationId()).isEqualTo(20L);  // t2
        assertThat(result.get(2).getApplicationId()).isEqualTo(10L);  // t1
        assertThat(result.get(3).getApplicationId()).isEqualTo(11L);  // t3
        // 验证类型标记
        assertThat(result).extracting(ResourcePendingApprovalDTO::getApplicationType)
                .containsExactly("CA", "CA", "ACCOUNT", "ACCOUNT");
    }

    @Test
    void custodianRole_returnsOnlyOwnApprovals() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);
        // 保管员走 findByCustodianIdAndStatusOrderByCreatedAtDesc(custodianId, status, pageable)
        // 和 findByApproverIdAndStatusOrderByCreatedAtDesc(approverId, status, pageable)
        List<AccountBorrowApplication> accountApps = List.of(new AccountBorrowApplication());
        when(accountBorrowRepository.findByCustodianIdAndStatusOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
                .thenReturn(accountApps);
        when(accountBorrowMapper.toDTOList(accountApps)).thenReturn(List.of(
                BorrowApplicationDTO.builder().id(10L).accountName("账号A").applicantId(2L)
                        .applicantName("张三").purpose("投标用").createdAt(LocalDateTime.now()).build()
        ));

        List<CaBorrowApplicationEntity> caApps = List.of(new CaBorrowApplicationEntity());
        when(caBorrowRepository.findByApproverIdAndStatusOrderByCreatedAtDesc(any(), any(String.class), any(Pageable.class)))
                .thenReturn(caApps);
        when(caNameEnricher.enrich(caApps)).thenReturn(List.of(
                CaBorrowApplicationDTO.builder().id(20L).caName("CA甲").applicantId(2L)
                        .applicantName("张三").purpose("签章").createdAt(LocalDateTime.now().minusHours(1)).build()
        ));

        List<ResourcePendingApprovalDTO> result = service.getPendingApprovals(userDetails);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ResourcePendingApprovalDTO::getApplicationType)
                .containsExactlyInAnyOrder("ACCOUNT", "CA");
    }

    @Test
    void nullRoleCode_returnsEmptyList_failClosed() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(null);

        List<ResourcePendingApprovalDTO> result = service.getPendingApprovals(userDetails);

        assertThat(result).isEmpty();
    }

    @Test
    void mergedResultsLimitedTo4() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        // 3 账户 + 3 CA = 6 条，应只返回前 4 条（按 createdAt 倒序）
        when(accountBorrowRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(new AccountBorrowApplication()));
        when(accountBorrowMapper.toDTOList(any())).thenReturn(List.of(
                buildAccountDto(10L, LocalDateTime.of(2026, 7, 15, 10, 0)),
                buildAccountDto(11L, LocalDateTime.of(2026, 7, 14, 10, 0)),
                buildAccountDto(12L, LocalDateTime.of(2026, 7, 13, 10, 0))
        ));
        when(caBorrowRepository.findByStatusOrderByCreatedAtDesc(any(String.class), any(Pageable.class)))
                .thenReturn(List.of(new CaBorrowApplicationEntity()));
        when(caNameEnricher.enrich(any())).thenReturn(List.of(
                buildCaDto(20L, LocalDateTime.of(2026, 7, 17, 10, 0)),
                buildCaDto(21L, LocalDateTime.of(2026, 7, 16, 10, 0)),
                buildCaDto(22L, LocalDateTime.of(2026, 7, 12, 10, 0))
        ));

        List<ResourcePendingApprovalDTO> result = service.getPendingApprovals(userDetails);

        assertThat(result).hasSize(4);
        // 按 createdAt 倒序前 4 条：7/17, 7/16, 7/15, 7/14
        assertThat(result).extracting(ResourcePendingApprovalDTO::getApplicationId)
                .containsExactly(20L, 21L, 10L, 11L);
    }

    @Test
    void pageableConfiguredToMaxItems() {
        // 验证 P0-4.1：service 使用 PageRequest.of(0, MAX_ITEMS) 调用 Repository
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        when(accountBorrowRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(caBorrowRepository.findByStatusOrderByCreatedAtDesc(any(String.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.getPendingApprovals(userDetails);

        // 通过 ArgumentCaptor 验证 Pageable 取值（这里用简单方式：MAX_ITEMS = 4）
        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(accountBorrowRepository).findByStatusOrderByCreatedAtDesc(any(), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(WorkbenchResourcePendingQueryService.MAX_ITEMS);
    }

    private BorrowApplicationDTO buildAccountDto(Long id, LocalDateTime createdAt) {
        return BorrowApplicationDTO.builder().id(id).accountName("账号" + id)
                .applicantId(2L).applicantName("张三").purpose("用").createdAt(createdAt).build();
    }

    private CaBorrowApplicationDTO buildCaDto(Long id, LocalDateTime createdAt) {
        return CaBorrowApplicationDTO.builder().id(id).caName("CA" + id)
                .applicantId(2L).applicantName("张三").purpose("用").createdAt(createdAt).build();
    }
}
