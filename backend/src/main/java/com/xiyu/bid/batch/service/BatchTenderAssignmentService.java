package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.batch.dto.TenderAssignmentCandidateResponse;
import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchTenderAssignmentService {

    private final BatchTenderStatusAppService batchTenderStatusAppService;
    private final BatchTenderAssignAppService batchTenderAssignAppService;
    private final TenderAssignmentQueryService tenderAssignmentQueryService;

    public BatchOperationResponse batchUpdateStatus(BatchTenderStatusUpdateRequest request, User currentUser) {
        return batchTenderStatusAppService.batchUpdateStatus(request, currentUser);
    }

    public BatchOperationResponse batchAssign(BatchTenderAssignRequest request, User currentUser) {
        return batchTenderAssignAppService.batchAssign(request, currentUser);
    }

    public TenderAssignmentResponse getAssignment(Long tenderId) {
        return tenderAssignmentQueryService.getAssignment(tenderId);
    }

    public List<TenderAssignmentCandidateResponse> getCandidates() {
        return tenderAssignmentQueryService.getCandidates();
    }
}
