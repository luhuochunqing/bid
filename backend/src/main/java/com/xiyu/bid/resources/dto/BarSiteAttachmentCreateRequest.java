package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BarSiteAttachmentCreateRequest {

    @NotBlank(message = "附件名称不能为空")
    private String name;

    private String size;

    private String contentType;

    private String url;

    private String uploadedBy;
}
