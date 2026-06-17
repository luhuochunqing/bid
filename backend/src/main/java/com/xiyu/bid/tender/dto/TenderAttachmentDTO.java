package com.xiyu.bid.tender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯附件 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderAttachmentDTO {

    private String fileName;
    private String fileType;
    private String fileUrl;
}
