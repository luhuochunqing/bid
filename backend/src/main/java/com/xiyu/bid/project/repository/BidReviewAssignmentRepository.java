// Input: reviewId / reviewerId
// Output: List<BidReviewAssignmentEntity> / Optional<BidReviewAssignmentEntity>
// Pos: project/repository/ - JPA Repository, data access shell
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.BidReviewAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标书审核人分配记录仓库（CO-483 + CO-484 多人审核）。
 */
@Repository
public interface BidReviewAssignmentRepository extends JpaRepository<BidReviewAssignmentEntity, Long> {

    /** 查询某条审核记录的所有审核人分配（按创建时间升序，保证审核人顺序稳定）。 */
    List<BidReviewAssignmentEntity> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    /** 查询某条审核记录中某审核人的分配记录。 */
    Optional<BidReviewAssignmentEntity> findByReviewIdAndReviewerId(Long reviewId, Long reviewerId);

    /** CO-591: 批量查询多条审核记录的审核人分配（按创建时间升序，保证列表展示顺序稳定）。 */
    List<BidReviewAssignmentEntity> findByReviewIdInOrderByCreatedAtAsc(Collection<Long> reviewIds);

    /**
     * 删除某条审核记录的所有审核人分配（驳回重提场景清空旧决策）。
     * <p>CO-484 修复：必须用 {@code @Modifying} bulk DELETE，让 DELETE SQL 立即执行。
     * 否则 Spring Data 的 derived delete 会把 DELETE 加入 Hibernate ActionQueue，
     * flush 时 INSERT 先于 DELETE 执行，重新提交选了相同审核人会撞 {@code uk_review_reviewer}
     * 唯一约束 → 500 系统繁忙。详见 {@code docs/references/jpa-hibernate-lessons.md §1}。</p>
     */
    @Modifying
    @Query("DELETE FROM BidReviewAssignmentEntity a WHERE a.reviewId = :reviewId")
    void deleteByReviewId(@Param("reviewId") Long reviewId);

    /**
     * 幂等插入审核人分配记录（CO-484 并发提交防重）。
     * <p>使用 {@code INSERT IGNORE} 依赖数据库唯一键 {@code uk_review_reviewer}
     * 兜底：并发场景下两个事务同时写入同一 {@code (review_id, reviewer_id)} 时，
     * 只有一个成功，另一个静默忽略，避免 {@code DataIntegrityViolationException}。</p>
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO bid_review_assignment"
            + " (review_id, reviewer_id, decision, comment, decided_at, created_at)"
            + " VALUES (:reviewId, :reviewerId, NULL, NULL, NULL, NOW())",
            nativeQuery = true)
    void insertIgnore(@Param("reviewId") Long reviewId,
                      @Param("reviewerId") Long reviewerId);
}
