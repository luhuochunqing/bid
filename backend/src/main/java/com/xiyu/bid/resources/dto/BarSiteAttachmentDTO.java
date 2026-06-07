package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BarSiteAttachmentDTO {
    Long id;
    Long barAssetId;
    String name;
    String size;
    String contentType;
    String url;
    String uploadedBy;
    LocalDateTime uploadedAt;
}
