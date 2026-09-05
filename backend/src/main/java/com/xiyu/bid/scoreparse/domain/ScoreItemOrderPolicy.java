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

    private static final Pattern CHINESE_CHAPTER_PATTERN =
            Pattern.compile("^[（(]?[一二三四五六七八九十]+[）)]?[、.．\\s]");

    /** 按章节大类自然序 + 局部编号数值序排序，返回新列表；入参为 null 或空时返回空列表 */
    public List<ScoreCandidate> sort(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        record IndexedCandidate(
                ScoreCandidate candidate,
                int originalIndex,
                String topCat,
                int chapterIndex,
                String parentDim,
                CodeKey codeKey
        ) {}

        Map<String, Integer> topCategoryFirstSeen = new LinkedHashMap<>();
        Map<String, Integer> parentDimFirstSeen = new LinkedHashMap<>();
        List<IndexedCandidate> list = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ScoreCandidate c = candidates.get(i);
            String topCat = extractTopCategory(c);
            int chapterIndex = extractChapterIndex(c);
            String parentDim = extractParentDim(c);
            topCategoryFirstSeen.putIfAbsent(topCat, i);
            parentDimFirstSeen.putIfAbsent(parentDim, i);
            list.add(new IndexedCandidate(c, i, topCat, chapterIndex, parentDim,
                    c == null ? null : parseKey(c.code())));
        }

        Map<String, Set<String>> seenByParent = new HashMap<>();
        Set<String> parentsWithDuplicateCodes = new HashSet<>();
        Map<Integer, Set<String>> seenByChapter = new HashMap<>();
        Set<Integer> chaptersWithDuplicateCodes = new HashSet<>();
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
            if (item.chapterIndex > 0) {
                Set<String> chapterSeen = seenByChapter.computeIfAbsent(item.chapterIndex, key -> new HashSet<>());
                if (!chapterSeen.add(code)) {
                    chaptersWithDuplicateCodes.add(item.chapterIndex);
                }
            }
        }

        list.sort((a, b) -> {
            // 0. 中文章节大类序（一、二、三、四...）：若都有中文大类序号，严格按大类序号数值升序
            if (a.chapterIndex > 0 && b.chapterIndex > 0 && a.chapterIndex != b.chapterIndex) {
                return Integer.compare(a.chapterIndex, b.chapterIndex);
            }
            if (a.chapterIndex > 0 && b.chapterIndex <= 0) {
                return -1;
            }
            if (a.chapterIndex <= 0 && b.chapterIndex > 0) {
                return 1;
            }

            // 0.5 若属于相同中文章节（如"四、综合评价"）且本章节内编号唯一无重复，优先按编号数值排序
            if (a.chapterIndex > 0 && a.chapterIndex == b.chapterIndex
                    && !chaptersWithDuplicateCodes.contains(a.chapterIndex)) {
                int codeCmp = compareCodeKeys(a.codeKey, b.codeKey);
                if (codeCmp != 0) return codeCmp;
            }

            // 1. 第一关键字：所属大类首次出现顺序（商务 -> 技术 -> 报价 等）
            int catOrderA = topCategoryFirstSeen.getOrDefault(a.topCat, 0);
            int catOrderB = topCategoryFirstSeen.getOrDefault(b.topCat, 0);
            if (catOrderA != catOrderB) return Integer.compare(catOrderA, catOrderB);

            // 2. 第二关键字：所属父维度首次出现顺序（财务状况 -> 服务网点 等）
            int parentOrderA = parentDimFirstSeen.getOrDefault(a.parentDim, 0);
            int parentOrderB = parentDimFirstSeen.getOrDefault(b.parentDim, 0);
            if (parentOrderA != parentOrderB) return Integer.compare(parentOrderA, parentOrderB);

            // 3. 同一父格内编号重复（财务 1 与网点 1 被塌成同一父维度）时保持表格物理行序
            if (parentsWithDuplicateCodes.contains(a.parentDim)) {
                return Integer.compare(a.originalIndex, b.originalIndex);
            }

            // 4. 同父维度且编号唯一：按局部编号数值序
            int codeCmp = compareCodeKeys(a.codeKey, b.codeKey);
            if (codeCmp != 0) return codeCmp;

            return Integer.compare(a.originalIndex, b.originalIndex);
        });

        return list.stream().map(IndexedCandidate::candidate).toList();
    }

    private static int compareCodeKeys(CodeKey a, CodeKey b) {
        if (a != null && b != null) {
            int byMajor = Integer.compare(a.major(), b.major());
            return byMajor != 0 ? byMajor : Integer.compare(a.minor(), b.minor());
        }
        return a != null ? -1 : (b != null ? 1 : 0);
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

    private int extractChapterIndex(ScoreCandidate c) {
        if (c == null) {
            return -1;
        }
        String parent = c.parentDim() == null ? "" : c.parentDim().trim();
        int idx = parseChineseNumber(parent);
        if (idx > 0) {
            return idx;
        }
        return parseChineseNumber(c.dim());
    }

    private static int parseChineseNumber(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String trimmed = text.trim();
        String[] parts = trimmed.split("\\s*[/／\\\\]\\s*");
        String candidate = parts[0].trim();
        Matcher m = CHINESE_CHAPTER_PATTERN.matcher(candidate);
        if (m.find()) {
            String numStr = m.group().replaceAll("[（()）]?[、.．\\s]", "");
            return chineseToNumber(numStr);
        }
        return -1;
    }

    private static final String CHINESE_DIGITS = "一二三四五六七八九十";
    private static int chineseToNumber(String s) {
        return (s != null && s.length() == 1) ? CHINESE_DIGITS.indexOf(s) + 1 : -1;
    }

    private static boolean isExplicitCategory(String s) {
        return s.contains("商务") || s.contains("技术") || s.contains("报价")
                || s.contains("价格") || s.contains("资信") || s.contains("经济")
                || s.contains("业绩") || s.contains("方案")
                || s.contains("综合") || s.contains("诚信") || s.contains("信用")
                || s.contains("财务") || s.contains("绿色") || s.contains("低碳")
                || s.contains("环保");
    }

    private String normalizeCategoryName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim().replaceAll("^[（(]?[一二三四五六七八九十]+[）)]?[、.．\\s]*", "").trim();
        if (s.contains("诚信") || s.contains("信用")) return "诚信评价";
        if (s.contains("财务")) return "财务状况";
        if (s.contains("绿色") || s.contains("低碳") || s.contains("环保")) return "绿色数智评价";
        if (s.contains("综合")) return "综合评价";
        if (s.contains("商务") || s.contains("资信") || s.contains("经济") || s.contains("业绩")) return "商务评分";
        if (s.contains("技术") || s.contains("方案")) return "技术评分";
        if (s.contains("报价") || s.contains("价格")) return "报价评分";
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
