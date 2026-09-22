package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.core.ProjectStage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 项目数据访问接口
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 根据状态查询项目
     */
    List<Project> findByStatus(Project.Status status);

    /**
     * 根据项目经理ID查询项目
     */
    List<Project> findByManagerId(Long managerId);

    /**
     * 根据标讯ID查询项目
     */
    List<Project> findByTenderId(Long tenderId);

    /**
     * 根据标讯ID列表批量查询项目（用于列表场景批量补全项目经理）
     */
    List<Project> findByTenderIdIn(Collection<Long> tenderIds);

    /**
     * 统计指定状态的项目数量
     */
    Long countByStatus(Project.Status status);

    /**
     * 查询所有活跃项目（非终态状态）
     */
    @Query("SELECT p FROM Project p WHERE p.status NOT IN ('WON', 'LOST', 'FAILED', 'ABANDONED')")
    List<Project> findActiveProjects();

    @Query("""
            SELECT DISTINCT p
            FROM Project p
            LEFT JOIN FETCH p.teamMembers tm
            WHERE p.tenderId IN :tenderIds
            """)
    List<Project> findAllWithTeamMembersByTenderIdIn(Collection<Long> tenderIds);

    @Query("""
            SELECT DISTINCT p.id
            FROM Project p
            LEFT JOIN p.teamMembers tm
            WHERE p.managerId = :userId OR tm = :userId
            """)
    List<Long> findAccessibleProjectIdsByUserId(Long userId);

    @Query("SELECT p.id FROM Project p")
    List<Long> findAllProjectIds();

    /** 按项目ID列表获取关联的标讯ID（去重） */
    @Query("SELECT DISTINCT p.tenderId FROM Project p WHERE p.id IN :projectIds")
    List<Long> findTenderIdsByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Query(value = """
            SELECT DISTINCT p.id
            FROM projects p
            LEFT JOIN users manager_user ON manager_user.id = p.manager_id
            LEFT JOIN project_team_members ptm ON ptm.project_id = p.id
            LEFT JOIN users member_user ON member_user.id = ptm.member_id
            WHERE COALESCE(manager_user.department_code, 'UNASSIGNED') IN (:departmentCodes)
               OR COALESCE(member_user.department_code, 'UNASSIGNED') IN (:departmentCodes)
            """, nativeQuery = true)
    List<Long> findAccessibleProjectIdsByDepartmentCodes(Collection<String> departmentCodes);

    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM Project p
            LEFT JOIN p.teamMembers tm
            WHERE p.id = :projectId AND (p.managerId = :userId OR tm = :userId)
            """)
    long countAccessibleProjectByIdAndUserId(Long projectId, Long userId);

    /**
     * 根据客户ID列表查询项目
     */
    List<Project> findBySourceCustomerIdIn(Collection<String> customerIds);

    /**
     * 根据项目名称模糊查询
     */
    List<Project> findByNameContainingIgnoreCase(String name);

    /**
     * 查询指定时间范围内开始的项目
     */
    @Query("SELECT p FROM Project p WHERE p.startDate BETWEEN :startDate AND :endDate")
    List<Project> findByStartDateBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    /** 按更新时间增量查询（用于 CRM 增量同步），按 updatedAt 降序。 */
    @Query("SELECT p FROM Project p WHERE p.updatedAt >= :since ORDER BY p.updatedAt DESC")
    List<Project> findByUpdatedAtAfter(@Param("since") java.time.LocalDateTime since);

    /**
     * 工作台角色化改造：按项目阶段（stage）集合查询项目。
     * 枚举类型入参保证类型安全；name() 转换收口在 default 方法，调用方无需手工映射。
     */
    default List<Project> findByStageIn(Collection<ProjectStage> stages) {
        return findByStageNameIn(stages.stream().map(ProjectStage::name).toList());
    }

    /** 底层查询：stage 在 Project 实体中以 String 存储。 */
    @Query("SELECT p FROM Project p WHERE p.stage IN :stages")
    List<Project> findByStageNameIn(@Param("stages") Collection<String> stages);

    /**
     * 列表接口的服务端分页取数（管理员/全局可见：不限制可见项目 id）。
     *
     * <p>只回当页 id，让调用方按 id 取实体并只富化当页——这正是本次改动的目的：
     * 原先为了取 10 行要富化全表。</p>
     *
     * <p><b>这里刻意没有任何筛选条件。</b>调用方只在"一个筛选都没传"时走到这里
     * （判据见 {@code ProjectListQuery#hasNoEffectiveFilter}）：SQL 的 {@code =} 与 Java 的
     * {@code String.equals} 在 {@code utf8mb4_unicode_ci} 下并非同一语义（大小写不敏感），
     * 任何值进入 SQL 比较都可能把结果放大，而 H2 测试环境对此无证明力。</p>
     *
     * <p>排序是全序：{@code createdAt} 降序（MySQL 在 DESC 下把 NULL 排最后，与慢路径
     * "null 视作 MIN 再反序"相同）；并列时按 id 升序——这一条必须与
     * {@code ProjectListQueryService#sortByCreatedAtDescIdAsc}（全序的唯一出处，全量路径也调它）
     * **逐字一致**，否则 `created_at`
     * 并列的两行在两条路径里会落到不同页。压测铺底数据用秒级随机时间，并列并非罕见。</p>
     *
     * <p>已知代价（记录不修）：{@code projects.created_at} 上没有索引，这里在 DB 侧仍是
     * 全表扫 + filesort；本次收益是"不再把 N 行搬到应用层富化"，不是扫描量下降。</p>
     */
    @Query("SELECT p.id FROM Project p ORDER BY p.createdAt DESC, p.id ASC")
    List<Long> findPageProjectIdsAll(Pageable pageable);

    /**
     * 列表接口的服务端分页取数（非管理员：限定在可见项目 id 集合内）。
     *
     * <p>{@code allowedIds} 必须非空——调用方在集合为空时直接返回空页，不会走到这里。
     * 这条路径刻意不做成"可空集合"参数：Hibernate 各版本对 {@code IN :null} 的处理并不一致，
     * 最坏的一种是静默返回空页，即"看起来正常"的错误答案。</p>
     */
    @Query("SELECT p.id FROM Project p WHERE p.id IN :allowedIds"
            + " ORDER BY p.createdAt DESC, p.id ASC")
    List<Long> findPageProjectIdsWithin(@Param("allowedIds") Collection<Long> allowedIds,
                                        Pageable pageable);
}
