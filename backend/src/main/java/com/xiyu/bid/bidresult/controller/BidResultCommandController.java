package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.bidresult.dto.BidResultAttachmentBindRequest;
import com.xiyu.bid.bidresult.dto.BidResultBatchRequest;
import com.xiyu.bid.bidresult.dto.BidResultConfirmRequest;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultDTO;
import com.xiyu.bid.bidresult.dto.BidResultIgnoreRequest;
import com.xiyu.bid.bidresult.dto.BidResultRegisterRequest;
import com.xiyu.bid.bidresult.dto.BidResultSyncResponseDTO;
import com.xiyu.bid.bidresult.dto.BidResultUpdateRequest;
import com.xiyu.bid.bidresult.service.BidResultAttachmentAppService;
import com.xiyu.bid.bidresult.service.BidResultFetchConfirmationAppService;
import com.xiyu.bid.bidresult.service.BidResultRegistrationAppService;
import com.xiyu.bid.bidresult.service.BidResultSyncAppService;
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
@RequestMapping("/api/bid-results")
@RequiredArgsConstructor
public class BidResultCommandController {

    private static final String ADMIN_MANAGER_STAFF_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final BidResultRegistrationAppService registrationAppService;
    private final BidResultFetchConfirmationAppService fetchConfirmationAppService;
    private final BidResultAttachmentAppService attachmentAppService;
    private final BidResultSyncAppService syncAppService;
    private final BidResultCurrentUserResolver currentUserResolver;

    @PostMapping("/sync")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultSyncResponseDTO> sync(@AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(syncAppService.syncInternal(user.getId(), user.getFullName()));
    }

    @PostMapping("/fetch")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultSyncResponseDTO> fetch(@AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(syncAppService.fetchPublicResults(user.getId(), user.getFullName()));
    }

    @PostMapping("/register")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultFetchResultDTO> register(
            @RequestBody BidResultRegisterRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(registrationAppService.register(request, user.getId(), user.getFullName()));
    }

    @PostMapping("/{id}/update")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultFetchResultDTO> update(
            @PathVariable Long id,
            @RequestBody BidResultUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(registrationAppService.update(id, request, user.getId(), user.getFullName()));
    }

    @PostMapping("/fetch-results/{id}/confirm-with-data")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultFetchResultDTO> confirmWithData(
            @PathVariable Long id,
            @RequestBody(required = false) BidResultConfirmRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(fetchConfirmationAppService.confirmWithData(id, request, user.getId(), user.getFullName()));
    }

    @PostMapping("/fetch-results/{id}/ignore")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<Void> ignore(@PathVariable Long id, @RequestBody BidResultIgnoreRequest request) {
        fetchConfirmationAppService.ignore(id, request.getComment());
        return ApiResponse.success("已忽略该记录", null);
    }

    @PostMapping("/fetch-results/confirm-batch")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultSyncResponseDTO> confirmBatch(
            @RequestBody BidResultBatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        int count = fetchConfirmationAppService.confirmBatch(
                request.getIds(),
                request.getComment(),
                user.getId(),
                user.getFullName()
        );
        return ApiResponse.success(BidResultSyncResponseDTO.builder().affectedCount(count).message("批量确认完成").build());
    }

    @PostMapping("/{resultId}/attachments/bind")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultFetchResultDTO> bindAttachment(
            @PathVariable Long resultId,
            @RequestBody BidResultAttachmentBindRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ApiResponse.success(attachmentAppService.bindAttachment(resultId, request, user.getId(), user.getFullName()));
    }
}

