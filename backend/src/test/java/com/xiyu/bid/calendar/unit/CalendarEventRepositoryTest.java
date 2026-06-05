// Input: CalendarEventRepository接口
// Output: Repository测试验证
// Pos: Test/单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CalendarEventRepository 单元测试")
class CalendarEventRepositoryTest {

    @Autowired
    private CalendarEventRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private CalendarEvent testEvent1;
    private CalendarEvent testEvent2;
    private CalendarEvent testEvent3;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        LocalDate now = LocalDate.now();
        testEvent1 = CalendarEvent.builder()
                .eventDate(now)
                .eventType(EventType.DEADLINE)
                .title("项目截止日期")
                .description("标书提交截止日期")
                .projectId(100L)
                .isUrgent(true)
                .build();

        testEvent2 = CalendarEvent.builder()
                .eventDate(now.plusDays(7))
                .eventType(EventType.MEETING)
                .title("项目会议")
                .description("项目进度讨论")
                .projectId(100L)
                .isUrgent(false)
                .build();

        testEvent3 = CalendarEvent.builder()
                .eventDate(now.plusDays(14))
                .eventType(EventType.MILESTONE)
                .title("里程碑完成")
                .description("第一阶段完成")
                .projectId(200L)
                .isUrgent(true)
                .build();

        testEvent1 = entityManager.persistAndFlush(testEvent1);
        testEvent2 = entityManager.persistAndFlush(testEvent2);
        testEvent3 = entityManager.persistAndFlush(testEvent3);
    }

    @Test
    @DisplayName("应该成功查找日期范围内的事件")
    void shouldFindEventsByDateRangeSuccessfully() {
        // Given
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(10);

        // When
        List<CalendarEvent> events = repository.findByEventDateBetween(start, end);

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting("title")
                .containsExactlyInAnyOrder("项目截止日期", "项目会议");
    }

    @Test
    @DisplayName("应该成功查找空日期范围内的事件")
    void shouldReturnEmptyListForInvalidDateRange() {
        // Given
        LocalDate start = LocalDate.now().plusDays(100);
        LocalDate end = LocalDate.now().plusDays(200);

        // When
        List<CalendarEvent> events = repository.findByEventDateBetween(start, end);

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应该成功根据项目ID查找事件")
    void shouldFindEventsByProjectIdSuccessfully() {
        // When
        List<CalendarEvent> events = repository.findByProjectId(100L);

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting("title")
                .containsExactlyInAnyOrder("项目截止日期", "项目会议");
    }

    @Test
    @DisplayName("应该成功返回空列表当项目ID不存在时")
    void shouldReturnEmptyListWhenProjectIdNotFound() {
        // When
        List<CalendarEvent> events = repository.findByProjectId(999L);

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应该成功根据事件类型查找")
    void shouldFindEventsByEventTypeSuccessfully() {
        // When
        List<CalendarEvent> events = repository.findByEventType(EventType.DEADLINE);

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("项目截止日期");
    }

    @Test
    @DisplayName("应该成功返回空列表当事件类型不存在时")
    void shouldReturnEmptyListWhenEventTypeNotFound() {
        // When
        List<CalendarEvent> events = repository.findByEventType(EventType.REVIEW);

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应该成功查找所有紧急事件")
    void shouldFindUrgentEventsSuccessfully() {
        // When
        List<CalendarEvent> events = repository.findByIsUrgentTrue();

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting("title")
                .containsExactlyInAnyOrder("项目截止日期", "里程碑完成");
        assertThat(events).allMatch(event -> event.getIsUrgent());
    }

    @Test
    @DisplayName("应该成功返回空列表当没有紧急事件时")
    void shouldReturnEmptyListWhenNoUrgentEvents() {
        // Given
        repository.deleteAll();

        // When
        List<CalendarEvent> events = repository.findByIsUrgentTrue();

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应该成功查找即将到来的事件")
    void shouldFindUpcomingEventsSuccessfully() {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(5);

        // When
        List<CalendarEvent> events = repository.findUpcomingEvents(startDate);

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting("title")
                .containsExactlyInAnyOrder("项目会议", "里程碑完成");
    }

    @Test
    @DisplayName("应该成功返回空列表当没有即将到来的事件时")
    void shouldReturnEmptyListWhenNoUpcomingEvents() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(100);

        // When
        List<CalendarEvent> events = repository.findUpcomingEvents(futureDate);

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理边界条件")
    void shouldHandleBoundaryConditionsCorrectly() {
        // Given
        LocalDate today = LocalDate.now();

        // When
        List<CalendarEvent> events = repository.findByEventDateBetween(today, today);

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("项目截止日期");
    }

    @Test
    @DisplayName("应该正确处理null projectId")
    void shouldHandleNullProjectIdCorrectly() {
        // Given
        CalendarEvent eventWithoutProject = CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.REMINDER)
                .title("系统提醒")
                .isUrgent(false)
                .build();
        entityManager.persistAndFlush(eventWithoutProject);

        // When
        List<CalendarEvent> events = repository.findByProjectId(null);

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("系统提醒");
    }
}
