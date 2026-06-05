// Input: Repository, DTOs, current-user project access scope
// Output: CalendarEvent CRUD/query service with project-linked visibility filtering
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.service;

import com.xiyu.bid.access.core.ProjectLinkedRecordVisibilityPolicy;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.repository.CalendarEventRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日历事件服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {

    private final CalendarEventRepository repository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 创建日历事件
     * @param request 创建请求
     * @return 创建的事件DTO
     */
    @Auditable(action = "CREATE", entityType = "CalendarEvent", description = "Created calendar event")
    @Transactional
    public CalendarEventDTO createEvent(CalendarEventCreateRequest request) {
        validateCreateRequest(request);
        assertProjectAccessIfLinked(request.getProjectId());

        CalendarEvent event = CalendarEvent.builder()
                .eventDate(request.getEventDate())
                .eventType(request.getEventType())
                .title(request.getTitle())
                .description(request.getDescription())
                .projectId(request.getProjectId())
                .isUrgent(request.getIsUrgent() != null ? request.getIsUrgent() : false)
                .build();

        CalendarEvent savedEvent = repository.save(event);
        log.info("Created calendar event: {}", savedEvent.getId());

        return convertToDTO(savedEvent);
    }

    /**
     * 更新日历事件
     * @param id 事件ID
     * @param request 更新请求
     * @return 更新后的事件DTO
     */
    @Auditable(action = "UPDATE", entityType = "CalendarEvent", description = "Updated calendar event")
    @Transactional
    public CalendarEventDTO updateEvent(Long id, CalendarEventUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update request cannot be null");
        }

        CalendarEvent event = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("CalendarEvent not found with id: " + id));
        assertProjectAccessIfLinked(event.getProjectId());
        if (request.getProjectId() != null) {
            assertProjectAccessIfLinked(request.getProjectId());
        }

        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getEventType() != null) {
            event.setEventType(request.getEventType());
        }
        if (request.getTitle() != null) {
            if (request.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be empty");
            }
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getProjectId() != null) {
            event.setProjectId(request.getProjectId());
        }
        if (request.getIsUrgent() != null) {
            event.setIsUrgent(request.getIsUrgent());
        }

        CalendarEvent updatedEvent = repository.save(event);
        log.info("Updated calendar event: {}", updatedEvent.getId());

        return convertToDTO(updatedEvent);
    }

    /**
     * 删除日历事件
     * @param id 事件ID
     */
    @Auditable(action = "DELETE", entityType = "CalendarEvent", description = "Deleted calendar event")
    @Transactional
    public void deleteEvent(Long id) {
        CalendarEvent event = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("CalendarEvent not found with id: " + id));
        assertProjectAccessIfLinked(event.getProjectId());

        repository.deleteById(id);
        log.info("Deleted calendar event: {}", id);
    }

    /**
     * 获取指定月份的事件
     * @param year 年份
     * @param month 月份 (1-12)
     * @return 事件DTO列表
     */
    public List<CalendarEventDTO> getEventsByMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }
        if (year < 0) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<CalendarEvent> events = repository.findByEventDateBetween(start, end);
        log.info("Found {} events for {}/{}", events.size(), year, month);

        return visibleDtos(events);
    }

    public List<CalendarEventDTO> getEventsByDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end are required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        return visibleDtos(repository.findByEventDateBetween(start, end));
    }

    /**
     * 根据项目ID获取事件
     * @param projectId 项目ID
     * @return 事件DTO列表
     */
    public List<CalendarEventDTO> getEventsByProject(Long projectId) {
        assertProjectAccessIfLinked(projectId);
        List<CalendarEvent> events = repository.findByProjectId(projectId);
        log.info("Found {} events for project {}", events.size(), projectId);

        return events.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有紧急事件
     * @return 事件DTO列表
     */
    public List<CalendarEventDTO> getUrgentEvents() {
        List<CalendarEvent> events = repository.findByIsUrgentTrue();
        log.info("Found {} urgent events", events.size());

        return visibleDtos(events);
    }

    /**
     * 获取即将到来的事件
     * @return 事件DTO列表
     */
    public List<CalendarEventDTO> getUpcomingEvents() {
        LocalDate today = LocalDate.now();
        List<CalendarEvent> events = repository.findUpcomingEvents(today);
        log.info("Found {} upcoming events", events.size());

        return visibleDtos(events);
    }

    private void assertProjectAccessIfLinked(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }

    private List<CalendarEventDTO> visibleDtos(List<CalendarEvent> events) {
        boolean admin = projectAccessScopeService.currentUserHasAdminAccess();
        List<Long> allowedProjectIds = admin ? List.of() : projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        return events.stream()
                .filter(event -> ProjectLinkedRecordVisibilityPolicy.visible(admin, allowedProjectIds, event.getProjectId()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 验证创建请求
     */
    private void validateCreateRequest(CalendarEventCreateRequest request) {
        if (request.getEventDate() == null) {
            throw new IllegalArgumentException("Event date is required");
        }
        if (request.getEventType() == null) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
    }

    /**
     * 转换为DTO
     */
    private CalendarEventDTO convertToDTO(CalendarEvent event) {
        return CalendarEventDTO.builder()
                .id(event.getId())
                .eventDate(event.getEventDate())
                .eventType(event.getEventType())
                .title(event.getTitle())
                .description(event.getDescription())
                .projectId(event.getProjectId())
                .isUrgent(event.getIsUrgent())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
