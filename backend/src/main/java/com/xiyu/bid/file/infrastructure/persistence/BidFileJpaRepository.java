package com.xiyu.bid.file.infrastructure.persistence;

import com.xiyu.bid.file.domain.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BidFileJpaRepository extends JpaRepository<BidFile, Long>, BidFileRepository {

    @Override
    Optional<BidFile> findByUploadId(String uploadId);

    @Override
    Optional<BidFile> findByUploadIdAndCreatorId(String uploadId, Long creatorId);
}
