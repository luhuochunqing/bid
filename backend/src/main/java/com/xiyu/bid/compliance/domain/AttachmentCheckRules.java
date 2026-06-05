package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.IssueSeverity;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.QualityIssue;

import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsAny;

/**
 * 投标附件清单检查规则（5项）.
 * 检查投标资料附件的完整性和合规性。
 */
final class AttachmentCheckRules {

    private AttachmentCheckRules() {
    }

    /** 委托人身份证原件 + 授权委托书. */
    static QualityIssue checkPerformanceProof(final String content) {
        boolean hasIdCard = containsAny(content, "身份证", "身份证复印件", "身份证明");
        boolean hasAuthLetter = containsAny(content, "授权委托书", "法人授权", "法定代表人授权");
        boolean passed = hasIdCard && hasAuthLetter;
        return new QualityIssue("id_auth_letter", "委托人身份证 + 授权委托书",
                passed ? IssueSeverity.PASS : hasIdCard ? IssueSeverity.WARNING : IssueSeverity.FAIL,
                passed ? "已上传委托人身份证和授权委托书" : hasIdCard ? "缺少授权委托书" : "缺少委托人身份证及授权委托书",
                passed ? null : "请上传法人授权委托书（彩色扫描）及受托人身份证正反面", passed);
    }

    /** 开标一览表. */
    static QualityIssue checkFinancialStatements(final String content) {
        boolean hasBidTable = containsAny(content, "开标一览表", "报价一览表", "投标报价表");
        return new QualityIssue("bid_opening_table", "开标一览表",
                hasBidTable ? IssueSeverity.PASS : IssueSeverity.FAIL,
                hasBidTable ? "已上传开标一览表" : "缺少开标一览表",
                hasBidTable ? null : "请按招标文件模板填写并上传开标一览表（须盖章）", hasBidTable);
    }

    /** 投标保证金回单. */
    static QualityIssue checkPersonnelQualification(final String content) {
        boolean hasReceipt = containsAny(content, "保证金回单", "保证金凭证", "保证金转账", "电汇凭证", "保函");
        boolean hasBasicAccount = containsAny(content, "基本账户", "基本户", "对公账户");
        boolean passed = hasReceipt && hasBasicAccount;
        return new QualityIssue("deposit_receipt", "投标保证金回单",
                passed ? IssueSeverity.PASS : hasReceipt ? IssueSeverity.WARNING : IssueSeverity.FAIL,
                passed ? "已上传保证金回单（基本账户汇出）" : hasReceipt ? "回单已上传但未确认是否从基本账户汇出" : "缺少投标保证金回单",
                passed ? null : "请上传从基本账户电汇的保证金回单，确保与招标文件要求一致", passed);
    }

    /** 电子标 CA 证书有效期. */
    static QualityIssue checkEquipmentList(final String content) {
        boolean hasCA = containsAny(content, "CA证书", "CA", "数字证书", "电子签章");
        boolean hasValidity = containsAny(content, "有效期", "有效至", "有效日期", "过期");
        boolean passed = hasCA && hasValidity;
        return new QualityIssue("ca_certificate", "电子标 CA 证书",
                passed ? IssueSeverity.PASS : hasCA ? IssueSeverity.WARNING : IssueSeverity.MANUAL,
                passed ? "已包含 CA 证书有效期信息" : hasCA ? "CA 证书信息存在但有效期不明确" : "请确认 CA 证书有效期覆盖本次投标全周期",
                passed ? null : "请确保 CA 证书在投标截止日及合同期内有效", passed);
    }

    /** 保证金退款信息表/收据. */
    static QualityIssue checkTechnicalProposal(final String content) {
        boolean hasRefundInfo = containsAny(content, "退款信息", "退款账户", "退款表");
        boolean hasReceipt = containsAny(content, "收据", "收款收据", "保证金收据");
        boolean passed = hasRefundInfo && hasReceipt;
        return new QualityIssue("deposit_refund", "保证金退款信息表 / 收据",
                passed ? IssueSeverity.PASS : (hasRefundInfo || hasReceipt) ? IssueSeverity.WARNING : IssueSeverity.MANUAL,
                passed ? "已上传退款信息表和收据" : (hasRefundInfo || hasReceipt) ? "退款信息或收据不完整" : "请人工核对退款账户信息表及收据存根",
                passed ? null : "请上传退款账户信息表（基本账户）和收据复印件，核对账号与对公账户一致", passed);
    }
}
