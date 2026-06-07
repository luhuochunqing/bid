package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.TenderAssignmentCandidateResponse;
import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenderAssignmentQueryService {

    private final TenderAssignmentRecordRepository tenderAssignmentRecordRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentViewAssembler assembler;

    @Transactional(readOnly = true)
    public TenderAssignmentResponse getAssignment(Long tenderId) {
        List<TenderAssignmentResponse.AssignmentRecord> history = tenderAssignmentRecordRepository
                .findByTenderIdOrderByAssignedAtDesc(tenderId)
                .stream()
                .map(assembler::toRecord)
                .toList();

        return TenderAssignmentResponse.builder()
                .latest(history.isEmpty() ? null : history.get(0))
                .history(history)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenderAssignmentCandidateResponse> getCandidates() {
        return userRepository.findByEnabledTrue().stream()
                .map(assembler::toCandidate)
                .toList();
    }
}
