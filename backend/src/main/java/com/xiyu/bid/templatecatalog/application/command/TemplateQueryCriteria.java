package com.xiyu.bid.templatecatalog.application.command;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateQueryCriteria {
    private String name;
    private Template.Category category;
    private Long createdBy;
    private ProductType productType;
    private IndustryType industry;
    private DocumentType documentType;
}
