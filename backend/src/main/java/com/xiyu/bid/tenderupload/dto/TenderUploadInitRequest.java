package com.xiyu.bid.tenderupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TenderUploadInitRequest {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "预估文件大小不能为空")
    @Positive(message = "预估文件大小必须大于 0")
    private Long expectedFileSize;
}
