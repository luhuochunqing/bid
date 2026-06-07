package com.xiyu.bid.crm.config;

import com.xiyu.bid.crm.domain.CrmTokenCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CRM 模块 Domain Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class CrmDomainConfig {

    /**
     * 注册 CRM Token 缓存（线程安全的领域对象，单例生命周期）.
     */
    @Bean
    public CrmTokenCache crmTokenCache() {
        return new CrmTokenCache();
    }
}
