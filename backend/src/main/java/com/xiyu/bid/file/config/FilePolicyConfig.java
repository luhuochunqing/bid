package com.xiyu.bid.file.config;

import com.xiyu.bid.file.domain.DownloadPolicy;
import com.xiyu.bid.file.domain.UploadCompletionPolicy;
import com.xiyu.bid.file.domain.UploadPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * file 模块纯核心 Policy 注册。
 *
 * <p>采用 RULES.md §2.5.1 模式 A（@Configuration + @Bean），零侵入纯核心：
 * Policy 类本身是普通 POJO，不加 @Component，由本配置类显式注册为 Bean。</p>
 */
@Configuration
public class FilePolicyConfig {

    @Bean
    public UploadPolicy uploadPolicy() {
        return new UploadPolicy();
    }

    @Bean
    public UploadCompletionPolicy uploadCompletionPolicy() {
        return new UploadCompletionPolicy();
    }

    @Bean
    public DownloadPolicy downloadPolicy() {
        return new DownloadPolicy();
    }
}
