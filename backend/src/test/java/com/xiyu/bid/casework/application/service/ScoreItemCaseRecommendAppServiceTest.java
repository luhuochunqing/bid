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
}
