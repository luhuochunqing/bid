package com.xiyu.bid.repository;

import com.xiyu.bid.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志数据访问层，内部沿用 AuditLog 命名兼容既有表结构。
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 查询用户的所有操作日志
     */
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * 查询指定实体的操作日志
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        String entityType, String entityId
    );

    /**
     * 查询指定类型的操作日志
     */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /**
     * 查询时间范围内的日志
     */
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime start, LocalDateTime end
    );

    /**
     * 查询失败的操作日志
     */
    List<AuditLog> findBySuccessFalseOrderByTimestampDesc();

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:keyword IS NULL OR
               LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.entityId, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.entityType, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.username, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:action IS NULL OR UPPER(a.action) = UPPER(:action))
          AND (:username IS NULL OR LOWER(COALESCE(a.username, '')) = LOWER(:username))
          AND (:start IS NULL OR a.timestamp >= :start)
          AND (:end IS NULL OR a.timestamp <= :end)
          AND (:success IS NULL OR a.success = :success)
        ORDER BY a.timestamp DESC
        """)
    List<AuditLog> searchLogs(
            @Param("keyword") String keyword,
            @Param("action") String action,
            @Param("username") String username,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("success") Boolean success
    );

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:keyword IS NULL OR
               LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.entityId, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.entityType, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(COALESCE(a.username, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:action IS NULL OR UPPER(a.action) = UPPER(:action))
          AND (LOWER(COALESCE(a.userId, '')) = LOWER(:actorUsername) OR
               LOWER(COALESCE(a.username, '')) = LOWER(:actorUsername) OR
               (:actorUserId IS NOT NULL AND a.userId = :actorUserId))
          AND (:start IS NULL OR a.timestamp >= :start)
          AND (:end IS NULL OR a.timestamp <= :end)
          AND (:success IS NULL OR a.success = :success)
        ORDER BY a.timestamp DESC
        """)
    List<AuditLog> searchLogsForActor(
            @Param("keyword") String keyword,
            @Param("action") String action,
            @Param("actorUsername") String actorUsername,
            @Param("actorUserId") String actorUserId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("success") Boolean success
    );

    /**
     * 统计用户在指定时间内的操作次数
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.timestamp BETWEEN :start AND :end")
    long countByUserIdAndTimestampBetween(
        @Param("userId") String userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * 统计指定时间之前的日志数量
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp < :beforeDate")
    long countByTimestampBefore(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 删除旧的日志（用于定期清理）
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :beforeDate")
    void deleteOldLogs(@Param("beforeDate") LocalDateTime beforeDate);
}
