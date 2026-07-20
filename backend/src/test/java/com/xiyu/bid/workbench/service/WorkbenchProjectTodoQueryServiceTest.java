package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作台角色化改造：WorkbenchProjectTodoQueryService 按角色分支返回测试。
 *
 * 2026-07-20 调整：标书审核人项目仅限 DRAFTING 阶段 + REVIEWING 状态，
 * 并返回 todoLabel 中文标签（已立项/待立项/投标中/待结项）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkbenchProjectTodoQueryServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock private BidDocumentReviewRepository bidDocumentReviewRepository;
    @Mock private CurrentUserLookupService currentUserLookupService;
    @Mock private EffectiveRoleResolver effectiveRoleResolver;
    @Mock private UserDetails userDetails;

    private WorkbenchProjectTodoQueryService service;
    private final User currentUser = mock(User.class);

    /** 全量项目池，按 ID 查询时过滤返回 */
    private final Project initiated = newProject(10L, "已立项项目", ProjectStage.INITIATED.name());
    private final Project closed = newProject(20L, "已结项项目", ProjectStage.CLOSED.name());
    private final Project drafting = newProject(30L, "投标中项目", ProjectStage.DRAFTING.name());
    private final Project retrospective = newProject(40L, "待结项项目", ProjectStage.RETROSPECTIVE.name());
    private final List<Project> allProjects = List.of(initiated, closed, drafting, retrospective);

    @BeforeEach
    void setUp() {
        service = new WorkbenchProjectTodoQueryService(
                projectRepository,
                projectLeadAssignmentRepository,
                bidDocumentReviewRepository,
                currentUserLookupService,
                effectiveRoleResolver
        );
        when(currentUserLookupService.requireUser(userDetails)).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(1L);
        when(currentUser.getUsername()).thenReturn("testuser");
        // findAllById 按输入 ID 集合过滤返回（service 多次调用，每次参数不同）
        when(projectRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            Set<Long> idSet = new HashSet<>();
            ids.forEach(idSet::add);
            return allProjects.stream().filter(p -> idSet.contains(p.getId())).toList();
        });
    }

    @Test
    void adminLeadRole_returnsInitiatedPlusPendingReview_withTodoLabels() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        when(projectRepository.findByStageIn(List.of(ProjectStage.INITIATED))).thenReturn(List.of(initiated));
        // 待审核标书（REVIEWING 状态），项目 30L 处于 DRAFTING 阶段
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 30L);
        // 验证 todoLabel 中文标签
        ProjectDTO initiatedDto = result.stream().filter(d -> d.getId() == 10L).findFirst().orElseThrow();
        assertThat(initiatedDto.getTodoLabel()).isEqualTo("已立项");
        ProjectDTO draftingDto = result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow();
        assertThat(draftingDto.getTodoLabel()).isEqualTo("投标中");
    }

    @Test
    void bidTeamRole_returnsLeadProjectsExcludingClosed_plusPendingReview() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);
        // 主负责人项目 10L（INITIATED），副负责人项目 20L（CLOSED，应被过滤）
        when(projectLeadAssignmentRepository.findByPrimaryLeadUserId(1L))
                .thenReturn(List.of(ProjectLeadAssignment.builder().projectId(10L).build()));
        when(projectLeadAssignmentRepository.findBySecondaryLeadUserId(1L))
                .thenReturn(List.of(ProjectLeadAssignment.builder().projectId(20L).build()));
        // 待审核标书项目 30L（DRAFTING）
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 10L（INITIATED，主负责人）+ 30L（DRAFTING，待审核标书）；20L（CLOSED）被过滤
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 30L);
        ProjectDTO draftingDto = result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow();
        assertThat(draftingDto.getTodoLabel()).isEqualTo("投标中");
    }

    @Test
    void projectLeaderRole_returnsInitiatedRetrospectivePlusPendingReview_withTodoLabels() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.SALES_CODE);
        when(projectRepository.findByStageIn(List.of(ProjectStage.INITIATED))).thenReturn(List.of(initiated));
        when(projectRepository.findByStageIn(List.of(ProjectStage.RETROSPECTIVE))).thenReturn(List.of(retrospective));
        // 待审核标书项目 30L（DRAFTING）
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 30L, 40L);
        // 验证 todoLabel：10L→"待立项", 30L→"投标中", 40L→"待结项"
        assertThat(result.stream().filter(d -> d.getId() == 10L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("待立项");
        assertThat(result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("投标中");
        assertThat(result.stream().filter(d -> d.getId() == 40L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("待结项");
    }

    @Test
    void pendingReview_filteredByDRAFTINGStage_only() {
        // 审核记录关联的项目不是 DRAFTING 阶段时，不应出现在结果中
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        when(projectRepository.findByStageIn(List.of(ProjectStage.INITIATED))).thenReturn(List.of());
        // 审核记录关联项目 10L（INITIATED，非 DRAFTING）→ 应被过滤
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(10L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 10L 是 INITIATED 不是 DRAFTING，待审核标书过滤后为空，admin_lead 的 INITIATED 查询也为空
        assertThat(result).isEmpty();
    }

    @Test
    void nullRoleCode_returnsEmptyList_failClosed() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(null);

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        assertThat(result).isEmpty();
    }

    @Test
    void otherRole_returnsEmptyList() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser))
                .thenReturn(RoleProfileCatalog.BID_OTHER_DEPT_CODE);

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        assertThat(result).isEmpty();
    }

    private Project newProject(Long id, String name, String stage) {
        return Project.builder().id(id).name(name).stage(stage).build();
    }
}
