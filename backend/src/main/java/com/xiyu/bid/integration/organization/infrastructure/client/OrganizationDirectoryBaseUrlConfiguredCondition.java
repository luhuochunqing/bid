package com.xiyu.bid.integration.organization.infrastructure.client;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class OrganizationDirectoryBaseUrlConfiguredCondition implements Condition {
    private static final String BASE_URL_PROPERTY = "xiyu.integrations.organization.directory.base-url";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String baseUrl = context.getEnvironment().getProperty(BASE_URL_PROPERTY);
        return baseUrl != null && !baseUrl.isBlank();
    }
}
