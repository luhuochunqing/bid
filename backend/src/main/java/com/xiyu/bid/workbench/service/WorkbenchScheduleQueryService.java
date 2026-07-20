// Input: CalendarService-filtered schedule events + Tender-derived bid opening / registration deadline events
// Output: Workbench schedule overview DTO
// Pos: Service/工作台聚合查询层
// 维护声明: 工作台日程不另建项目权限体系，项目可见性继承 CalendarService 的真实 API 单一路径过滤。
//           CO-594: 聚合 Tender.bidOpeningTime（开标时间，绿点）和 Tender.registrationDeadline（报名截止，红点）为日历事件。
//           权限口径与 CO-593 WorkbenchDeadlineQueryService 一致，使用 currentUserHasGlobalAccess() 覆盖投标管理员/组长。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.ScheduleOverviewDTO;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.service.CalendarService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkbenchScheduleQueryService {

    /** IN-clause 安全上限，与 WorkbenchDeadlineQueryService 对齐（MySQL 性能防护）。 */
    private static final int MAX_TENDER_IDS_FOR_IN_CLAUSE = 500;

    private final CalendarService calendarService;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional(readOnly = true)
    public ScheduleOverviewDTO getScheduleOverview(LocalDate start, LocalDate end, Long assigneeId) {
        List<CalendarEventDTO> events = new ArrayList<>(calendarService.getEventsByDateRange(start, end));
        events.addAll(buildTenderDerivedEvents(start, end));
        events.sort(Comparator.comparing(CalendarEventDTO::getEventDate));

        return ScheduleOverviewDTO.builder()
                .start(start)
                .end(end)
                .assigneeId(assigneeId)
                .total(events.size())
                .urgent(events.stream().filter(item -> Boolean.TRUE.equals(item.getIsUrgent())).count())
                .events(events)
                .build();
    }

    /**
     * CO-594: 聚合 Tender 表的开标时间和报名截止时间为日历事件。
     * <p>
     * 权限分支（与 CO-593 WorkbenchDeadlineQueryService 对齐）：
     * <ul>
     *   <li>全局访问角色（admin / bidAdmin / bid-TeamLeader / bid-SystemAdmin / ROLE_EXTERNAL_API）→ 全量查询</li>
     *   <li>非全局角色 → 按 allowedProjectIds 反查 tenderIds 过滤</li>
     *   <li>无可见项目的非全局角色 → 返回空列表</li>
     * </ul>
     * 无关联 Project 的 Tender 对非全局角色不展示（符合"只能看到和自己相关项目的数据"）。
     */
    private List<CalendarEventDTO> buildTenderDerivedEvents(LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        boolean hasGlobalAccess = projectAccessScopeService.currentUserHasGlobalAccess();

        List<Tender> openingTenders;
        List<Tender> deadlineTenders;

        if (hasGlobalAccess) {
            openingTenders = tenderRepository.findTendersByBidOpeningTimeBetween(startDateTime, endDateTime);
            deadlineTenders = tenderRepository.findTendersByRegistrationDeadlineBetween(startDateTime, endDateTime);
        } else {
            List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
            if (allowedProjectIds == null || allowedProjectIds.isEmpty()) {
                return List.of();
            }
            List<Long> tenderIds = projectRepository.findTenderIdsByProjectIds(allowedProjectIds);
            if (tenderIds == null || tenderIds.isEmpty()) {
                return List.of();
            }
            List<Long> safeTenderIds = tenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE
                    ? tenderIds.subList(0, MAX_TENDER_IDS_FOR_IN_CLAUSE)
                    : tenderIds;
            if (tenderIds.size() > MAX_TENDER_IDS_FOR_IN_CLAUSE) {
                log.warn("Tender ID count {} exceeds safe IN-clause limit {}, truncating to {}",
                        tenderIds.size(), MAX_TENDER_IDS_FOR_IN_CLAUSE, safeTenderIds.size());
            }
            openingTenders = tenderRepository.findTendersByBidOpeningTimeAndTenderIds(safeTenderIds, startDateTime, endDateTime);
            deadlineTenders = tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(safeTenderIds, startDateTime, endDateTime);
        }

        List<CalendarEventDTO> result = new ArrayList<>(openingTenders.size() + deadlineTenders.size());
        for (Tender t : openingTenders) {
            if (t.getBidOpeningTime() != null) {
                result.add(toTenderEventDto(t, t.getBidOpeningTime(), EventType.OPENING));
            }
        }
        for (Tender t : deadlineTenders) {
            if (t.getRegistrationDeadline() != null) {
                result.add(toTenderEventDto(t, t.getRegistrationDeadline(), EventType.DEADLINE));
            }
        }
        log.debug("Aggregated {} opening and {} deadline tender events for range {} to {}",
                openingTenders.size(), deadlineTenders.size(), start, end);
        return result;
    }

    private CalendarEventDTO toTenderEventDto(Tender tender, LocalDateTime dateTime, EventType type) {
        // description 不设置：前端按 eventType 渲染"开标时间"/"报名截止"，无需后端重复传死数据。
        return CalendarEventDTO.builder()
                .id(tender.getId())
                .eventDate(dateTime.toLocalDate())
                .eventType(type)
                .title(tender.getTitle())
                .projectId(tender.getProjectId())
                .isUrgent(false)
                .build();
    }
}
