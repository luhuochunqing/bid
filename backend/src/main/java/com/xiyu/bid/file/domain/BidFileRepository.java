package com.xiyu.bid.file.domain;

import com.xiyu.bid.file.entity.BidFile;

import java.util.Optional;

public interface BidFileRepository {

    BidFile save(BidFile bidFile);

    Optional<BidFile> findById(Long id);

    Optional<BidFile> findByUploadId(String uploadId);

    Optional<BidFile> findByUploadIdAndCreatorId(String uploadId, Long creatorId);
}
