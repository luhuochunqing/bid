package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QualificationPerformanceRetriever - 业绩评分项穿透资格项业绩要求检索策略（FP-Java 纯核心）")
class QualificationPerformanceRetrieverTest {

    private final QualificationPerformanceRetriever retriever = new QualificationPerformanceRetriever();

    @Test
    @DisplayName("精准识别业绩类评分项：支持名称命中及详细要素中对资格业绩的引用")
    void testIsPerformanceScoreItem() {
        // 1. 标题/维度明确包含业绩
        assertThat(retriever.isPerformanceScoreItem("3 同类项目业绩", "每增加1个业绩得1分")).isTrue();
        assertThat(retriever.isPerformanceScoreItem("供应商业绩", "近三年类似合同")).isTrue();
        assertThat(retriever.isPerformanceScoreItem("同类项目", "同类供货记录")).isTrue();

        // 2. 标题为泛化商务，但详细要素引用了资格项/初步评审业绩
        String screenshotDetail = "在满足资格条件规定的2项有效业绩基础上，每额外增加2项符合资格条款要求的有效业绩得1分，最高4分。需提供的业绩证明材料同初步评审第7条供应商资格中的业绩证明材料。";
        assertThat(retriever.isPerformanceScoreItem("商务评分", screenshotDetail)).isTrue();
        assertThat(retriever.isPerformanceScoreItem("项目实施综合评价", "业绩证明材料同供应商资格审查业绩要求")).isTrue();

        // 3. 非业绩评分项（技术方案、售后服务等）绝不误判
        assertThat(retriever.isPerformanceScoreItem("技术架构方案", "微服务高可用架构与容灾备份")).isFalse();
        assertThat(retriever.isPerformanceScoreItem("售后服务与SLA保障", "7x24小时应急响应预案")).isFalse();
        assertThat(retriever.isPerformanceScoreItem("价格评审", "报价明细表")).isFalse();
    }

    @Test
    @DisplayName("从资格项列表中精准检索业绩要求：过滤通用法人资格，锁定具体业绩门槛")
    void testRetrieveFromQualificationList() {
        String dim = "3 同类项目业绩";
        String detail = "在满足资格条件规定的2项有效业绩基础上，每额外增加2项符合资格条款要求的有效业绩得1分，最高4分。需提供的业绩证明材料同初步评审第7条供应商资格中的业绩证明材料。";

        List<String> qualificationList = List.of(
                "具有独立承担民事责任的能力（提供营业执照）",
                "具有良好的商业信誉和健全的财务会计制度",
                "依法缴纳税收和社会保障资金的良好记录",
                "供应商近三年（2023年1月1日起至响应文件递交截止日），应至少具有1项大型集团级企业电商化品类年度供应业绩：参与标包1的，单项集团层级业绩的项目服务范围应包含办公用品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于2000万元；参与标包2的，单项集团层级业绩的项目服务范围应包含标准工业品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于4500万元。",
                "未被列入失信被执行人、重大税收违法失信主体"
        );

        Optional<QualificationPerformanceRetriever.PerformanceRequirementResult> resultOpt =
                retriever.retrieve(dim, detail, qualificationList, null);

        assertThat(resultOpt).isPresent();
        QualificationPerformanceRetriever.PerformanceRequirementResult result = resultOpt.get();
        assertThat(result.matchedRequirementText()).contains("大型集团级企业电商化品类年度供应业绩");
        assertThat(result.matchedRequirementText()).contains("办公用品");
        assertThat(result.matchedRequirementText()).contains("标准工业品");
        assertThat(result.keywords()).contains("大型集团", "电商", "办公用品", "标准工业品", "发票");
    }

    @Test
    @DisplayName("资格项列表中未结构化时，从标书全文初步评审/资格审查章节精准提取业绩条款")
    void testRetrieveFromRawTenderTextPreliminarySection() {
        String dim = "3 同类项目业绩";
        String detail = "需提供的业绩证明材料同初步评审第7条供应商资格中的业绩证明材料。";

        String rawTenderText = """
                第一章 招标公告
                本项目旨在提升采购数字化转型水平，促进业务高质量发展。
                
                第二章 投标人须知
                ...
                第三章 评标办法
                2. 初步评审标准与资格审查
                2.1 形式评审：按招标文件格式要求签署、盖章。
                2.2 资格评审标准：
                第1条 具有独立法人资格；
                第2条 财务审计报告；
                第7条 供应商资格：供应商近三年（2023年1月1日起至响应文件递交截止日），应至少具有1项大型集团级企业电商化品类年度供应业绩：参与标包1的，单项集团层级业绩的项目服务范围应包含办公用品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于2000万元；参与标包2的，单项集团层级业绩的项目服务范围应包含标准工业品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于4500万元。
                
                第四章 技术需求规范书
                ...
                """;

        Optional<QualificationPerformanceRetriever.PerformanceRequirementResult> resultOpt =
                retriever.retrieve(dim, detail, List.of(), rawTenderText);

        assertThat(resultOpt).isPresent();
        QualificationPerformanceRetriever.PerformanceRequirementResult result = resultOpt.get();
        assertThat(result.matchedRequirementText()).contains("大型集团级企业电商化品类年度供应业绩");
        assertThat(result.matchedRequirementText()).contains("2000万元");
        assertThat(result.matchedRequirementText()).contains("4500万元");
    }

    @Test
    @DisplayName("非业绩评分项不会触发穿透检索，即使存在资格业绩条款也返回 empty")
    void testNonPerformanceItemReturnsEmpty() {
        String dim = "技术方案";
        String detail = "微服务部署与监控运维架构设计";

        List<String> qualificationList = List.of(
                "供应商近三年应至少具有1项大型企业类似项目供货业绩"
        );

        Optional<QualificationPerformanceRetriever.PerformanceRequirementResult> resultOpt =
                retriever.retrieve(dim, detail, qualificationList, null);

        assertThat(resultOpt).isEmpty();
    }

    @Test
    @DisplayName("防泛化守卫：招标公告或项目介绍等非资格审查区域出现的'业绩'词汇被坚决阻断")
    void testExcludeIrrelevantTenderTextMentions() {
        String dim = "3 同类项目业绩";
        String detail = "需提供有效业绩证明材料。";

        // 仅在项目概况、招标背景中提到泛化业绩，但资格项与初步评审完全没有业绩条款
        String rawTenderText = """
                第一章 招标公告
                项目背景：近年来我单位业务规模扩张迅速，取得了优秀的经营业绩。为进一步规范采购...
                
                第二章 资格审查标准
                1. 具有营业执照；
                2. 具备质量管理体系认证；
                
                第三章 详细评审
                ...
                """;

        Optional<QualificationPerformanceRetriever.PerformanceRequirementResult> resultOpt =
                retriever.retrieve(dim, detail, List.of("具备营业执照", "具备质量管理体系认证"), rawTenderText);

        // 资格项中确实无业绩条款时，不应误采纳第一章背景中的经营业绩
        assertThat(resultOpt).isEmpty();
    }
}
