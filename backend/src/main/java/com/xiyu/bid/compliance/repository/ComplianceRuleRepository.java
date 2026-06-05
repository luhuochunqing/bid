package com.xiyu.bid.compliance.repository;

import com.xiyu.bid.compliance.entity.ComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 合规规则数据访问接口
 */
@Repository
public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, Long> {

    /**
     * 查找所有启用的规则
     */
    List<ComplianceRule> findByEnabledTrue();

    /**
     * 根据规则类型查找规则
     */
    List<ComplianceRule> findByRuleType(ComplianceRule.RuleType ruleType);

    /**
     * 根据规则类型和启用状态查找规则
     */
    List<ComplianceRule> findByRuleTypeAndEnabledTrue(ComplianceRule.RuleType ruleType);
}
