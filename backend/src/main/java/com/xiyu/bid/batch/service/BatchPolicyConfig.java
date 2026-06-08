package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchAssignmentPolicy;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Batch 模块 Policy Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class BatchPolicyConfig {

    /**
     * 注册批量分配策略.
     *
     * @param projectAccessScopeService 项目数据权限服务
     * @return 批量分配策略实例
     */
    @Bean
    public BatchAssignmentPolicy batchAssignmentPolicy(
            final ProjectAccessScopeService projectAccessScopeService) {
        return new BatchAssignmentPolicy(projectAccessScopeService);
    }

    // tenderStatusTransitionPolicy 和 batchValidationPolicy
    // 由 CorePolicyBeanConfig 统一提供，避免 e2e profile 下的 bean 冲突。
}
