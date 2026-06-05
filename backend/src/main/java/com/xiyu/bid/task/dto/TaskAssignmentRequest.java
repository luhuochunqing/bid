package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignmentRequest {

    private Long assigneeId;

    private String assigneeDeptCode;

    private String assigneeDeptName;

    private String assigneeRoleCode;

    private String assigneeRoleName;

    @Builder.Default
    private Boolean allowCrossDeptCollaboration = false;

    private String remark;

    public boolean hasAssignmentTarget() {
        return assigneeId != null
                || (assigneeDeptCode != null && !assigneeDeptCode.isBlank())
                || (assigneeRoleCode != null && !assigneeRoleCode.isBlank());
    }
}
