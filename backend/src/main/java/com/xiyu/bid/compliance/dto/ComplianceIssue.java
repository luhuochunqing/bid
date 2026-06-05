package com.xiyu.bid.compliance.dto;

import com.xiyu.bid.compliance.entity.ComplianceRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合规问题DTO
 * 记录单个合规检查发现的问题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceIssue {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型
     */
    private ComplianceRule.RuleType ruleType;

    /**
     * 问题严重程度
     */
    private Severity severity;

    /**
     * 问题描述
     */
    private String description;

    /**
     * 建议措施
     */
    private String recommendation;

    /**
     * 是否通过
     */
    private Boolean passed;

    /**
     * 严重程度枚举
     */
    public enum Severity {
        CRITICAL,  // 严重
        HIGH,      // 高
        MEDIUM,    // 中
        LOW        // 低
    }
}
