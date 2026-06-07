package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.repository.CalendarEventRepository;
import com.xiyu.bid.calendar.service.CalendarService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class AbstractCalendarServiceTest {

    @Mock
    protected CalendarEventRepository repository;
    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    protected CalendarService calendarService;
    protected CalendarEvent testEvent;
    protected CalendarEventCreateRequest createRequest;
    protected CalendarEventUpdateRequest updateRequest;

    @BeforeEach
    void setUpCalendarServiceFixture() {
        lenient().when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        calendarService = new CalendarService(repository, projectAccessScopeService);

        testEvent = CalendarEvent.builder()
                .id(1L)
                .eventDate(LocalDate.of(2024, 3, 15))
                .eventType(EventType.DEADLINE)
                .title("项目截止日期")
                .description("标书提交截止日期")
                .projectId(100L)
                .isUrgent(true)
                .build();

        createRequest = CalendarEventCreateRequest.builder()
                .eventDate(LocalDate.of(2024, 3, 15))
                .eventType(EventType.DEADLINE)
                .title("项目截止日期")
                .description("标书提交截止日期")
                .projectId(100L)
                .isUrgent(true)
                .build();

        updateRequest = CalendarEventUpdateRequest.builder()
                .title("更新后的标题")
                .isUrgent(false)
                .build();
    }
}
