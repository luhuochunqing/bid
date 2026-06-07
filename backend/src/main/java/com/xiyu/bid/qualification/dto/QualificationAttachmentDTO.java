package com.xiyu.bid.qualification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationAttachmentDTO {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String uploadedAt;
}
