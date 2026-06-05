package com.xiyu.bid.templatecatalog.application.mapper;

import com.xiyu.bid.entity.TemplateVersion;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogVersionView;
import org.springframework.stereotype.Component;

@Component
public class TemplateVersionDtoMapper {

    public TemplateCatalogVersionView toDto(TemplateVersion version) {
        return TemplateCatalogVersionView.builder()
                .id(version.getId())
                .version(version.getVersion())
                .description(version.getDescription())
                .snapshotName(version.getSnapshotName())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
