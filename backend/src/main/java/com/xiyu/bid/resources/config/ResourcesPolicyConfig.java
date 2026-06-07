package com.xiyu.bid.resources.config;

import com.xiyu.bid.resources.domain.service.DepositReturnReminderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resources 模块 Policy Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class ResourcesPolicyConfig {

    /**
     * 注册保证金退还提醒策略.
     */
    @Bean
    public DepositReturnReminderPolicy depositReturnReminderPolicy() {
        return new DepositReturnReminderPolicy();
    }
}
