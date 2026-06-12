package com.xiyu.bid.templatecatalog.config;

import com.xiyu.bid.templatecatalog.domain.service.TemplateClassificationPolicy;
import com.xiyu.bid.templatecatalog.domain.service.TemplateVersionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class TemplateCatalogPolicyConfig {
    @Bean
    public TemplateClassificationPolicy templateClassificationPolicy() { return new TemplateClassificationPolicy(); }
    @Bean
    public TemplateVersionPolicy templateVersionPolicy() { return new TemplateVersionPolicy(); }
}
