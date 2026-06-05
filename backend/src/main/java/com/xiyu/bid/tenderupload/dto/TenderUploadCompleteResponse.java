package com.xiyu.bid.tenderupload.dto;

import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TenderUploadCompleteResponse {
    private final Long fileId;
    private final Long taskId;
    private final TenderTaskStatus status;
    private final boolean deduplicated;
}
