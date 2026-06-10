package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaCertificatePlatformRepository
        extends JpaRepository<CaCertificatePlatformEntity, Long> {

    List<CaCertificatePlatformEntity> findByCaCertificateId(Long caCertificateId);

    void deleteByCaCertificateId(Long caCertificateId);
}
