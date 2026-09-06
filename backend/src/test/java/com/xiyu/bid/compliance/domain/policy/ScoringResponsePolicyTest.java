package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringResponsePolicyTest {

    @Test
    @DisplayName("评分项响应：仅在目录中列出评分响应或技术方案条目判定 FAIL")
    void testCheckFirstStage_WhenOnlyInCatalog_Fails() {
        String text = """
                目 录
                第三章 评分细则逐项对应表与技术实施方案.......................... 45
                第四章 施工组织设计与管理机构.................................. 68
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", text);

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("仅在目录中");
    }

    @Test
    @DisplayName("评分项响应：技术方案中存在占位符模板时判定 FAIL")
    void testCheckFirstStage_WhenBracketPlaceholderPresent_Fails() {
        DocumentContent doc = new DocumentContent("bid.pdf", "hash2", "full", List.of(
                new DocumentContent.PageContent(1, "目 录\n第一章 总体方案...... 1", List.of()),
                new DocumentContent.PageContent(2, """
                        第三章 技术方案与项目实施组织设计
                        一、总体设计思路
                        【请根据实际情况填写技术实施方案及架构图】
                        二、进度计划安排
                        满足招标全部进度要求。
                        """, List.of())
        ));

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("占位符或下划线留白");
    }

    @Test
    @DisplayName("评分项响应：技术方案中存在下划线空白留白时判定 FAIL")
    void testCheckFirstStage_WhenUnderlineBlankPresent_Fails() {
        DocumentContent doc = new DocumentContent("bid.pdf", "hash3", "full", List.of(
                new DocumentContent.PageContent(1, "目 录\n第一章 实施方案...... 1", List.of()),
                new DocumentContent.PageContent(2, """
                        第二章 项目管理机构及团队配置
                        项目经理拟派：___________，身份证号：___________。
                        具有高级项目经理证书，负责项目整体交付管控。
                        """, List.of())
        ));

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("占位符或下划线留白");
    }

    @Test
    @DisplayName("评分项响应：正文包含完整技术实施方案且无占位符时移交 LLM 语义裁决")
    void testCheckFirstStage_WhenValidProposalWithoutPlaceholders_HandsOverToLlm() {
        DocumentContent doc = new DocumentContent("bid.pdf", "hash4", "full", List.of(
                new DocumentContent.PageContent(1, "目 录\n技术方案与实施组织设计...... 1", List.of()),
                new DocumentContent.PageContent(2, """
                        第三章 评分标准逐项对应表
                        序号 | 评分项 | 招标要求 | 投标响应描述 | 对应正文页码
                        1    | 技术架构 | 微服务架构 | 采用Spring Boot + K8s高可用部署 | 第 15 页
                        2    | 项目管理 | PMP认证经理 | 派驻资深PMP专家全职驻场 | 第 28 页
                        
                        第四章 技术方案与项目实施方案正文
                        系统采用高可用分布式微服务架构，前端采用Vue 3并支持千万级高并发。
                        售后服务提供7x24小时本地化响应保障。
                        """, List.of())
        ));

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty());

        // 无硬伤，移交 LLM lane 深度评估得分点
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("评分项响应：标书为空或未命中评分关键词时移交 LLM 语义裁决")
    void testCheckFirstStage_WhenEmptyOrNoKeywords_ReturnsEmpty() {
        assertThat(ScoringResponsePolicy.checkFirstStage(null, TenderRequirements.empty())).isEmpty();

        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", "简要说明文档正文");
        assertThat(ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty())).isEmpty();
    }
}
