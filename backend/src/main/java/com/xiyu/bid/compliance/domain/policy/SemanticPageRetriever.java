// Input: 检查项 + 招标要求 + 标书文档 + 字符预算
// Output: 格式化后的相关上下文（内部内容块与已知原件物理页分开标注），预算内全文透传，超预算 TOP-N 相关页；单页即超预算时硬截断并标注
// Pos: compliance/domain/policy/ - 标书语义页面检索器（纯核心，spec 049 R1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 按检查项相关性挑选预算内页面，保留真实页码并显式声明抽样边界（FP-Java）。 */
public final class SemanticPageRetriever {
    public static final int DEFAULT_BID_BUDGET = 60_000;
    public static final int DEFAULT_TENDER_BUDGET = 30_000;
    private static final int SCORE_HEADING_MATCH = 50;
    private static final int SCORE_KEYWORD_MATCH = 10;
    private static final int SCORE_FREQUENCY_UNIT = 2;
    private static final int MAX_FREQUENCY_COUNT = 20;
    private static final String PAGE_HEADER_PREFIX = "--- [第 ";
    private static final String PAGE_HEADER_SUFFIX = " 页] ---";
    private static final String NO_CONTENT = "（无内容）";
    private static final String SAMPLED_NOTICE = "【相关页抽样：页码间隔不代表原文件缺页；未召回内容不能证明原文缺失，扫描图片须人工核实】\n";

    private SemanticPageRetriever() { }

    /** 针对投标文件执行语义检索（使用默认投标文件预算 60,000 字符）. */
    public static String retrieveBidContext(
            final BidQualityCheckItem item, final TenderRequirements tenderReqs, final DocumentContent document) {
        return retrieveContext(item, tenderReqs, document, DEFAULT_BID_BUDGET);
    }

    /** 针对招标文件执行语义检索（使用默认招标文件预算 30,000 字符）. */
    public static String retrieveTenderContext(
            final BidQualityCheckItem item, final TenderRequirements tenderReqs, final DocumentContent document) {
        return retrieveContext(item, tenderReqs, document, DEFAULT_TENDER_BUDGET);
    }

    /** 执行分页检索与预算上下文拼装。 */
    public static String retrieveContext(
            final BidQualityCheckItem item, final TenderRequirements tenderReqs,
            final DocumentContent document, final int charBudget) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            if (document != null && document.fullText() != null && !document.fullText().isBlank()) {
                return formatSinglePage(1, document.fullText(), charBudget);
            }
            return NO_CONTENT;
        }
        List<DocumentContent.PageContent> pages = document.pages();
        int totalDocLength = 0;
        for (DocumentContent.PageContent p : pages) {
            if (p.text() != null) {
                totalDocLength += p.text().length();
            }
        }
        // 1. 若文档总字符量在预算内，100% 全文无损透传
        if (totalDocLength <= charBudget) {
            return formatPages(pages);
        }
        // 2. 超出预算：计算每页相关性得分，挑选 TOP-N 页面
        Set<String> queryCues = collectQueryCues(item, tenderReqs);
        List<ScoredPage> scoredPages = new ArrayList<>(pages.size());
        for (DocumentContent.PageContent p : pages) {
            scoredPages.add(new ScoredPage(p, scorePage(p, queryCues)));
        }
        scoredPages.sort(Comparator.comparingInt(ScoredPage::score).reversed()
                .thenComparingInt(sp -> sp.page().pageNumber()));

        List<DocumentContent.PageContent> selected = new ArrayList<>();
        int accumulatedChars = 0;
        for (ScoredPage sp : scoredPages) {
            int pageLen = sp.page().text() != null ? sp.page().text().length() : 0;
            if (selected.isEmpty() && pageLen > charBudget) {
                selected.add(truncatePage(sp.page(), charBudget));
                break;
            }
            if (selected.isEmpty() || accumulatedChars + pageLen <= charBudget) {
                selected.add(sp.page());
                accumulatedChars += pageLen;
                if (accumulatedChars >= charBudget) {
                    break;
                }
            }
        }

        selected.sort(Comparator.comparingInt(DocumentContent.PageContent::pageNumber));
        return SAMPLED_NOTICE + formatPages(selected);
    }

    private static int scorePage(final DocumentContent.PageContent page, final Set<String> cues) {
        if (page == null) return 0;
        String text = page.text() == null ? "" : page.text();
        List<String> headings = page.headings() == null ? List.of() : page.headings();
        if (text.isBlank() && headings.isEmpty()) return 0;

        int score = 0;
        for (String heading : headings) {
            if (heading != null && !heading.isBlank()) {
                for (String cue : cues) {
                    if (heading.contains(cue)) {
                        score += SCORE_HEADING_MATCH;
                    }
                }
            }
        }

        if (!text.isBlank()) {
            for (String cue : cues) {
                int count = countOccurrences(text, cue);
                if (count > 0) {
                    score += SCORE_KEYWORD_MATCH + Math.min(count, MAX_FREQUENCY_COUNT) * SCORE_FREQUENCY_UNIT;
                }
            }
        }
        return score;
    }

    private static int countOccurrences(final String text, final String sub) {
        if (text == null || sub == null || sub.isEmpty()) return 0;
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static Set<String> collectQueryCues(final BidQualityCheckItem item, final TenderRequirements req) {
        Set<String> cues = new LinkedHashSet<>();
        if (item != null) {
            addCue(cues, item.getTitle());
            addDescriptionCues(cues, item.getDescription());
            addStaticItemCues(cues, item);
        }
        if (req != null && !req.missing()) {
            cues.addAll(addDynamicRequirementsCues(req, item));
        }
        return cues;
    }

    private static Set<String> addStaticItemCues(final Set<String> cues, final BidQualityCheckItem item) {
        switch (item) {
            case QUALIFICATION_MATCH -> addCues(cues, "资质", "证书", "认证", "ISO", "质量管理体系", "环境管理体系",
                    "职业健康安全", "体系认证", "等级", "有效期", "认证机构", "资格证明", "资质证书");
            case QUALIFICATION_VETO -> addCues(cues, "资格条件", "一票否决", "废标", "实质性要求", "强制性要求",
                    "授权书", "品牌授权", "原厂授权", "制造商授权", "营业执照", "独立法人", "资格审查");
            case FRONT_TABLE_RESPONSE -> addCues(cues, "投标人须知前附表", "前附表", "商务条款响应表", "商务响应表", "关键条款", "条款响应",
                    "响应表", "偏离表", "项目概况", "质量标准", "标段", "投标有效期", "质保期", "工期");
            case RESPONSIVENESS_REVIEW -> addCues(cues, "响应性评审", "实质性响应", "商务偏离表", "技术偏离表",
                    "偏离表", "负偏离", "正偏离", "无偏离", "付款方式", "支付条款", "交货期", "供货周期", "售后服务承诺");
            case QUALIFICATION_REVIEW -> addCues(cues, "资格性评审", "类似业绩", "业绩证明", "业绩合同", "财务状况",
                    "审计报告", "财务报表", "资产负债表", "纳税证明", "社保证明", "人员配备", "项目经理", "职称", "信誉声明", "资格审查");
            case SCORING_RESPONSE -> addCues(cues, "评分标准", "评分办法", "评分细则", "技术方案", "实施方案",
                    "项目实施组织设计", "质量保证措施", "售后服务方案", "服务体系", "进度计划", "培训方案", "应急预案", "技术要求");
            case CLARIFICATION_RESPONSE -> addCues(cues, "澄清文件", "答疑纪要", "补遗书", "澄清公告",
                    "答疑文件", "变更通知", "澄清响应", "答疑响应", "确认函", "补遗响应说明");
            case DEPOSIT_RECEIPT -> addCues(cues, "投标保证金", "保证金回单", "银行回单", "电子保函", "保函凭证",
                    "汇款回执", "保证金转账", "保证金缴纳", "转账凭证", "担保凭证", "保函原件", "基本账户",
                    "响应担保", "响应保证金", "免收响应担保", "免收保证金", "不要求递交响应担保");
            case DEPOSIT_REFUND -> addCues(cues, "退款信息表", "保证金退还", "退还账户", "退款开户行", "基本存款账户",
                    "退保证金", "开户许可证", "退还响应担保", "响应担保退还");
            case BID_LETTER_FORMAT -> addCues(cues, "投标函", "报价函", "响应函", "投标书", "致招标人", "致采购人",
                    "投标总价", "投标有效期", "法人或授权代表", "投标人盖章", "法定代表人签字");
            case AUTHORIZATION_LETTER -> addCues(cues, "授权委托书", "法定代表人身份证明", "法定代表人授权书",
                    "委托代理人", "被授权人", "授权代表", "授权范围", "授权期限", "法定代表人证明书");
            case BUSINESS_LICENSE -> addCues(cues, "营业执照", "统一社会信用代码", "市场监督管理", "登记机关",
                    "事业单位法人证书", "社会团体法人登记证书", "民办非企业单位登记证书", "执业许可证");
            case ID_AUTH_LETTER -> addCues(cues, "身份证", "身份证明", "居民身份证", "公民身份号码",
                    "法定代表人身份证明", "委托代理人身份证", "受托人身份证", "社保证明", "社会保险", "人像面", "国徽面");
            case BID_OPENING_TABLE -> addCues(cues, "开标一览表", "报价一览表", "分项报价表", "报价明细表",
                    "投标报价表", "投标总价", "合价", "分项明细");
            case PRICE_CONSISTENCY -> addCues(cues, "投标总价", "投标报价", "大写", "小写", "最高限价",
                    "控制价", "采购预算", "最高投标限价", "开标一览表");
            case CA_CERTIFICATE -> addCues(cues, "CA证书", "数字证书", "电子签章", "有效性证明",
                    "证书序列号", "CFCA", "契约锁", "沃通", "CA锁", "电子标");
            default -> { }
        }
        return cues;
    }

    private static Set<String> addDynamicRequirementsCues(
            final TenderRequirements req, final BidQualityCheckItem item) {
        Set<String> cues = new LinkedHashSet<>();
        if (item == null) return cues;
        switch (item) {
            case QUALIFICATION_MATCH, QUALIFICATION_REVIEW, QUALIFICATION_VETO -> {
                req.mandatoryQualifications().forEach(qual -> addCue(cues, qual));
                if (item == BidQualityCheckItem.QUALIFICATION_VETO) req.vetoClauses().forEach(veto -> {
                    addCue(cues, veto.clause());
                    veto.keywords().forEach(kw -> addCue(cues, kw));
                });
                if (item == BidQualityCheckItem.QUALIFICATION_REVIEW && req.financialRequirement() != null) {
                    req.financialRequirement().requiredStatements().forEach(stmt -> addCue(cues, stmt));
                    addDescriptionCues(cues, req.financialRequirement().description());
                }
            }
            case SCORING_RESPONSE -> req.scoringItems().forEach(scoring -> {
                addCue(cues, scoring.name());
                addCue(cues, scoring.criteria());
            });
            case FRONT_TABLE_RESPONSE -> {
                req.compositionRequirements().forEach(comp -> addCue(cues, comp));
                req.formatRequirements().forEach(fmt -> addCue(cues, fmt));
                if (req.frontTableRequirement() != null) addDescriptionCues(cues, req.frontTableRequirement().description());
            }
            case DEPOSIT_RECEIPT, DEPOSIT_REFUND -> {
                if (req.depositRequirement() != null) addDescriptionCues(cues, req.depositRequirement().description());
            }
            case BID_LETTER_FORMAT -> {
                if (req.bidLetterRequirement() != null) {
                    addCue(cues, req.bidLetterRequirement().letterType());
                    addCue(cues, req.bidLetterRequirement().recipientName());
                    addDescriptionCues(cues, req.bidLetterRequirement().description());
                }
            }
            case AUTHORIZATION_LETTER -> {
                if (req.authorizationRequirement() != null) {
                    addCue(cues, req.authorizationRequirement().templateTitle());
                    addDescriptionCues(cues, req.authorizationRequirement().description());
                }
            }
            case BUSINESS_LICENSE -> {
                if (req.businessLicenseRequirement() != null) req.businessLicenseRequirement().allowCertificates()
                        .forEach(cert -> addCue(cues, cert));
            }
            case ID_AUTH_LETTER -> {
                if (req.idAuthLetterRequirement() != null) addDescriptionCues(cues, req.idAuthLetterRequirement().description());
            }
            case PRICE_CONSISTENCY -> {
                if (req.priceRequirement() != null) addDescriptionCues(cues, req.priceRequirement().description());
            }
            case CA_CERTIFICATE -> {
                if (req.caCertificateRequirement() != null) addCue(cues, req.caCertificateRequirement().platformName());
            }
            case RESPONSIVENESS_REVIEW -> {
                if (req.businessTermsRequirement() != null) addDescriptionCues(cues, req.businessTermsRequirement().description());
            }
            default -> { }
        }
        return cues;
    }

    private static Set<String> addDescriptionCues(final Set<String> cues, final String description) {
        if (description == null || description.isBlank()) return cues;
        for (String part : description.split("[,，。、；;：:]+")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 12) cues.add(trimmed);
        }
        return cues;
    }

    private static Set<String> addCues(final Set<String> cues, final String... items) {
        for (String item : items) addCue(cues, item);
        return cues;
    }

    private static Set<String> addCue(final Set<String> cues, final String cue) {
        if (cue != null && !cue.trim().isEmpty()) cues.add(cue.trim());
        return cues;
    }

    private static String formatPages(final List<DocumentContent.PageContent> pages) {
        if (pages == null || pages.isEmpty()) return NO_CONTENT;
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent page : pages) {
            if (page.text() == null || page.text().isBlank()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(pageHeader(page)).append('\n').append(page.text().trim());
        }
        return sb.length() == 0 ? NO_CONTENT : sb.toString();
    }

    private static String pageHeader(DocumentContent.PageContent page) {
        String chunk = "内容块 " + page.pageNumber();
        if (page.sourcePageNumber() == null) return "--- [" + chunk + "；原件页码未知] ---";
        String physical = PAGE_HEADER_PREFIX + page.sourcePageNumber() + PAGE_HEADER_SUFFIX;
        return page.sourcePageNumber() == page.pageNumber() ? physical
                : physical + "\n[" + chunk + "；来源：" + page.sourceFileName() + "]";
    }

    private static String formatSinglePage(final int pageNumber, final String text, final int budget) {
        return (text.length() > budget ? SAMPLED_NOTICE : "")
                + "--- [内容块 " + pageNumber + "；原件页码未知] ---\n"
                + (text.length() <= budget ? text : text.substring(0, budget) + "……（已截断）").trim();
    }

    /** 单页超预算硬截断：保留页码与标题元数据，正文截断至预算并标注；非正预算优雅保底不截断. */
    private static DocumentContent.PageContent truncatePage(
            final DocumentContent.PageContent page, final int charBudget) {
        String text = page.text() == null ? "" : page.text();
        if (charBudget <= 0 || text.length() <= charBudget) return page;
        return new DocumentContent.PageContent(page.pageNumber(), text.substring(0, charBudget) + "……（已截断）", page.headings(), page.sourcePageNumber(), page.sourceFileName(), page.sourceFileHash());
    }

    private record ScoredPage(DocumentContent.PageContent page, int score) {}
}
