package com.xiyu.bid.tenderupload.repository;

import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderFileUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenderFileRepository extends JpaRepository<TenderFile, Long> {

    Optional<TenderFile> findByUploadId(String uploadId);

    Optional<TenderFile> findByUploadIdAndUserId(String uploadId, Long userId);

    Optional<TenderFile> findByUserIdAndFileSha256(Long userId, String fileSha256);

    long countByUploadStatus(TenderFileUploadStatus uploadStatus);
}
