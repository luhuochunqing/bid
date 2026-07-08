package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BidCaseSliceRepository extends JpaRepository<BidCaseSlice, Long> {

    List<BidCaseSlice> findByEmbeddingIsNotNull();

    long countByEmbeddingIsNotNull();

    List<BidCaseSlice> findByProjectDirAndDocxFileAndSectionIdx(String projectDir, String docxFile, int sectionIdx);

    boolean existsByProjectDirAndDocxFileAndSectionIdx(String projectDir, String docxFile, int sectionIdx);

    @Query("""
            SELECT COUNT(s)
            FROM BidCaseSlice s
            WHERE s.embedding IS NULL
              AND (s.embeddingModel IS NULL OR s.embeddingModel <> 'FAILED')
            """)
    long countUnembedded();

    @Query("""
            SELECT s
            FROM BidCaseSlice s
            WHERE s.embedding IS NULL
              AND (s.embeddingModel IS NULL OR s.embeddingModel <> 'FAILED')
            """)
    List<BidCaseSlice> findUnembedded(Pageable pageable);
}
