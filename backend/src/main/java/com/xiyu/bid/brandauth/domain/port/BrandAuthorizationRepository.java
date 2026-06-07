package com.xiyu.bid.brandauth.domain.port;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;

import java.util.List;
import java.util.Optional;

public interface BrandAuthorizationRepository {

    BrandAuthorization save(BrandAuthorization auth);

    Optional<BrandAuthorization> findById(Long id);

    List<BrandAuthorization> findAll();

    List<BrandAuthorization> findByBrandName(String brandName);

    List<BrandAuthorization> findByStatus(AuthorizationStatus status);

    List<BrandAuthorization> findExpiringSoon(int warningDays);

    void deleteById(Long id);

    long count();
}
