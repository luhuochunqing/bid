package com.xiyu.bid.workbench.service;

import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workbench.domain.DeadlinePeriod;
import com.xiyu.bid.workbench.domain.WorkbenchDeadlinePolicy;
import com.xiyu.bid.workbench.dto.DeadlineItemDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineItemsDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkbenchDeadlineQueryService {

    private static final int MAX_TENDER_IDS_FOR_IN_CLAUSE = 500;
    private static final int QUERY_WINDOW_WARN_THRESHOLD_DAYS = 45;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TenderRepository tenderRepository;
    private final FeeRepository feeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional(readOnly = true)
    public WorkbenchDeadlineStatsDTO getDeadlineStats(LocalDate today) {
        // P0-1 fix: Empty allowedProjectIds is ambiguous (admin OR non-admin-with-zero-access).
        // We MUST consult currentUserHasAdminAccess() to distinguish. Mirrors FeeService /
        // DashboardAnalyticsQueryService pattern in this codebase. See PR #285 review.
        boolean isAdmin = projectAccessScopeService.currentUserHasAdminAccess();
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();

        if (!isAdmin && allowedProjectIds.isEmpty()) {
            log.debug("Workbench deadline stats: non-admin user has no project access; returning zero stats");
            return zeroStats();
        }

        // P1 fix: query window must cover the union of today/week/month bounds.
        // Otherwise weekly counts under-report when current week crosses a month boundary
        // (e.g. today=Sat 2026-08-01 → week is Jul 27 ~ Aug 2, but monthStart is Aug 1).
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);
        LocalDateTime queryStart = earliest(bounds.todayStart(), bounds.weekStart(), bounds.monthStart());
        LocalDateTime queryEnd = latest(bounds.todayEnd(), bounds.weekEnd(), bounds.monthEnd());

        List<LocalDateTime> regDeadlines;
        List<LocalDateTime> openingTimes;
        List<LocalDateTime> depositDeadlines;

        if (isAdmin) {
            regDeadlines = tenderRepository.findRegistrationDeadlinesBetween(queryStart, queryEnd);
            openingTimes = tenderRepository.findBidOpeningTimesBetween(queryStart, queryEnd);
            depositDeadlines = feeRepository.findDepositDeadlinesBetween(queryStart, queryEnd);
        } else {
            List<Long> allowedTenderIds = projectRepository.findTenderIdsByProjectIds(allowedProjectIds);

            if (!allowedTenderIds.isEmpty()) {
                List<Long> safeTenderIds = allowedTenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE
                        ? allowedTenderIds.subList(0, MAX_TENDER_IDS_FOR_IN_CLAUSE)
                        : allowedTenderIds;
                if (allowedTenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE) {
                    log.warn("Tender ID count {} exceeds safe IN-clause limit {}, truncating to {}",
                            allowedTenderIds.size(), MAX_TENDER_IDS_FOR_IN_CLAUSE, safeTenderIds.size());
                }
                regDeadlines = tenderRepository.findRegistrationDeadlinesByTenderIds(safeTenderIds, queryStart, queryEnd);
                openingTimes = tenderRepository.findBidOpeningTimesByTenderIds(safeTenderIds, queryStart, queryEnd);
            } else {
                regDeadlines = List.of();
                openingTimes = List.of();
            }
            depositDeadlines = feeRepository.findDepositDeadlinesByProjectIds(allowedProjectIds, queryStart, queryEnd);
        }

        long queryWindowDays = java.time.temporal.ChronoUnit.DAYS.between(queryStart.toLocalDate(), queryEnd.toLocalDate());
        if (queryWindowDays > QUERY_WINDOW_WARN_THRESHOLD_DAYS) {
            log.warn("Deadline query window spans {} days (threshold={}), admin={}. "
                    + "Consider adding a database index on deadline columns or tightening the window logic.",
                    queryWindowDays, QUERY_WINDOW_WARN_THRESHOLD_DAYS, isAdmin);
        }

        log.debug("Workbench deadline stats: reg={}, opening={}, deposit={}",
                regDeadlines.size(), openingTimes.size(), depositDeadlines.size());

        var stats = WorkbenchDeadlinePolicy.buildDeadlineStats(today, regDeadlines, openingTimes, depositDeadlines);

        return new WorkbenchDeadlineStatsDTO(
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                        stats.registrationDeadline().counts().todayCount(),
                        stats.registrationDeadline().counts().weekCount(),
                        stats.registrationDeadline().counts().monthCount()
                ),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                        stats.bidOpening().counts().todayCount(),
                        stats.bidOpening().counts().weekCount(),
                        stats.bidOpening().counts().monthCount()
                ),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                        stats.depositDeadline().counts().todayCount(),
                        stats.depositDeadline().counts().weekCount(),
                        stats.depositDeadline().counts().monthCount()
                )
        );
    }

    // ==================== CO-593: Deadline items (list data) ====================

    /**
     * 查询截止时间模块列表条目（CO-593）。
     *
     * <p>按 period 时间窗返回三类条目（报名截止/开标/保证金截止），每类按日期升序。
     * 全局管理角色（admin/bidAdmin/bid-TeamLeader/bid-SystemAdmin）看全部，其他角色只看自己参与的项目数据。</p>
     */
    @Transactional(readOnly = true)
    public WorkbenchDeadlineItemsDTO getDeadlineItems(LocalDate today, DeadlinePeriod period) {
        boolean hasGlobalAccess = projectAccessScopeService.currentUserHasGlobalAccess();
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();

        if (!hasGlobalAccess && allowedProjectIds.isEmpty()) {
            log.debug("Workbench deadline items: non-global user has no project access; returning empty");
            return WorkbenchDeadlineItemsDTO.empty();
        }

        WorkbenchDeadlinePolicy.Window window = WorkbenchDeadlinePolicy.resolveWindow(today, period);
        LocalDateTime queryStart = window.start();
        LocalDateTime queryEnd = window.end();

        List<Tender> regTenders;
        List<Tender> openingTenders;
        List<Fee> depositFees;

        if (hasGlobalAccess) {
            regTenders = tenderRepository.findTendersByRegistrationDeadlineBetween(queryStart, queryEnd);
            openingTenders = tenderRepository.findTendersByBidOpeningTimeBetween(queryStart, queryEnd);
            depositFees = feeRepository.findFeesByDepositDeadlineBetween(queryStart, queryEnd);
        } else {
            List<Long> allowedTenderIds = projectRepository.findTenderIdsByProjectIds(allowedProjectIds);
            List<Long> safeTenderIds = allowedTenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE
                    ? allowedTenderIds.subList(0, MAX_TENDER_IDS_FOR_IN_CLAUSE)
                    : allowedTenderIds;
            if (allowedTenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE) {
                log.warn("Tender ID count {} exceeds safe IN-clause limit {}, truncating to {}",
                        allowedTenderIds.size(), MAX_TENDER_IDS_FOR_IN_CLAUSE, safeTenderIds.size());
            }
            if (safeTenderIds.isEmpty()) {
                regTenders = List.of();
                openingTenders = List.of();
            } else {
                regTenders = tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(safeTenderIds, queryStart, queryEnd);
                openingTenders = tenderRepository.findTendersByBidOpeningTimeAndTenderIds(safeTenderIds, queryStart, queryEnd);
            }
            depositFees = feeRepository.findFeesByDepositDeadlineAndProjectIds(allowedProjectIds, queryStart, queryEnd);
        }

        log.debug("Workbench deadline items: reg={}, opening={}, deposit={}",
                regTenders.size(), openingTenders.size(), depositFees.size());

        // 批量解析 Project.name（保证金显示项目名；开标/报名截止显示标讯名）
        Set<Long> projectIdsToResolve = new LinkedHashSet<>();
        for (Fee f : depositFees) {
            if (f.getProjectId() != null) {
                projectIdsToResolve.add(f.getProjectId());
            }
        }
        Map<Long, String> projectNameById = resolveProjectNames(projectIdsToResolve);

        // 报名截止 → 标讯名称 → 标讯详情
        List<DeadlineItemDTO> regItems = regTenders.stream()
                .filter(t -> t.getRegistrationDeadline() != null)
                .sorted(Comparator.comparing(Tender::getRegistrationDeadline))
                .map(t -> new DeadlineItemDTO(
                        t.getId(), t.getTitle(),
                        DATE_FORMATTER.format(t.getRegistrationDeadline()),
                        t.getId(), "tender"))
                .toList();

        // 开标 → 标讯名称 → 标讯详情
        List<DeadlineItemDTO> openingItems = openingTenders.stream()
                .filter(t -> t.getBidOpeningTime() != null)
                .sorted(Comparator.comparing(Tender::getBidOpeningTime))
                .map(t -> new DeadlineItemDTO(
                        t.getId(), t.getTitle(),
                        DATE_FORMATTER.format(t.getBidOpeningTime()),
                        t.getId(), "tender"))
                .toList();

        // 保证金截止 → 项目名称 → 项目详情
        List<DeadlineItemDTO> depositItems = depositFees.stream()
                .filter(f -> f.getFeeDate() != null && f.getProjectId() != null)
                .sorted(Comparator.comparing(Fee::getFeeDate))
                .map(f -> new DeadlineItemDTO(
                        f.getId(), projectNameById.getOrDefault(f.getProjectId(), "未知项目"),
                        DATE_FORMATTER.format(f.getFeeDate()),
                        f.getProjectId(), "project"))
                .toList();

        return new WorkbenchDeadlineItemsDTO(regItems, openingItems, depositItems);
    }

    private Map<Long, String> resolveProjectNames(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        List<Project> projects = projectRepository.findAllById(projectIds);
        Map<Long, String> map = new HashMap<>();
        for (Project p : projects) {
            map.put(p.getId(), p.getName());
        }
        return map;
    }

    private static WorkbenchDeadlineStatsDTO zeroStats() {
        var zero = new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(0L, 0L, 0L);
        return new WorkbenchDeadlineStatsDTO(zero, zero, zero);
    }

    private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        LocalDateTime ab = a.isBefore(b) ? a : b;
        return ab.isBefore(c) ? ab : c;
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        LocalDateTime ab = a.isAfter(b) ? a : b;
        return ab.isAfter(c) ? ab : c;
    }
}
