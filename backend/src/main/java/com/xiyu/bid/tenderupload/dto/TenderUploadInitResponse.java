package com.xiyu.bid.tenderupload.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TenderUploadInitResponse {
    private final String uploadId;
    private final String relativePath;
    private final String uploadMode;
}
