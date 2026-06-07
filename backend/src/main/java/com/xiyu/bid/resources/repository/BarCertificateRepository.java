package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.BarCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BarCertificateRepository extends JpaRepository<BarCertificate, Long> {

    List<BarCertificate> findByBarAssetIdOrderByExpiryDateAsc(Long barAssetId);
}
