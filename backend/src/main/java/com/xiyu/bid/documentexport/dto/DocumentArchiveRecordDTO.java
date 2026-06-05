package com.xiyu.bid.documentexport.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentArchiveRecordDTO {

    private Long id;
    private Long projectId;
    private Long structureId;
    private Long archivedBy;
    private String archivedByName;
    private String archiveReason;
    private Long exportId;
    private String exportFileName;
    private String projectName;
    private DocumentCaseSnapshotDTO caseSnapshot;
    private LocalDateTime archivedAt;
}
