package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaseContentQualityPolicyTest {

    private record TestCandidate(
            Long id,
            String scoringPointTitle,
            String responseText,
            Integer qualityScore
    ) implements KnowledgeCaseReadModel {
        @Override public Long getId() { return id; }
        @Override public Long getSourceProjectId() { return 10L; }
        @Override public String getSourceProjectName() { return "TestProject"; }
        @Override public String getScoringPointTitle() { return scoringPointTitle; }
        @Override public Long getScoringPointId() { return id; }
        @Override public String getRequirementRaw() { return "技术要求"; }
        @Override public String getResponseText() { return responseText; }
        @Override public Integer getReuseCount() { return 0; }
        @Override public Boolean getIsPinned() { return false; }
        @Override public String getStatus() { return "ACTIVE"; }
        @Override public String getCustomerType() { return "STATE_OWNED"; }
        @Override public String getProjectType() { return "IT"; }
        @Override public String getBidResult() { return "WON"; }
        @Override public String getScoringCategory() { return "技术"; }
        @Override public String getProductLine() { return "MRO"; }
        @Override public Integer getQualityScore() { return qualityScore; }
        @Override public LocalDateTime getCreatedAt() { return LocalDateTime.now(); }
    }

    @Test
    @DisplayName("空值或超短无实质文本判定为低质")
    void blankOrTooShortTextIsLowQuality() {
        assertThat(CaseContentQualityPolicy.isLowQuality(null, null, null)).isTrue();
        assertThat(CaseContentQualityPolicy.isLowQuality("标题", "", 60)).isTrue();
        assertThat(CaseContentQualityPolicy.isLowQuality("标题", "   \n\t  ", 60)).isTrue();
        assertThat(CaseContentQualityPolicy.isLowQuality("简短", "满足要求。", 60)).isTrue();
        assertThat(CaseContentQualityPolicy.isLowQuality("简短", "详见附件说明。", 60)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "法定代表人授权委托书格式如下：本授权委托书声明：我______系______法定代表人，现授权委托______为我公司代理人，代理签署______项目投标文件。特此委托。\n法定代表人（签字或盖章）：______\n代理人（签字）：______\n日期：____年__月__日",
            "投标承诺函模板：\n1. 我方承诺在投标有效期内不撤销投标文件；\n2. 如我方中标，我方承诺在收到中标通知书后按期签订合同。\n投标人：（盖章）________\n法定代表人：（签字）________\n____年____月____日",
            "投标保证金银行保函格式如下：致（招标人）：鉴于______参加贵方______项目投标，我行开立不可撤销保函...银行名称（公章）：______ 法定代表人签字：______",
            "廉政承诺书（格式自拟）：为加强本项目招投标过程中的廉政建设，特签署本承诺书。投标人（盖章）：_________ 法定代表人（签字）：_________ 日期：_____年___月___日"
    })
    @DisplayName("纯格式模板与签字盖章下划线切片判定为低质")
    void formTemplatesAreLowQuality(String templateText) {
        CaseContentQualityPolicy.QualityVerdict verdict =
                CaseContentQualityPolicy.evaluate("承诺书/授权书格式", templateText, 60);

        assertThat(verdict.isAdmissible()).isFalse();
        assertThat(verdict.rejectReason()).contains("模板");
        assertThat(CaseContentQualityPolicy.isLowQuality("授权格式", templateText, 60)).isTrue();
    }

    @Test
    @DisplayName("目录与排版残片判定为低质")
    void tableOfContentsResidueIsLowQuality() {
        String tocText = """
                第一章 招标公告 .................................................... 1
                第二章 投标人须知 .................................................... 5
                第三章 评标办法 .................................................... 12
                第四章 合同条款及格式 ................................................. 25
                第五章 发包人要求与技术规范 ........................................... 40
                """;

        CaseContentQualityPolicy.QualityVerdict verdict =
                CaseContentQualityPolicy.evaluate("第X章 目录", tocText, 60);

        assertThat(verdict.isAdmissible()).isFalse();
        assertThat(verdict.rejectReason()).contains("残片");
    }

    @Test
    @DisplayName("低信息熵单一字符重复文本判定为低质")
    void lowEntropyTextIsLowQuality() {
        String repeated = "----------------------------------------------------------------------------------------------------";
        assertThat(CaseContentQualityPolicy.isLowQuality("分割线", repeated, 60)).isTrue();

        String singleChar = "测试".repeat(50);
        assertThat(CaseContentQualityPolicy.isLowQuality("单一词重复", singleChar, 60)).isTrue();
    }

    @Test
    @DisplayName("敷衍占位符切片判定为低质")
    void placeholderResponsesAreLowQuality() {
        String placeholder = "本项完全满足招标文件要求，严格执行招标文件中的相关规范及要求，特此说明。";
        CaseContentQualityPolicy.QualityVerdict verdict =
                CaseContentQualityPolicy.evaluate("服务要求", placeholder, 50);

        assertThat(verdict.isAdmissible()).isFalse();
        assertThat(verdict.rejectReason()).contains("占位符");
    }

    @Test
    @DisplayName("真实高质技术方案与履约论述必须 100% 正常放行（零误杀）")
    void genuineTechnicalProposalsMustPass() {
        String logisticsPlan = """
                一、总体仓储与配送保障方案
                我司为本项目提供专属全天候技术保障，在华东与华南设立核心枢纽备件库。
                1. 响应时效：30分钟内快速响应，2小时内到达现场排查；
                2. 应急周转：中心仓储备品备件储备金额超 500 万元，保障 4 小时紧急出库；
                3. 日常运维：每月输出健康巡检与耗材消耗分析报告。
                """;
        CaseContentQualityPolicy.QualityVerdict verdict1 =
                CaseContentQualityPolicy.evaluate("仓储物流方案", logisticsPlan, 85);
        assertThat(verdict1.isAdmissible()).isTrue();
        assertThat(verdict1.rejectReason()).isNull();
        assertThat(CaseContentQualityPolicy.isLowQuality("仓储物流方案", logisticsPlan, 85)).isFalse();

        String integrationPlan = """
                针对三峡集团电商化采购平台对接，我方采用统一企业级 API 网关实现标准化数据交互。
                系统提供 RESTful 接口与消息队列两套通道，支持高并发订单推送、实时库存同步与物流状态追踪。
                采用 JWT 双向鉴权与国密 SM4 传输加密，接口响应时间在 200ms 以内，保障全年 99.99% 高可用。
                """;
        CaseContentQualityPolicy.QualityVerdict verdict2 =
                CaseContentQualityPolicy.evaluate("接口对接方案", integrationPlan, 90);
        assertThat(verdict2.isAdmissible()).isTrue();
        assertThat(verdict2.rejectReason()).isNull();
        assertThat(CaseContentQualityPolicy.isLowQuality("接口对接方案", integrationPlan, 90)).isFalse();
    }

    @Test
    @DisplayName("filterAdmissible 批量过滤有效剔除低质废料切片")
    void filterAdmissibleFiltersOutLowQualityCases() {
        TestCandidate c1 = new TestCandidate(1L, "物流仓储", "一、总体保障方案：全国设立30个本地备件库，30分钟内响应，2小时内到达现场...", 85);
        TestCandidate c2 = new TestCandidate(2L, "授权委托书格式", "法定代表人授权委托书格式如下：我______系______法定代表人，特此委托。签字：______", 50);
        TestCandidate c3 = new TestCandidate(3L, "系统对接方案", "针对电商采购平台对接，采用统一企业级 API 网关实现标准化数据交互与实时订单推送...", 90);
        TestCandidate c4 = new TestCandidate(4L, "承诺函格式", "投标承诺函格式自拟：我方承诺在收到中标通知书后签订合同。盖章：________ 日期：____年__月__日", 50);
        TestCandidate c5 = new TestCandidate(5L, "简短占位", "详见附件说明。", 30);

        List<KnowledgeCaseReadModel> filtered = CaseContentQualityPolicy.filterAdmissible(List.of(c1, c2, c3, c4, c5));

        assertThat(filtered).extracting(KnowledgeCaseReadModel::getId)
                .containsExactly(1L, 3L);
    }
}
