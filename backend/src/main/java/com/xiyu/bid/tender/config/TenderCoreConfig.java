package com.xiyu.bid.tender.config;

import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tender 模块 Policy/Mapper Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class TenderCoreConfig {

    /**
     * 注册标讯评测提交 Mapper（纯映射，不含业务规则）.
     */
    @Bean
    public TenderEvaluationSubmissionMapper tenderEvaluationSubmissionMapper() {
        return new TenderEvaluationSubmissionMapper();
    }
}
