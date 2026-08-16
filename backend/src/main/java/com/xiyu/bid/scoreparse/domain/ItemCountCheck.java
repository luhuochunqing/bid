// Input: 解析产出的评分项数量及编号列表
// Output: 数量与编号连续性校验结果（0 项判定解析失败，断号标记回补）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-006 / FR-007
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析项数量与编号连续性校验（spec 041 FR-006 / FR-007）。
 * <p>0 项 → 解析失败终态：任务 FAILED + 明确 message。
 * <p>按前缀序列检查全段编号跳跃/断号（如 A1→A10）→ 标记 needRecheck 触发完整性回补。
 */
public class ItemCountCheck {

    private static final String FAILURE_MESSAGE = "未在文件中识别到评分标准章节，请确认文件内容或手动联系管理员";
    private static final Pattern CODE_PREFIX_NUM_PATTERN = Pattern.compile("^([A-Za-z一二三四五六七八九十\\d\\.]+?[-_#、\\. ]*)(\\d+)$");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final int MAX_GAP_SPAN = 50;

    public Result check(int itemCount) {
        boolean failed = itemCount <= 0;
        return new Result(failed, failed ? FAILURE_MESSAGE : null, false, Collections.emptyList());
    }

    /** 校验数量及按前缀序列检查全段编号连续性 */
    public Result checkCandidates(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Result(true, FAILURE_MESSAGE, false, Collections.emptyList());
        }
        Map<String, List<Integer>> prefixGroups = new HashMap<>();
        List<Integer> allNumbers = new ArrayList<>();

        for (ScoreCandidate candidate : candidates) {
            String code = candidate.code();
            if (code != null && !code.isBlank()) {
                String clean = code.trim();
                Matcher pm = CODE_PREFIX_NUM_PATTERN.matcher(clean);
                if (pm.matches()) {
                    String prefix = pm.group(1);
                    int num = Integer.parseInt(pm.group(2));
                    prefixGroups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(num);
                } else {
                    Matcher dm = DIGIT_PATTERN.matcher(clean);
                    if (dm.find()) {
                        try {
                            allNumbers.add(Integer.parseInt(dm.group(1)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        List<Integer> gaps = new ArrayList<>();
        if (!prefixGroups.isEmpty()) {
            for (List<Integer> nums : prefixGroups.values()) {
                gaps.addAll(findGaps(nums));
            }
        } else {
            gaps.addAll(findGaps(allNumbers));
        }

        boolean needRecheck = !gaps.isEmpty();
        return new Result(false, null, needRecheck, Collections.unmodifiableList(gaps));
    }

    private List<Integer> findGaps(List<Integer> numbers) {
        if (numbers == null || numbers.size() < 2) {
            return Collections.emptyList();
        }
        List<Integer> sorted = numbers.stream().distinct().sorted().toList();
        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            int current = sorted.get(i);
            int next = sorted.get(i + 1);
            if (next - current > 1 && next - current <= MAX_GAP_SPAN) {
                for (int missing = current + 1; missing < next; missing++) {
                    gaps.add(missing);
                }
            }
        }
        return gaps;
    }

    /**
     * @param failed         true 表示解析失败终态
     * @param failureMessage 失败原因（面向用户展示）
     * @param needRecheck    编号断号/跳跃触发完整性回补
     * @param missingNumbers 缺失序号列表
     */
    public record Result(boolean failed, String failureMessage, boolean needRecheck, List<Integer> missingNumbers) {
        public Result(boolean failed, String failureMessage) {
            this(failed, failureMessage, false, Collections.emptyList());
        }
    }
}
