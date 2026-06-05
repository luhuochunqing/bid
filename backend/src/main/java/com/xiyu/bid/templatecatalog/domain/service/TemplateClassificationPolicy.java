package com.xiyu.bid.templatecatalog.domain.service;

import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
import org.springframework.stereotype.Component;

@Component
public class TemplateClassificationPolicy {

    public TemplateCatalogValidationResult validateComplete(
            ProductType productType,
            IndustryType industry,
            DocumentType documentType
    ) {
        if (productType == null || industry == null || documentType == null) {
            return TemplateCatalogValidationResult.invalid("产品类型、行业、文档类型不能为空");
        }
        return TemplateCatalogValidationResult.success();
    }
}
