// Input: ScoreVocabularyRegistry（项目特定评分词汇注册表）
// Output: 注册/查询/匹配/prompt 注入行为验证
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreVocabularyRegistryTest {

    @Test
    void emptyRegistry_returnsEmptyEntries() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder().build();
        assertThat(registry.getEntries("any-project")).isEmpty();
        assertThat(registry.getGlobalEntries()).isEmpty();
    }

    @Test
    void globalEntry_visibleToAllProjects() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addGlobal("财务状况", Set.of("财务", "财报"), Set.of("注册资本", "营业收入"))
                .build();

        assertThat(registry.getEntries("project-1")).hasSize(1);
        assertThat(registry.getEntries("project-2")).hasSize(1);
        assertThat(registry.getGlobalEntries()).hasSize(1);
    }

    @Test
    void projectEntry_visibleOnlyToThatProject() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addProject("1358", "信用评价", Set.of("信用", "征信"), Set.of("不良记录"))
                .addProject("1362", "体系认证", Set.of("认证", "ISO"), Set.of("ISO9001"))
                .build();

        assertThat(registry.getEntries("1358")).hasSize(1);
        assertThat(registry.getEntries("1358").get(0).dim()).isEqualTo("信用评价");
        assertThat(registry.getEntries("1362")).hasSize(1);
        assertThat(registry.getEntries("1362").get(0).dim()).isEqualTo("体系认证");
        assertThat(registry.getEntries("9999")).isEmpty();
    }

    @Test
    void mixedGlobalAndProject_bothVisible() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addGlobal("价格折扣", Set.of("报价", "折扣"), Set.of())
                .addProject("1358", "运营服务能力", Set.of("运营"), Set.of())
                .build();

        assertThat(registry.getEntries("1358")).hasSize(2);
        assertThat(registry.getEntries("other")).hasSize(1);
    }

    @Test
    void matches_dimName() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "财务状况", Set.of(), Set.of());
        assertThat(entry.matches("公司财务状况评审")).isTrue();
        assertThat(entry.matches("财务")).isFalse();
    }

    @Test
    void matches_alias() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "财务状况", Set.of("财务", "财报"), Set.of());
        assertThat(entry.matches("财务评审")).isTrue();
        assertThat(entry.matches("财报分析")).isTrue();
    }

    @Test
    void matches_keyword() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "财务状况", Set.of(), Set.of("注册资本", "营业收入"));
        assertThat(entry.matches("注册资本金≥1000万")).isTrue();
        assertThat(entry.matches("营业收入近三年")).isTrue();
        assertThat(entry.matches("现金流")).isFalse();
    }

    @Test
    void matches_caseInsensitive() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "ISO认证", Set.of("iso"), Set.of("ISO9001"));
        assertThat(entry.matches("iso9001质量管理体系")).isTrue();
        assertThat(entry.matches("ISO认证")).isTrue();
    }

    @Test
    void weightRange_validation() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "财务状况", Set.of(), Set.of(), 3.0, 10.0);
        assertThat(entry.isWeightInRange(5.0)).isTrue();
        assertThat(entry.isWeightInRange(3.0)).isTrue();
        assertThat(entry.isWeightInRange(10.0)).isTrue();
        assertThat(entry.isWeightInRange(2.0)).isFalse();
        assertThat(entry.isWeightInRange(11.0)).isFalse();
    }

    @Test
    void weightRange_nullMeansNoConstraint() {
        ScoreVocabularyRegistry.Entry entry = new ScoreVocabularyRegistry.Entry(
                "财务状况", Set.of(), Set.of());
        assertThat(entry.isWeightInRange(0.0)).isTrue();
        assertThat(entry.isWeightInRange(100.0)).isTrue();
    }

    @Test
    void matchesAny_delegatesToEntries() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addGlobal("财务状况", Set.of("财务"), Set.of())
                .build();

        assertThat(registry.matchesAny("any", "公司财务")).isTrue();
        assertThat(registry.matchesAny("any", "技术方案")).isFalse();
    }

    @Test
    void findMatch_returnsFirstMatchingEntry() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addGlobal("财务状况", Set.of(), Set.of())
                .addGlobal("财务审计", Set.of(), Set.of())
                .build();

        var match = registry.findMatch("any", "公司财务状况评审");
        assertThat(match).isPresent();
        assertThat(match.get().dim()).isEqualTo("财务状况");
    }

    @Test
    void toPromptExamples_formatsCorrectly() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder()
                .addGlobal("财务状况", Set.of(), Set.of(), 3.0, 10.0)
                .addGlobal("信用评价", Set.of(), Set.of())
                .build();

        String examples = registry.toPromptExamples("any");
        assertThat(examples).contains("已知评分维度示例：");
        assertThat(examples).contains("财务状况（3-10分）");
        assertThat(examples).contains("信用评价");
    }

    @Test
    void toPromptExamples_emptyRegistry_returnsEmpty() {
        ScoreVocabularyRegistry registry = ScoreVocabularyRegistry.builder().build();
        assertThat(registry.toPromptExamples("any")).isEmpty();
    }
}
