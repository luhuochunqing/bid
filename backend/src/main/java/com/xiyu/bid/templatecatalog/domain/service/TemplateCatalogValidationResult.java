package com.xiyu.bid.templatecatalog.domain.service;

public record TemplateCatalogValidationResult(boolean valid, String message) {

    public static TemplateCatalogValidationResult success() {
        return new TemplateCatalogValidationResult(true, null);
    }

    public static TemplateCatalogValidationResult invalid(String message) {
        return new TemplateCatalogValidationResult(false, message);
    }
}
