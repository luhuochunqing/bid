package com.xiyu.bid.templatecatalog.application.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCatalogVersionView {
    private Long id;
    private String version;
    private String description;
    private String snapshotName;
    private Long createdBy;
    private LocalDateTime createdAt;
}
