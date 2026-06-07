package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Application service for listing and querying brand authorizations. */
@Service
@RequiredArgsConstructor
public class ListManufacturerAuthAppService {

    /** JPA repository for dynamic queries. */
    private final ManufacturerAuthorizationJpaRepository jpaRepository;
    /** Domain repository for domain-level operations. */
    private final ManufacturerAuthorizationRepository repository;
    /** Attachment repository. */
    private final BrandAuthAttachmentJpaRepository attachmentRepository;

    /**
     * List authorizations with dynamic filters and pagination.
     *
     * @param filter the filter criteria
     * @param page   zero-based page number
     * @param size   page size
     * @return paginated DTOs
     */
    public Page<ManufacturerAuthorizationDTO> list(
            final ListFilter filter, final int page, final int size) {
        Specification<ManufacturerAuthorizationEntity> spec =
                (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filter.productLines != null
                    && !filter.productLines.isEmpty()) {
                p.add(root.get("productLine")
                        .in(filter.productLines));
            }
            if (notBlank(filter.brandId)) {
                p.add(cb.like(root.get("brandId"),
                        "%" + filter.brandId + "%"));
            }
            if (notBlank(filter.brandName)) {
                p.add(cb.like(root.get("brandName"),
                        "%" + filter.brandName + "%"));
            }
            if (notBlank(filter.importDomestic)) {
                p.add(cb.equal(root.get("importDomestic"),
                        filter.importDomestic));
            }
            if (notBlank(filter.manufacturerName)) {
                p.add(cb.like(root.get("manufacturerName"),
                        "%" + filter.manufacturerName + "%"));
            }
            if (filter.authStartFrom != null) {
                p.add(cb.greaterThanOrEqualTo(
                        root.get("authStartDate"),
                        filter.authStartFrom));
            }
            if (filter.authStartTo != null) {
                p.add(cb.lessThanOrEqualTo(
                        root.get("authStartDate"),
                        filter.authStartTo));
            }
            if (filter.authEndFrom != null) {
                p.add(cb.greaterThanOrEqualTo(
                        root.get("authEndDate"),
                        filter.authEndFrom));
            }
            if (filter.authEndTo != null) {
                p.add(cb.lessThanOrEqualTo(
                        root.get("authEndDate"),
                        filter.authEndTo));
            }
            if (filter.statuses != null
                    && !filter.statuses.isEmpty()) {
                p.add(root.get("status").in(filter.statuses));
            }
            if (notBlank(filter.keyword)) {
                String kw = "%" + filter.keyword + "%";
                p.add(cb.or(
                        cb.like(root.get("brandId"), kw),
                        cb.like(root.get("brandName"), kw),
                        cb.like(root.get("manufacturerName"), kw),
                        cb.like(root.get("remarks"), kw)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        PageRequest pr = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "authEndDate"));
        return jpaRepository.findAll(spec, pr)
                .map(entity -> {
                    var domain = toDomain(entity);
                    var atts = attachmentRepository
                            .findByAuthorizationId(entity.getId());
                    return ManufacturerAuthMapper.toDTO(domain, atts);
                });
    }

    /**
     * Get a single authorization DTO by ID.
     *
     * @param id authorization ID
     * @return DTO or empty
     */
    public Optional<ManufacturerAuthorizationDTO> getDetail(
            final Long id) {
        return repository.findById(id).map(domain -> {
            var atts = attachmentRepository.findByAuthorizationId(id);
            return ManufacturerAuthMapper.toDTO(domain, atts);
        });
    }

    private ManufacturerAuthorization toDomain(
            final ManufacturerAuthorizationEntity e) {
        return new ManufacturerAuthorization(
                e.getId(), e.getAuthorizationType(),
                e.getProductLine(), e.getBrandId(), e.getBrandName(),
                e.getImportDomestic(), e.getManufacturerName(),
                e.getAgentName(),
                e.getAuthStartDate(), e.getAuthEndDate(),
                e.getAuth1StartDate(), e.getAuth1EndDate(),
                e.getAuth1Remarks(),
                e.getAuth2StartDate(), e.getAuth2EndDate(),
                e.getAuth2Remarks(),
                e.getRemarks(), e.getStatus(), e.getRevokeReason(),
                e.getCreatedBy(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    private static boolean notBlank(final String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Value object for list query filter parameters.
     *
     * @param productLines      product lines to filter by
     * @param brandId           brand ID (fuzzy)
     * @param brandName         brand name (fuzzy)
     * @param importDomestic    import/domestic flag
     * @param manufacturerName  manufacturer name (fuzzy)
     * @param authStartFrom     auth start date lower bound
     * @param authStartTo       auth start date upper bound
     * @param authEndFrom       auth end date lower bound
     * @param authEndTo         auth end date upper bound
     * @param statuses          status values to filter by
     * @param keyword           keyword search
     */
    public record ListFilter(
            List<com.xiyu.bid.brandauth.manufacturer.domain.valueobject
                    .ProductLine> productLines,
            String brandId, String brandName,
            String importDomestic, String manufacturerName,
            LocalDate authStartFrom, LocalDate authStartTo,
            LocalDate authEndFrom, LocalDate authEndTo,
            List<AuthStatus> statuses, String keyword) {
    }
}
