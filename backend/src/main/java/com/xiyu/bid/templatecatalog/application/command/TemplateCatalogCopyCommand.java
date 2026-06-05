package com.xiyu.bid.templatecatalog.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCatalogCopyCommand {
    private String name;
    private Long createdBy;
}
