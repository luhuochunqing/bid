package com.xiyu.bid.biddraftagent.config;

import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaClassificationPolicy;
import com.xiyu.bid.biddraftagent.domain.commercial.CommercialSubTypePolicy;
import com.xiyu.bid.biddraftagent.domain.risk.RedLineRiskPolicy;
import com.xiyu.bid.biddraftagent.domain.technical.TechnicalSubTypePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BidDraftAgent 模块 Policy Bean 注册.
 * 纯核心类不加 @Component，由此统一管理。
 */
@Configuration(proxyBeanMethods = false)
public final class BidDraftAgentPolicyConfig {

    @Bean
    public CommercialSubTypePolicy commercialSubTypePolicy() {
        return new CommercialSubTypePolicy();
    }

    @Bean
    public RedLineRiskPolicy redLineRiskPolicy() {
        return new RedLineRiskPolicy();
    }

    @Bean
    public ScoringCriteriaClassificationPolicy scoringCriteriaClassificationPolicy() {
        return new ScoringCriteriaClassificationPolicy();
    }

    @Bean
    public TechnicalSubTypePolicy technicalSubTypePolicy() {
        return new TechnicalSubTypePolicy();
    }
}
