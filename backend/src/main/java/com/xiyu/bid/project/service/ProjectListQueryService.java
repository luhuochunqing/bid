// Input: 控制器装配的列表查询意图（16 个筛选 + page/size）与当前用户
// Output: 一页（或全量）项目 DTO——无筛选走 SQL 分页快路径，有筛选回落全量内存筛选
// Pos: project/service - 项目列表读取路径；ProjectQueryService 的下游消费者之一
// 维护声明: 只决定"怎么取数"，不改"返回什么"；全序定义在本类，快慢两条路径共用同一份.
package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectListQuery;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目列表接口的取数：把"只为展示 10 行却富化全表"改为"SQL 分页 + 只富化当页"。
 *
 * <p>为什么要与 {@link ProjectQueryService} 分开：本类承载的是**列表读取路径的判据**（走哪条路、
 * 下推了什么、筛了什么），而 {@link ProjectQueryService} 承载的是富化链本身。两者原来的调用方
 * （导出、工作台）只需要后者；分开后，读列表契约的人不必穿过整条富化链。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectListQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final DemoModeService demoModeService;
    private final ProjectQueryService projectQueryService;

    /**
     * 全序：{@code createdAt} 降序（null 视作 MIN，故排最后）+ {@code id} 升序。
     *
     * <p><b>这个顺序是快慢两条路径的共同契约，必须与 {@link ProjectRepository} 两个分页查询里的
     * {@code ORDER BY p.createdAt DESC, p.id ASC} 逐字一致。</b>原因：{@code created_at} 并列时，
     * 页的划分完全由兜底键决定；两条路径若用不同的兜底键，并列的两行就会落到不同的页里——
     * 表现是"翻页时某一行出现两次、另一行从未出现"。压测铺底数据的时间戳是秒级随机的，
     * 并列并不罕见。定义在这里而不是各写一份，是因为"两处手工保持一致"没有守卫，
     * 而一个被两处调用的静态方法是有的。</p>
     */
    static void sortByCreatedAtDescIdAsc(final List<ProjectDTO> projects) {
        projects.sort(Comparator.comparing(
                        (ProjectDTO dto) -> dto.getCreatedAt() != null
                                ? dto.getCreatedAt()
                                : java.time.LocalDateTime.MIN,
                        Comparator.reverseOrder())
                .thenComparing(ProjectDTO::getId));
    }

    /**
     * 列表接口的唯一入口：决定走"服务端分页快路径"还是"全量内存筛选慢路径"。
     *
     * <p>两条路径的筛选语义必须一致，所以它们的判据与实现同处本类以便对读：快路径没有任何筛选
     * （判据见 {@link ProjectListQuery#hasNoEffectiveFilter()}），只把排序、分页与权限过滤下推 SQL、
     * 只富化当页；慢路径就是原先写在控制器里的那 16 条 stream filter，原样搬入
     * {@link #filterInMemory}。</p>
     *
     * <p>四条否决理由，缺一不可：</p>
     * <ol>
     *   <li>本次传了筛选（哪怕只有 {@code stage}）——见 {@link ProjectListQuery#hasNoEffectiveFilter()}
     *       对"为什么一个筛选都不能下推"的说明；</li>
     *   <li>e2e profile 下会在结果里合入演示项目，先分页就会把它们漏掉；</li>
     *   <li>{@code size} 为空表示契约要求全量——{@code src/api/modules/projects.js} 与
     *       {@code ProjectExportService} 都依赖这条路径；</li>
     *   <li>{@code size < 1}：{@code PageRequest.of} 会抛 {@code IllegalArgumentException}。
     *       控制器有 {@code @Min(1)} 兜住，但本方法是公开入口，内部调用方传 0 时旧实现返回空列表、
     *       新实现会变成一次莫名的 500，所以在这里也判一次。</li>
     * </ol>
     */
    public List<ProjectDTO> getProjects(final ProjectListQuery query) {
        if (query.size() != null
                && query.size() > 0
                && query.hasNoEffectiveFilter()
                && !demoModeService.isEnabled()) {
            return getProjectsPage(query);
        }
        return filterInMemory(projectQueryService.getAllProjects(), query);
    }

    /**
     * 快路径：排序、分页与权限过滤全部落在 SQL 上，只取回当页 id，再只富化当页。
     *
     * <p>与全量路径相比，富化的行数从"全表 N 行"降到 {@code size} 行，这正是该接口 P95 超标
     * 122% 的根因所在。</p>
     */
    private List<ProjectDTO> getProjectsPage(final ProjectListQuery query) {
        final int page = query.page() == null ? 0 : query.page();
        final int size = query.size();

        // 权限判据必须是 currentUserHasAdminAccess()：getAllowedProjectIds 对管理员**和**
        // "合法但零项目"的非管理员都返回 List.of()，用"集合是否为空"取分支会把后者误判成管理员、
        // 让它看到全部项目。这里的分支与 filterAccessibleProjects 用的是同一个判据。
        final boolean admin = projectAccessScopeService.currentUserHasAdminAccess();
        final List<Long> allowedIds = admin
                ? null
                : projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (!admin && allowedIds.isEmpty()) {
            return List.of();
        }

        final Pageable pageable = PageRequest.of(page, size);
        final List<Long> ids = admin
                ? projectRepository.findPageProjectIdsAll(pageable)
                : projectRepository.findPageProjectIdsWithin(allowedIds, pageable);
        if (ids.isEmpty()) {
            return List.of();
        }

        // findAllById 不保证返回顺序，必须按 SQL 给出的当页顺序重排；否则就是"取对了行、排错了序"，
        // 而列表的分页正确性恰恰依赖这个顺序。
        final Map<Long, Project> byId = projectRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        Project::getId,
                        Function.identity(),
                        (a, b) -> a)); // 主键去重本不该发生；合并函数只用于满足宪法 v2.0.0 原则 VII
        final List<ProjectDTO> projects = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProjectMapper::toDTO)
                .collect(Collectors.toList());

        if (!projects.isEmpty()) {
            projectQueryService.enrichWithTenderAndDetails(projects);
        }
        return projects;
    }

    /**
     * 慢路径：全量取出后按 16 个条件内存筛选，再按 {@code page}/{@code size} 切片。
     *
     * <p>这 16 条筛选原先写在 {@code ProjectController} 里，原样搬入本类——搬动本身不改语义，
     * 目的是让"快路径下推了什么"与"慢路径筛了什么"能对读。只要这 16 条里有一条被改动，
     * 就必须同步检查 {@link ProjectListQuery#hasNoEffectiveFilter()} 的 16 个缺席判据：
     * 少一个判据就意味着那个筛选被默许进入 SQL 比较，而 SQL 的 {@code =} 与
     * {@code String.equals} 在 {@code utf8mb4_unicode_ci} 下不等价。</p>
     */
    private List<ProjectDTO> filterInMemory(
            final List<ProjectDTO> source, final ProjectListQuery query) {
        List<ProjectDTO> projects = source;
        // name 关键词同时匹配项目名、客户名与招标主体（ownerUnit），与前端 placeholder"搜索项目名称、招标主体"一致。
        if (query.name() != null && !query.name().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getName(), query.name()) || containsIgnoreCase(p.getCustomer(), query.name()) || containsIgnoreCase(p.getOwnerUnit(), query.name())).toList();
        if (query.ownerUnit() != null && !query.ownerUnit().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getOwnerUnit(), query.ownerUnit())).toList();
        if (query.projectType() != null && !query.projectType().isBlank()) projects = projects.stream().filter(p -> query.projectType().equals(p.getProjectType())).toList();
        if (query.customerType() != null && !query.customerType().isBlank()) projects = projects.stream().filter(p -> query.customerType().equals(p.getCustomerType())).toList();
        if (query.priority() != null && !query.priority().isBlank()) projects = projects.stream().filter(p -> query.priority().equals(p.getPriority())).toList();
        if (query.sourceModule() != null && !query.sourceModule().isBlank()) projects = projects.stream().filter(p -> query.sourceModule().equals(p.getSourceModule())).toList();
        if (query.bidStatus() != null && !query.bidStatus().isBlank()) projects = projects.stream().filter(p -> query.bidStatus().equals(p.getBidStatus())).toList();
        if (query.stage() != null && !query.stage().isBlank()) projects = projects.stream().filter(p -> query.stage().equals(p.getStage())).toList();
        if (query.projectLeaderId() != null) projects = projects.stream().filter(p -> query.projectLeaderId().equals(p.getProjectLeaderId())).toList();
        // 投标负责人筛选只匹配主负责人，与前端 useProjectFilter 契约保持一致。
        // 副负责人不参与筛选（避免筛 A 时主=B/副=A 的项目混入，而列表只显示主负责人姓名）。
        if (query.biddingLeaderId() != null) projects = projects.stream().filter(p -> query.biddingLeaderId().equals(p.getBiddingLeaderId())).toList();
        if (query.projectLeaderName() != null && !query.projectLeaderName().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getProjectLeaderName(), query.projectLeaderName())).toList();
        if (query.biddingLeaderName() != null && !query.biddingLeaderName().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getBiddingLeaderName(), query.biddingLeaderName())).toList();
        if (query.leaderDepartment() != null && !query.leaderDepartment().isBlank()) projects = projects.stream().filter(p -> query.leaderDepartment().equals(p.getLeaderDepartment())).toList();
        if (query.region() != null && !query.region().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getRegion(), query.region())).toList();
        if (query.biddingPlatform() != null && !query.biddingPlatform().isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getBiddingPlatform(), query.biddingPlatform())).toList();
        if (query.bidMonth() != null && !query.bidMonth().isBlank()) projects = projects.stream().filter(p -> query.bidMonth().equals(p.getBidMonth())).toList();
        if (query.size() != null) {
            long fromLong = (query.page() == null ? 0L : (long) query.page()) * (long) query.size();
            int fromIndex = (int) Math.min(fromLong, (long) projects.size());
            int toIndex = Math.min(fromIndex + query.size(), projects.size());
            projects = projects.subList(fromIndex, toIndex);
        }
        return projects;
    }

    private boolean containsIgnoreCase(String source, String needle) {
        return source != null && needle != null && source.toLowerCase().contains(needle.toLowerCase());
    }
}
