// Input: 投标文件 DocumentContent + 招标要求 TenderRequirements
// Output: Optional<QualityCheckItemResult>（评分留白只提示人工，不从自然文本推断必填义务或判FAIL；证据保留输入页号或null）
// Pos: compliance/domain/policy/ - 评分标准逐项对应与支撑材料核验策略（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分标准逐项对应与支撑材料核验策略（纯核心）.
 */
public final class ScoringResponsePolicy {

    private static final List<String> SCORING_KEYWORDS = List.of(
            "评分标准对应表", "评分细则响应表", "评分细则对应表", "得分点对应表", "评分索引表",
            "评分标准响应", "技术方案与组织设计", "技术方案与项目实施", "技术方案", "施工组织设计",
            "项目实施方案", "实施组织设计", "项目管理机构", "服务方案", "售后服务方案");

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "_{3,}|【(?:请|在此|在此处|此处|填写|输入|插入|粘贴|待填|说明)[^】]{0,30}】|\\[(?:请|在此|此处|填写|粘贴|待填|说明)[^\\]]{0,30}\\]|【(?:技术方案|实施方案|团队人员|服务方案)[^】]{0,15}】");

    private static final Pattern SCORING_FIELD = Pattern.compile(
            "技术方案|实施方案|施工组织|项目经理|项目管理|团队人员|服务方案|进度计划|技术架构|人员配置");

    private ScoringResponsePolicy() {
    }

    /**
     * 评分项响应第一阶段确定性核验.
     *
     * @return 留白或仅目录时人工确认；其余返回 empty 移交 LLM 语义裁决
     */
    // REUSE-OK: * — 质检评分标准响应核查入口，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc, final TenderRequirements requirements) {
        List<EvidenceTextSlice> slices = EvidenceTextSlice.from(bidDoc);
        if (slices.isEmpty()) {
            return Optional.empty();
        }

        List<EvidenceTextSlice> candidatePages = findCandidatePages(slices);

        // 1. 目录召回不能证明全文缺失，保留人工确认
        boolean allCatalog = !candidatePages.isEmpty() && candidatePages.stream().allMatch(EvidenceTextSlice::isCatalogPage);
        // 字段与相邻行共同定位；保留输入切片来源，不把整页合同示例归为评分字段。
        String precedingLine = "";
        for (EvidenceTextSlice page : slices) {
            if (page.isCatalogPage()) {
                precedingLine = "";
                continue;
            }
            PlaceholderHit match = findScoringPlaceholder(page.text(), precedingLine, requirements);
            if (match != null) {
                QualityEvidence evidence = QualityEvidence.bid("技术方案占位留白", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(page.text(), match.start(), match.end(), 200));
                return Optional.of(ManualReasons.manual(BidQualityCheckItem.SCORING_RESPONSE, ManualReasons.AMBIGUOUS,
                        "评分相关文本存在留白，尚不能确认该位置是本次必填应答或参考样例",
                        "请对照招标评分细则与原文件核对留白用途，确认是否需要补充本次应答", List.of(evidence)));
            }
            precedingLine = lastNonBlankLine(page.text());
        }

        if (allCatalog) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "评分响应条目（目录）", SCORING_KEYWORDS);
            return Optional.of(ManualReasons.manual(
                    BidQualityCheckItem.SCORING_RESPONSE, ManualReasons.EVIDENCE_INSUFFICIENT,
                    "仅检索到评分响应目录索引，尚不能确认正文评分点的实际响应",
                    "请对照评分细则核对真实响应章节与得分点，目录索引仅供定位",
                    List.of(evidence)));
        }

        // 无硬伤：移交 LLM lane 进行评分点深度对照与打分
        return Optional.empty();
    }

    private static List<EvidenceTextSlice> findCandidatePages(final List<EvidenceTextSlice> slices) {
        return slices.stream()
                .filter(page -> SCORING_KEYWORDS.stream().anyMatch(page.text()::contains))
                .toList();
    }

    /** 只召回评分相关留白供人工核对，不推断字段用途、义务或样例范围。 */
    private static PlaceholderHit findScoringPlaceholder(final String text, final String precedingLine,
            final TenderRequirements requirements) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            int lineStart = text.lastIndexOf('\n', matcher.start()) + 1;
            int start = lineStart;
            for (char separator : new char[]{'。', '；', ';', '，', ','}) {
                start = Math.max(start, text.lastIndexOf(separator, matcher.start()) + 1);
            }
            String field = text.substring(start, matcher.end());
            if (text.substring(lineStart, matcher.start()).isBlank()) {
                String previous = lineStart == 0 ? precedingLine : lastNonBlankLine(text.substring(0, lineStart));
                if (previous.endsWith(":") || previous.endsWith("：") || SCORING_FIELD.matcher(previous).matches()) {
                    field = previous + field;
                }
            }
            String label = field.split("[:：_【\\[]", 2)[0].strip();
            boolean mentionedByScoring = requirements != null && label.length() >= 2
                    && !List.of("备注", "说明", "附件", "内容", "其他").contains(label)
                    && requirements.scoringItems().stream().filter(java.util.Objects::nonNull)
                        .anyMatch(item -> label.equals(item.name())
                                || item.criteria() != null && item.criteria().contains(label));
            if (SCORING_FIELD.matcher(field).find() || mentionedByScoring
                    || SCORING_KEYWORDS.stream().anyMatch(text::contains)) {
                return new PlaceholderHit(matcher.start(), matcher.end());
            }
        }
        return null;
    }

    private record PlaceholderHit(int start, int end) { }

    private static String lastNonBlankLine(String text) {
        String[] lines = text.stripTrailing().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }
}
