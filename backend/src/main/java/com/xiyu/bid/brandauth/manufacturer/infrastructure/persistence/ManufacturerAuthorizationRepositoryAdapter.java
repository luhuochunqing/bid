package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ManufacturerAuthorizationRepositoryAdapter implements ManufacturerAuthorizationRepository {

    private final ManufacturerAuthorizationJpaRepository jpaRepository;

    @Override
    public ManufacturerAuthorization save(ManufacturerAuthorization auth) {
        ManufacturerAuthorizationEntity entity = toEntity(auth);
        ManufacturerAuthorizationEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ManufacturerAuthorization> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ManufacturerAuthorization> findByStatus(AuthStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ManufacturerAuthorization> findExpiringSoon(int daysBefore) {
        LocalDate now = LocalDate.now();
        LocalDate threshold = now.plusDays(daysBefore);
        return jpaRepository.findByStatusInAndAuthEndDateBetween(
                        List.of(AuthStatus.ACTIVE, AuthStatus.EXPIRING_SOON), now, threshold)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsByBrandIdAndManufacturerNameAndProductLineAndStatusIn(
            String brandId, String manufacturerName, ProductLine productLine, List<AuthStatus> statuses) {
        return jpaRepository.existsByBrandIdAndManufacturerNameAndProductLineAndStatusIn(
                brandId, manufacturerName, productLine, statuses);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private ManufacturerAuthorization toDomain(ManufacturerAuthorizationEntity e) {
        return new ManufacturerAuthorization(
                e.getId(), e.getAuthorizationType(),
                e.getProductLine(), e.getBrandId(), e.getBrandName(),
                e.getImportDomestic(), e.getManufacturerName(), e.getAgentName(),
                e.getAuthStartDate(), e.getAuthEndDate(),
                e.getAuth1StartDate(), e.getAuth1EndDate(), e.getAuth1Remarks(),
                e.getAuth2StartDate(), e.getAuth2EndDate(), e.getAuth2Remarks(),
                e.getRemarks(), e.getStatus(), e.getRevokeReason(), e.getCreatedBy(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion()
        );
    }

    private ManufacturerAuthorizationEntity toEntity(ManufacturerAuthorization a) {
        return ManufacturerAuthorizationEntity.builder()
                .id(a.id())
                .authorizationType(a.authorizationType())
                .productLine(a.productLine())
                .brandId(a.brandId())
                .brandName(a.brandName())
                .importDomestic(a.importDomestic())
                .manufacturerName(a.manufacturerName())
                .agentName(a.agentName())
                .authStartDate(a.authStartDate())
                .authEndDate(a.authEndDate())
                .auth1StartDate(a.auth1StartDate())
                .auth1EndDate(a.auth1EndDate())
                .auth1Remarks(a.auth1Remarks())
                .auth2StartDate(a.auth2StartDate())
                .auth2EndDate(a.auth2EndDate())
                .auth2Remarks(a.auth2Remarks())
                .remarks(a.remarks())
                .status(a.status())
                .revokeReason(a.revokeReason())
                .createdBy(a.createdBy())
                .createdAt(a.createdAt())
                .updatedAt(a.updatedAt())
                .version(a.version())
                .build();
    }
}
