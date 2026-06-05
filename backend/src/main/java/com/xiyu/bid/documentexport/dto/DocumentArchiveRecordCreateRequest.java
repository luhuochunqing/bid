package com.xiyu.bid.documentexport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentArchiveRecordCreateRequest {

    private Long archivedBy;

    @NotBlank(message = "Archived by name is required")
    private String archivedByName;

    @NotBlank(message = "Archive reason is required")
    private String archiveReason;
}
