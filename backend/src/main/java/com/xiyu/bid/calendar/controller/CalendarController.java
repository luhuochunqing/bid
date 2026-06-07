// Input: CalendarService, DTOs
// Output: CalendarEvent API端点
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.controller;

import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.service.CalendarService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历事件控制器
 * 处理日历事件相关的HTTP请求
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 获取日期范围内的事件
     * @param start 开始日期
     * @param end 结束日期
     * @return 事件列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CalendarEventDTO>>> getEventsByDateRange(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        log.info("GET /api/calendar - Fetching events from {} to {}", start, end);

        List<CalendarEventDTO> events = calendarService.getEventsByDateRange(start, end);

        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved events", events)
        );
    }

    /**
     * 获取指定月份的事件
     * @param year 年份
     * @param month 月份 (1-12)
     * @return 事件列表
     */
    @GetMapping("/month/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CalendarEventDTO>>> getEventsByMonth(
            @PathVariable int year,
            @PathVariable int month) {
        log.info("GET /api/calendar/month/{}/{} - Fetching events", year, month);

        List<CalendarEventDTO> events = calendarService.getEventsByMonth(year, month);

        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved events for " + year + "-" + month, events)
        );
    }

    /**
     * 根据项目ID获取事件
     * @param projectId 项目ID
     * @return 事件列表
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CalendarEventDTO>>> getEventsByProject(
            @PathVariable Long projectId) {
        log.info("GET /api/calendar/project/{} - Fetching events", projectId);

        List<CalendarEventDTO> events = calendarService.getEventsByProject(projectId);

        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved events for project " + projectId, events)
        );
    }

    /**
     * 获取所有紧急事件
     * @return 紧急事件列表
     */
    @GetMapping("/urgent")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CalendarEventDTO>>> getUrgentEvents() {
        log.info("GET /api/calendar/urgent - Fetching urgent events");

        List<CalendarEventDTO> events = calendarService.getUrgentEvents();

        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved urgent events", events)
        );
    }

    /**
     * 创建日历事件
     * @param request 创建请求
     * @return 创建的事件
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CalendarEventDTO>> createEvent(
            @Valid @RequestBody CalendarEventCreateRequest request) {
        log.info("POST /api/calendar - Creating event: {}", request.getTitle());

        // 清洗用户输入
        sanitizeCreateRequest(request);

        CalendarEventDTO event = calendarService.createEvent(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Event created successfully", event)
        );
    }

    /**
     * 更新日历事件
     * @param id 事件ID
     * @param request 更新请求
     * @return 更新后的事件
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CalendarEventDTO>> updateEvent(
            @PathVariable Long id,
            @RequestBody CalendarEventUpdateRequest request) {
        log.info("PUT /api/calendar/{} - Updating event", id);

        // 清洗用户输入
        sanitizeUpdateRequest(request);

        CalendarEventDTO event = calendarService.updateEvent(id, request);

        return ResponseEntity.ok(
                ApiResponse.success("Event updated successfully", event)
        );
    }

    /**
     * 删除日历事件
     * @param id 事件ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        log.info("DELETE /api/calendar/{} - Deleting event", id);

        calendarService.deleteEvent(id);

        return ResponseEntity.ok(
                ApiResponse.success("Event deleted successfully", null)
        );
    }

    /**
     * 清洗创建请求中的用户输入
     */
    private void sanitizeCreateRequest(CalendarEventCreateRequest request) {
        if (request.getTitle() != null) {
            request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getDescription() != null) {
            request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 5000));
        }
    }

    /**
     * 清洗更新请求中的用户输入
     */
    private void sanitizeUpdateRequest(CalendarEventUpdateRequest request) {
        if (request.getTitle() != null) {
            request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        }
        if (request.getDescription() != null) {
            request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 5000));
        }
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid argument: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        if (e instanceof AccessDeniedException) {
            log.warn("Access denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, "权限不足，无法访问该资源"));
        }

        log.error("Runtime error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
    }
}
