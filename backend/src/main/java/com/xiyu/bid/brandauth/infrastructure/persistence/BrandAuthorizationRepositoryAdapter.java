package com.xiyu.bid.brandauth.infrastructure.persistence;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import com.xiyu.bid.brandauth.infrastructure.persistence.entity.BrandAuthorizationEntity;
import com.xiyu.bid.brandauth.infrastructure.persistence.repository.BrandAuthorizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BrandAuthorizationRepositoryAdapter implements BrandAuthorizationRepository {

    private final BrandAuthorizationJpaRepository jpaRepository;

    @Override
    @Transactional
    public BrandAuthorization save(BrandAuthorization auth) {
        BrandAuthorizationEntity entity = toEntity(auth);
        BrandAuthorizationEntity saved = jpaRepository.save(entity);
        return findById(saved.getId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandAuthorization> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandAuthorization> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandAuthorization> findByBrandName(String brandName) {
        return jpaRepository.findByBrandName(brandName).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandAuthorization> findByStatus(AuthorizationStatus status) {
        return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandAuthorization> findExpiringSoon(int warningDays) {
        LocalDate threshold = LocalDate.now().plusDays(warningDays);
        return jpaRepository.findExpiringByThreshold(threshold).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long count() { return jpaRepository.count(); }

    private BrandAuthorizationEntity toEntity(BrandAuthorization a) {
        var e = new BrandAuthorizationEntity();
        if (a.id() != null) e.setId(a.id());
        e.setBrandName(a.brandName());
        e.setSupplierName(a.supplierName());
        e.setAuthorizationScope(a.scope());
        e.setScopeDetail(a.scopeDetail());
        e.setStartDate(a.startDate());
        e.setEndDate(a.endDate());
        e.setStatus(a.status());
        e.setAuthorizationDocUrl(a.authorizationDocUrl());
        e.setRemarks(a.remarks());
        e.setCreatedAt(a.createdAt());
        e.setUpdatedAt(a.updatedAt());
        return e;
    }

    private BrandAuthorization toDomain(BrandAuthorizationEntity e) {
        return new BrandAuthorization(
                e.getId(), e.getBrandName(), e.getSupplierName(),
                e.getAuthorizationScope(), e.getScopeDetail(),
                e.getStartDate(), e.getEndDate(), e.getStatus(),
                e.getAuthorizationDocUrl(), e.getRemarks(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
