package com.xiyu.bid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.approval.core.ApprovalDecisionPolicy;
import com.xiyu.bid.approval.core.ApprovalPermissionPolicy;
import com.xiyu.bid.casework.domain.policy.CaseExportPolicy;
import com.xiyu.bid.casework.domain.policy.KnowledgeCaseMatchPolicy;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryRetryPolicy;
import com.xiyu.bid.platform.async.application.AsyncDecisionResolver;
import com.xiyu.bid.marketprediction.domain.IntervalBasedPredictionPolicy;
import com.xiyu.bid.resources.expenseledger.domain.ExpenseLedgerStatisticsCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 纯核心 Policy Bean 注册.
 *
 * <p>domain.service 包下的策略类由各模块的 PolicyConfig 注册，
 * 避免违反 ArchitectureTest RULE 9（config 不依赖 service）。
 */
@Configuration(proxyBeanMethods = false)
public final class CorePolicyBeanConfig {

    @Bean
    ApprovalDecisionPolicy approvalDecisionPolicy() { return new ApprovalDecisionPolicy(); }

    @Bean
    ApprovalPermissionPolicy approvalPermissionPolicy() { return new ApprovalPermissionPolicy(); }

    @Bean
    OrganizationDirectoryRetryPolicy organizationDirectoryRetryPolicy(AsyncDecisionResolver decisionResolver) {
        return new OrganizationDirectoryRetryPolicy(decisionResolver);
    }

    @Bean
    ExpenseLedgerStatisticsCalculator expenseLedgerStatisticsCalculator() { return new ExpenseLedgerStatisticsCalculator(); }

    @Bean
    IntervalBasedPredictionPolicy intervalBasedPredictionPolicy() { return new IntervalBasedPredictionPolicy(); }

    @Bean
    StructuralDocumentChunker structuralDocumentChunker(ObjectMapper objectMapper) { return new StructuralDocumentChunker(objectMapper); }

    @Bean
    CaseExportPolicy caseExportPolicy() { return new CaseExportPolicy(); }

    @Bean
    KnowledgeCaseMatchPolicy knowledgeCaseMatchPolicy() { return new KnowledgeCaseMatchPolicy(); }
}
