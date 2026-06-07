package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Project;
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
}
