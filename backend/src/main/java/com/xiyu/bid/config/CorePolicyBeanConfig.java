package com.xiyu.bid.config;

import com.xiyu.bid.approval.core.ApprovalDecisionPolicy;
import com.xiyu.bid.approval.core.ApprovalPermissionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 纯核心 Policy Bean 注册.
 */
@Configuration(proxyBeanMethods = false)
public final class CorePolicyBeanConfig {

    @Bean
    ApprovalDecisionPolicy approvalDecisionPolicy() {
        return new ApprovalDecisionPolicy();
    }

    @Bean
    ApprovalPermissionPolicy approvalPermissionPolicy() {
        return new ApprovalPermissionPolicy();
    }
}
