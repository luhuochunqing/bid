package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignmentCandidateDTO {
    private Long userId;
    private String name;
    private String roleCode;
    private String roleName;
    private String deptCode;
    private String deptName;
    private Boolean enabled;
}
