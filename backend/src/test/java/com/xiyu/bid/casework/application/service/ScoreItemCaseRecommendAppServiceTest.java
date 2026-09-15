package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendRequest;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreItemCaseRecommendAppServiceTest {

    @Mock
    private com.xiyu.bid.casework.application.port.KnowledgeCaseQueryPort knowledgeCaseQueryPort;

    @Mock
    private ScoreItemRepository scoreItemRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private ScoreItemCaseSourceMetaLoader sourceMetaLoader;

    @Mock
    private com.xiyu.bid.casework.application.port.TenderQualificationQueryPort tenderQualificationQueryPort;

    @InjectMocks
    private ScoreItemCaseRecommendAppService recommendAppService;

    @Test
    @DisplayName("推荐服务编排：成功根据评分项 detail 计算匹配度并排序分页")
    void testRecommendWorkflow() {
        Long projectId = 100L;
        Long scoreItemId = 1L;

        Project project = new Project();
        project.setId(projectId);
        project.setCustomerType("STATE_OWNED");
        project.setIndustry("ENERGY");

        ScoreItem scoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("A1")
                .dim("技术方案")
                .detail("微服务架构与容灾备份方案")
                .itemIndex(1)
                .build();

        KnowledgeCase case1 = new KnowledgeCase();
        case1.setId(101L);
        case1.setScoringPointTitle("微服务架构与容灾备份方案");
        case1.setScoringCategory("技术方案");
        case1.setRequirementRaw("要求提供微服务与容灾备份");
        case1.setResponseText("我方具备微服务容灾部署架构");
        case1.setProjectType("ENERGY");
        case1.setCustomerType("STATE_OWNED");
        case1.setBidResult("WON");
        case1.setStatus("ACTIVE");
        case1.setReuseCount(5);

        KnowledgeCase case2 = new KnowledgeCase();
        case2.setId(102L);
        case2.setScoringPointTitle("财务审计要求");
        case2.setScoringCategory("商务要求");
        case2.setRequirementRaw("提供三年审计报告");
        case2.setResponseText("已提供审计报告");
        case2.setStatus("ACTIVE");
        case2.setReuseCount(1);

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(scoreItem));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(case1, case2));

        ScoreItemCaseRecommendRequest request = ScoreItemCaseRecommendRequest.builder()
                .scoreItemId(scoreItemId)
                .keyword("容灾")
                .page(0)
                .size(10)
                .build();

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId, request);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(projectId);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).isNotEmpty();
        // case1 应该排在前面且高分
        assertThat(response.getItems().get(0).getId()).isEqualTo(101L);
        assertThat(response.getItems().get(0).getMatchScore()).isGreaterThan(70);
        assertThat(response.getItems().get(0).getMatchedKeywords()).contains("容灾");
    }

    @Test
    @DisplayName("推荐条目装配来源项目元信息：招标主体、创建时间、项目状态")
    void testRecommendAssemblesSourceProjectMeta() {
        Long projectId = 100L;
        Long sourceProjectId = 501L;

        Project currentProject = new Project();
        currentProject.setId(projectId);
        currentProject.setCustomerType("STATE_OWNED");
        currentProject.setIndustry("ENERGY");

        KnowledgeCase case1 = new KnowledgeCase();
        case1.setId(101L);
        case1.setSourceProjectId(sourceProjectId);
        case1.setSourceProjectName("某智能电网项目");
        case1.setScoringPointTitle("微服务架构与容灾备份方案");
        case1.setScoringCategory("技术方案");
        case1.setRequirementRaw("要求提供微服务与容灾备份");
        case1.setResponseText("我方具备微服务容灾部署架构");
        case1.setProjectType("ENERGY");
        case1.setCustomerType("STATE_OWNED");
        case1.setBidResult("WON");
        case1.setStatus("ACTIVE");
        case1.setCreatedAt(LocalDateTime.of(2026, 3, 12, 9, 30));
        case1.setReuseCount(5);

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(currentProject));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of(
                sourceProjectId, new ScoreItemCaseSourceMetaLoader.SourceMeta("某省电力公司", "WON")));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(case1));

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(
                projectId,
                ScoreItemCaseRecommendRequest.builder().keyword("容灾").page(0).size(10).build());

        assertThat(response.getItems()).hasSize(1);
        ScoreItemCaseRecommendResponse.Item item = response.getItems().get(0);
        assertThat(item.getSourceProjectName()).isEqualTo("某智能电网项目");
        assertThat(item.getBidEntity()).isEqualTo("某省电力公司");
        assertThat(item.getCreatedAt()).isEqualTo("2026-03-12");
        assertThat(item.getProjectStatus()).isEqualTo("WON");
        assertThat(item.getCustomerType()).isEqualTo("STATE_OWNED");
        assertThat(item.getProjectType()).isEqualTo("ENERGY");
    }

    @Test
    @DisplayName("端到端场景验证：编号评分项 2.2.4(3) 商城系统对接能力 成功召回大类为技术方案的历史案例")
    void testNumberedScoreItemWithBroadCategoryCaseInRecommendService() {
        Long projectId = 100L;
        Long scoreItemId = 1L;

        Project currentProject = new Project();
        currentProject.setId(projectId);
        currentProject.setCustomerType("央企");
        currentProject.setIndustry("工业品");

        ScoreItem scoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("2.2.4(3)")
                .dim("2.2.4(3) 商城系统对接能力")
                .detail("对供应商商城系统对接能力进行综合评价，供应商应根据工会规定定义的接口标准进行对接，使商城接口标准向职工商城采购接口服务，包括商品、商品组、订单、售后、对账等")
                .itemIndex(1)
                .build();

        KnowledgeCase matchedCase = new KnowledgeCase();
        matchedCase.setId(201L);
        matchedCase.setSourceProjectId(502L);
        matchedCase.setSourceProjectName("某央企工业品采购项目");
        matchedCase.setScoringPointTitle("技术方案");
        matchedCase.setScoringCategory("技术方案");
        matchedCase.setRequirementRaw("对供应商商城系统对接能力进行综合评价");
        matchedCase.setResponseText("技术方案内容包含但不限于：商城系统对接能力供应商按照“三峡e购”商城定义的接口标准进行对接，依据接口标准向“三峡e购”商城提供接口服务，包括商品、商品组、订单、售后、对账等");
        matchedCase.setProjectType("工业品");
        matchedCase.setCustomerType("央企");
        matchedCase.setBidResult("WON");
        matchedCase.setStatus("ACTIVE");
        matchedCase.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        matchedCase.setReuseCount(3);

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(currentProject));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(scoreItem));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(matchedCase));

        ScoreItemCaseRecommendRequest request = ScoreItemCaseRecommendRequest.builder()
                .scoreItemId(scoreItemId)
                .keyword(null)
                .page(0)
                .size(20)
                .build();

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);

        ScoreItemCaseRecommendResponse.Item item = response.getItems().get(0);
        assertThat(item.getId()).isEqualTo(201L);
        assertThat(item.getMatchScore()).isGreaterThanOrEqualTo(80);
        assertThat(item.getMatchLabel()).isEqualTo("优质");
        assertThat(item.getMatchReasons()).contains("评分点匹配", "评分项条款高度契合", "类别一致");
    }

    @Test
    @DisplayName("推荐服务编排：业绩评分项穿透资格项成功提取实质要求并推荐高分案例")
    void testPerformanceScoreItemPenetrationToQualification() {
        Long projectId = 200L;
        Long scoreItemId = 3L;

        Project project = new Project();
        project.setId(projectId);
        project.setCustomerType("STATE_OWNED");
        project.setIndustry("工业品");

        ScoreItem scoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("3")
                .dim("3 同类项目业绩")
                .detail("在满足资格条件规定的2项有效业绩基础上...需提供的业绩证明材料同初步评审第7条供应商资格中的业绩证明材料")
                .itemIndex(3)
                .build();

        String qualText = "供应商近三年（2023年1月1日起至响应文件递交截止日），应至少具有1项大型集团级企业电商化品类年度供应业绩：参与标包1的，单项集团层级业绩的项目服务范围应包含办公用品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于2000万元；参与标包2的，单项集团层级业绩的项目服务范围应包含标准工业品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于4500万元。";

        var qualInfo = new com.xiyu.bid.casework.application.port.TenderQualificationQueryPort.TenderQualificationInfo(
                List.of(qualText),
                null
        );

        KnowledgeCase perfCase = new KnowledgeCase();
        perfCase.setId(301L);
        perfCase.setScoringPointTitle("同类项目业绩");
        perfCase.setScoringCategory("业绩");
        perfCase.setRequirementRaw("供应商需具备大型集团电商化供应业绩，单项金额不少于2000万元");
        perfCase.setResponseText("我司与大型集团签署年度电商化采购合同，累计发票金额4500万元，包含标准工业品与办公用品。");
        perfCase.setProjectType("工业品");
        perfCase.setCustomerType("STATE_OWNED");
        perfCase.setBidResult("WON");
        perfCase.setStatus("ACTIVE");
        perfCase.setReuseCount(10);

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(scoreItem));
        when(tenderQualificationQueryPort.findByProjectId(eq(projectId))).thenReturn(Optional.of(qualInfo));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(perfCase));

        ScoreItemCaseRecommendRequest request = ScoreItemCaseRecommendRequest.builder()
                .scoreItemId(scoreItemId)
                .page(0)
                .size(10)
                .build();

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId, request);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);
        var item = response.getItems().get(0);
        assertThat(item.getId()).isEqualTo(301L);
        assertThat(item.getMatchScore()).isGreaterThanOrEqualTo(80);
        assertThat(item.getMatchLabel()).isEqualTo("优质");
        assertThat(item.getMatchReasons()).anyMatch(r -> r.contains("资格项业绩品类"));
    }
}
