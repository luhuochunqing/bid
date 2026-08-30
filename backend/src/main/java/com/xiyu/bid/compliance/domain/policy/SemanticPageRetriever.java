// Input: 检查项 + 招标要求 + 标书文档 + 字符预算
// Output: 格式化后的相关页面上下文（含真实页码 --- [第 X 页] ---），预算内全文透传，超预算 TOP-N 相关页
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

/**
 * 标书质量核查纯领域语义页面检索器 (FP-Java).
 *
 * <p>零框架依赖、无 I/O、无异常流、不可变纯函数。
 * 负责根据检查项主题、静态关键词及招标结构化要求，对标书分页文档进行相关性检索打分并受控预算组装上下文，
 * 彻底替换旧版前缀硬截断，保留物理页码元数据 {@code --- [第 X 页] ---}。
 */
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

    private SemanticPageRetriever() {
    }

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

    /**
     * 执行分页文档语义检索与预算上下文拼装.
     */
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

        // 按得分降序排序，同分按页码升序稳定排序
        scoredPages.sort(Comparator.comparingInt(ScoredPage::score).reversed()
                .thenComparingInt(sp -> sp.page().pageNumber()));

        List<DocumentContent.PageContent> selected = new ArrayList<>();
        int accumulatedChars = 0;
        for (ScoredPage sp : scoredPages) {
            int pageLen = sp.page().text() != null ? sp.page().text().length() : 0;
            if (selected.isEmpty() || accumulatedChars + pageLen <= charBudget) {
                selected.add(sp.page());
                accumulatedChars += pageLen;
                if (accumulatedChars >= charBudget) {
                    break;
                }
            }
        }

        // 重新按自然页码升序排列输出
        selected.sort(Comparator.comparingInt(DocumentContent.PageContent::pageNumber));
        return formatPages(selected);
    }

    /** 单页相关性打分算法. */
    private static int scorePage(final DocumentContent.PageContent page, final Set<String> cues) {
        if (page == null) {
            return 0;
        }
        String text = page.text() == null ? "" : page.text();
        List<String> headings = page.headings() == null ? List.of() : page.headings();
        if (text.isBlank() && headings.isEmpty()) {
            return 0;
        }

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

    /** 快速字串出现频次统计 (O(N) 纯扫描，零异常流). */
    private static int countOccurrences(final String text, final String sub) {
        if (text == null || sub == null || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /** 收集检查项静态线索与招标要求动态线索. */
    private static Set<String> collectQueryCues(final BidQualityCheckItem item, final TenderRequirements req) {
        Set<String> cues = new LinkedHashSet<>();
        if (item != null) {
            addCue(cues, item.getTitle());
            addDescriptionCues(cues, item.getDescription());
            addStaticItemCues(cues, item);
        }
        if (req != null && !req.missing()) {
            addDynamicRequirementsCues(cues, req);
        }
        return cues;
    }

    private static Set<String> addStaticItemCues(final Set<String> cues, final BidQualityCheckItem item) {
        switch (item) {
            case QUALIFICATION_MATCH -> addCues(cues, "资质", "证书", "认证", "ISO", "质量管理体系", "环境管理体系",
                    "职业健康安全", "体系认证", "等级", "有效期", "认证机构", "资格证明", "资质证书");
            case QUALIFICATION_VETO -> addCues(cues, "资格条件", "一票否决", "废标", "实质性要求", "强制性要求",
                    "授权书", "品牌授权", "原厂授权", "制造商授权", "营业执照", "独立法人", "资格审查");
            case FRONT_TABLE_RESPONSE -> addCues(cues, "投标人须知前附表", "前附表", "关键条款", "条款响应",
                    "响应表", "偏离表", "项目概况", "质量标准", "标段", "投标有效期", "质保期", "工期");
            case RESPONSIVENESS_REVIEW -> addCues(cues, "响应性评审", "实质性响应", "商务偏离表", "技术偏离表",
                    "偏离表", "负偏离", "正偏离", "无偏离", "付款方式", "支付条款", "交货期", "供货周期", "售后服务承诺");
            case QUALIFICATION_REVIEW -> addCues(cues, "资格性评审", "类似业绩", "业绩证明", "业绩合同", "财务状况",
                    "审计报告", "财务报表", "资产负债表", "纳税证明", "社保证明", "人员配备", "项目经理", "职称", "信誉声明", "资格审查");
            case SCORING_RESPONSE -> addCues(cues, "评分标准", "评分办法", "评分细则", "技术方案", "实施方案",
                    "项目实施组织设计", "质量保证措施", "售后服务方案", "服务体系", "进度计划", "培训方案", "应急预案", "技术要求");
            case CLARIFICATION_RESPONSE -> addCues(cues, "澄清文件", "答疑纪要", "补遗书", "澄清公告",
                    "答疑文件", "变更通知", "澄清响应", "答疑响应", "确认函", "补遗响应说明");
            default -> { }
        }
        return cues;
    }

    private static Set<String> addDynamicRequirementsCues(final Set<String> cues, final TenderRequirements req) {
        req.mandatoryQualifications().forEach(qual -> addCue(cues, qual));
        req.scoringItems().forEach(scoring -> {
            addCue(cues, scoring.name());
            addCue(cues, scoring.criteria());
        });
        req.vetoClauses().forEach(veto -> {
            addCue(cues, veto.clause());
            veto.keywords().forEach(kw -> addCue(cues, kw));
        });
        req.compositionRequirements().forEach(comp -> addCue(cues, comp));
        req.formatRequirements().forEach(fmt -> addCue(cues, fmt));
        return cues;
    }

    private static Set<String> addDescriptionCues(final Set<String> cues, final String description) {
        if (description == null || description.isBlank()) {
            return cues;
        }
        String[] parts = description.split("[,，。、；;：:]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 12) {
                cues.add(trimmed);
            }
        }
        return cues;
    }

    private static Set<String> addCues(final Set<String> cues, final String... items) {
        for (String item : items) {
            addCue(cues, item);
        }
        return cues;
    }

    private static Set<String> addCue(final Set<String> cues, final String cue) {
        if (cue != null) {
            String trimmed = cue.trim();
            if (!trimmed.isEmpty()) {
                cues.add(trimmed);
            }
        }
        return cues;
    }

    private static String formatPages(final List<DocumentContent.PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return NO_CONTENT;
        }
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(PAGE_HEADER_PREFIX).append(page.pageNumber()).append(PAGE_HEADER_SUFFIX)
                    .append('\n').append(page.text().trim());
        }
        return sb.length() == 0 ? NO_CONTENT : sb.toString();
    }

    private static String formatSinglePage(final int pageNumber, final String text, final int budget) {
        String content = text.length() <= budget ? text : text.substring(0, budget) + "……（已截断）";
        return PAGE_HEADER_PREFIX + pageNumber + PAGE_HEADER_SUFFIX + "\n" + content.trim();
    }

    private record ScoredPage(DocumentContent.PageContent page, int score) {
    }
}
