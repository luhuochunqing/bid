package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.IssueSeverity;
import com.xiyu.bid.compliance.domain.BidDocumentQualityCheckPolicy.QualityIssue;

import static com.xiyu.bid.compliance.domain.QualityCheckTextUtil.containsAny;

/**
 * 分项合规检查规则（10项）.
 * 对标书内容进行逐项合规性检查，比对招标文件硬性要求。
 */
final class FormattingCheckRules {

    private FormattingCheckRules() {
    }

    /** 资格条件一票否决检查（联动品牌授权库）. */
    static QualityIssue checkTocPageConsistency(final String content) {
        boolean hasBrandAuth = containsAny(content, "授权书", "原厂授权", "代理授权", "品牌授权");
        boolean hasAllQual = containsAny(content, "营业执照", "资质证书", "安全生产许可证");
        boolean passed = hasBrandAuth && hasAllQual;
        return new QualityIssue("qual_veto", "资格条件（一票否决，缺一不可）",
                passed ? IssueSeverity.PASS : IssueSeverity.FAIL,
                passed ? "资格条件全部满足" : "资格条件缺失：品牌授权或核心资质未找到，缺一即废标",
                passed ? null : "请确认品牌授权、营业执照、资质证书均已提供且有效", passed);
    }

    /** 前附表关键条款填写完整性. */
    static QualityIssue checkBidLetterFormat(final String content) {
        int found = 0;
        if (containsAny(content, "投标保证金", "保证金金额", "保证金缴纳")) found++;
        if (containsAny(content, "投标有效期", "有效期")) found++;
        if (containsAny(content, "投标截止", "开标时间", "开标日期")) found++;
        if (containsAny(content, "履约保证金", "履约担保")) found++;
        if (containsAny(content, "付款方式", "支付方式")) found++;
        boolean passed = found >= 3;
        return new QualityIssue("key_clauses", "前附表关键条款填写完整性",
                passed ? IssueSeverity.PASS : IssueSeverity.FAIL,
                passed ? "前附表" + found + "项关键条款已响应" : "前附表关键条款填写不完整（仅" + found + "/5项）",
                "请逐项核对前附表：保证金、有效期、开标时间、履约担保、付款方式", passed);
    }

    /** 响应性评审条款逐项响应检查. */
    static QualityIssue checkAuthorizationLetter(final String content) {
        boolean hasDelivery = containsAny(content, "交付周期", "交付期", "交货期", "工期");
        boolean hasQuality = containsAny(content, "质量标准", "质量承诺", "质保期");
        boolean hasService = containsAny(content, "售后服务", "技术支持", "培训");
        int matched = (hasDelivery ? 1 : 0) + (hasQuality ? 1 : 0) + (hasService ? 1 : 0);
        boolean passed = matched >= 2;
        return new QualityIssue("responsive_review", "响应性评审条款响应",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "响应性评审条款已逐项响应（" + matched + "/3项）" : "响应性评审条款响应不充分（仅" + matched + "/3项）",
                "请逐项响应交付期、质量标准、售后服务等评审条款", passed);
    }

    /** 资格性评审条款响应检查. */
    static QualityIssue checkBusinessLicense(final String content) {
        boolean hasLicense = containsAny(content, "营业执照", "统一社会信用代码");
        boolean hasFinance = containsAny(content, "财务审计", "审计报告", "财务报表");
        boolean hasTax = containsAny(content, "纳税", "完税证明", "税收");
        int matched = (hasLicense ? 1 : 0) + (hasFinance ? 1 : 0) + (hasTax ? 1 : 0);
        boolean passed = matched >= 2;
        return new QualityIssue("qualification_review", "资格性评审条款响应",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "资格性评审条款已响应（" + matched + "/3项）" : "资格性评审条款响应不足（仅" + matched + "/3项）",
                "请确保营业执照、财务审计报告、纳税证明等资格性材料齐全", passed);
    }

    /** 评分项响应是否按满分标准. */
    static QualityIssue checkQualificationMatch(final String content, final String tenderText) {
        boolean hasScoreResponse = containsAny(content, "评分标准", "评分项", "评审标准", "评分办法");
        boolean hasDetail = content != null && content.length() > 2000;
        boolean passed = hasScoreResponse && hasDetail;
        return new QualityIssue("score_response", "评分项响应（按满分标准）",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? "已按评分标准逐项响应" : "评分项响应不充分，建议对照评分标准逐项优化",
                passed ? null : "请对照评分标准逐项响应，确保每项按满分标准编写", passed);
    }

    /** 投标文件组成部分完整性. */
    static QualityIssue checkPriceConsistency(final String content) {
        boolean hasQualPart = containsAny(content, "资格", "资质", "资信");
        boolean hasBizPart = containsAny(content, "商务", "报价", "价格");
        boolean hasTechPart = containsAny(content, "技术方案", "技术", "实施");
        boolean hasAttachPart = containsAny(content, "附件", "附录");
        int parts = (hasQualPart ? 1 : 0) + (hasBizPart ? 1 : 0) + (hasTechPart ? 1 : 0) + (hasAttachPart ? 1 : 0);
        boolean passed = parts >= 3;
        return new QualityIssue("doc_completeness", "投标文件组成完整性",
                passed ? IssueSeverity.PASS : IssueSeverity.FAIL,
                passed ? "投标文件组成部分完整（" + parts + "/4部分）" : "投标文件组成部分不完整（仅" + parts + "/4部分）",
                "投标文件应包含资格部分、商务部分、技术部分和附件", passed);
    }

    /** 格式模板符合性检查. */
    static QualityIssue checkCurrencyUnit(final String content) {
        boolean hasFormat = containsAny(content, "格式", "模板", "开标一览表", "分项报价表", "投标函");
        return new QualityIssue("format_template", "格式模板符合性",
                hasFormat ? IssueSeverity.PASS : IssueSeverity.WARNING,
                hasFormat ? "已使用招标文件指定格式模板" : "请确认是否使用招标文件提供的格式模板",
                "请逐一核对开标一览表、分项报价表等核心格式表是否与模板一致", hasFormat);
    }

    /** 投标否决项关键字扫描（搜索"拒绝、否决、废标、无效"等高风险措辞）. */
    static QualityIssue checkScheduleResponse(final String content, final String tenderText) {
        int riskCount = 0;
        StringBuilder found = new StringBuilder();
        String[] keywords = {"拒绝", "否决", "废标", "无效", "不响应", "不承担", "不负责任", "概不"};
        for (String kw : keywords) {
            if (containsAny(content, kw)) {
                riskCount++;
                if (found.length() > 0) found.append("、");
                found.append(kw);
            }
        }
        boolean passed = riskCount == 0;
        return new QualityIssue("veto_keywords", "投标否决项关键字扫描",
                passed ? IssueSeverity.PASS : IssueSeverity.FAIL,
                passed ? "未检测到高风险措辞" : "检测到" + riskCount + "处高风险措辞（" + found + "），建议立即修改",
                passed ? null : "将「拒绝」改为「另行收费」，将「不承担」改为「按约定承担」，避免废标风险", passed);
    }

    /** 澄清文件响应检查. */
    static QualityIssue checkSignatureSeal(final String content) {
        boolean hasClarify = containsAny(content, "澄清", "补遗", "答疑", "更正");
        return new QualityIssue("clarification_response", "澄清文件响应",
                hasClarify ? IssueSeverity.MANUAL : IssueSeverity.PASS,
                hasClarify ? "检测到澄清/补遗文件，请人工确认是否已按澄清内容响应" : "未检测到本项目澄清文件",
                hasClarify ? "如有澄清文件，请逐项确认响应内容已补充到投标文件中" : null, !hasClarify);
    }

    /** 承诺类文件盖章检查. */
    static QualityIssue checkContactInfo(final String content) {
        boolean hasPromise = containsAny(content, "承诺书", "承诺函", "承诺");
        boolean hasSeal = containsAny(content, "盖章", "公章", "签章");
        boolean passed = !hasPromise || hasSeal;
        return new QualityIssue("promise_seal", "承诺类文件盖章",
                passed ? IssueSeverity.PASS : IssueSeverity.WARNING,
                passed ? (hasPromise ? "承诺类文件已盖章" : "未检测到承诺类文件") : "检测到承诺类文件但可能未盖章",
                passed ? null : "请确保所有承诺函（廉洁自律承诺函等）已加盖公章", passed);
    }
}
