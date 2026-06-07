package com.xiyu.bid.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderAssignmentCandidateResponse {
    private Long id;
    private String name;
    private String departmentName;
    private String roleCode;
}
