package com.xiyu.bid.templatecatalog.application.mapper;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.templatecatalog.application.view.TemplateCatalogView;
import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.EnumParseResult;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateDtoMapper {

    public TemplateCatalogView toDto(Template template, long downloads, long useCount) {
        return TemplateCatalogView.builder()
                .id(template.getId())
                .name(template.getName())
                .category(template.getCategory())
                .productType(readProductType(template.getProductType()))
                .industry(readIndustryType(template.getIndustry()))
                .documentType(readDocumentType(template.getDocumentType()))
                .fileUrl(template.getFileUrl())
                .description(template.getDescription())
                .currentVersion(template.getCurrentVersion())
                .fileSize(template.getFileSize())
                .downloads(downloads)
                .useCount(useCount)
                .tags(copyTags(template.getTags()))
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private List<String> copyTags(List<String> tags) {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    private ProductType readProductType(String value) {
        EnumParseResult<ProductType> parseResult = ProductType.parse(value);
        return parseResult.valid() ? parseResult.value() : null;
    }

    private IndustryType readIndustryType(String value) {
        EnumParseResult<IndustryType> parseResult = IndustryType.parse(value);
        return parseResult.valid() ? parseResult.value() : null;
    }

    private DocumentType readDocumentType(String value) {
        EnumParseResult<DocumentType> parseResult = DocumentType.parse(value);
        return parseResult.valid() ? parseResult.value() : null;
    }
}
