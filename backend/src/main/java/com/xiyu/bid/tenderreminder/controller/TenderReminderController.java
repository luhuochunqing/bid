package com.xiyu.bid.tenderreminder.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tenderreminder.dto.CreateReminderRequest;
import com.xiyu.bid.tenderreminder.dto.ReminderSettingDTO;
import com.xiyu.bid.tenderreminder.dto.UpdateReminderRequest;
import com.xiyu.bid.tenderreminder.service.TenderReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标讯提醒设置 REST API
 */
@RestController
@RequestMapping("/api/tenders/{tenderId}/reminders")
@RequiredArgsConstructor
@Slf4j
public class TenderReminderController {

    private final TenderReminderService reminderService;
    private final AuthService authService;

    /**
     * 获取标讯的所有提醒设置
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ReminderSettingDTO>>> getReminders(@PathVariable Long tenderId) {
        log.info("GET /api/tenders/{}/reminders - 获取提醒设置", tenderId);
        List<ReminderSettingDTO> reminders = reminderService.getRemindersByTenderId(tenderId);
        return ResponseEntity.ok(ApiResponse.success("获取提醒设置成功", reminders));
    }

    /**
     * 获取单个提醒设置详情
     */
    @GetMapping("/{reminderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReminderSettingDTO>> getReminderById(
            @PathVariable Long tenderId,
            @PathVariable Long reminderId) {
        log.info("GET /api/tenders/{}/reminders/{} - 获取提醒设置详情", tenderId, reminderId);
        return reminderService.getReminderById(reminderId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("获取提醒设置成功", dto)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "提醒设置不存在")));
    }

    /**
     * 创建提醒设置
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReminderSettingDTO>> createReminder(
            @PathVariable Long tenderId,
            @Valid @RequestBody CreateReminderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/reminders - 创建提醒设置", tenderId);
        Long userId = resolveUserId(userDetails);
        ReminderSettingDTO created = reminderService.createReminder(tenderId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("提醒设置创建成功", created));
    }

    /**
     * 更新提醒设置
     */
    @PutMapping("/{reminderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReminderSettingDTO>> updateReminder(
            @PathVariable Long tenderId,
            @PathVariable Long reminderId,
            @Valid @RequestBody UpdateReminderRequest request) {
        log.info("PUT /api/tenders/{}/reminders/{} - 更新提醒设置", tenderId, reminderId);
        return reminderService.updateReminder(reminderId, request)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("提醒设置更新成功", dto)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "提醒设置不存在")));
    }

    /**
     * 删除提醒设置
     */
    @DeleteMapping("/{reminderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(
            @PathVariable Long tenderId,
            @PathVariable Long reminderId) {
        log.info("DELETE /api/tenders/{}/reminders/{} - 删除提醒设置", tenderId, reminderId);
        reminderService.deleteReminder(reminderId);
        return ResponseEntity.ok(ApiResponse.success("提醒设置删除成功", null));
    }

    /**
     * 切换提醒启用状态
     */
    @PostMapping("/{reminderId}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ReminderSettingDTO>> toggleReminder(
            @PathVariable Long tenderId,
            @PathVariable Long reminderId) {
        log.info("POST /api/tenders/{}/reminders/{}/toggle - 切换提醒状态", tenderId, reminderId);
        return reminderService.toggleReminder(reminderId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("提醒状态切换成功", dto)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "提醒设置不存在")));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
