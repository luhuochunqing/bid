package com.xiyu.bid.repository;

import com.xiyu.bid.entity.ProjectGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, Long> {

    boolean existsByGroupCode(String groupCode);

    boolean existsByGroupCodeAndIdNot(String groupCode, Long id);

    @Query("""
            select distinct projectId
            from ProjectGroup pg
            join pg.projectIds projectId
            left join pg.memberUserIds memberUserId
            left join pg.allowedRoles allowedRole
            where pg.visibility = 'all'
               or (pg.visibility = 'manager' and pg.managerUserId = :userId)
               or (pg.visibility = 'members' and (pg.managerUserId = :userId or memberUserId = :userId))
               or (pg.visibility = 'custom' and (pg.managerUserId = :userId or allowedRole = :roleCode))
            """)
    List<Long> findGrantedProjectIds(Long userId, String roleCode);
}
