package com.xiyu.bid.historyproject.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HistoricalProjectSnapshotDTO {

    private Long projectId;
    private Long archiveRecordId;
    private Long exportId;
    private String projectName;
    private String customerName;
    private String productLine;
    private String archiveSummary;
    private String documentSnapshotText;
    private List<String> recommendedTags;
    private LocalDateTime capturedAt;
}
