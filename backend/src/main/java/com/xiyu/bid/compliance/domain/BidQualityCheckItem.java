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
            "检查封面项目名称、标段、投标人全称、日期等基础信息是否完整"),
    BID_LETTER_FORMAT(
            "BID_LETTER_FORMAT",
            QualityCheckCategory.BASIC_INFO,
            "投标函格式规范",
            "HIGH",
            2,
            "检查投标函格式、致招标人称谓、项目编号等是否符合模板规范"),
    AUTHORIZATION_LETTER(
            "AUTHORIZATION_LETTER",
            QualityCheckCategory.BASIC_INFO,
            "法定代表人身份证明与授权委托书",
            "CRITICAL",
            3,
            "检查法定代表人身份证明书、法定代表人授权委托书及委托权限是否齐备"),
    BUSINESS_LICENSE(
            "BUSINESS_LICENSE",
            QualityCheckCategory.BASIC_INFO,
            "营业执照与主体资格",
            "CRITICAL",
            4,
            "检查统一社会信用代码、营业执照经营范围是否有效涵盖本项目内容"),
    QUALIFICATION_MATCH(
            "QUALIFICATION_MATCH",
            QualityCheckCategory.BASIC_INFO,
            "资质等级与范围匹配",
            "CRITICAL",
            5,
            "核对企业资质等级、许可证书类型是否完全满足招标文件最低要求"),
    PRICE_CONSISTENCY(
            "PRICE_CONSISTENCY",
            QualityCheckCategory.BASIC_INFO,
            "报价金额大写与小写一致性",
            "CRITICAL",
            6,
            "严格核对投标报价大写金额与阿拉伯小写金额是否完全一致"),
    CURRENCY_UNIT(
            "CURRENCY_UNIT",
            QualityCheckCategory.BASIC_INFO,
            "货币单位与计价规范",
            "MEDIUM",
            7,
            "检查计价货币符号（如人民币/元/万元）是否符合招标文件规范"),
    SCHEDULE_RESPONSE(
            "SCHEDULE_RESPONSE",
            QualityCheckCategory.BASIC_INFO,
            "工期/交货期承诺响应",
            "HIGH",
            8,
            "核对承诺工期或交货期是否明确且不劣于招标文件要求期限"),
    SIGNATURE_SEAL(
            "SIGNATURE_SEAL",
            QualityCheckCategory.BASIC_INFO,
            "法定代表人/委托代理人签章完整性",
            "CRITICAL",
            9,
            "检查关键声明页法定代表人签字、委托代理人签字及企业公章是否完整齐全"),
    CONTACT_INFO(
            "CONTACT_INFO",
            QualityCheckCategory.BASIC_INFO,
            "投标人联系人及联系方式",
            "MEDIUM",
            10,
            "检查联系人姓名、联系电话、电子邮箱、通讯地址是否清晰有效"),

    // === 分项合规检查 (10项) ===
    QUALIFICATION_VETO(
            "QUALIFICATION_VETO",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "资格条件一票否决项核查",
            "CRITICAL",
            11,
            "核查财务状况、体系认证（如ISO）、安全许可证等一票否决硬性指标"),
    FRONT_TABLE_RESPONSE(
            "FRONT_TABLE_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "招标前附表关键条款响应",
            "CRITICAL",
            12,
            "逐条比对前附表中加注星号(*)或明确重点要求的商务/技术条款"),
    RESPONSIVENESS_REVIEW(
            "RESPONSIVENESS_REVIEW",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "实质性响应条款符合性",
            "CRITICAL",
            13,
            "检查付款方式、质保期、验收标准等实质性条款是否存在负偏离"),
    QUALIFICATION_REVIEW(
            "QUALIFICATION_REVIEW",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "资格性评审条款响应",
            "HIGH",
            14,
            "核对企业信誉、类似项目业绩数量与规模门槛是否满足评审要求"),
    SCORING_RESPONSE(
            "SCORING_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "评分标准逐项对应与支撑材料",
            "HIGH",
            15,
            "检查技术方案、实施方案、售后服务是否逐条对应评分细则并附支撑材料"),
    DOCUMENT_COMPLETENESS(
            "DOCUMENT_COMPLETENESS",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "投标文件组成要素完整性",
            "HIGH",
            16,
            "检查投标文件目录卷册、正文各部分组成是否完整无缺页漏卷"),
    TEMPLATE_FORMAT(
            "TEMPLATE_FORMAT",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "排版与格式模板符合性",
            "MEDIUM",
            17,
            "核对字体、字号、段落间距、表格样式是否符合招标文件格式要求"),
    VETO_KEYWORD(
            "VETO_KEYWORD",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "废标/否决项敏感关键词扫描",
            "CRITICAL",
            18,
            "排查是否存在串标、转包、歧义或自相矛盾等废标风险关键词句"),
    CLARIFICATION_RESPONSE(
            "CLARIFICATION_RESPONSE",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "补遗/澄清文件响应",
            "HIGH",
            19,
            "检查是否充分吸收并响应了最新的招标答疑澄清与补遗文件"),
    COMMITMENT_SEAL(
            "COMMITMENT_SEAL",
            QualityCheckCategory.SUB_ITEM_COMPLIANCE,
            "各类承诺函签署与印章",
            "HIGH",
            20,
            "检查廉洁承诺书、无违法违规记录承诺函等承诺文件签署印章"),

    // === 附件清单检查 (5项) ===
    ID_AUTH_LETTER(
            "ID_AUTH_LETTER",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "授权委托人身份证及社保证明",
            "HIGH",
            21,
            "检查授权委托人身份证正反面扫描件及合规社保证明文件"),
    BID_OPENING_TABLE(
            "BID_OPENING_TABLE",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "开标一览表/分项报价明细表",
            "CRITICAL",
            22,
            "检查开标一览表、分项报价明细表计算关系及分项合计是否准确"),
    DEPOSIT_RECEIPT(
            "DEPOSIT_RECEIPT",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "投标保证金缴纳凭证/保函",
            "CRITICAL",
            23,
            "核对投标保证金汇款凭证、保函有效期、受让人及金额是否合规"),
    CA_CERTIFICATE(
            "CA_CERTIFICATE",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "电子标 CA 数字证书签章及加密状态",
            "HIGH",
            24,
            "检查电子投标文件 CA 锁签章有效性与加密锁定状态"),
    DEPOSIT_REFUND(
            "DEPOSIT_REFUND",
            QualityCheckCategory.ATTACHMENT_CHECKLIST,
            "保证金退还信息与基本账户信息",
            "MEDIUM",
            25,
            "检查保证金退还开户行行号、基本账户信息表是否准确完整");

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
