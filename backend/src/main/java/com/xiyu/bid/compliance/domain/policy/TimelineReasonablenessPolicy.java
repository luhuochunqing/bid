// Input: 投标文件 DocumentContent + 招标商务条款要求 BusinessTermsRequirement（工期天数）
// Output: 进度计划逻辑合理性核验结果（PASS / FAIL / null）
// Pos: compliance/domain/policy/ - 进度计划逻辑合理性核验策略（FP-Java，纯核心，行预算严格 <= 260）
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
 * 进度计划逻辑合理性核验策略（纯核心）.
 *
 * <p>检测维度：
 * <ol>
 *   <li>开竣工日期倒挂（竣工日期早于开工日期）</li>
 *   <li>标书自述工期与招标天数矛盾（超出招标最大工期要求）</li>
 *   <li>关键节点里程碑（横道图/甘特图）占位模板未填</li>
 *   <li>工期总天数排期推算异常（开竣工跨度与自述工期不一致）</li>
 * </ol>
 */
public final class TimelineReasonablenessPolicy {

    // 匹配开工日期（如"开工日期：2024年3月1日"、"计划开工：2024-03-01"）
    private static final Pattern START_DATE_PATTERN = Pattern.compile(
            "(?:计划)?开工(?:日期|时间)?[：:、]?\\s*(20[2-9][0-9])年(1[0-2]|0?[1-9])月(3[01]|[12][0-9]|0?[1-9])日");

    // 匹配竣工日期
    private static final Pattern END_DATE_PATTERN = Pattern.compile(
            "(?:计划)?竣工(?:日期|时间)?[：:、]?\\s*(20[2-9][0-9])年(1[0-2]|0?[1-9])月(3[01]|[12][0-9]|0?[1-9])日");

    // 匹配自述总工期（如"总工期：180日历天"、"工期：60天"）
    private static final Pattern STATED_DURATION_PATTERN = Pattern.compile(
            "(?:总)?工期[：:]?\\s*([1-9][0-9]{0,3})\\s*(?:日历天|日历日|天|个?工作日|个?月|个?自然日)");

    // 匹配横道图/甘特图占位模板
    private static final Pattern GANTT_PLACEHOLDER_PATTERN = Pattern.compile(
            "(?:横道图|甘特图|进度计划图|施工进度计划)[^\\n]{0,30}(?:________|【待填】|【此处】|（格式范本）|（参考模板）|（详见附件）|省略)");

    // 匹配进度计划章节标题（用于判断是否存在进度计划正文）
    private static final List<String> SCHEDULE_KEYWORDS = List.of(
            "施工进度计划", "工程进度计划", "进度安排", "里程碑计划", "施工工期安排",
            "工期保证措施", "横道图", "网络计划");

    private TimelineReasonablenessPolicy() {
    }

    /**
     * 核验进度计划逻辑合理性.
     *
     * @param bidDoc   投标文件内容
     * @param maxDays  招标文件要求的最大工期天数（null 表示未提取到，跳过超期校验）
     * @return 核验结果，null 表示无法确定（移交 LLM 语义裁决）
     */
    public static QualityCheckItemResult checkTimeline(
            final DocumentContent bidDoc, final Integer maxDays) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return null;
        }

        List<DocumentContent.PageContent> schedulePagesFound = findSchedulePages(bidDoc);
        if (schedulePagesFound.isEmpty()) {
            // 未找到进度计划关键字，留交 LLM 裁决
            return null;
        }

        List<QualityEvidence> evidences = new ArrayList<>();
        for (DocumentContent.PageContent page : schedulePagesFound) {
            String text = page.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            // 1. 检测占位模板未填（横道图/甘特图）
            Matcher ganttM = GANTT_PLACEHOLDER_PATTERN.matcher(text);
            if (ganttM.find()) {
                QualityEvidence ev = QualityEvidence.bid("进度计划占位模板", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, ganttM.group()));
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.SCHEDULE_RESPONSE,
                        "进度计划中检测到未填写的横道图/甘特图占位模板（" + ganttM.group() + "）",
                        "请在标书中替换占位模板，填写实际进度计划横道图或甘特图",
                        List.of(ev));
            }

            // 2. 检测开竣工日期倒挂
            Matcher startM = START_DATE_PATTERN.matcher(text);
            Matcher endM = END_DATE_PATTERN.matcher(text);
            if (startM.find() && endM.find()) {
                int startOrdinal = toOrdinal(
                        Integer.parseInt(startM.group(1)),
                        Integer.parseInt(startM.group(2)),
                        Integer.parseInt(startM.group(3)));
                int endOrdinal = toOrdinal(
                        Integer.parseInt(endM.group(1)),
                        Integer.parseInt(endM.group(2)),
                        Integer.parseInt(endM.group(3)));
                if (endOrdinal <= startOrdinal) {
                    String snippet = BasicInfoCheckPolicy.snippetAround(text, 200,
                            startM.group(), endM.group());
                    evidences.add(QualityEvidence.bid("开竣工日期冲突", page.pageNumber(), snippet));
                    return QualityCheckItemResult.fail(
                            BidQualityCheckItem.SCHEDULE_RESPONSE,
                            "进度计划开竣工日期倒挂：开工日期（" + startM.group() + "）晚于或等于竣工日期（" + endM.group() + "）",
                            "请核实并修正进度计划中的开工与竣工日期，确保竣工日期晚于开工日期",
                            evidences);
                }
                // 推算跨度天数与自述工期是否一致
                int spanDays = endOrdinal - startOrdinal;
                Matcher durationM = STATED_DURATION_PATTERN.matcher(text);
                if (durationM.find()) {
                    int statedDays = Integer.parseInt(durationM.group(1));
                    // 跨度差距超过 10 天发预警（宽容工作日 vs 日历天差异）
                    if (Math.abs(spanDays - statedDays) > 10 && spanDays > 0 && statedDays > 0) {
                        String snippet = BasicInfoCheckPolicy.snippetAround(text, 200,
                                startM.group(), endM.group(), durationM.group());
                        evidences.add(QualityEvidence.bid("工期推算差异", page.pageNumber(), snippet));
                    }
                }
                // 3. 检测超过招标最大工期
                if (maxDays != null && spanDays > maxDays + 2) {
                    String snippet = BasicInfoCheckPolicy.snippetAround(text, 150,
                            startM.group(), endM.group());
                    evidences.add(QualityEvidence.bid("工期超标", page.pageNumber(), snippet));
                    return QualityCheckItemResult.fail(
                            BidQualityCheckItem.SCHEDULE_RESPONSE,
                            String.format(
                                    "进度计划开竣工跨度（%d 天）超出招标最大工期要求（%d 天），不满足工期约束",
                                    spanDays, maxDays),
                            "请缩短施工工期计划，确保开竣工跨度在招标文件规定的最大工期内",
                            evidences);
                }
            }

            // 4. 检测自述工期超限
            Matcher durationM = STATED_DURATION_PATTERN.matcher(text);
            if (durationM.find() && maxDays != null) {
                int statedDays = Integer.parseInt(durationM.group(1));
                if (statedDays > maxDays) {
                    String snippet = BasicInfoCheckPolicy.snippetAround(text, 150, durationM.group());
                    evidences.add(QualityEvidence.bid("自述工期超限", page.pageNumber(), snippet));
                    return QualityCheckItemResult.fail(
                            BidQualityCheckItem.SCHEDULE_RESPONSE,
                            String.format(
                                    "标书自述工期 %d 天，超出招标要求最大工期 %d 天",
                                    statedDays, maxDays),
                            "请将工期响应修改为不超过招标文件规定的最大工期天数",
                            evidences);
                }
            }
        }

        // 有进度计划正文但无明显异常，移交 LLM 做深度语义核验
        if (!evidences.isEmpty()) {
            // 有工期推算差异预警，返回人工复核
            return QualityCheckItemResult.manualReview(
                    BidQualityCheckItem.SCHEDULE_RESPONSE,
                    "进度计划存在工期推算差异（开竣工跨度与自述天数不一致），建议人工核验",
                    "请核实进度计划横道图日期与投标函承诺工期是否一致",
                    evidences);
        }
        return null;
    }

    private static List<DocumentContent.PageContent> findSchedulePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> result = new ArrayList<>();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (page.text() == null) {
                continue;
            }
            for (String kw : SCHEDULE_KEYWORDS) {
                if (page.text().contains(kw)) {
                    result.add(page);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 将年月日转换为纪元天数（公历无分支纯算术算法，满足 FP-Java 零异常约束）.
     */
    static int toOrdinal(final int year, final int month, final int day) {
        int y = month <= 2 ? year - 1 : year;
        int m = month <= 2 ? month + 12 : month;
        int era = (y >= 0 ? y : y - 399) / 400;
        int yoe = y - era * 400;
        int doy = (153 * (m - 3) + 2) / 5 + day - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }
}
