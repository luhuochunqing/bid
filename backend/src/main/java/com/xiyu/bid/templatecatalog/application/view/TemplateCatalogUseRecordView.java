package com.xiyu.bid.templatecatalog.application.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCatalogUseRecordView {
    private Long id;
    private String documentName;
    private String docType;
    private Long projectId;
    private List<String> applyOptions;
    private Long usedBy;
    private LocalDateTime usedAt;
}
