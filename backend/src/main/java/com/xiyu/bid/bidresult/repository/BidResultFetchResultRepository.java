package com.xiyu.bid.bidresult.repository;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BidResultFetchResultRepository extends JpaRepository<BidResultFetchResult, Long> {
    List<BidResultFetchResult> findByStatusOrderByFetchTimeDesc(BidResultFetchResult.Status status);
    long countByStatus(BidResultFetchResult.Status status);
    Optional<BidResultFetchResult> findFirstByTenderIdAndStatusOrderByFetchTimeDesc(Long tenderId, BidResultFetchResult.Status status);
    Optional<BidResultFetchResult> findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(Long projectId, BidResultFetchResult.Status status);
    List<BidResultFetchResult> findByIdIn(Collection<Long> ids);
    List<BidResultFetchResult> findAllByOrderByFetchTimeDesc();

    @Query("""
            select r
            from BidResultFetchResult r
            where r.status = com.xiyu.bid.bidresult.entity.BidResultFetchResult$Status.CONFIRMED
              and r.result = com.xiyu.bid.bidresult.entity.BidResultFetchResult$Result.WON
              and (
                (:tenderId is not null and r.tenderId = :tenderId)
                or (:keyword is not null and :keyword <> '' and (
                    lower(coalesce(r.projectName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(r.remark, '')) like lower(concat('%', :keyword, '%'))
                ))
              )
            order by r.fetchTime desc
            """)
    List<BidResultFetchResult> findScopedConfirmedWins(
            @Param("tenderId") Long tenderId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
