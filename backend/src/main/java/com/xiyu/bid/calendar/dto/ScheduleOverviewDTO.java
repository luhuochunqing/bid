package com.xiyu.bid.calendar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleOverviewDTO {
    private LocalDate start;
    private LocalDate end;
    private Long assigneeId;
    private long total;
    private long urgent;
    private List<CalendarEventDTO> events;
}
