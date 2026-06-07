package com.xiyu.bid.templatecatalog.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCatalogDownloadRecordCommand {
    private Long downloadedBy;
}
