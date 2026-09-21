package com.xiyu.bid.compliance.domain;

/**
 * 标书质量核查标准 25 项检查点枚举.
 */
public enum BidQualityCheckItem {

    // === 基本信息检查 (10项) ===
    COVER_INFO(
            "COVER_INFO",
            QualityCheckCategory.BASIC_INFO,
            "封面信息完整性",
            "HIGH",
            1,
            "检查投标文件封面是否包含项目名称、项目编号、投标人名称、投标日期等必要信息，是否符合招标文件模板要求"),
    BID_LETTER_FORMAT(
            "BID_LETTER_FORMAT",
            QualityCheckCategory.BASIC_INFO,
            "投标函格式规范",
            "HIGH",
            2,
            "检查投标函格式是否符合招标文件模板要求，包括标题、正文、落款等要素"),
    AUTHORIZATION_LETTER(
            "AUTHORIZATION_LETTER",
            QualityCheckCategory.BASIC_INFO,
            "授权委托书",
            "CRITICAL",
            3,
            "检查法人授权委托书是否存在、是否加盖公章、授权范围和期限是否明确，是否符合招标文件模板要求"),
    BUSINESS_LICENSE(
            "BUSINESS_LICENSE",
            QualityCheckCategory.BASIC_INFO,
            "营业执照",
            "CRITICAL",
            4,
            "检查营业执照复印件是否清晰、是否在有效期内、经营范围是否覆盖招标要求"),
    QUALIFICATION_MATCH(
            "QUALIFICATION_MATCH",
            QualityCheckCategory.BASIC_INFO,
            "资质匹配",
            "CRITICAL",
            5,
            "依据招标文件资格要求，对照企业资质库与投标文件逐项比对。核验证书关键词、等级与有效期"),
    PRICE_CONSISTENCY(
            "PRICE_CONSISTENCY",
            QualityCheckCategory.BASIC_INFO,
            "报价一致性",
            "CRITICAL",
            6,
            "检查总报价与开标一览表、报价明细表中的金额是否一致，是否超过限价；报价内分项报价与总价是否正确；报价的大小写是否一致"),
    CURRENCY_UNIT(
            "CURRENCY_UNIT",
            QualityCheckCategory.BASIC_INFO,
            "货币单位",
            "MEDIUM",
            7,
            "检查全部报价是否使用统一的货币单位（人民币 CNY），是否存在混用外币情况"),
    SCHEDULE_RESPONSE(
            "SCHEDULE_RESPONSE",
            QualityCheckCategory.BASIC_INFO,
            "工期响应",
            "HIGH",
            8,
            "检查投标文件中的工期响应是否符合招标文件要求的天数"),
    SIGNATURE_SEAL(
            "SIGNATURE_SEAL",
            QualityCheckCategory.BASIC_INFO,
            "签章完整性",
            "CRITICAL",
            9,
            "结合文本层签章证据与页面图像（PDF）核验签章是否存在，重点检查技术方案末尾、承诺函等需盖章页面；非分页文档（.docx）仅做文本层核验，无法确认时转人工复核"),
    CONTACT_INFO(
            "CONTACT_INFO",
            QualityCheckCategory.BASIC_INFO,
            "联系信息",
            "MEDIUM",
            10,
            "检查联系人、电话、邮箱等联系信息是否完整填写"),

    // === 分项合规检查 (10项) ===
    QUALIFICATION_VETO(
            "QUALIFICATION_VETO",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "资格条件一票否决",
            "CRITICAL",
            11,
            "联动资质库和品牌授权库，检查招标文件中的强制资格条件（如 ISO 认证、行业资质）是否全部满足。任一缺失即为不通过，存在废标风险"),
    FRONT_TABLE_RESPONSE(
            "FRONT_TABLE_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "前附表关键条款填写完整性",
            "CRITICAL",
            12,
            "检查前附表中的全部条款是否已填写响应，无空项、无漏项"),
    RESPONSIVENESS_REVIEW(
            "RESPONSIVENESS_REVIEW",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "响应性评审条款",
            "CRITICAL",
            13,
            "检查投标文件对招标文件响应性条款的响应情况，全部响应为通过，部分响应为待确认，未响应为不通过"),
    QUALIFICATION_REVIEW(
            "QUALIFICATION_REVIEW",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "资格性评审条款响应",
            "HIGH",
            14,
            "检查投标文件对招标文件资格性条款的响应情况，全部响应为通过，部分响应为待确认，未响应为不通过"),
    SCORING_RESPONSE(
            "SCORING_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "评分项响应（按满分标准）",
            "HIGH",
            15,
            "检查投标文件对评分项的响应是否达到满分标准，未达到满分的标记为待确认并建议补充，显示出客观项评分的分数，并可以跳转 AI 评分标准解析"),
    DOCUMENT_COMPLETENESS(
            "DOCUMENT_COMPLETENESS",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "投标文件组成完整性",
            "HIGH",
            16,
            "检查投标文件组成是否符合招标文件要求的章节结构和内容要素"),
    TEMPLATE_FORMAT(
            "TEMPLATE_FORMAT",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "格式模板符合性",
            "MEDIUM",
            17,
            "检查投标文件格式是否符合招标文件模板要求，包括字体、字号、页边距、页码格式等"),
    VETO_KEYWORD(
            "VETO_KEYWORD",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "投标否决项关键字扫描",
            "CRITICAL",
            18,
            "从招标文件动态提取否决项关键词，对投标文件全文进行扫描。检测到关键词为不通过，记录页码和原文上下文"),
    CLARIFICATION_RESPONSE(
            "CLARIFICATION_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "澄清文件响应",
            "HIGH",
            19,
            "检查本项目是否存在澄清文件，若存在则检查投标文件是否已完整响应澄清文件要求。AI 无法确认完整性的标记为待确认"),
    COMMITMENT_SEAL(
            "COMMITMENT_SEAL",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "承诺类文件盖章检查",
            "HIGH",
            20,
            "检查承诺函、声明书等承诺类文件是否已加盖公章"),

    // === 附件清单检查 (5项) ===
    ID_AUTH_LETTER(
            "ID_AUTH_LETTER",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "委托人身份证原件 + 授权委托书",
            "HIGH",
            21,
            "检查授权委托书和受托人身份证复印件是否齐全、信息是否一致"),
    BID_OPENING_TABLE(
            "BID_OPENING_TABLE",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "开标一览表/报价表",
            "CRITICAL",
            22,
            "检查开标一览表/报价表是否按招标文件模板填写、是否盖章、关键信息是否完整"),
    DEPOSIT_RECEIPT(
            "DEPOSIT_RECEIPT",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "投标保证金回单",
            "CRITICAL",
            23,
            "检查银行回单是否清晰、是否从基本账户汇出、金额是否符合要求，保证金回单的要素与招标文件中保证金要求是否符合，包括账号、金额、账户名称、回款备注/摘要"),
    CA_CERTIFICATE(
            "CA_CERTIFICATE",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "电子标 CA 证书",
            "HIGH",
            24,
            "检查 CA 证书是否在有效期内、是否覆盖投标全周期"),
    DEPOSIT_REFUND(
            "DEPOSIT_REFUND",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "保证金退款信息表 / 收据",
            "MEDIUM",
            25,
            "检查退款账户信息表和收据存根是否齐全、信息是否完整");

    private final String code;
    private final QualityCheckCategory category;
    private final String title;
    private final String defaultSeverity;
    private final int order;
    private final String description;

    BidQualityCheckItem(
            final String code,
            final QualityCheckCategory category,
            final String title,
            final String defaultSeverity,
            final int order,
            final String description) {
        this.code = code;
        this.category = category;
        this.title = title;
        this.defaultSeverity = defaultSeverity;
        this.order = order;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public QualityCheckCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDefaultSeverity() {
        return defaultSeverity;
    }

    public int getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }
}
