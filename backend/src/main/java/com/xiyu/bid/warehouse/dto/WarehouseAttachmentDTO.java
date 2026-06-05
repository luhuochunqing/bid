package com.xiyu.bid.warehouse.dto;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;

import java.time.LocalDateTime;

public record WarehouseAttachmentDTO(
        Long id,
        WarehouseAttachmentType type,
        String originalFilename,
        String storedFilename,
        Long fileSize,
        String contentType,
        LocalDateTime uploadedAt
) {}
