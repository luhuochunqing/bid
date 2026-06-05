package com.xiyu.bid.tenderupload.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenderUploadCompleteRequest {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    private Integer pageCount;

    private Integer priority;
}
