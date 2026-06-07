package com.xiyu.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectGroupConfigRequest {

    @Builder.Default
    private List<ProjectGroupItem> projectGroups = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectGroupItem {
        private Long id;
        private String groupCode;
        private String groupName;
        private Long managerUserId;
        private String visibility;
        @Builder.Default
        private List<Long> memberUserIds = new ArrayList<>();
        @Builder.Default
        private List<String> allowedRoles = new ArrayList<>();
        @Builder.Default
        private List<Long> projectIds = new ArrayList<>();
    }
}
