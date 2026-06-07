// Input: DocumentVersion实体
// Output: 版本历史数据访问接口
// Pos: Repository/数据访问层
// 提供文档版本的数据访问操作

package com.xiyu.bid.versionhistory.repository;

import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档版本Repository
 * 提供文档版本的数据访问方法
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /**
     * 根据项目ID查询所有版本（按创建时间倒序）
     */
    List<DocumentVersion> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /**
     * 查找项目的当前版本
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.projectId = :projectId AND v.isCurrent = true")
    Optional<DocumentVersion> findCurrentVersionByProjectId(@Param("projectId") Long projectId);

    /**
     * 查找项目的所有版本号
     */
    @Query("SELECT v.versionNumber FROM DocumentVersion v WHERE v.projectId = :projectId ORDER BY v.versionNumber DESC")
    List<Integer> findVersionNumbersByProjectId(@Param("projectId") Long projectId);

    /**
     * 获取项目的下一个版本号
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) + 1 FROM DocumentVersion v WHERE v.projectId = :projectId")
    Integer getNextVersionNumber(@Param("projectId") Long projectId);

    /**
     * 根据项目ID更新所有版本的当前标志为false
     */
    @Query("UPDATE DocumentVersion v SET v.isCurrent = false WHERE v.projectId = :projectId AND v.isCurrent = true")
    void markAllAsNotCurrent(@Param("projectId") Long projectId);
}
