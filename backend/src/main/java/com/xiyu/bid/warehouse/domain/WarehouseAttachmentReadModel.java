package com.xiyu.bid.warehouse.domain;

import java.time.LocalDateTime;

public interface WarehouseAttachmentReadModel {
    Long getId();
    WarehouseAttachmentType getType();
    String getOriginalFilename();
    String getStoredFilename();
    Long getFileSize();
    String getContentType();
    Long getUploadedBy();
    LocalDateTime getUploadedAt();
}
