// Input: CalendarService-filtered schedule events
// Output: Workbench schedule overview DTO
// Pos: Service/工作台聚合查询层
// 维护声明: 工作台日程不另建项目权限体系，项目可见性继承 CalendarService 的真实 API 单一路径过滤。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.ScheduleOverviewDTO;
import com.xiyu.bid.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkbenchScheduleQueryService {

    private final CalendarService calendarService;

    public ScheduleOverviewDTO getScheduleOverview(LocalDate start, LocalDate end, Long assigneeId) {
        List<CalendarEventDTO> events = calendarService.getEventsByDateRange(start, end);
        events = events.stream()
                .sorted(Comparator.comparing(CalendarEventDTO::getEventDate))
                .toList();

        return ScheduleOverviewDTO.builder()
                .start(start)
                .end(end)
                .assigneeId(assigneeId)
                .total(events.size())
                .urgent(events.stream().filter(item -> Boolean.TRUE.equals(item.getIsUrgent())).count())
                .events(events)
                .build();
    }
}
