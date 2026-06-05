package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamTaskWorkloadDTO {
    private String scope;
    private boolean orgConfigured;
    private String emptyReason;
    private List<TeamMemberWorkloadDTO> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberWorkloadDTO {
        private Long userId;
        private String name;
        private String roleCode;
        private String roleName;
        private String deptCode;
        private String deptName;
        private long todoCount;
        private long inProgressCount;
        private long overdueCount;
        private long completedThisWeekCount;
        private int workloadScore;
        private String workloadLevel;
        private List<MemberTaskSummaryDTO> tasks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberTaskSummaryDTO {
        private Long id;
        private String title;
        private String priority;
        private String status;
        private String deadline;
        private boolean overdue;
        private boolean dueSoon;
    }
}
