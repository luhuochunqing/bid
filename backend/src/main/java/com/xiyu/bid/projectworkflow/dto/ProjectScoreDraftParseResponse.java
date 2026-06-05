package com.xiyu.bid.projectworkflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScoreDraftParseResponse {
    private List<ProjectScoreDraftDTO> drafts;
    private int totalCount;
    private long draftCount;
    private long readyCount;
    private long skippedCount;
}
