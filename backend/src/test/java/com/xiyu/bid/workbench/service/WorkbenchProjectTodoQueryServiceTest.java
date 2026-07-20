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

    /**
     * 全量项目池（按 ID 查询时过滤返回）。
     * CO-596：stage 与 status 是独立字段——
     * - initiatedReal：stage=INITIATED + status=INITIATED（真正的已立项项目）
     * - pendingInitiation：stage=INITIATED + status=PENDING_INITIATION（待立项项目，stage 相同但 status 不同）
     * - closed：stage=CLOSED + status=CLOSED（终态）
     * - drafting：stage=DRAFTING + status=BIDDING（投标中）
     * - retrospective：stage=RETROSPECTIVE + status=BIDDING（待结项）
     */
    private final Project initiatedReal = newProject(10L, "真正已立项", ProjectStage.INITIATED.name(), Project.Status.INITIATED);
    private final Project pendingInitiation = newProject(15L, "待立项项目", ProjectStage.INITIATED.name(), Project.Status.PENDING_INITIATION);
    private final Project closed = newProject(20L, "已结项项目", ProjectStage.CLOSED.name(), Project.Status.WON);
    private final Project drafting = newProject(30L, "投标中项目", ProjectStage.DRAFTING.name(), Project.Status.BIDDING);
    private final Project retrospective = newProject(40L, "待结项项目", ProjectStage.RETROSPECTIVE.name(), Project.Status.BIDDING);
    private final List<Project> allProjects = List.of(initiatedReal, pendingInitiation, closed, drafting, retrospective);

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
    void adminLeadRole_returnsStatusInitiatedPlusPendingReview_withTodoLabels() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        // CO-596: admin_lead 按 status=INITIATED 查询，只返回真正已立项的项目（10L），不含待立项项目（15L）
        when(projectRepository.findByStatus(Project.Status.INITIATED)).thenReturn(List.of(initiatedReal));
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

    /**
     * CO-596 回归：stage=INITIATED 但 status=PENDING_INITIATION 的项目
     * 不应出现在 admin_lead 的"已立项"列表中。
     */
    @Test
    void adminLeadRole_excludesPendingInitiationProjects_evenIfStageIsInitiated() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        // 只返回 status=INITIATED 的项目（不含 pendingInitiation）
        when(projectRepository.findByStatus(Project.Status.INITIATED)).thenReturn(List.of(initiatedReal));
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of());

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 不应包含 15L（pendingInitiation）
        assertThat(result).hasSize(1);
        assertThat(result).extracting(ProjectDTO::getId).containsExactly(10L);
        assertThat(result.get(0).getTodoLabel()).isEqualTo("已立项");
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
    void projectLeaderRole_returnsPendingInitiationRetrospectivePlusPendingReview_withTodoLabels() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.SALES_CODE);
        // CO-596: sales 按 status=PENDING_INITIATION 查询"待立项"项目，不含已立项项目
        when(projectRepository.findByStatus(Project.Status.PENDING_INITIATION)).thenReturn(List.of(pendingInitiation));
        // "待结项"按 stage=RETROSPECTIVE 查询（Project.Status 无对应值）
        when(projectRepository.findByStageIn(List.of(ProjectStage.RETROSPECTIVE))).thenReturn(List.of(retrospective));
        // 待审核标书项目 30L（DRAFTING）
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 15L（待立项）+ 30L（投标中）+ 40L（待结项）
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(15L, 30L, 40L);
        // 验证 todoLabel：15L→"待立项", 30L→"投标中", 40L→"待结项"
        assertThat(result.stream().filter(d -> d.getId() == 15L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("待立项");
        assertThat(result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("投标中");
        assertThat(result.stream().filter(d -> d.getId() == 40L).findFirst().orElseThrow().getTodoLabel()).isEqualTo("待结项");
    }

    /**
     * CO-596 回归：sales 分支查询 status=PENDING_INITIATION 的项目，
     * 不应包含 status=INITIATED 的项目（即使 stage 都是 INITIATED）。
     */
    @Test
    void projectLeaderRole_excludesInitiatedProjects_fromPendingInitiationList() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.SALES_CODE);
        // 只返回 PENDING_INITIATION 项目（不含 initiatedReal）
        when(projectRepository.findByStatus(Project.Status.PENDING_INITIATION)).thenReturn(List.of(pendingInitiation));
        when(projectRepository.findByStageIn(List.of(ProjectStage.RETROSPECTIVE))).thenReturn(List.of());
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING")).thenReturn(List.of());

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 不应包含 10L（initiatedReal，已立项）
        assertThat(result).hasSize(1);
        assertThat(result).extracting(ProjectDTO::getId).containsExactly(15L);
        assertThat(result.get(0).getTodoLabel()).isEqualTo("待立项");
    }

    @Test
    void pendingReview_filteredByDRAFTINGStage_only() {
        // 审核记录关联的项目不是 DRAFTING 阶段时，不应出现在结果中
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        when(projectRepository.findByStatus(Project.Status.INITIATED)).thenReturn(List.of());
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

    /**
     * P1-1 回归：角色变体（大小写/连字符）必须通过 canonicalCode 归一化后匹配分支。
     * 验证 "bid-team"（小写变体）能正确命中 BID_SPECIALIST_CODE 分支，
     * 且同一测试同时覆盖主负责人项目 + 待审核标书场景（todoLabel 分别正确）。
     */
    @Test
    void bidTeamRole_roleVariant_canonicalMatched() {
        // OSS 返回小写变体 "bid-team"，canonicalCode 应归一化为 "bid-Team"
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn("bid-team");
        // 主负责人项目 10L（INITIATED）
        when(projectLeadAssignmentRepository.findByPrimaryLeadUserId(1L))
                .thenReturn(List.of(ProjectLeadAssignment.builder().projectId(10L).build()));
        when(projectLeadAssignmentRepository.findBySecondaryLeadUserId(1L))
                .thenReturn(List.of());
        // 待审核标书项目 30L（DRAFTING）
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING"))
                .thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        // 10L→"已立项"（主负责人项目，按实际阶段映射）
        // 30L→"投标中"（待审核标书，优先级最高）
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 30L);
        assertThat(result.stream().filter(d -> d.getId() == 10L).findFirst().orElseThrow().getTodoLabel())
                .isEqualTo("已立项");
        assertThat(result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow().getTodoLabel())
                .isEqualTo("投标中");
    }

    /**
     * P3-6 边界用例：投标专员同时有主负责人项目（INITIATED）+ 待审核标书项目（DRAFTING），
     * 验证 todoLabel 分别正确（主负责人→"已立项"，待审核标书→"投标中"）。
     * 注：同一 projectId 不可能同时是 INITIATED 和 DRAFTING，所以不会发生标签冲突。
     */
    @Test
    void bidTeamRole_leadProjectPlusPendingReview_todoLabelsDistinct() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser))
                .thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);
        // 主负责人项目 10L（INITIATED）
        when(projectLeadAssignmentRepository.findByPrimaryLeadUserId(1L))
                .thenReturn(List.of(ProjectLeadAssignment.builder().projectId(10L).build()));
        when(projectLeadAssignmentRepository.findBySecondaryLeadUserId(1L))
                .thenReturn(List.of());
        // 待审核标书项目 30L（DRAFTING）
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerIdAndStatus(1L, "REVIEWING"))
                .thenReturn(List.of(review));

        List<ProjectDTO> result = service.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 30L);
        // 10L→"已立项"（主负责人项目，按实际阶段映射）
        ProjectDTO leadDto = result.stream().filter(d -> d.getId() == 10L).findFirst().orElseThrow();
        assertThat(leadDto.getTodoLabel()).isEqualTo("已立项");
        // 30L→"投标中"（待审核标书，优先级最高）
        ProjectDTO reviewDto = result.stream().filter(d -> d.getId() == 30L).findFirst().orElseThrow();
        assertThat(reviewDto.getTodoLabel()).isEqualTo("投标中");
    }

    private Project newProject(Long id, String name, String stage, Project.Status status) {
        return Project.builder().id(id).name(name).stage(stage).status(status).build();
    }
}
