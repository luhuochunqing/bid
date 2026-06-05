package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarSiteSop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BarSiteSopRepository extends JpaRepository<BarSiteSop, Long> {

    Optional<BarSiteSop> findByBarAssetId(Long barAssetId);
}
