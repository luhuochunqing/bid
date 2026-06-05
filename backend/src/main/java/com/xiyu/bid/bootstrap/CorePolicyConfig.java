package com.xiyu.bid.bootstrap;

import com.xiyu.bid.approval.core.ApprovalDecisionPolicy;
import com.xiyu.bid.approval.core.ApprovalPermissionPolicy;
import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.scoreanalysis.core.ScoreAnalysisCalculationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CorePolicyConfig {

    @Bean
    public ApprovalDecisionPolicy approvalDecisionPolicy() {
        return new ApprovalDecisionPolicy();
    }

    @Bean
    public ApprovalPermissionPolicy approvalPermissionPolicy() {
        return new ApprovalPermissionPolicy();
    }

    @Bean
    public BatchValidationPolicy batchValidationPolicy() {
        return new BatchValidationPolicy();
    }

    @Bean
    public TenderStatusTransitionPolicy tenderStatusTransitionPolicy() {
        return new TenderStatusTransitionPolicy();
    }

    @Bean
    public ScoreAnalysisCalculationPolicy scoreAnalysisCalculationPolicy() {
        return new ScoreAnalysisCalculationPolicy();
    }
}
