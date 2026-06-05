package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.FetchResultConfirmationPolicy;
import com.xiyu.bid.bidresult.dto.BidResultConfirmRequest;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultAssembler;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultDTO;
import com.xiyu.bid.bidresult.dto.BidResultUpdateRequest;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BidResultFetchConfirmationAppService {

    private static final int MAX_BATCH_SIZE = 200;

    private final BidResultFetchResultRepository fetchResultRepository;
    private final BidResultReminderAppService reminderAppService;
    private final BidResultProjectAccessGuard accessGuard;
    private final BidResultRegistrationFactory registrationFactory = new BidResultRegistrationFactory();

    @Transactional
    public BidResultFetchResultDTO confirmWithData(Long id, BidResultConfirmRequest request, Long operatorId, String operatorName) {
        BidResultFetchResult fetchResult = getFetchResult(id);
        BidResultUpdateRequest updateRequest = request == null ? new BidResultUpdateRequest() : request;
        var registration = registrationFactory.fromUpdateRequest(fetchResult, updateRequest);
        var decision = FetchResultConfirmationPolicy.validateForConfirmation(fetchResult, registration);
        if (!decision.valid()) {
            throw new BusinessException(String.join("; ", decision.errors()));
        }
        registrationFactory.applyRegistration(fetchResult, registration);
        fetchResult.setStatus(BidResultFetchResult.Status.CONFIRMED);
        fetchResult.setConfirmedAt(LocalDateTime.now());
        fetchResult.setConfirmedBy(operatorId);
        BidResultFetchResult saved = fetchResultRepository.save(fetchResult);
        reminderAppService.ensurePendingReminderForResult(saved, "结果已确认，待上传资料", operatorId, operatorName);
        return BidResultFetchResultAssembler.toDto(saved);
    }

    @Transactional
    public void ignore(Long id, String comment) {
        BidResultFetchResult entity = getFetchResult(id);
        var decision = FetchResultConfirmationPolicy.validateIgnore(comment);
        if (!decision.valid()) {
            throw new BusinessException(String.join("; ", decision.errors()));
        }
        entity.setStatus(BidResultFetchResult.Status.IGNORED);
        entity.setIgnoredReason(comment.trim());
        fetchResultRepository.save(entity);
    }

    @Transactional
    public int confirmBatch(List<Long> ids, String comment, Long operatorId, String operatorName) {
        List<Long> safeIds = Optional.ofNullable(ids).orElse(List.of());
        if (safeIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("批量数量不得超过 " + MAX_BATCH_SIZE);
        }
        int count = 0;
        for (Long id : safeIds) {
            BidResultConfirmRequest request = new BidResultConfirmRequest();
            request.setRemark(comment);
            confirmWithData(id, request, operatorId, operatorName);
            count++;
        }
        return count;
    }

    private BidResultFetchResult getFetchResult(Long id) {
        BidResultFetchResult result = fetchResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bid result fetch record not found: " + id));
        accessGuard.assertCanAccess(result.getProjectId());
        return result;
    }
}
