// Input: 投标文件 DocumentContent + 招标前附表要求 FrontTableRequirement
// Output: QualityCheckItemResult（PASS / FAIL）或 null（存疑移交 LLM lane）
// Pos: compliance/domain/policy/ - 前附表关键条款填写完整性与表格留白反假核验策略（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前附表关键条款填写完整性与反假核验策略（纯核心）.
 */
public final class FrontTableResponsePolicy {

    private static final List<String> FRONT_TABLE_KEYWORDS = List.of(
            "前附表关键条款响应表", "投标人须知前附表关键条款响应", "前附表响应表",
            "前附表关键条款响应", "投标人须知前附表响应", "前附表响应", "关键条款响应表", "前附表");

    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|此处)[^】]{0,30}】|\\[(?:请|在此|填写|待填)[^\\]]{0,30}\\]|【(?:交货期|工期|质保期|有效期)[^】]{0,10}】");

    private FrontTableResponsePolicy() {
    }

    /**
     * 核查投标文件前附表关键条款填写完整性与反假.
     */
    // REUSE-OK: * — 质检前附表关键条款核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc, final FrontTableRequirement req) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return null;
        }

        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);
        if (candidatePages.isEmpty()) {
            // 全文若未出现前附表关键词，留交 LLM 语义裁决
            return null;
        }

        // 1. 过滤目录页：若候选页均为目录点线，判定 FAIL
        boolean allCatalog = candidatePages.stream().allMatch(p -> isCatalogDotLine(p.text()));
        if (allCatalog) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "前附表条目（目录）", FRONT_TABLE_KEYWORDS);
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                    "投标文件仅在目录中列出前附表条目，未检索到实质性前附表正文或响应表章节",
                    "请在标书正文中完整编制前附表关键条款响应表，切勿仅在目录中列出标题",
                    List.of(evidence));
        }

        // 2. 检查正文候选页是否存在未填写的占位符或下划线留白
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) {
                continue;
            }
            String placeholder = findPlaceholder(page.text());
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "前附表占位留白", List.of(placeholder));
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                        "前附表关键条款中存在未填写的占位符或下划线留白（" + placeholder + "）",
                        "请在标书前附表响应中补全具体应答内容，不得保留空白下划线或模板占位符",
                        List.of(evidence));
            }

            TableCellIntegrityCalculator.TableIntegrityResult tableResult =
                    TableCellIntegrityCalculator.validateText(page.pageNumber(), page.text());
            if (tableResult.hasViolation()) {
                TableCellIntegrityCalculator.CellViolation first = tableResult.violations().getFirst();
                QualityEvidence evidence = QualityEvidence.bid(
                        "前附表表格填写缺陷",
                        page.pageNumber(),
                        "第" + first.rowNumber() + "行" + (first.colHeader().isBlank() ? "" : "「" + first.colHeader() + "」列")
                                + "存在填写缺陷: " + first.violationType() + " (原单元格内容: " + first.cellContent() + ")"
                );
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                        "前附表响应表格中存在填写不完整单元格（" + first.violationType() + "）",
                        "请检查前附表响应表格中的留白、斜杠及占位符，务必如实填写具体响应参数与承诺",
                        List.of(evidence));
            }
        }

        // 3. 检查正文候选页是否包含实质性前附表响应（工期/质保期/有效期/质量标准等）
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) {
                continue;
            }
            String text = page.text();
            boolean hasResponseCues = text.contains("工期") || text.contains("交货期") || text.contains("供货周期")
                    || text.contains("质保期") || text.contains("保修期") || text.contains("有效期")
                    || text.contains("质量标准") || text.contains("付款方式");
            boolean hasSubstantiveResponse = text.contains("响应") || text.contains("满足") || text.contains("承诺")
                    || text.contains("日历天") || text.contains("月") || text.contains("年") || text.contains("天");
            if (hasResponseCues && hasSubstantiveResponse) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "前附表关键条款响应表", FRONT_TABLE_KEYWORDS);
                return QualityCheckItemResult.pass(
                        BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                        "前附表关键条款填写完整，核心商务条款均已实质性响应且无占位留白",
                        List.of(evidence));
            }
        }

        return null;
    }

    private static List<DocumentContent.PageContent> findCandidatePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> list = new ArrayList<>();
        if (bidDoc.pages() != null && !bidDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : bidDoc.pages()) {
                if (page.text() == null) {
                    continue;
                }
                for (String kw : FRONT_TABLE_KEYWORDS) {
                    if (page.text().contains(kw)) {
                        list.add(page);
                        break;
                    }
                }
            }
        }
        if (list.isEmpty() && bidDoc.fullText() != null) {
            for (String kw : FRONT_TABLE_KEYWORDS) {
                if (bidDoc.fullText().contains(kw)) {
                    list.add(new DocumentContent.PageContent(1, bidDoc.fullText(), List.of()));
                    break;
                }
            }
        }
        return list;
    }

    private static boolean isCatalogDotLine(final String text) {
        if (text == null) {
            return false;
        }
        return text.contains("......") || text.contains(". . . .") || text.contains("……");
    }

    private static String findPlaceholder(final String text) {
        Matcher m1 = UNDERLINE_BLANK_PATTERN.matcher(text);
        if (m1.find()) {
            return m1.group();
        }
        Matcher m2 = BRACKET_PLACEHOLDER_PATTERN.matcher(text);
        if (m2.find()) {
            return m2.group();
        }
        return null;
    }
}
