package com.xiyu.bid.documentexport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentExportCreateRequest {

    @NotBlank(message = "Export format is required")
    private String format;

    private Long exportedBy;

    @NotBlank(message = "Exporter name is required")
    private String exportedByName;
}
