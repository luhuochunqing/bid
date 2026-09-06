// Input: 招标文件原文（含废标条款、一票否决情形、实质性要求）
// Output: 验证确定性一票否决/废标条款及关键词提取
// Pos: compliance/domain/policy/ - 确定性废标条款提取纯核心测试（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 确定性一票否决/废标条款提取器测试.
 */
class DeterministicVetoExtractorTest {

    private static final String TEXT_STANDARD_VETO_SECTION = """
            2. 投标人须知
            2.1 综合说明
            3. 否决投标的情形
            投标人或其投标文件有下列情形之一的，其投标将被否决（作废标处理）：
            （1）投标文件未经投标人盖章和单位负责人签字或盖章的；
            （2）投标人不符合国家或者招标文件规定的资格条件的；
            （3）同一投标人提交两个以上不同的投标文件或者投标报价的；
            （4）投标报价低于成本或者高于招标文件设定的最高投标限价的；
            （5）投标人有串通投标、弄虚作假、行贿等违法违规行为的；
            （6）投标人拟将中标项目的主体、关键性工作分包的；
            （7）明显不符合技术规格、技术标准的要求，或者实质性响应存在负偏离的。
            4. 评标办法
            4.1 综合评估法
            """;

    private static final String TEXT_INLINE_VETO_CLAUSES = """
            1. 采购公告
            2. 投标要求
            供应商必须具备独立法人资格。严禁转包和违法分包，一经发现取消其投标资格并作废标处理。
            本项目不接受联合体投标，以联合体形式参与的一票否决。
            投标报价不得超过最高限价 500 万元，否则否决其投标。
            """;

    @Test
    @DisplayName("标准章节废标条款提取：准确提取否决投标条款及高频关键词")
    void extract_standardSection_extractsAllVetoClauses() {
        List<TenderRequirements.VetoClause> vetoClauses = DeterministicVetoExtractor.extract(TEXT_STANDARD_VETO_SECTION);

        assertThat(vetoClauses).isNotEmpty();
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("主体、关键性工作分包") && c.keywords().contains("分包"));
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("串通投标") && c.keywords().contains("串通投标"));
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("最高投标限价") && c.keywords().contains("最高限价"));
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("未经投标人盖章") && c.keywords().contains("签章"));

        // 下一章节不被误提
        assertThat(vetoClauses).noneMatch(c -> c.clause().contains("评标办法") || c.clause().contains("综合评估法"));
    }

    @Test
    @DisplayName("行内嵌入废标条款提取：能从正文中抓取包含废标/一票否决后果的条款")
    void extract_inlineClauses_extractsClausesWithConsequences() {
        List<TenderRequirements.VetoClause> vetoClauses = DeterministicVetoExtractor.extract(TEXT_INLINE_VETO_CLAUSES);

        assertThat(vetoClauses).isNotEmpty();
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("严禁转包") && c.keywords().contains("转包"));
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("联合体") && c.keywords().contains("联合体"));
        assertThat(vetoClauses).anyMatch(c -> c.clause().contains("最高限价") && c.keywords().contains("最高限价"));
    }

    @Test
    @DisplayName("空文本与无废标条款文本防御")
    void extract_blankOrNoVeto_returnsEmptyList() {
        assertThat(DeterministicVetoExtractor.extract(null)).isEmpty();
        assertThat(DeterministicVetoExtractor.extract("")).isEmpty();
        assertThat(DeterministicVetoExtractor.extract("普通介绍文本，没有任何废标或否决投标说明。")).isEmpty();
    }
}
