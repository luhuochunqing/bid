package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.TenderAssignmentCandidateResponse;
import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

@Component
public class TenderAssignmentViewAssembler {

    public TenderAssignmentResponse.AssignmentRecord toRecord(TenderAssignmentRecord entity) {
        return TenderAssignmentResponse.AssignmentRecord.builder()
                .id(entity.getId())
                .tenderId(entity.getTenderId())
                .assigneeId(entity.getAssigneeId())
                .assigneeName(entity.getAssigneeName())
                .assignedById(entity.getAssignedById())
                .assignedByName(entity.getAssignedByName())
                .remark(entity.getRemark())
                .assignedAt(entity.getAssignedAt())
                .build();
    }

    public TenderAssignmentCandidateResponse toCandidate(User user) {
        return TenderAssignmentCandidateResponse.builder()
                .id(user.getId())
                .name(user.getFullName())
                .departmentName(user.getDepartmentName())
                .roleCode(user.getRoleCode())
                .build();
    }
}
