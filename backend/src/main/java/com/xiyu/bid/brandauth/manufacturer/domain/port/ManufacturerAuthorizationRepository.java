package com.xiyu.bid.brandauth.manufacturer.domain.port;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;

import java.util.List;
import java.util.Optional;

public interface ManufacturerAuthorizationRepository {
    ManufacturerAuthorization save(ManufacturerAuthorization entity);
    Optional<ManufacturerAuthorization> findById(Long id);
    List<ManufacturerAuthorization> findByStatus(AuthStatus status);
    List<ManufacturerAuthorization> findExpiringSoon(int daysBefore);
    boolean existsByBrandIdAndManufacturerNameAndProductLineAndStatusIn(
            String brandId, String manufacturerName, ProductLine productLine, List<AuthStatus> statuses);
    long count();
}
