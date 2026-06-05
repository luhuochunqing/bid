package com.xiyu.bid.compliance.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书文档质量核查纯核心策略.
 * 对投标文件进行25项文档质量检查，识别低级错误和格式问题.
 * 无Spring依赖，无副作用，可独立单元测试.
 *
 * <p>规则实现拆分至：
 * {@link BasicInfoCheckRules}（10项基本信息）、
 * {@link FormattingCheckRules}（10项格式排版）、
 * {@link AttachmentCheckRules}（5项附件清单）。
 */
public final class BidDocumentQualityCheckPolicy {

    private BidDocumentQualityCheckPolicy() {
    }

    /**
     * 标书文档质量核查结果.
     *
     * @param issues       问题列表
     * @param overallStatus 整体状态
     * @param totalIssues   总问题数
     * @param criticalCount 严重问题数
     * @param warningCount  警告问题数
     */
    public record QualityCheckResult(
            List<QualityIssue> issues,
            OverallStatus overallStatus,
            int totalIssues,
            int criticalCount,
            int warningCount
    ) {
    }

    /**
     * 单项质量问题.
     *
     * @param checkItem   检查项编码
     * @param checkName   检查项名称
     * @param severity    严重程度
     * @param description 描述
     * @param suggestion  建议
     * @param passed      是否通过
     */
    public record QualityIssue(
            String checkItem,
            String checkName,
            IssueSeverity severity,
            String description,
            String suggestion,
            boolean passed
    ) {
    }

    /**
     * 问题严重程度（对齐蓝图分级）.
     */
    public enum IssueSeverity {
        /** 通过. */
        PASS,
        /** 警告. */
        WARNING,
        /** 不通过. */
        FAIL,
        /** 需人工确认. */
        MANUAL
    }

    /**
     * 整体状态.
     */
    public enum OverallStatus {
        /** 通过. */
        PASS,
        /** 警告. */
        WARNING,
        /** 不通过. */
        FAIL,
        /** 需人工确认. */
        MANUAL
    }

    /**
     * 执行完整的标书文档质量核查.
     *
     * @param documentContent 文档内容（已解析的文本）
     * @param tenderText      招标文件文本（用于比对硬性要求）
     * @return 质量核查结果
     */
    public static QualityCheckResult check(
            final String documentContent, final String tenderText) {
        List<QualityIssue> issues = new ArrayList<>();

        // 基本信息检查（10项）
        issues.add(BasicInfoCheckRules.checkCoverInfo(documentContent));
        issues.add(BasicInfoCheckRules.checkBidLetterFormat(documentContent));
        issues.add(BasicInfoCheckRules.checkAuthorizationLetter(documentContent));
        issues.add(BasicInfoCheckRules.checkBusinessLicense(documentContent));
        issues.add(BasicInfoCheckRules.checkQualificationMatch(documentContent, tenderText));
        issues.add(BasicInfoCheckRules.checkPriceConsistency(documentContent));
        issues.add(BasicInfoCheckRules.checkCurrencyUnit(documentContent));
        issues.add(BasicInfoCheckRules.checkScheduleResponse(documentContent, tenderText));
        issues.add(BasicInfoCheckRules.checkSignatureSeal(documentContent));
        issues.add(BasicInfoCheckRules.checkContactInfo(documentContent));

        // 分项合规检查（10项）
        issues.add(FormattingCheckRules.checkTocPageConsistency(documentContent));
        issues.add(FormattingCheckRules.checkBidLetterFormat(documentContent));
        issues.add(FormattingCheckRules.checkAuthorizationLetter(documentContent));
        issues.add(FormattingCheckRules.checkBusinessLicense(documentContent));
        issues.add(FormattingCheckRules.checkQualificationMatch(documentContent, tenderText));
        issues.add(FormattingCheckRules.checkPriceConsistency(documentContent));
        issues.add(FormattingCheckRules.checkCurrencyUnit(documentContent));
        issues.add(FormattingCheckRules.checkScheduleResponse(documentContent, tenderText));
        issues.add(FormattingCheckRules.checkSignatureSeal(documentContent));
        issues.add(FormattingCheckRules.checkContactInfo(documentContent));

        // 附件清单检查（5项）
        issues.add(AttachmentCheckRules.checkPerformanceProof(documentContent));
        issues.add(AttachmentCheckRules.checkFinancialStatements(documentContent));
        issues.add(AttachmentCheckRules.checkPersonnelQualification(documentContent));
        issues.add(AttachmentCheckRules.checkEquipmentList(documentContent));
        issues.add(AttachmentCheckRules.checkTechnicalProposal(documentContent));

        return summarize(issues);
    }

    // ── 委派方法（向后兼容，供测试直接调用） ────────────────────────────────────

    static QualityIssue checkCoverInfo(String c) { return BasicInfoCheckRules.checkCoverInfo(c); }
    static QualityIssue checkBidLetterFormat(String c) { return BasicInfoCheckRules.checkBidLetterFormat(c); }
    static QualityIssue checkAuthorizationLetter(String c) { return BasicInfoCheckRules.checkAuthorizationLetter(c); }
    static QualityIssue checkBusinessLicense(String c) { return BasicInfoCheckRules.checkBusinessLicense(c); }
    static QualityIssue checkQualificationMatch(String c, String t) { return BasicInfoCheckRules.checkQualificationMatch(c, t); }
    static QualityIssue checkPriceConsistency(String c) { return BasicInfoCheckRules.checkPriceConsistency(c); }
    static QualityIssue checkCurrencyUnit(String c) { return BasicInfoCheckRules.checkCurrencyUnit(c); }
    static QualityIssue checkScheduleResponse(String c, String t) { return BasicInfoCheckRules.checkScheduleResponse(c, t); }
    static QualityIssue checkSignatureSeal(String c) { return BasicInfoCheckRules.checkSignatureSeal(c); }
    static QualityIssue checkContactInfo(String c) { return BasicInfoCheckRules.checkContactInfo(c); }

    static QualityIssue checkTocPageConsistency(String c) { return FormattingCheckRules.checkTocPageConsistency(c); }
    static QualityIssue checkBidLetterFormat_Detailed(String c) { return FormattingCheckRules.checkBidLetterFormat(c); }
    static QualityIssue checkAuthorizationLetter_Detailed(String c) { return FormattingCheckRules.checkAuthorizationLetter(c); }
    static QualityIssue checkBusinessLicense_Detailed(String c) { return FormattingCheckRules.checkBusinessLicense(c); }
    static QualityIssue checkQualificationMatch_Detailed(String c, String t) { return FormattingCheckRules.checkQualificationMatch(c, t); }
    static QualityIssue checkPriceConsistency_Detailed(String c) { return FormattingCheckRules.checkPriceConsistency(c); }
    static QualityIssue checkCurrencyUnit_Detailed(String c) { return FormattingCheckRules.checkCurrencyUnit(c); }
    static QualityIssue checkScheduleResponse_Detailed(String c, String t) { return FormattingCheckRules.checkScheduleResponse(c, t); }
    static QualityIssue checkSignatureSeal_Detailed(String c) { return FormattingCheckRules.checkSignatureSeal(c); }
    static QualityIssue checkContactInfo_Detailed(String c) { return FormattingCheckRules.checkContactInfo(c); }

    static QualityIssue checkPerformanceProof(String c) { return AttachmentCheckRules.checkPerformanceProof(c); }
    static QualityIssue checkFinancialStatements(String c) { return AttachmentCheckRules.checkFinancialStatements(c); }
    static QualityIssue checkPersonnelQualification(String c) { return AttachmentCheckRules.checkPersonnelQualification(c); }
    static QualityIssue checkEquipmentList(String c) { return AttachmentCheckRules.checkEquipmentList(c); }
    static QualityIssue checkTechnicalProposal(String c) { return AttachmentCheckRules.checkTechnicalProposal(c); }

    // ── 汇总 ─────────────────────────────────────────────────────────────────

    static QualityCheckResult summarize(final List<QualityIssue> issues) {
        int total = issues.size();
        int criticalCount = (int) issues.stream()
                .filter(i -> i.severity() == IssueSeverity.FAIL).count();
        int warningCount = (int) issues.stream()
                .filter(i -> i.severity() == IssueSeverity.WARNING).count();
        int manualCount = (int) issues.stream()
                .filter(i -> i.severity() == IssueSeverity.MANUAL).count();

        OverallStatus status;
        if (criticalCount > 0) {
            status = OverallStatus.FAIL;
        } else if (warningCount > 0) {
            status = OverallStatus.WARNING;
        } else if (manualCount > 0) {
            status = OverallStatus.MANUAL;
        } else {
            status = OverallStatus.PASS;
        }

        return new QualityCheckResult(issues, status, total, criticalCount, warningCount);
    }

    // ── 工具方法委派（向后兼容） ────────────────────────────────────────────────

    static boolean containsAny(String content, String... keywords) {
        return QualityCheckTextUtil.containsAny(content, keywords);
    }

    static boolean containsPattern(String content, String regex) {
        return QualityCheckTextUtil.containsPattern(content, regex);
    }

    static boolean containsDatePattern(String content) {
        return QualityCheckTextUtil.containsDatePattern(content);
    }

    static boolean containsPhonePattern(String content) {
        return QualityCheckTextUtil.containsPhonePattern(content);
    }

    static boolean containsEmailPattern(String content) {
        return QualityCheckTextUtil.containsEmailPattern(content);
    }

    static boolean containsPageNumberPattern(String content) {
        return QualityCheckTextUtil.containsPageNumberPattern(content);
    }

    static List<String> extractAmounts(String content) {
        return QualityCheckTextUtil.extractAmounts(content);
    }

    static boolean allEqual(List<String> list) {
        return QualityCheckTextUtil.allEqual(list);
    }
}
