package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarSiteAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarSiteAccountRepository extends JpaRepository<BarSiteAccount, Long> {

    List<BarSiteAccount> findByBarAssetIdOrderByCreatedAtAsc(Long barAssetId);
}
