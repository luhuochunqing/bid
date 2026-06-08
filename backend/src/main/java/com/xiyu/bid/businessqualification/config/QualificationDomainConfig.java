package com.xiyu.bid.businessqualification.config;

import com.xiyu.bid.businessqualification.domain.service.QualificationCreationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 资质域纯核心策略 Bean 注册。
 * 纯核心类不依赖 Spring 注解，通过此配置显式注册。
 */
@Configuration
public class QualificationDomainConfig {

    @Bean
    public QualificationCreationPolicy qualificationCreationPolicy() {
        return new QualificationCreationPolicy();
    }
}
