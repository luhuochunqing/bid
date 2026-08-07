package com.xiyu.bid.workbench.service;

import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workbench.domain.DeadlinePeriod;
import com.xiyu.bid.workbench.dto.DeadlineItemDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineItemsDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchDeadlineQueryServiceTest {

    @Mock TenderRepository tenderRepository;
    @Mock FeeRepository feeRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @InjectMocks WorkbenchDeadlineQueryService service;

    @Test
    void adminShouldSeeAllDeadlines() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any()))
                .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);

        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
        assertThat(result.bidOpening().todayCount()).isZero();
        assertThat(result.depositDeadline().todayCount()).isZero();
        verifyNoInteractions(projectRepository);
    }

    @Test
    void managerShouldSeeOnlyOwnProjects() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L, 2L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L, 2L))).thenReturn(List.of(10L));
        when(tenderRepository.findRegistrationDeadlinesByTenderIds(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesByTenderIds(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L, 2L)), any(), any()))
                .thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
    }

    /**
     * P0-1 regression guard: non-admin user with completely empty project scope MUST get zero
     * counts and MUST NOT hit any repository (no data leak).
     */
    @Test
    void nonAdminWithEmptyProjectScopeMustGetZeroCountsWithoutDataAccess() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);

        assertThat(result.registrationDeadline().todayCount()).isZero();
        assertThat(result.registrationDeadline().weekCount()).isZero();
        assertThat(result.registrationDeadline().monthCount()).isZero();
        assertThat(result.bidOpening().todayCount()).isZero();
        assertThat(result.depositDeadline().todayCount()).isZero();
        // Critical: repositories must NOT be hit when the user has no project access
        verifyNoInteractions(tenderRepository, feeRepository, projectRepository);
    }

    @Test
    void managerWithEmptyTenderIdsShouldGetZeroCounts() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L))).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L)), any(), any()))
                .thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isZero();
    }

    /**
     * P1 regression guard: when current week spans a month boundary, the query window
     * must include the out-of-month days so that weekly counts don't under-report.
     * Example: today = 2026-08-01 (Saturday) → week = Jul 27 ~ Aug 2.
     * Query start MUST be <= 2026-07-27 (week's Monday), not 2026-08-01 (month start).
     */
    @Test
    void crossMonthWeekWindowMustExpandQueryRangeBackward() {
        var today = LocalDate.of(2026, 8, 1); // Saturday
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findRegistrationDeadlinesBetween(startCap.capture(), endCap.capture());

        assertThat(startCap.getValue())
                .as("query start should cover the week's Monday (Jul 27) which is before monthStart (Aug 1)")
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 0, 0));
        assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2026, 8, 31).atTime(java.time.LocalTime.MAX));
    }

    /**
     * P1 regression guard: when current week extends past month end, query window
     * must include the out-of-month days going forward.
     * Example: today = 2026-05-30 (Saturday) → week = May 25 ~ May 31 (within month).
     * Example: today = 2026-06-30 (Tuesday) → week = Jun 29 ~ Jul 5, query end must
     * cover Jul 5, not just Jun 30.
     */
    @Test
    void crossMonthWeekWindowMustExpandQueryRangeForward() {
        var today = LocalDate.of(2026, 6, 30); // Tuesday
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findRegistrationDeadlinesBetween(startCap.capture(), endCap.capture());

        assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(endCap.getValue())
                .as("query end should cover the week's Sunday (Jul 5) which is after monthEnd (Jun 30)")
                .isEqualTo(LocalDate.of(2026, 7, 5).atTime(java.time.LocalTime.MAX));
    }

    /**
     * FMEA guard: when allowedTenderIds exceeds MAX_TENDER_IDS_FOR_IN_CLAUSE (500),
     * the list must be truncated and the repository must receive a safe-sized sublist.
     */
    @Test
    void tenderIdsExceedingInClauseLimitMustBeTruncated() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L));

        List<Long> hugeTenderIds = java.util.stream.LongStream.rangeClosed(1, 600).boxed().toList();
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L))).thenReturn(hugeTenderIds);
        when(tenderRepository.findRegistrationDeadlinesByTenderIds(any(), any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesByTenderIds(any(), any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L)), any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tenderRepository).findRegistrationDeadlinesByTenderIds(captor.capture(), any(), any());
        verify(tenderRepository).findBidOpeningTimesByTenderIds(captor.capture(), any(), any());

        for (List<Long> captured : captor.getAllValues()) {
            assertThat(captured.size())
                    .as("tender IDs passed to repo must not exceed 500")
                    .isLessThanOrEqualTo(500);
            assertThat(captured.get(0)).isEqualTo(1L);
            assertThat(captured.get(captured.size() - 1)).isEqualTo(500L);
        }
    }

    // ==================== CO-593: getDeadlineItems tests ====================

    /**
     * 全局管理角色（admin/bidAdmin/bid-TeamLeader/bid-SystemAdmin）应看到全量数据。
     * 验证三类条目的 name/targetType/date/targetId 正确映射。
     */
    @Test
    void globalAccessUserShouldSeeAllDeadlineItems() {
        var today = LocalDate.of(2026, 5, 17); // Sunday
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender regTender = Tender.builder()
                .id(10L).title("标讯A")
                .registrationDeadline(LocalDateTime.of(2026, 5, 17, 10, 0))
                .build();
        Tender openingTender = Tender.builder()
                .id(20L).title("标讯B")
                .bidOpeningTime(LocalDateTime.of(2026, 5, 18, 14, 0))
                .projectId(100L)
                .build();
        Fee depositFee = Fee.builder()
                .id(30L).projectId(100L)
                .feeDate(LocalDateTime.of(2026, 5, 19, 9, 0))
                .build();
        Project project = Project.builder().id(100L).name("项目X").build();

        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(regTender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(openingTender));
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of(depositFee));
        when(projectRepository.findAllById(any(Collection.class))).thenReturn(List.of(project));

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        // 报名截止 → 标讯名称 + targetType=tender
        assertThat(result.registrationDeadline()).hasSize(1);
        DeadlineItemDTO regItem = result.registrationDeadline().get(0);
        assertThat(regItem.id()).isEqualTo(10L);
        assertThat(regItem.name()).isEqualTo("标讯A");
        assertThat(regItem.date()).isEqualTo("2026-05-17");
        assertThat(regItem.targetId()).isEqualTo(10L);
        assertThat(regItem.targetType()).isEqualTo("tender");

        // 开标 → 标讯名称 + targetType=tender（CO-593: 开标跳标讯详情，不跳项目详情）
        assertThat(result.bidOpening()).hasSize(1);
        DeadlineItemDTO openingItem = result.bidOpening().get(0);
        assertThat(openingItem.id()).isEqualTo(20L);
        assertThat(openingItem.name()).isEqualTo("标讯B");
        assertThat(openingItem.date()).isEqualTo("2026-05-18");
        assertThat(openingItem.targetId()).isEqualTo(20L);
        assertThat(openingItem.targetType()).isEqualTo("tender");

        // 保证金截止 → 项目名称 + targetType=project
        assertThat(result.depositDeadline()).hasSize(1);
        DeadlineItemDTO depositItem = result.depositDeadline().get(0);
        assertThat(depositItem.id()).isEqualTo(30L);
        assertThat(depositItem.name()).isEqualTo("项目X");
        assertThat(depositItem.date()).isEqualTo("2026-05-19");
        assertThat(depositItem.targetId()).isEqualTo(100L);
        assertThat(depositItem.targetType()).isEqualTo("project");
    }

    /**
     * 非全局角色只能看到自己参与项目（allowedProjectIds）范围内的数据。
     * 必须走 ByTenderIds / ByProjectIds 查询路径，禁止全量查询（防越权）。
     */
    @Test
    void nonGlobalUserShouldSeeOnlyOwnProjectDeadlineItems() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L, 200L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(100L, 200L))).thenReturn(List.of(10L, 20L));

        Tender regTender = Tender.builder()
                .id(10L).title("自有标讯")
                .registrationDeadline(LocalDateTime.of(2026, 5, 17, 10, 0))
                .build();

        when(tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(eq(List.of(10L, 20L)), any(), any()))
                .thenReturn(List.of(regTender));
        when(tenderRepository.findTendersByBidOpeningTimeAndTenderIds(eq(List.of(10L, 20L)), any(), any()))
                .thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineAndProjectIds(eq(List.of(100L, 200L)), any(), any()))
                .thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        assertThat(result.registrationDeadline()).hasSize(1);
        assertThat(result.registrationDeadline().get(0).name()).isEqualTo("自有标讯");
        assertThat(result.bidOpening()).isEmpty();
        assertThat(result.depositDeadline()).isEmpty();

        // 关键：未调用全量查询方法（防止越权读取其他用户的数据）
        verify(tenderRepository, org.mockito.Mockito.never())
                .findTendersByRegistrationDeadlineBetween(any(), any());
        verify(tenderRepository, org.mockito.Mockito.never())
                .findTendersByBidOpeningTimeBetween(any(), any());
        verify(feeRepository, org.mockito.Mockito.never())
                .findFeesByDepositDeadlineBetween(any(), any());
    }

    /**
     * P0 防越权回归：非全局角色 + 无项目权限 → 必须返回空且不访问任何 repository。
     */
    @Test
    void nonGlobalUserWithEmptyProjectScopeMustGetEmptyItemsWithoutDataAccess() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        assertThat(result.registrationDeadline()).isEmpty();
        assertThat(result.bidOpening()).isEmpty();
        assertThat(result.depositDeadline()).isEmpty();
        // 关键：repositories 必须不被调用
        verifyNoInteractions(tenderRepository, feeRepository, projectRepository);
    }

    /**
     * CO-593: 开标条目即使 Tender.projectId 为 null（未关联项目）也必须展示，
     * 因为开标跳标讯详情（targetType=tender），不依赖 project。
     */
    @Test
    void openingTenderWithNullProjectIdShouldStillShow() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender openingWithProject = Tender.builder()
                .id(20L).title("标讯B").bidOpeningTime(LocalDateTime.of(2026, 5, 18, 14, 0)).projectId(100L).build();
        Tender openingWithoutProject = Tender.builder()
                .id(21L).title("标讯C").bidOpeningTime(LocalDateTime.of(2026, 5, 19, 14, 0)).projectId(null).build();

        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any()))
                .thenReturn(List.of(openingWithProject, openingWithoutProject));
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        // 两条开标条目都应展示（projectId=null 不再过滤）
        assertThat(result.bidOpening()).hasSize(2);
        // 都用标讯名称 + 标讯 ID + targetType=tender
        assertThat(result.bidOpening().get(0).name()).isEqualTo("标讯B");
        assertThat(result.bidOpening().get(0).targetId()).isEqualTo(20L);
        assertThat(result.bidOpening().get(0).targetType()).isEqualTo("tender");
        assertThat(result.bidOpening().get(1).name()).isEqualTo("标讯C");
        assertThat(result.bidOpening().get(1).targetId()).isEqualTo(21L);
        assertThat(result.bidOpening().get(1).targetType()).isEqualTo("tender");
        // 开标不再需要解析 Project.name
        verifyNoInteractions(projectRepository);
    }

    /**
     * 列表必须按日期升序排列（前端不做排序，依赖后端顺序）。
     */
    @Test
    void deadlineItemsMustBeSortedByDateAscending() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender t1 = Tender.builder().id(1L).title("晚")
                .registrationDeadline(LocalDateTime.of(2026, 5, 20, 10, 0)).build();
        Tender t2 = Tender.builder().id(2L).title("早")
                .registrationDeadline(LocalDateTime.of(2026, 5, 12, 10, 0)).build();
        Tender t3 = Tender.builder().id(3L).title("中")
                .registrationDeadline(LocalDateTime.of(2026, 5, 15, 10, 0)).build();

        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any()))
                .thenReturn(List.of(t1, t2, t3)); // 故意乱序
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.MONTH);

        assertThat(result.registrationDeadline()).hasSize(3);
        assertThat(result.registrationDeadline().get(0).name()).isEqualTo("早");
        assertThat(result.registrationDeadline().get(1).name()).isEqualTo("中");
        assertThat(result.registrationDeadline().get(2).name()).isEqualTo("晚");
    }

    // ==================== 防御性去重（dedupDeadlineItems）测试 ====================

    /**
     * 去重核心：Tender 表存在重复标讯（同标题+同日期）时，报名截止/开标条目必须合并为一条。
     * 覆盖生产事故：日历/截止时间出现重复项目，根因是 Tender 表重复记录。
     */
    @Test
    void duplicateTendersWithSameTitleAndDateMustBeDeduplicated() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        // 两条标题相同、报名截止日期相同的标讯（数据库重复记录）
        Tender dup1 = Tender.builder().id(1L).title("重复标讯")
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        Tender dup2 = Tender.builder().id(2L).title("重复标讯")
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any()))
                .thenReturn(List.of(dup1, dup2));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        // 去重后仅保留 1 条（保留首次出现的 dup1）
        assertThat(result.registrationDeadline()).hasSize(1);
        assertThat(result.registrationDeadline().get(0).id()).isEqualTo(1L);
        assertThat(result.registrationDeadline().get(0).name()).isEqualTo("重复标讯");
    }

    /**
     * 去重边界：标题相同但日期不同 → 不是重复，必须全部保留。
     * 防止去重键过宽导致不同批次标讯被误合并。
     */
    @Test
    void sameTitleDifferentDateMustNotBeDeduplicated() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender day1 = Tender.builder().id(1L).title("同名标讯")
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        Tender day2 = Tender.builder().id(2L).title("同名标讯")
                .registrationDeadline(LocalDateTime.of(2026, 5, 20, 10, 0)).build();
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any()))
                .thenReturn(List.of(day1, day2));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        assertThat(result.registrationDeadline()).hasSize(2);
    }

    /**
     * 去重 null 安全：标讯 title 为 null 时不得抛 NPE，条目仍应正常展示。
     */
    @Test
    void nullTitleTenderMustNotThrowDuringDedup() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender nullTitle = Tender.builder().id(1L).title(null)
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any()))
                .thenReturn(List.of(nullTitle));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        assertThat(result.registrationDeadline()).hasSize(1);
        assertThat(result.registrationDeadline().get(0).name()).isNull();
    }

    /**
     * 去重边界：两条 title = null 的标讯（同日期）应当被合并为一条，不抛异常。
     */
    @Test
    void duplicateNullTitleTendersMustBeDeduplicatedWithoutNpe() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Tender nullTitle1 = Tender.builder().id(1L).title(null)
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        Tender nullTitle2 = Tender.builder().id(2L).title(null)
                .registrationDeadline(LocalDateTime.of(2026, 5, 18, 10, 0)).build();
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any()))
                .thenReturn(List.of(nullTitle1, nullTitle2));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineItemsDTO result = service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        assertThat(result.registrationDeadline()).hasSize(1);
    }

    /**
     * period=TODAY 时，查询窗口必须收敛到当天 0:00 - 23:59:59.999999999。
     */
    @Test
    void resolveWindowTodayPeriodProducesCorrectBounds() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineItems(today, DeadlinePeriod.TODAY);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findTendersByRegistrationDeadlineBetween(startCap.capture(), endCap.capture());
        assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 17, 0, 0));
        assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2026, 5, 17).atTime(java.time.LocalTime.MAX));
    }

    /**
     * period=MONTH 时，查询窗口必须覆盖整月。
     */
    @Test
    void resolveWindowMonthPeriodProducesCorrectBounds() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineItems(today, DeadlinePeriod.MONTH);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findTendersByRegistrationDeadlineBetween(startCap.capture(), endCap.capture());
        assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
        assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2026, 5, 31).atTime(java.time.LocalTime.MAX));
    }

    /**
     * FMEA guard: 非全局角色 allowedTenderIds 超 500 时必须截断后传给 IN 查询。
     */
    @Test
    void deadlineItemsTenderIdsExceedingInClauseLimitMustBeTruncated() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L));

        List<Long> hugeTenderIds = java.util.stream.LongStream.rangeClosed(1, 600).boxed().toList();
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L))).thenReturn(hugeTenderIds);
        when(tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(any(), any(), any())).thenReturn(List.of());
        when(tenderRepository.findTendersByBidOpeningTimeAndTenderIds(any(), any(), any())).thenReturn(List.of());
        when(feeRepository.findFeesByDepositDeadlineAndProjectIds(eq(List.of(1L)), any(), any())).thenReturn(List.of());

        service.getDeadlineItems(today, DeadlinePeriod.WEEK);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tenderRepository).findTendersByRegistrationDeadlineAndTenderIds(captor.capture(), any(), any());
        verify(tenderRepository).findTendersByBidOpeningTimeAndTenderIds(captor.capture(), any(), any());

        for (List<Long> captured : captor.getAllValues()) {
            assertThat(captured.size())
                    .as("tender IDs passed to repo must not exceed 500")
                    .isLessThanOrEqualTo(500);
            assertThat(captured.get(0)).isEqualTo(1L);
            assertThat(captured.get(captured.size() - 1)).isEqualTo(500L);
        }
    }
}
