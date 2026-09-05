// Input: 候选池（code 为评分项编号原文，dim 为评分维度或大类路径）
// Output: 按章节/大类自然出现序 + 局部编号数值序重排结果（禁止跨大类全局扁平乱排）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-003 / FR-004
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项编号与层级排序策略（spec 046 FR-003）。
 * <p>核心纪律：
 * <ul>
 *   <li>1. 以文档大类/章节出现顺序（如 商务评分 -> 技术评分 -> 报价评分）或表格自然顺序为第一关键字；</li>
 *   <li>2. 同一父维度内编号唯一时，按子编号（1, 2...）数值序局部重排；编号重复则保持表格物理行序；</li>
 *   <li>3. 严禁跨大类/跨维度全局扁平排序（防止不同维度的 1, 2, 3 被强行交叉杂糅）；</li>
 *   <li>4. 同一组内不可解析编码（A1/B2/null/空白）稳定保持相对输入序，排在该组有编号项之后。</li>
 * </ul>
 */
public class ScoreItemOrderPolicy {

    private static final Pattern CODE_PATTERN = Pattern.compile("^(\\d+)(?:[-—.](\\d+))?$");
    /** 支持 "2.2.4(1)" / "3.1（2）" 复合编号：取首段整数 + 括号内子段 */
    private static final Pattern COMPOUND_CODE_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s*[（(](\\d+)[）)]$");

    /** 按章节大类自然序 + 局部编号数值序排序，返回新列表；入参为 null 或空时返回空列表 */
    public List<ScoreCandidate> sort(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        record IndexedCandidate(
                ScoreCandidate candidate,
                int originalIndex,
                String topCat,
                String parentDim,
                CodeKey codeKey
        ) {}

        Map<String, Integer> topCategoryFirstSeen = new LinkedHashMap<>();
        Map<String, Integer> parentDimFirstSeen = new LinkedHashMap<>();
        List<IndexedCandidate> list = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ScoreCandidate c = candidates.get(i);
            String topCat = extractTopCategory(c);
            String parentDim = extractParentDim(c);
            topCategoryFirstSeen.putIfAbsent(topCat, i);
            parentDimFirstSeen.putIfAbsent(parentDim, i);
            list.add(new IndexedCandidate(c, i, topCat, parentDim,
                    c == null ? null : parseKey(c.code())));
        }

        Map<String, Set<String>> seenByParent = new HashMap<>();
        Set<String> parentsWithDuplicateCodes = new HashSet<>();
        for (IndexedCandidate item : list) {
            if (item.codeKey == null || item.candidate == null || item.candidate.code() == null) {
                continue;
            }
            String code = item.candidate.code().trim();
            if (code.isEmpty()) {
                continue;
            }
            Set<String> seen = seenByParent.computeIfAbsent(item.parentDim, key -> new HashSet<>());
            if (!seen.add(code)) {
                parentsWithDuplicateCodes.add(item.parentDim);
            }
        }

        list.sort((a, b) -> {
            // 1. 第一关键字：所属大类首次出现顺序（商务 -> 技术 -> 报价 等）
            int catOrderA = topCategoryFirstSeen.getOrDefault(a.topCat, 0);
            int catOrderB = topCategoryFirstSeen.getOrDefault(b.topCat, 0);
            if (catOrderA != catOrderB) {
                return Integer.compare(catOrderA, catOrderB);
            }

            // 2. 第二关键字：所属父维度首次出现顺序（财务状况 -> 服务网点 等）
            int parentOrderA = parentDimFirstSeen.getOrDefault(a.parentDim, 0);
            int parentOrderB = parentDimFirstSeen.getOrDefault(b.parentDim, 0);
            if (parentOrderA != parentOrderB) {
                return Integer.compare(parentOrderA, parentOrderB);
            }

            // 3. 同一父格内编号重复（财务 1 与网点 1 被塌成同一父维度）时保持表格物理行序
            if (parentsWithDuplicateCodes.contains(a.parentDim)) {
                return Integer.compare(a.originalIndex, b.originalIndex);
            }

            // 4. 同父维度且编号唯一：按局部编号数值序
            if (a.codeKey != null && b.codeKey != null) {
                int byMajor = Integer.compare(a.codeKey.major(), b.codeKey.major());
                if (byMajor != 0) {
                    return byMajor;
                }
                int byMinor = Integer.compare(a.codeKey.minor(), b.codeKey.minor());
                if (byMinor != 0) {
                    return byMinor;
                }
            } else if (a.codeKey != null) {
                return -1;
            } else if (b.codeKey != null) {
                return 1;
            }

            return Integer.compare(a.originalIndex, b.originalIndex);
        });

        return list.stream().map(IndexedCandidate::candidate).toList();
    }

    private String extractTopCategory(ScoreCandidate c) {
        if (c == null) {
            return "";
        }
        String parent = c.parentDim() == null ? "" : c.parentDim().trim();
        String fromParent = extractTopCategory(parent);
        if (!fromParent.isEmpty()) {
            return fromParent;
        }
        return extractTopCategory(c.dim());
    }

    private String extractTopCategory(String dim) {
        if (dim == null || dim.isBlank()) {
            return "";
        }
        String trimmed = dim.trim();
        String[] parts = trimmed.split("\\s*[/／\\\\]\\s*");
        if (parts.length > 1) {
            return normalizeCategoryName(parts[0]);
        }
        return isExplicitCategory(trimmed) ? normalizeCategoryName(trimmed) : "";
    }

    private String extractParentDim(ScoreCandidate c) {
        if (c == null) {
            return "";
        }
        String parent = c.parentDim() == null ? "" : c.parentDim().trim();
        if (!parent.isEmpty()) {
            String[] parts = parent.split("\\s*[/／\\\\]\\s*");
            return parts[0].trim();
        }
        return extractParentDim(c.dim());
    }

    private String extractParentDim(String dim) {
        if (dim == null || dim.isBlank()) {
            return "";
        }
        String trimmed = dim.trim();
        String[] parts = trimmed.split("\\s*[/／\\\\]\\s*");
        if (parts.length >= 2) {
            return normalizeCategoryName(parts[0]) + "/" + parts[1].trim();
        }
        return isExplicitCategory(trimmed) ? normalizeCategoryName(trimmed) : trimmed;
    }

    private static boolean isExplicitCategory(String s) {
        return s.contains("商务") || s.contains("技术") || s.contains("报价")
                || s.contains("价格") || s.contains("资信") || s.contains("经济")
                || s.contains("业绩") || s.contains("方案");
    }

    private String normalizeCategoryName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.contains("商务") || s.contains("资信") || s.contains("经济") || s.contains("业绩")) {
            return "商务评分";
        }
        if (s.contains("技术") || s.contains("方案")) {
            return "技术评分";
        }
        if (s.contains("报价") || s.contains("价格")) {
            return "报价评分";
        }
        return s;
    }

    private CodeKey parseKey(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        // 先尝试简单编号（4 / 4-1 / 4.1）
        Matcher matcher = CODE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            try {
                int major = Integer.parseInt(matcher.group(1));
                int minor = matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2));
                return new CodeKey(major, minor);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // 再尝试复合编号（2.2.4(1) / 3.1（2））：取首段整数 + 括号内子段
        Matcher cm = COMPOUND_CODE_PATTERN.matcher(trimmed);
        if (cm.matches()) {
            try {
                String majorStr = cm.group(1).split("\\.")[0];
                int major = Integer.parseInt(majorStr);
                int minor = Integer.parseInt(cm.group(2));
                return new CodeKey(major, minor);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * @param major 主段数值
     * @param minor 子段数值（无子段为 -1，排序时视同纯数值最小）
     */
    private record CodeKey(int major, int minor) {
    }
}
