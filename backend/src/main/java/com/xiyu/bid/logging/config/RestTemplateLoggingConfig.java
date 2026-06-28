package com.xiyu.bid.logging.config;

import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateLoggingConfig {

    @Bean
    public RestTemplateCustomizer loggingRestTemplateCustomizer() {
        return new RestTemplateCustomizer() {
            @Override
            public void customize(RestTemplate restTemplate) {
                // 使用 BufferingClientHttpRequestFactory 包装原始 factory
                // 以便在日志拦截器中可以重复读取响应流
                if (!(restTemplate.getRequestFactory() instanceof BufferingClientHttpRequestFactory)) {
                    restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));
                }
                
                // 添加日志拦截器
                restTemplate.getInterceptors().add(new LoggingClientHttpRequestInterceptor());
            }
        };
    }
}
