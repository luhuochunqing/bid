package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarSiteAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarSiteAttachmentRepository extends JpaRepository<BarSiteAttachment, Long> {

    List<BarSiteAttachment> findByBarAssetIdOrderByUploadedAtDesc(Long barAssetId);
}
