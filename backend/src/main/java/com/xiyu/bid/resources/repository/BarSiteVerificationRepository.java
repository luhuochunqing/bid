package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarSiteVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarSiteVerificationRepository extends JpaRepository<BarSiteVerification, Long> {

    List<BarSiteVerification> findByBarAssetIdOrderByVerifiedAtDesc(Long barAssetId);

    Optional<BarSiteVerification> findTopByBarAssetIdOrderByVerifiedAtDesc(Long barAssetId);
}
