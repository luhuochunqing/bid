// Input: 项目特定评分词汇（维度名、别名、关键词）
// Output: 注册表查询结果（用于 prompt 注入或候选过滤）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: Phase 5 escape-hatch 注册表（词汇治理）——将项目特定示例从硬编码 prompt 中解耦，
//   允许按项目/租户注册评分词汇，避免 prompt 污染其他项目解析
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 评分词汇 escape-hatch 注册表（Phase 5 词汇治理）。
 * <p>集中管理项目特定的评分维度名、别名、关键词，用于：
 * <ul>
 *   <li>prompt 注入：将注册词汇作为示例注入 LLM prompt，提高特定项目解析准确率</li>
 *   <li>候选过滤：识别并保留注册词汇匹配的候选项，防止被归并丢弃</li>
 *   <li>权重校验：注册词汇可携带期望权重范围，用于校验解析结果</li>
 * </ul>
 * <p>纯核心，无框架依赖，线程安全（不可变注册后只读）。
 */
public final class ScoreVocabularyRegistry {

    /** 注册条目：维度名 + 别名集合 + 关键词集合 + 期望权重范围（可选） */
    public record Entry(
            String dim,
            Set<String> aliases,
            Set<String> keywords,
            Double minWeight,
            Double maxWeight
    ) {
        public Entry(String dim, Set<String> aliases, Set<String> keywords) {
            this(dim, aliases, keywords, null, null);
        }

        public boolean matches(String text) {
            if (text == null || text.isBlank()) return false;
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (dim != null && normalized.contains(dim.toLowerCase(Locale.ROOT))) return true;
            for (String alias : aliases) {
                if (normalized.contains(alias.toLowerCase(Locale.ROOT))) return true;
            }
            for (String kw : keywords) {
                if (normalized.contains(kw.toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        }

        public boolean isWeightInRange(double weight) {
            if (minWeight == null && maxWeight == null) return true;
            if (minWeight != null && weight < minWeight) return false;
            if (maxWeight != null && weight > maxWeight) return false;
            return true;
        }
    }

    private final Map<String, List<Entry>> projectEntries;
    private final List<Entry> globalEntries;

    private ScoreVocabularyRegistry(Builder builder) {
        this.projectEntries = Map.copyOf(builder.projectEntries);
        this.globalEntries = List.copyOf(builder.globalEntries);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 获取指定项目的注册词汇（含全局词汇） */
    public List<Entry> getEntries(String projectId) {
        List<Entry> result = new ArrayList<>(globalEntries);
        if (projectId != null) {
            result.addAll(projectEntries.getOrDefault(projectId, List.of()));
        }
        return Collections.unmodifiableList(result);
    }

    /** 获取全局注册词汇 */
    public List<Entry> getGlobalEntries() {
        return globalEntries;
    }

    /** 检查文本是否匹配任何注册词汇 */
    public boolean matchesAny(String projectId, String text) {
        return getEntries(projectId).stream().anyMatch(e -> e.matches(text));
    }

    /** 查找匹配文本的第一个注册条目 */
    public Optional<Entry> findMatch(String projectId, String text) {
        return getEntries(projectId).stream().filter(e -> e.matches(text)).findFirst();
    }

    /** 生成 prompt 注入片段：将注册词汇格式化为示例文本 */
    public String toPromptExamples(String projectId) {
        List<Entry> entries = getEntries(projectId);
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("已知评分维度示例：");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append("、");
            Entry e = entries.get(i);
            sb.append(e.dim());
            if (e.minWeight() != null && e.maxWeight() != null) {
                sb.append("（").append(e.minWeight().intValue()).append("-").append(e.maxWeight().intValue()).append("分）");
            }
        }
        return sb.toString();
    }

    public static final class Builder {
        private final Map<String, List<Entry>> projectEntries = new HashMap<>();
        private final List<Entry> globalEntries = new ArrayList<>();

        public Builder addGlobal(String dim, Set<String> aliases, Set<String> keywords) {
            globalEntries.add(new Entry(dim, aliases, keywords));
            return this;
        }

        public Builder addGlobal(String dim, Set<String> aliases, Set<String> keywords,
                Double minWeight, Double maxWeight) {
            globalEntries.add(new Entry(dim, aliases, keywords, minWeight, maxWeight));
            return this;
        }

        public Builder addProject(String projectId, String dim, Set<String> aliases, Set<String> keywords) {
            projectEntries.computeIfAbsent(projectId, k -> new ArrayList<>())
                    .add(new Entry(dim, aliases, keywords));
            return this;
        }

        public Builder addProject(String projectId, String dim, Set<String> aliases, Set<String> keywords,
                Double minWeight, Double maxWeight) {
            projectEntries.computeIfAbsent(projectId, k -> new ArrayList<>())
                    .add(new Entry(dim, aliases, keywords, minWeight, maxWeight));
            return this;
        }

        public ScoreVocabularyRegistry build() {
            return new ScoreVocabularyRegistry(this);
        }
    }
}
