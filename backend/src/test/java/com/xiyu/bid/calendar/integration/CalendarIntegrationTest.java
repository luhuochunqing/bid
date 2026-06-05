// Input: CalendarController, Service, Repository
// Output: 端到端集成测试
// Pos: Test/集成测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Calendar 集成测试")
class CalendarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CalendarEventRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private CalendarEvent testEvent;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        testEvent = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 15))
                .eventType(EventType.DEADLINE)
                .title("项目截止日期")
                .description("标书提交截止日期")
                .projectId(100L)
                .isUrgent(true)
                .build();
        testEvent = repository.save(testEvent);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该成功完成创建-读取-更新-删除流程")
    void shouldSuccessfullyCompleteCRUDFlow() throws Exception {
        // 1. Create
        CalendarEventCreateRequest createRequest = CalendarEventCreateRequest.builder()
                .eventDate(LocalDate.of(2024, 4, 1))
                .eventType(EventType.MEETING)
                .title("集成测试会议")
                .description("集成测试描述")
                .projectId(200L)
                .isUrgent(false)
                .build();

        mockMvc.perform(post("/api/calendar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("集成测试会议"));

        // 2. Read by month
        mockMvc.perform(get("/api/calendar/month/2024/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("集成测试会议"));

        // 3. Update
        CalendarEventUpdateRequest updateRequest = CalendarEventUpdateRequest.builder()
                .title("更新后的会议标题")
                .isUrgent(true)
                .build();

        mockMvc.perform(put("/api/calendar/" + testEvent.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("更新后的会议标题"))
                .andExpect(jsonPath("$.data.isUrgent").value(true));

        // 4. Delete
        mockMvc.perform(delete("/api/calendar/" + testEvent.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify deletion
        assertThat(repository.findById(testEvent.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该成功根据项目ID获取事件")
    void shouldSuccessfullyGetEventsByProject() throws Exception {
        mockMvc.perform(get("/api/calendar/project/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].projectId").value(100))
                .andExpect(jsonPath("$.data[0].title").value("项目截止日期"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该成功获取紧急事件")
    void shouldSuccessfullyGetUrgentEvents() throws Exception {
        mockMvc.perform(get("/api/calendar/urgent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].isUrgent").value(true));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该成功获取指定月份的事件")
    void shouldSuccessfullyGetEventsByMonth() throws Exception {
        mockMvc.perform(get("/api/calendar/month/2024/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("项目截止日期"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该正确处理多个事件")
    void shouldHandleMultipleEvents() throws Exception {
        // Create multiple events
        CalendarEvent event2 = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 20))
                .eventType(EventType.MEETING)
                .title("项目会议")
                .projectId(100L)
                .build();
        repository.save(event2);

        CalendarEvent event3 = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 25))
                .eventType(EventType.MILESTONE)
                .title("里程碑")
                .projectId(100L)
                .isUrgent(true)
                .build();
        repository.save(event3);

        // Get all events for the month
        mockMvc.perform(get("/api/calendar/month/2024/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该正确处理空项目ID")
    void shouldHandleNullProjectId() throws Exception {
        // Create event without project
        CalendarEvent eventWithoutProject = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 20))
                .eventType(EventType.REMINDER)
                .title("系统提醒")
                .build();
        repository.save(eventWithoutProject);

        // Get events by project should return empty
        mockMvc.perform(get("/api/calendar/project/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("应该正确处理日期边界")
    void shouldHandleDateBoundaries() throws Exception {
        // Create events at month boundaries
        CalendarEvent firstDayEvent = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 1))
                .eventType(EventType.DEADLINE)
                .title("月初截止")
                .build();
        repository.save(firstDayEvent);

        CalendarEvent lastDayEvent = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 3, 31))
                .eventType(EventType.MILESTONE)
                .title("月末里程碑")
                .build();
        repository.save(lastDayEvent);

        // Get all events for March
        mockMvc.perform(get("/api/calendar/month/2024/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3)); // Including testEvent
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("管理员应该能够创建所有类型的事件")
    void adminShouldCreateAllEventTypes() throws Exception {
        EventType[] types = EventType.values();

        for (EventType type : types) {
            CalendarEventCreateRequest request = CalendarEventCreateRequest.builder()
                    .eventDate(LocalDate.now())
                    .eventType(type)
                    .title(type.name() + " Event")
                    .build();

            mockMvc.perform(post("/api/calendar")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.eventType").value(type.name()));
        }
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("普通员工不应该能够创建事件")
    void staffShouldNotCreateEvents() throws Exception {
        CalendarEventCreateRequest request = CalendarEventCreateRequest.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.DEADLINE)
                .title("测试事件")
                .build();

        mockMvc.perform(post("/api/calendar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
