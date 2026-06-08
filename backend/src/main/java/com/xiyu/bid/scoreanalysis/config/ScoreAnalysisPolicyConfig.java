package com.xiyu.bid.scoreanalysis.config;

import org.springframework.context.annotation.Configuration;

/**
 * 评分分析模块 Policy Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class ScoreAnalysisPolicyConfig {

    // scoreAnalysisCalculationPolicy 由 CorePolicyBeanConfig 统一提供，
    // 避免 e2e profile 下的 bean 冲突。
}
