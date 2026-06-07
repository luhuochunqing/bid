package com.xiyu.bid.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for export operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {
    private String filename;
    private int recordCount;
    private long fileSize;
    private String downloadUrl;
}
