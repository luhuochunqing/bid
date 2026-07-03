// Input: reviewId / reviewerId
// Output: List<BidReviewAssignmentEntity> / Optional<BidReviewAssignmentEntity>
// Pos: project/repository/ - JPA Repository, data access shell
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.BidReviewAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    /** 删除某条审核记录的所有审核人分配（驳回重提场景清空旧决策）。 */
    void deleteByReviewId(Long reviewId);
}
