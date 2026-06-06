package com.xiyu.bid.casework.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectArchiveQuery {
    private Long archiveId;
    private Long projectId;
    private String projectName;
    private List<String> documentCategories;
    private List<String> projectStatus;
    private List<String> projectType;
    private String uploadTimeStart;
    private String uploadTimeEnd;
    private String closeTimeStart;
    private String closeTimeEnd;
    private String projectManager;
    private String bidManager;
}
