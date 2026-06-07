package com.xiyu.bid.calendar.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.calendar.controller.CalendarController;
import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
abstract class AbstractCalendarControllerTest {

    @Mock
    protected CalendarService calendarService;

    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper;
    protected CalendarEventDTO testEventDTO;
    protected CalendarEventCreateRequest createRequest;
    protected CalendarEventUpdateRequest updateRequest;

    @BeforeEach
    void setUpCalendarControllerFixture() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        CalendarController calendarController = new CalendarController(calendarService);
        mockMvc = MockMvcBuilders.standaloneSetup(calendarController)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        testEventDTO = CalendarEventDTO.builder()
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
