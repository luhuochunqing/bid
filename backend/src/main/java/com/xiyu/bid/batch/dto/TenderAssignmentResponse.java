package com.xiyu.bid.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderAssignmentResponse {
    private AssignmentRecord latest;
    private List<AssignmentRecord> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentRecord {
        private Long id;
        private Long tenderId;
        private Long assigneeId;
        private String assigneeName;
        private Long assignedById;
        private String assignedByName;
        private String remark;
        private LocalDateTime assignedAt;
    }
}
