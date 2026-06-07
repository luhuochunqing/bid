package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ManufacturerAuthorizationJpaRepository
        extends JpaRepository<ManufacturerAuthorizationEntity, Long>,
                JpaSpecificationExecutor<ManufacturerAuthorizationEntity> {

    List<ManufacturerAuthorizationEntity> findByStatus(AuthStatus status);

    List<ManufacturerAuthorizationEntity> findByStatusInAndAuthEndDateBetween(
            List<AuthStatus> statuses, LocalDate start, LocalDate end);

    boolean existsByBrandIdAndManufacturerNameAndProductLineAndStatusIn(
            String brandId, String manufacturerName, ProductLine productLine, List<AuthStatus> statuses);

    long countByStatusIn(List<AuthStatus> statuses);
}
