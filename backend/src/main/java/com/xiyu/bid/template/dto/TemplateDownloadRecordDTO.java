package com.xiyu.bid.template.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDownloadRecordDTO {
    private Long id;
    private Long downloadedBy;
    private LocalDateTime downloadedAt;
}
