// Input: projectId
// Output: Optional<BidDocumentReviewEntity>
// Pos: project/repository/ - JPA Repository, data access shell
package com.xiyu.bid.project.repository;

import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 标书审核记录持久化仓库。
 */
@Repository
public interface BidDocumentReviewRepository extends JpaRepository<BidDocumentReviewEntity, Long> {

    Optional<BidDocumentReviewEntity> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
