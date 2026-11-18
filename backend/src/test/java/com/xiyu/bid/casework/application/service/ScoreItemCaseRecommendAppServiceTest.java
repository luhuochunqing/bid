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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Mock
    private com.xiyu.bid.casework.application.port.EnterpriseQualificationQueryPort enterpriseQualificationQueryPort;

    @Mock
    private com.xiyu.bid.casework.application.port.CaseVectorSemanticQueryPort vectorSemanticQueryPort;

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

    @Test
    @DisplayName("业绩评分项准入：人员候选被剔除、低于门槛业绩被排除，仅保留达门槛业绩案例")
    void testPerformanceItemAdmissionExcludesPersonnelAndBelowThreshold() {
        Long projectId = 300L;
        Long scoreItemId = 5L;

        Project project = new Project();
        project.setId(projectId);
        project.setCustomerType("STATE_OWNED");
        project.setIndustry("工业品");

        ScoreItem scoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("3")
                .dim("类似业绩")
                .detail("投标人须提供类似业绩合同或协议（双方名称清晰齐全），每个合同均须提供结算发票复印件。")
                .itemIndex(3)
                .build();

        String qualText = "供应商近三年应具有大型集团级企业年度供应业绩：办公用品累计金额不低于2000万元；"
                + "标准工业品累计金额不低于4500万元。";
        var qualInfo = new com.xiyu.bid.casework.application.port.TenderQualificationQueryPort.TenderQualificationInfo(
                List.of(qualText), null);

        KnowledgeCase personnel = new KnowledgeCase();
        personnel.setId(401L);
        personnel.setSourceProjectId(9401L);
        personnel.setScoringPointTitle("项目实施经验");
        personnel.setScoringCategory("EXPERIENCE");
        personnel.setResponseText("证明王工有5年以上研发管理经验，拥有高级职称，参与过相似项目。");
        personnel.setStatus("ACTIVE");
        personnel.setBidResult("WON");
        personnel.setQualityScore(90);

        KnowledgeCase perfMet = new KnowledgeCase();
        perfMet.setId(402L);
        perfMet.setSourceProjectId(9402L);
        perfMet.setScoringPointTitle("类似业绩");
        perfMet.setScoringCategory("资质业绩");
        perfMet.setResponseText("为大型集团提供办公用品年度供应，累计发票金额2600万元。");
        perfMet.setStatus("ACTIVE");
        perfMet.setBidResult("WON");
        perfMet.setQualityScore(90);

        KnowledgeCase perfBelowThreshold = new KnowledgeCase();
        perfBelowThreshold.setId(403L);
        perfBelowThreshold.setSourceProjectId(9403L);
        perfBelowThreshold.setScoringPointTitle("类似业绩");
        perfBelowThreshold.setScoringCategory("资质业绩");
        perfBelowThreshold.setResponseText("为大型集团提供办公用品年度供应，累计发票金额800万元。");
        perfBelowThreshold.setStatus("ACTIVE");

        KnowledgeCase perfNoAmount = new KnowledgeCase();
        perfNoAmount.setId(404L);
        perfNoAmount.setSourceProjectId(9404L);
        perfNoAmount.setScoringPointTitle("供货业绩");
        perfNoAmount.setScoringCategory("资质业绩");
        perfNoAmount.setResponseText("为大型集团提供办公用品年度供应服务，履约评价优秀，发票齐全。");
        perfNoAmount.setStatus("ACTIVE");
        perfNoAmount.setBidResult("WON");

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(scoreItem));
        when(tenderQualificationQueryPort.findByProjectId(eq(projectId))).thenReturn(Optional.of(qualInfo));
        when(knowledgeCaseQueryPort.findAllActive())
                .thenReturn(List.of(personnel, perfMet, perfBelowThreshold, perfNoAmount));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(scoreItemId).page(0).size(10).build());

        // tier2（品类命中、无金额佐证）保留展示；人员与明确低于门槛的候选被剔除
        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactlyInAnyOrder(402L, 404L);
        assertThat(response.getItems().stream()
                .filter(item -> item.getId().equals(402L))
                .findFirst().orElseThrow().getMatchScore()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("业绩评分项 + 资格项快照缺失：仅凭业绩信号准入，人员案例仍被排除")
    void testPerformanceItemWithoutQualificationSnapshotStillExcludesPersonnel() {
        Long projectId = 304L;
        Long scoreItemId = 6L;

        Project project = new Project();
        project.setId(projectId);

        ScoreItem scoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("3")
                .dim("类似业绩")
                .detail("投标人须提供类似业绩合同或协议及结算发票复印件。")
                .itemIndex(3)
                .build();

        KnowledgeCase personnel = new KnowledgeCase();
        personnel.setId(441L);
        personnel.setSourceProjectId(9441L);
        personnel.setScoringPointTitle("项目实施经验");
        personnel.setResponseText("证明王工有5年以上研发管理经验，拥有高级职称。");
        personnel.setStatus("ACTIVE");
        personnel.setBidResult("WON");

        KnowledgeCase perfSignal = new KnowledgeCase();
        perfSignal.setId(442L);
        perfSignal.setSourceProjectId(9442L);
        perfSignal.setScoringPointTitle("供货业绩");
        perfSignal.setResponseText("我司近三年累计供货业绩丰富，服务多家大型企业客户。");
        perfSignal.setStatus("ACTIVE");
        perfSignal.setBidResult("WON");

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(scoreItem));
        when(tenderQualificationQueryPort.findByProjectId(eq(projectId))).thenReturn(Optional.empty());
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(personnel, perfSignal));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(scoreItemId).page(0).size(10).build());

        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactly(442L);
    }

    @Test
    @DisplayName("未选评分项回退：资格项存在业绩要求时按门槛准入，人员候选不出现")
    void testDefaultRecommendationFallsBackToQualificationPerformance() {
        Long projectId = 301L;

        Project project = new Project();
        project.setId(projectId);

        String qualText = "供应商近三年应具有大型集团级企业年度供应业绩：办公用品累计金额不低于2000万元。";
        var qualInfo = new com.xiyu.bid.casework.application.port.TenderQualificationQueryPort.TenderQualificationInfo(
                List.of(qualText), null);

        KnowledgeCase personnel = new KnowledgeCase();
        personnel.setId(411L);
        personnel.setSourceProjectId(9411L);
        personnel.setScoringPointTitle("人员配置方案");
        personnel.setResponseText("项目经理与团队配置科学合理，人员资质齐全。");
        personnel.setStatus("ACTIVE");
        personnel.setBidResult("WON");

        KnowledgeCase perfMet = new KnowledgeCase();
        perfMet.setId(412L);
        perfMet.setSourceProjectId(9412L);
        perfMet.setScoringPointTitle("类似业绩");
        perfMet.setScoringCategory("资质业绩");
        perfMet.setResponseText("为大型集团提供办公用品年度供应，累计发票金额2600万元。");
        perfMet.setStatus("ACTIVE");
        perfMet.setBidResult("WON");

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(tenderQualificationQueryPort.findByProjectId(eq(projectId))).thenReturn(Optional.of(qualInfo));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(personnel, perfMet));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId,
                ScoreItemCaseRecommendRequest.builder().keyword("").page(0).size(20).build());

        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactly(412L);
    }

    @Test
    @DisplayName("未选评分项且资格项无业绩条款：保持现状不做准入过滤（回归防护）")
    void testDefaultRecommendationWithoutQualificationKeepsExistingBehavior() {
        Long projectId = 302L;

        Project project = new Project();
        project.setId(projectId);

        KnowledgeCase case1 = new KnowledgeCase();
        case1.setId(421L);
        case1.setSourceProjectId(9421L);
        case1.setScoringPointTitle("技术方案");
        case1.setResponseText("微服务容灾架构方案");
        case1.setStatus("ACTIVE");
        case1.setBidResult("WON");

        KnowledgeCase case2 = new KnowledgeCase();
        case2.setId(422L);
        case2.setSourceProjectId(9422L);
        case2.setScoringPointTitle("人员配置");
        case2.setResponseText("项目经理与团队配置方案");
        case2.setStatus("ACTIVE");
        case2.setBidResult("WON");

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(tenderQualificationQueryPort.findByProjectId(eq(projectId))).thenReturn(Optional.empty());
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(case1, case2));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId,
                ScoreItemCaseRecommendRequest.builder().keyword("").page(0).size(20).build());

        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactlyInAnyOrder(421L, 422L);
    }

    @Test
    @DisplayName("未选评分项 + 关键词检索：不触发资格项回退准入，保持原检索行为")
    void testKeywordSearchWithoutScoreItemSkipsQualificationFallback() {
        Long projectId = 303L;

        Project project = new Project();
        project.setId(projectId);

        KnowledgeCase technical = new KnowledgeCase();
        technical.setId(431L);
        technical.setSourceProjectId(9431L);
        technical.setScoringPointTitle("技术方案");
        technical.setResponseText("微服务容灾架构方案");
        technical.setStatus("ACTIVE");
        technical.setBidResult("WON");

        KnowledgeCase personnel = new KnowledgeCase();
        personnel.setId(432L);
        personnel.setSourceProjectId(9432L);
        personnel.setScoringPointTitle("人员配置");
        personnel.setResponseText("项目经理与团队配置方案");
        personnel.setStatus("ACTIVE");
        personnel.setBidResult("WON");

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(project));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(technical, personnel));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(projectId,
                ScoreItemCaseRecommendRequest.builder().keyword("团队").page(0).size(20).build());

        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactlyInAnyOrder(431L, 432L);
        verify(tenderQualificationQueryPort, never()).findByProjectId(projectId);
    }

    @Test
    @DisplayName("推荐条目装配原文来源标题与位置元数据")
    void testRecommendAssemblesSourceProvenance() {
        Long projectId = 100L;
        Long sourceProjectId = 601L;

        Project currentProject = new Project();
        currentProject.setId(projectId);

        KnowledgeCase case1 = new KnowledgeCase();
        case1.setId(105L);
        case1.setSourceProjectId(sourceProjectId);
        case1.setSourceProjectName("某数据中心建设");
        case1.setScoringPointTitle("容灾备份能力");
        case1.setScoringCategory("技术方案");
        case1.setRequirementRaw("要求提供异地双活");
        case1.setResponseText("我司具备异地双活数据中心部署与容灾能力");
        case1.setStatus("ACTIVE");
        case1.setSourceSectionTitle("4.1 异地双活架构");
        case1.setSourceSectionPath("第四章 建设方案 > 4.1 异地双活架构");
        case1.setSourceDocumentName("技术方案响应书.docx");
        case1.setSourcePageNumber(88);
        case1.setSourceSectionIndex(15);

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(currentProject));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(case1));

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(
                projectId,
                ScoreItemCaseRecommendRequest.builder().keyword("双活").page(0).size(10).build());

        assertThat(response.getItems()).hasSize(1);
        ScoreItemCaseRecommendResponse.Item item = response.getItems().get(0);
        assertThat(item.getSourceSectionTitle()).isEqualTo("4.1 异地双活架构");
        assertThat(item.getSourceSectionPath()).isEqualTo("第四章 建设方案 > 4.1 异地双活架构");
        assertThat(item.getSourceDocumentName()).isEqualTo("技术方案响应书.docx");
        assertThat(item.getSourcePageNumber()).isEqualTo(88);
        assertThat(item.getSourceSectionIndex()).isEqualTo(15);
    }

    @Test
    @DisplayName("评分项为资质证书时：直连企业资质库返回匹配证书及扫描件图片")
    void testRecommendQualificationItemDirectlyReturnsCertificatesWithImages() {
        Long projectId = 100L;
        Long scoreItemId = 99L;

        Project currentProject = new Project();
        currentProject.setId(projectId);

        ScoreItem certScoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("Q1")
                .dim("企业资质")
                .detail("投标人具有ISO 9001质量管理体系认证证书，并在有效期内")
                .itemIndex(1)
                .build();

        ScoreItemCaseRecommendResponse.CertificateItem certItem = ScoreItemCaseRecommendResponse.CertificateItem.builder()
                .id(888L)
                .name("质量管理体系认证证书 (ISO 9001)")
                .certificateNo("00121Q31234R0M")
                .certScope("工业品MRO供应链服务")
                .issuer("中国质量认证中心")
                .status("VALID")
                .expiryDate("2028-12-31")
                .fileUrl("https://oss.ehsy.com/certs/iso9001.jpg")
                .fileName("iso9001_scanned.jpg")
                .matchScore(95)
                .matchLabel("HIGH")
                .matchReasons(List.of("资质名称匹配", "证书在有效期内"))
                .build();

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(currentProject));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(certScoreItem));
        when(enterpriseQualificationQueryPort.findMatchingCertificates(eq("企业资质"), eq("投标人具有ISO 9001质量管理体系认证证书，并在有效期内"), eq(null)))
                .thenReturn(List.of(certItem));

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(
                projectId,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(scoreItemId).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getMatchedCertificates()).hasSize(1);
        ScoreItemCaseRecommendResponse.CertificateItem matched = response.getMatchedCertificates().get(0);
        assertThat(matched.getName()).contains("ISO 9001");
        assertThat(matched.getFileUrl()).isEqualTo("https://oss.ehsy.com/certs/iso9001.jpg");
        assertThat(matched.getStatus()).isEqualTo("VALID");
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems()).isEmpty();
        verify(knowledgeCaseQueryPort, never()).findAllActive();
    }

    @Test
    @DisplayName("评分项为资质证书但资质端口抛出异常时：优雅降级返回空证书列表而不拖垮主流程")
    void testRecommendQualificationItemPortThrowsExceptionGracefullyDegrades() {
        Long projectId = 100L;
        Long scoreItemId = 99L;

        Project currentProject = new Project();
        currentProject.setId(projectId);

        ScoreItem certScoreItem = ScoreItem.builder()
                .id(scoreItemId)
                .projectId(projectId)
                .code("Q1")
                .dim("企业资质")
                .detail("投标人具有ISO 9001质量管理体系认证证书")
                .itemIndex(1)
                .build();

        when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(currentProject));
        when(scoreItemRepository.findById(eq(scoreItemId))).thenReturn(Optional.of(certScoreItem));
        when(enterpriseQualificationQueryPort.findMatchingCertificates(eq("企业资质"), eq("投标人具有ISO 9001质量管理体系认证证书"), eq(null)))
                .thenThrow(new RuntimeException("Transient DB Connection Failure"));

        ScoreItemCaseRecommendResponse response = recommendAppService.recommend(
                projectId,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(scoreItemId).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getMatchedCertificates()).isEmpty();
        assertThat(response.getTotal()).isEqualTo(0);
        assertThat(response.getItems()).isEmpty();
        verify(knowledgeCaseQueryPort, never()).findAllActive();
    }

    @Test
    void pinnedSameCustomerCertificateCannotEnterDeliveryRecommendations() {
        Project project = new Project();
        project.setId(100L);
        project.setName("三峡集团采购项目");
        project.setCustomer("三峡集团");
        project.setIndustry("综合");
        project.setCustomerType("央企");
        ScoreItem item = ScoreItem.builder().id(45L).projectId(100L).code("45")
                .dim("配送能力评价").detail("对配送响应速度及配送网点情况进行评价。")
                .itemIndex(1).build();
        KnowledgeCase certificate = new KnowledgeCase();
        certificate.setId(901L);
        certificate.setSourceProjectId(100L);
        certificate.setScoringPointTitle("网站安全等级防护证书");
        certificate.setScoringCategory("技术方案");
        certificate.setResponseText("具有公安部信息系统安全等级保护三级备案证明证书。");
        certificate.setSourceProjectName("三峡集团历史采购项目");
        certificate.setProjectType("综合");
        certificate.setCustomerType("央企");
        certificate.setBidResult("WON");
        certificate.setQualityScore(90);
        certificate.setIsPinned(true);
        certificate.setReuseCount(1000);
        KnowledgeCase delivery = new KnowledgeCase();
        delivery.setId(902L);
        delivery.setSourceProjectId(101L);
        delivery.setScoringPointTitle("配送能力评价");
        delivery.setScoringCategory("配送方案");
        delivery.setResponseText("全国配送网点提供物流配送服务，并承诺配送时效。");
        delivery.setSourceProjectName("其他客户采购项目");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(45L)).thenReturn(Optional.of(item));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(certificate, delivery));
        when(sourceMetaLoader.load(eq(java.util.Set.of(100L, 101L)))).thenReturn(Map.of());

        var response = recommendAppService.recommend(100L,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(45L).size(20).build());

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems()).extracting(ScoreItemCaseRecommendResponse.Item::getId)
                .containsExactly(902L);
    }

    @Test
    @DisplayName("US5 向量双轨融合：当向量端口提供高相似度时，评分项与案例无公共字词仍可通过语义召回")
    void testRecommend_WithVectorSemanticQueryPort_IntegratesVectorSimilarities() {
        Project project = new Project();
        project.setId(200L);
        project.setIndustry("工业品");
        project.setCustomerType("央企");

        ScoreItem item = ScoreItem.builder()
                .id(50L)
                .projectId(200L)
                .code("3.1")
                .dim("智能语音交互")
                .detail("语义理解与多轮会话应答")
                .itemIndex(1)
                .build();

        KnowledgeCase coldChainCase = new KnowledgeCase();
        coldChainCase.setId(888L);
        coldChainCase.setSourceProjectId(200L);
        coldChainCase.setScoringPointTitle("冷链恒温监控仓储说明");
        coldChainCase.setScoringCategory("技术方案");
        coldChainCase.setResponseText("温湿度超限报警与喷淋降温");
        coldChainCase.setProjectType("工业品");
        coldChainCase.setCustomerType("央企");
        coldChainCase.setBidResult("WON");
        coldChainCase.setStatus("ACTIVE");

        when(projectRepository.findById(200L)).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(50L)).thenReturn(Optional.of(item));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(coldChainCase));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());
        when(vectorSemanticQueryPort.querySimilarities(org.mockito.ArgumentMatchers.contains("智能语音交互")))
                .thenReturn(Map.of(888L, 0.85));

        var response = recommendAppService.recommend(200L,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(50L).size(10).build());

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(888L);
        assertThat(response.getItems().getFirst().getMatchScore()).isGreaterThanOrEqualTo(30);
        assertThat(response.getItems().getFirst().getMatchReasons())
                .anyMatch(r -> r.contains("语义相似度匹配") && r.contains("85%"));
    }

    @Test
    @DisplayName("US5 向量优雅降级：当向量端口异常抛错时，服务安全降级回退至纯词法评估，零异常中断")
    void testRecommend_WhenVectorSemanticQueryPortThrows_GracefullyDegrades() {
        Project project = new Project();
        project.setId(201L);
        project.setIndustry("工业品");
        project.setCustomerType("央企");

        ScoreItem item = ScoreItem.builder()
                .id(51L)
                .projectId(201L)
                .code("1.1")
                .dim("技术方案")
                .detail("微服务架构与容灾备份方案")
                .itemIndex(1)
                .build();

        KnowledgeCase microserviceCase = new KnowledgeCase();
        microserviceCase.setId(777L);
        microserviceCase.setSourceProjectId(201L);
        microserviceCase.setScoringPointTitle("微服务架构与容灾备份方案");
        microserviceCase.setScoringCategory("技术方案");
        microserviceCase.setResponseText("具备微服务容灾备份体系");
        microserviceCase.setProjectType("工业品");
        microserviceCase.setCustomerType("央企");
        microserviceCase.setStatus("ACTIVE");

        when(projectRepository.findById(201L)).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(51L)).thenReturn(Optional.of(item));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(microserviceCase));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());
        when(vectorSemanticQueryPort.querySimilarities(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("Vector Service Unavailable"));

        var response = recommendAppService.recommend(201L,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(51L).size(10).build());

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("US7/US8 质量门禁：推荐服务自动过滤纯模板与低质废料切片，仅推荐高质实质方案")
    void testRecommend_FiltersOutLowQualityTemplates() {
        Project project = new Project();
        project.setId(301L);
        project.setIndustry("工业品");
        project.setCustomerType("央企");

        ScoreItem item = ScoreItem.builder()
                .id(61L)
                .projectId(301L)
                .code("2.1")
                .dim("技术方案")
                .detail("微服务架构与容灾备份方案")
                .itemIndex(1)
                .build();

        KnowledgeCase genuineCase = new KnowledgeCase();
        genuineCase.setId(801L);
        genuineCase.setSourceProjectId(301L);
        genuineCase.setScoringPointTitle("微服务架构与容灾备份方案");
        genuineCase.setScoringCategory("技术方案");
        genuineCase.setResponseText("具备完整的微服务高可用容灾备份体系与灾备演练机制。");
        genuineCase.setProjectType("工业品");
        genuineCase.setCustomerType("央企");
        genuineCase.setStatus("ACTIVE");

        KnowledgeCase templateCase = new KnowledgeCase();
        templateCase.setId(802L);
        templateCase.setSourceProjectId(301L);
        templateCase.setScoringPointTitle("法定代表人授权委托书格式");
        templateCase.setScoringCategory("技术方案");
        templateCase.setResponseText("法定代表人授权委托书格式如下：我______系法定代表人，特此委托。签字：______ 日期：____年__月__日");
        templateCase.setProjectType("工业品");
        templateCase.setCustomerType("央企");
        templateCase.setStatus("ACTIVE");

        KnowledgeCase placeholderCase = new KnowledgeCase();
        placeholderCase.setId(803L);
        placeholderCase.setSourceProjectId(301L);
        placeholderCase.setScoringPointTitle("容灾方案附件");
        placeholderCase.setScoringCategory("技术方案");
        placeholderCase.setResponseText("详见技术标附件。");
        placeholderCase.setProjectType("工业品");
        placeholderCase.setCustomerType("央企");
        placeholderCase.setStatus("ACTIVE");

        when(projectRepository.findById(301L)).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(61L)).thenReturn(Optional.of(item));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(genuineCase, templateCase, placeholderCase));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        var response = recommendAppService.recommend(301L,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(61L).size(10).build());

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(801L);
    }

    @Test
    @DisplayName("复合评分项双轨兼容：当评分项同时包含资质要求与技术实施方案时，方案案例与资质证书并存返回")
    void testRecommend_HybridScoringItemReturnsBothCasesAndCertificates() {
        Project project = new Project();
        project.setId(401L);
        project.setIndustry("工业品");
        project.setCustomerType("央企");

        ScoreItem item = ScoreItem.builder()
                .id(71L)
                .projectId(401L)
                .code("3.1")
                .dim("企业资质认证")
                .detail("具备ISO9001专业资质认证，并提供完整的系统实施与部署方案")
                .itemIndex(1)
                .build();

        KnowledgeCase technicalCase = new KnowledgeCase();
        technicalCase.setId(901L);
        technicalCase.setSourceProjectId(401L);
        technicalCase.setScoringPointTitle("系统实施与部署方案");
        technicalCase.setScoringCategory("技术方案");
        technicalCase.setResponseText("我司提供标准化的系统实施计划，包含需求调研、环境部署、联调测试与上线交付四个阶段。");
        technicalCase.setProjectType("工业品");
        technicalCase.setCustomerType("央企");
        technicalCase.setStatus("ACTIVE");

        ScoreItemCaseRecommendResponse.CertificateItem cert = ScoreItemCaseRecommendResponse.CertificateItem.builder()
                .name("ISO9001质量管理体系认证")
                .certificateNo("CERT-9001")
                .build();

        when(projectRepository.findById(401L)).thenReturn(Optional.of(project));
        when(scoreItemRepository.findById(71L)).thenReturn(Optional.of(item));
        when(enterpriseQualificationQueryPort.findMatchingCertificates(any(), any(), any()))
                .thenReturn(List.of(cert));
        when(knowledgeCaseQueryPort.findAllActive()).thenReturn(List.of(technicalCase));
        when(sourceMetaLoader.load(anyCollection())).thenReturn(Map.of());

        var response = recommendAppService.recommend(401L,
                ScoreItemCaseRecommendRequest.builder().scoreItemId(71L).size(10).build());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(901L);
        assertThat(response.getMatchedCertificates()).hasSize(1);
        assertThat(response.getMatchedCertificates().getFirst().getName()).isEqualTo("ISO9001质量管理体系认证");
    }
}
