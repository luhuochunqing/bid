package com.xiyu.bid.casework.application;

import org.springframework.context.annotation.Configuration;

/**
 * Casework 领域策略配置。
 *
 * <p>纯核心 {@link KnowledgeCaseMatchPolicy} 无 Spring 依赖，由 Spring 容器统一管理注入。
 */
@Configuration
public class CaseworkPolicyConfig {
    // Note: KnowledgeCaseMatchPolicy bean is provided centrally by CorePolicyBeanConfig
    // to avoid duplicate bean definition errors in dev profile.
    // This config remains for potential casework-specific extensions.
}
