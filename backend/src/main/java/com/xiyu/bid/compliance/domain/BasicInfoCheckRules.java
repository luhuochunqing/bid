package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.IssueSeverity;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.QualityIssue;

import java.util.List;

import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.allEqual;
import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsAny;
import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsDatePattern;
import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsEmailPattern;
import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsPhonePattern;
import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.extractAmounts;

/**
 * 基本信息检查规则（10项）.
 */
final class BasicInfoCheckRules {

    private BasicInfoCheckRules() {
    }

    static QualityIssue checkCoverInfo(final String content) {
        boolean hasProjectName = containsAny(content, "项目名称", "工程名称", "标段名称");
        boolean hasBidderName = containsAny(content, "投标人", "投标单位", "竞标人");
        boolean hasDate = containsDatePattern(content);
        boolean passed = hasProjectName && hasBidderName && hasDate;

        return new QualityIssue("cover_info", "封面信息完整性",
                passed ? IssueSeverity.PASS : IssueSeverity.FAIL,
                passed ? "封面信息完整" : "封面缺少必要信息（项目名称/投标单位/日期）",
                passed ? null : "请补充封面上的项目名称、投标单位和日期", passed);
    }

    static QualityIssue checkBidLetterFormat(final String content) {
        boolean hasQuote = containsAny(content, "报价", "投标总价", "金额");
        boolean hasSchedule = containsAny(content, "工期", "日历天", "交付期");
        boolean hasQuality = containsAny(content, "质量承诺", "质量标准", "质保期");
        boolean passed = hasQuote && hasSchedule && hasQuality;

        return new QualityIssue("bid_letter_format", "投标函格式规范",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "投标函格式规范" : "投标函缺少必要内容（报价/工期/质量承诺）",
                passed ? null : "请确保投标函包含报价、工期和质量承诺", passed);
    }

    static QualityIssue checkAuthorizationLetter(final String content) {
        boolean hasAuth = containsAny(content, "授权书", "授权委托", "法定代表人授权");
        return new QualityIssue("authorization_letter", "法定代表人授权书",
                hasAuth ? IssueSeverity.PASS : IssueSeverity.FAIL,
                hasAuth ? "已包含法定代表人授权书" : "缺少法定代表人授权书",
                hasAuth ? null : "请补充法定代表人授权书", hasAuth);
    }

    static QualityIssue checkBusinessLicense(final String content) {
        boolean hasLicense = containsAny(content, "营业执照", "统一社会信用代码");
        return new QualityIssue("business_license", "营业执照有效性",
                hasLicense ? IssueSeverity.PASS : IssueSeverity.FAIL,
                hasLicense ? "已包含营业执照信息" : "缺少营业执照信息",
                hasLicense ? null : "请补充营业执照复印件", hasLicense);
    }

    static QualityIssue checkQualificationMatch(final String content, final String tenderText) {
        boolean hasQualification = containsAny(content, "资质等级", "资质证书", "专业承包");
        return new QualityIssue("qualification_match", "资质证书匹配",
                hasQualification ? IssueSeverity.PASS : IssueSeverity.MANUAL,
                hasQualification ? "已包含资质证书信息" : "请人工核对资质证书是否与招标要求匹配",
                "请核对资质等级、证书编号与招标文件要求是否一致", hasQualification);
    }

    static QualityIssue checkPriceConsistency(final String content) {
        List<String> amounts = extractAmounts(content);
        boolean consistent = amounts.size() < 2 || allEqual(amounts);
        return new QualityIssue("price_consistency", "报价金额一致性",
                consistent ? IssueSeverity.PASS : IssueSeverity.FAIL,
                consistent ? "报价金额一致" : "不同位置的报价金额不一致",
                consistent ? null : "请核对投标函、报价表、汇总表中的金额是否一致", consistent);
    }

    static QualityIssue checkCurrencyUnit(final String content) {
        boolean hasMixedUnits = containsAny(content, "万元")
                && containsAny(content, "元")
                && !containsAny(content, "（万元）", "（元）");
        boolean hasUnit = containsAny(content, "元", "CNY", "RMB", "人民币");
        boolean passed = hasUnit && !hasMixedUnits;
        return new QualityIssue("currency_unit", "货币单位规范",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "货币单位规范" : "货币单位可能不统一或混用",
                passed ? null : "请统一使用人民币（元）作为货币单位", passed);
    }

    static QualityIssue checkScheduleResponse(final String content, final String tenderText) {
        boolean hasSchedule = containsAny(content, "工期", "日历天", "交付期", "实施周期");
        return new QualityIssue("schedule_response", "工期响应符合性",
                hasSchedule ? IssueSeverity.PASS : IssueSeverity.MANUAL,
                hasSchedule ? "已包含工期响应" : "请人工核对工期响应是否符合招标要求",
                "请核对工期天数是否满足招标文件要求", hasSchedule);
    }

    static QualityIssue checkSignatureSeal(final String content) {
        boolean hasSignature = containsAny(content, "签字", "签章", "盖章", "公章");
        return new QualityIssue("signature_seal", "签字盖章完整性",
                hasSignature ? IssueSeverity.PASS : IssueSeverity.MANUAL,
                hasSignature ? "已包含签章信息" : "请人工核对关键位置是否签字盖章",
                "请确保投标函、报价表、授权书等关键位置已签字盖章", hasSignature);
    }

    static QualityIssue checkContactInfo(final String content) {
        boolean hasAddress = containsAny(content, "地址", "邮编");
        boolean hasPhone = containsPhonePattern(content);
        boolean hasEmail = containsEmailPattern(content);
        boolean passed = hasAddress && hasPhone && hasEmail;
        return new QualityIssue("contact_info", "联系方式准确性",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "联系方式完整" : "联系方式可能不完整",
                passed ? null : "请补充完整的联系地址、电话和邮箱", passed);
    }
}
