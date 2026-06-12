package com.xiyu.bid.brandauth.config;

import com.xiyu.bid.brandauth.domain.service.AuthorizationExpiryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class BrandAuthPolicyConfig {
    @Bean
    public AuthorizationExpiryPolicy authorizationExpiryPolicy() { return new AuthorizationExpiryPolicy(); }
}
