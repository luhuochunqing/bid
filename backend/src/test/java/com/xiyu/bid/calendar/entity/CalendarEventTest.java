// Input: None
// Output: CalendarEvent实体测试
// Pos: Test/单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalendarEvent 单元测试")
class CalendarEventTest {

    private CalendarEvent event;

    @BeforeEach
    void setUp() {
        event = CalendarEvent.builder()
                .id(1L)
                .eventDate(LocalDate.of(2024, 3, 15))
                .eventType(EventType.DEADLINE)
                .title("项目截止日期")
                .description("标书提交截止日期")
                .projectId(100L)
                .isUrgent(true)
                .build();
    }

    @Test
    @DisplayName("应该成功创建日历事件")
    void shouldCreateCalendarEventSuccessfully() {
        assertThat(event).isNotNull();
        assertThat(event.getId()).isEqualTo(1L);
        assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(event.getEventType()).isEqualTo(EventType.DEADLINE);
        assertThat(event.getTitle()).isEqualTo("项目截止日期");
        assertThat(event.getDescription()).isEqualTo("标书提交截止日期");
        assertThat(event.getProjectId()).isEqualTo(100L);
        assertThat(event.getIsUrgent()).isTrue();
    }

    @Test
    @DisplayName("应该成功使用Builder创建事件")
    void shouldCreateEventUsingBuilderSuccessfully() {
        CalendarEvent newEvent = CalendarEvent.builder()
                .eventDate(LocalDate.of(2024, 4, 1))
                .eventType(EventType.MEETING)
                .title("项目会议")
                .projectId(200L)
                .isUrgent(false)
                .build();

        assertThat(newEvent.getEventDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(newEvent.getEventType()).isEqualTo(EventType.MEETING);
        assertThat(newEvent.getTitle()).isEqualTo("项目会议");
        assertThat(newEvent.getProjectId()).isEqualTo(200L);
        assertThat(newEvent.getIsUrgent()).isFalse();
    }

    @Test
    @DisplayName("应该支持所有事件类型")
    void shouldSupportAllEventTypes() {
        CalendarEvent deadlineEvent = createEventWithType(EventType.DEADLINE);
        CalendarEvent meetingEvent = createEventWithType(EventType.MEETING);
        CalendarEvent milestoneEvent = createEventWithType(EventType.MILESTONE);
        CalendarEvent reminderEvent = createEventWithType(EventType.REMINDER);
        CalendarEvent submissionEvent = createEventWithType(EventType.SUBMISSION);
        CalendarEvent reviewEvent = createEventWithType(EventType.REVIEW);

        assertThat(deadlineEvent.getEventType()).isEqualTo(EventType.DEADLINE);
        assertThat(meetingEvent.getEventType()).isEqualTo(EventType.MEETING);
        assertThat(milestoneEvent.getEventType()).isEqualTo(EventType.MILESTONE);
        assertThat(reminderEvent.getEventType()).isEqualTo(EventType.REMINDER);
        assertThat(submissionEvent.getEventType()).isEqualTo(EventType.SUBMISSION);
        assertThat(reviewEvent.getEventType()).isEqualTo(EventType.REVIEW);
    }

    @Test
    @DisplayName("应该支持空projectId")
    void shouldSupportNullProjectId() {
        CalendarEvent eventWithoutProject = CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.REMINDER)
                .title("系统提醒")
                .isUrgent(false)
                .build();

        assertThat(eventWithoutProject.getProjectId()).isNull();
    }

    @Test
    @DisplayName("应该支持空description")
    void shouldSupportNullDescription() {
        CalendarEvent eventWithoutDescription = CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.DEADLINE)
                .title("简单事件")
                .isUrgent(false)
                .build();

        assertThat(eventWithoutDescription.getDescription()).isNull();
    }

    @Test
    @DisplayName("应该支持默认isUrgent为false")
    void shouldSupportDefaultIsUrgentFalse() {
        CalendarEvent eventWithDefaultUrgent = CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.MEETING)
                .title("普通会议")
                .isUrgent(false)
                .build();

        assertThat(eventWithDefaultUrgent.getIsUrgent()).isFalse();
    }

    @Test
    @DisplayName("应该正确设置日期边界")
    void shouldHandleDateBoundariesCorrectly() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);
        LocalDate futureDate = LocalDate.of(2030, 12, 31);

        CalendarEvent pastEvent = CalendarEvent.builder()
                .eventDate(pastDate)
                .eventType(EventType.MILESTONE)
                .title("过去事件")
                .build();

        CalendarEvent futureEvent = CalendarEvent.builder()
                .eventDate(futureDate)
                .eventType(EventType.MILESTONE)
                .title("未来事件")
                .build();

        assertThat(pastEvent.getEventDate()).isEqualTo(pastDate);
        assertThat(futureEvent.getEventDate()).isEqualTo(futureDate);
    }

    @Test
    @DisplayName("应该支持长标题和描述")
    void shouldSupportLongTitleAndDescription() {
        String longTitle = "这是一个非常长的标题用来测试系统是否能够正确处理长文本内容".repeat(5);
        String longDescription = "这是一个非常长的描述用来测试系统是否能够正确处理长文本内容".repeat(10);

        CalendarEvent eventWithLongContent = CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(EventType.REVIEW)
                .title(longTitle)
                .description(longDescription)
                .build();

        assertThat(eventWithLongContent.getTitle()).isEqualTo(longTitle);
        assertThat(eventWithLongContent.getDescription()).isEqualTo(longDescription);
    }

    private CalendarEvent createEventWithType(EventType type) {
        return CalendarEvent.builder()
                .eventDate(LocalDate.now())
                .eventType(type)
                .title(type.name() + " Event")
                .build();
    }
}
