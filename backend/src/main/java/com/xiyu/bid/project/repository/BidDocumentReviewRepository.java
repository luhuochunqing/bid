// Input: projectId
// Output: Optional<BidDocumentReviewEntity>
// Pos: project/repository/ - JPA Repository, data access shell
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标书审核记录持久化仓库。
 */
@Repository
public interface BidDocumentReviewRepository extends JpaRepository<BidDocumentReviewEntity, Long> {

    Optional<BidDocumentReviewEntity> findByProjectId(Long projectId);

    List<BidDocumentReviewEntity> findByReviewerId(Long reviewerId);

    /** CO-591: 批量查询项目标书审核记录，用于列表展示标书审核人。 */
    List<BidDocumentReviewEntity> findByProjectIdIn(Collection<Long> projectIds);

    void deleteByProjectId(Long projectId);
}
