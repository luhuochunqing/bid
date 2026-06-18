package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.SystemRoleListMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 组织架构集成模块的 Bean 装配配置。
 * <p>
 * 将纯函数风格的领域策略类装配为 Spring Bean，保持 domain 层不依赖 Spring 框架。
 */
@Configuration
public class OrganizationIntegrationConfig {

    @Bean
    public JobRoleLookupResolver jobRoleLookupResolver(
            OrganizationIntegrationProperties properties,
            PositionToRoleMapper positionToRoleMapper
    ) {
        return new JobRoleLookupResolver(
                properties,
                positionToRoleMapper,
                new SystemRoleListMapper(positionToRoleMapper)
        );
    }
}
