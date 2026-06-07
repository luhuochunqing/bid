package com.xiyu.bid.templatecatalog.application.view;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
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
public class TemplateCatalogView {
    private Long id;
    private String name;
    private Template.Category category;
    private ProductType productType;
    private IndustryType industry;
    private DocumentType documentType;
    private String fileUrl;
    private String description;
    private String currentVersion;
    private String fileSize;
    private Long downloads;
    private Long useCount;
    private List<String> tags;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
