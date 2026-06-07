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
public class TemplateVersionDTO {
    private Long id;
    private String version;
    private String description;
    private String snapshotName;
    private Long createdBy;
    private LocalDateTime createdAt;
}
