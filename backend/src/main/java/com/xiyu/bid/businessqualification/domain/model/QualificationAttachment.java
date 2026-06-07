package com.xiyu.bid.businessqualification.domain.model;

import java.time.LocalDateTime;

public record QualificationAttachment(
        Long id,
        String fileName,
        String fileUrl,
        LocalDateTime uploadedAt
) {

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
