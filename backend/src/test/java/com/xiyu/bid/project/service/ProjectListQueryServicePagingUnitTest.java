package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectListQuery;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 列表分页快路径的**路由判据**与**准入判据**单测。
 *
 * <p>这里只测"走哪条路"和"下推了什么"，不测富化的字段内容（那是集成测试的事）。之所以必须
 * 用 mock 而不是端到端，是因为其中最要紧的一条回归**在端到端里测不到**：
 * <b>非管理员且可见项目集为空时，快路径必须返回空页</b>。若日后有人把管理员判据从
 * {@code currentUserHasAdminAccess()} 改成"allowedIds 是否为空"，在"数据范围=全部"的测试配置下
 * 端到端用例依然会绿——只有这里把 {@code ProjectAccessScopeService} 换成 mock 才钉得住。</p>
 *
 * <p>{@link ProjectQueryService} 在这里是 mock：本类测的是取数路径，全量路径的内容由
 * {@code ProjectQueryServiceTest} 与集成测试负责。这样切开之后，"回落"的断言从
 * "验证没有再查一次全表"变成"验证确实调了全量入口"——后者才是真正的判据。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectListQueryServicePagingUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private DemoModeService demoModeService;

    /** 全量路径的上游（含富化链）：mock 掉，使"回落"与"当页富化"都可被断言。 */
    @Mock
    private ProjectQueryService projectQueryService;

    @InjectMocks
    private ProjectListQueryService service;

    /** 查询意图的可变装配器：16 个筛选 + page/size，让每个用例能"一次只设一个字段"。 */
    private static final class Q {
        private String name;
        private String ownerUnit;
        private String projectType;
        private String customerType;
        private String priority;
        private String sourceModule;
        private String bidStatus;
        private String stage;
        private Long projectLeaderId;
        private Long biddingLeaderId;
        private String projectLeaderName;
        private String biddingLeaderName;
        private String leaderDepartment;
        private String region;
        private String biddingPlatform;
        private String bidMonth;
        private Integer page = 0;
        private Integer size = 10;

        private Q size(Integer value) {
            this.size = value;
            return this;
        }

        private ProjectListQuery build() {
            return new ProjectListQuery(
                    name, ownerUnit, projectType, customerType, priority, sourceModule,
                    bidStatus, stage, projectLeaderId, biddingLeaderId,
                    projectLeaderName, biddingLeaderName, leaderDepartment,
                    region, biddingPlatform, bidMonth, page, size);
        }
    }

    private record FilterCase(String label, Consumer<Q> setup) {
    }

    private static Project project(Long id, String name, Long managerId) {
        return Project.builder()
                .id(id)
                .name(name)
                .managerId(managerId)
                .status(Project.Status.BIDDING)
                .stage("DRAFTING")
                .build();
    }

    /** 断言本次请求**没有**走快路径：全量入口发生，两条分页查询都没发生。 */
    private void assertTookTheFullListPath() {
        verify(projectQueryService).getAllProjects();
        verify(projectRepository, never()).findPageProjectIdsAll(any());
        verify(projectRepository, never()).findPageProjectIdsWithin(any(), any());
    }

    // ---------- 快路径：路由与权限 ----------

    @Test
    @DisplayName("非管理员且可见项目集为空：返回空页，且一条项目查询都不发")
    void nonAdminWithEmptyVisibleSetShouldReturnEmptyPageWithoutQuerying() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        assertThat(service.getProjects(new Q().build())).isEmpty();

        // 关键断言：不得回退到"不限 id"的查询。这条路径的失效方式是把"零可见项目"当成
        // "管理员"，从而把全部项目暴露给一个本应看不到任何项目的用户——静默且致命。
        verify(projectRepository, never()).findPageProjectIdsAll(any());
        verify(projectRepository, never()).findPageProjectIdsWithin(any(), any());
        verify(projectQueryService, never()).getAllProjects();
    }

    @Test
    @DisplayName("非管理员：只走 IN allowedIds 那条查询，并按 SQL 给出的当页顺序重排结果")
    void nonAdminShouldQueryWithinAllowedIdsOnly() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L, 2L));
        when(projectRepository.findPageProjectIdsWithin(List.of(1L, 2L), PageRequest.of(0, 10)))
                .thenReturn(List.of(1L, 2L));
        // 刻意让 findAllById 返回逆序：页内顺序由 SQL 的 ORDER BY 决定，
        // 若实现直接用 findAllById 的返回顺序，页内次序就会随机。
        when(projectRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(project(2L, "乙项目", 7L), project(1L, "甲项目", 7L)));

        List<ProjectDTO> result = service.getProjects(new Q().build());

        assertThat(result).extracting(ProjectDTO::getName).containsExactly("甲项目", "乙项目");
        verify(projectRepository, never()).findPageProjectIdsAll(any());
        // 富化只覆盖当页 id——这正是本次改动要拿到的规模收益。
        verify(projectQueryService).enrichWithTenderAndDetails(argThat(page -> page.size() == 2
                && page.stream().map(ProjectDTO::getId).toList().equals(List.of(1L, 2L))));
    }

    @Test
    @DisplayName("管理员：走不限 id 的查询，且不去取可见项目集")
    void adminShouldQueryWithoutIdRestriction() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectRepository.findPageProjectIdsAll(PageRequest.of(0, 10)))
                .thenReturn(List.of(1L));
        when(projectRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(project(1L, "甲项目", 7L)));

        List<ProjectDTO> result = service.getProjects(new Q().build());

        assertThat(result).extracting(ProjectDTO::getName).containsExactly("甲项目");
        verify(projectRepository, never()).findPageProjectIdsWithin(any(), any());
        // 管理员分支不该去算可见项目集：那是非管理员分支才需要的、代价更高的一次查询。
        verify(projectAccessScopeService, never()).getAllowedProjectIdsForCurrentUser();
    }

    // ---------- 回落：四种否决理由各一条 ----------

    @Test
    @DisplayName("size 为空：契约要求全量，必须回落慢路径")
    void absentSizeShouldFallBackToTheFullListPath() {
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        assertThat(service.getProjects(new Q().size(null).build())).isEmpty();

        assertTookTheFullListPath();
    }

    @Test
    @DisplayName("size < 1：回落慢路径，不得让 PageRequest.of 抛 IllegalArgumentException")
    void nonPositiveSizeShouldFallBackToTheFullListPath() {
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        assertThat(service.getProjects(new Q().size(0).build())).isEmpty();

        assertTookTheFullListPath();
    }

    @Test
    @DisplayName("传了不可下推的筛选：回落慢路径")
    void nonPushdownableFilterShouldFallBackToTheFullListPath() {
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        Q q = new Q();
        q.name = "国家电网";
        assertThat(service.getProjects(q.build())).isEmpty();

        assertTookTheFullListPath();
    }

    @Test
    @DisplayName("只传 stage 也必须回落：SQL 的 = 不是 String.equals（大小写不敏感）")
    void stagePresentShouldAlsoFallBackToTheFullListPath() {
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        Q q = new Q();
        q.stage = "DRAFTING";
        assertThat(service.getProjects(q.build())).isEmpty();

        assertTookTheFullListPath();
    }

    @Test
    @DisplayName("筛选值为空白视作未传：仍走快路径，且 SQL 里没有任何筛选值")
    void blankFilterShouldStillTakeTheFastPath() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectRepository.findPageProjectIdsAll(PageRequest.of(0, 10))).thenReturn(List.of());

        // 旧实现把空白的 stage 原样传进 SQL 的 `p.stage = ?`，于是 `?stage=` 从"返回第 1 页"
        // 变成了"返回空页"——一条静默的错误答案。现在 SQL 不再接受任何筛选值，空白与缺省等价。
        Q q = new Q();
        q.stage = "   ";
        assertThat(service.getProjects(q.build())).isEmpty();

        verify(projectQueryService, never()).getAllProjects();
    }

    @Test
    @DisplayName("e2e 演示模式：禁用快路径——先分页会把演示项目漏在页外")
    void demoModeShouldDisableThePagedPath() {
        when(demoModeService.isEnabled()).thenReturn(true);
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        assertThat(service.getProjects(new Q().build())).isEmpty();

        assertTookTheFullListPath();
    }

    // ---------- 准入判据真值表 ----------

    @Test
    @DisplayName("准入判据：16 个筛选全体缺席（含空白字符串）才为真，任一个在场即为假")
    void hasNoEffectiveFilterShouldRequireEveryFilterAbsent() {
        // 真：一个筛选都没传——k6 与前端首屏就是这一种
        assertThat(new Q().build().hasNoEffectiveFilter()).isTrue();

        // 真：传了但值是空白，等价于没传（慢路径对每个筛选都有 !isBlank() 守卫）
        Q blank = new Q();
        blank.name = "  ";
        blank.stage = "";
        blank.leaderDepartment = "\t";
        assertThat(blank.build().hasNoEffectiveFilter()).isTrue();

        // 假：16 个筛选逐一验证。抽掉其中任何一行，就意味着那个筛选被默许进入 SQL 比较，
        // 而 SQL 的 = 与 String.equals 在 utf8mb4_unicode_ci 下不等价——理由写在
        // ProjectListQuery#hasNoEffectiveFilter 的 javadoc 里。stage 也在其中：它虽然满足
        // "真列 + 等值比较 + 富化不改写"三条，却**不满足**第 4 条"SQL 等值语义与 String.equals 一致"。
        List<FilterCase> present = List.of(
                new FilterCase("name", q -> q.name = "X"),
                new FilterCase("ownerUnit", q -> q.ownerUnit = "X"),
                new FilterCase("projectType", q -> q.projectType = "X"),
                new FilterCase("customerType", q -> q.customerType = "X"),
                new FilterCase("sourceModule", q -> q.sourceModule = "X"),
                new FilterCase("priority", q -> q.priority = "X"),
                new FilterCase("bidStatus", q -> q.bidStatus = "X"),
                new FilterCase("stage", q -> q.stage = "DRAFTING"),
                new FilterCase("projectLeaderName", q -> q.projectLeaderName = "X"),
                new FilterCase("biddingLeaderName", q -> q.biddingLeaderName = "X"),
                new FilterCase("leaderDepartment", q -> q.leaderDepartment = "X"),
                new FilterCase("region", q -> q.region = "X"),
                new FilterCase("biddingPlatform", q -> q.biddingPlatform = "X"),
                new FilterCase("bidMonth", q -> q.bidMonth = "X"),
                // 两个 Long 型筛选不是字符串，"空白即未传"的判据盖不住，必须单独判 null
                new FilterCase("projectLeaderId", q -> q.projectLeaderId = 9L),
                new FilterCase("biddingLeaderId", q -> q.biddingLeaderId = 9L));

        for (FilterCase c : present) {
            Q q = new Q();
            c.setup().accept(q);
            assertThat(q.build().hasNoEffectiveFilter())
                    .as("筛选 %s 在场时不得走快路径——它没有等价的 SQL 比较语义", c.label())
                    .isFalse();
        }
    }
}
