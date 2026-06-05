package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.bidresult.dto.BidResultAttachmentBindRequest;
import com.xiyu.bid.bidresult.dto.BidResultBatchRequest;
import com.xiyu.bid.bidresult.dto.BidResultReminderDTO;
import com.xiyu.bid.bidresult.dto.BidResultReminderSendRequest;
import com.xiyu.bid.bidresult.dto.BidResultSyncResponseDTO;
import com.xiyu.bid.bidresult.service.BidResultReminderAppService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bid-results/reminders")
@RequiredArgsConstructor
public class BidResultReminderController {

    private static final String ADMIN_MANAGER_STAFF_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final BidResultReminderAppService reminderAppService;
    private final BidResultCurrentUserResolver currentUserResolver;

    @PostMapping("/send")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultReminderDTO> sendReminder(
            @RequestBody BidResultReminderSendRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(reminderAppService.sendReminder(
                request.getResultId(),
                request.getComment(),
                user.getId(),
                user.getFullName()
        ));
    }

    @PostMapping("/send-batch")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultSyncResponseDTO> sendReminderBatch(
            @RequestBody BidResultBatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        int count = reminderAppService.sendReminders(request.getIds(), request.getComment(), user.getId(), user.getFullName());
        return ApiResponse.success(BidResultSyncResponseDTO.builder().affectedCount(count).message("批量提醒完成").build());
    }

    @PostMapping("/{reminderId}/mark-uploaded")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultReminderDTO> markUploaded(
            @PathVariable Long reminderId,
            @RequestBody BidResultAttachmentBindRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(reminderAppService.markUploaded(reminderId, request.getDocumentId(), user.getId()));
    }
}

