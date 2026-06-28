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
                .username(user.getUsername())
                .departmentName(user.getDepartmentName())
                // SAFE: 标书分配候选人下拉展示用字段，由前端做权限过滤展示；后端不做业务权限判定。CO-373 治理范围外。
                .roleCode(user.getRoleCode())
                .build();
    }
}
