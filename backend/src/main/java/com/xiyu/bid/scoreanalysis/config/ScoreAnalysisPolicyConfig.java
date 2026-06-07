package com.xiyu.bid.scoreanalysis.config;

import com.xiyu.bid.scoreanalysis.core.ScoreAnalysisCalculationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 评分分析模块 Policy Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class ScoreAnalysisPolicyConfig {

    /**
     * 注册评分计算策略.
     *
     * @return 评分计算策略实例
     */
    @Bean
    public ScoreAnalysisCalculationPolicy scoreAnalysisCalculationPolicy() {
        return new ScoreAnalysisCalculationPolicy();
    }
}
