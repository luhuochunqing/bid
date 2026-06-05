package com.xiyu.bid.templatecatalog.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCatalogUseRecordCommand {
    private String documentName;
    private String docType;
    private Long projectId;
    private List<String> applyOptions;
    private Long usedBy;
}
