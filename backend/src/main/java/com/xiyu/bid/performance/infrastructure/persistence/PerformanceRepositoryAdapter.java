// checkstyle:off
package com.xiyu.bid.performance.infrastructure.persistence;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceAttachmentJpaRepository;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.performance.infrastructure.persistence.spec.PerformanceRecordSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PerformanceRepositoryAdapter implements PerformanceRepository {

    private final PerformanceRecordJpaRepository jpaRepository;
    private final PerformanceAttachmentJpaRepository attRepository;

    @Override
    @Transactional
    public PerformanceRecord save(PerformanceRecord record) {
        PerformanceRecordEntity entity;
        if (record.id() != null) {
            entity = jpaRepository.findById(record.id())
                    .orElseGet(PerformanceRecordEntity::new);
            updateEntityFields(entity, record);
        } else {
            entity = toEntity(record);
        }
        PerformanceRecordEntity saved = jpaRepository.save(entity);
        attRepository.deleteByPerformanceId(saved.getId());
        for (var att : record.attachments()) {
            attRepository.save(toAttEntity(att, saved.getId()));
        }
        return findById(saved.getId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerformanceRecord> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerformanceRecord> findByContractName(String contractName) {
        return jpaRepository.findByContractName(contractName).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceRecord> findAll(PerformanceSearchCriteria criteria, PerformanceAlertConfig config) {
        var spec = PerformanceRecordSpecification.build(criteria, config);
        return jpaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<PerformanceRecord> findAllPageable(
            PerformanceSearchCriteria criteria, PerformanceAlertConfig config, int pageNumber, int pageSize) {
        var spec = PerformanceRecordSpecification.build(criteria, config);
        var page = jpaRepository.findAll(spec, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<PerformanceRecord> content = page.getContent().stream().map(this::toDomain).toList();
        return PagedResult.of(content, page.getTotalElements(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        attRepository.deleteByPerformanceId(id);
        jpaRepository.deleteById(id);
    }

    @Override
    public long count() { return jpaRepository.count(); }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceRecord> findAllWithExpiryDate() {
        return jpaRepository.findAllWithExpiryDate(LocalDate.now())
                .stream().map(this::toDomain).toList();
    }

    private void updateEntityFields(PerformanceRecordEntity e, PerformanceRecord r) {
        e.setContractName(r.contractName());
        e.setSigningEntity(r.signingEntity());
        e.setGroupCompany(r.groupCompany());
        e.setCustomerType(r.customerType());
        e.setIndustry(r.industry());
        e.setProjectType(r.projectType());
        e.setDockingMethod(r.dockingMethod());
        e.setCustomerLevel(r.customerLevel());
        e.setSigningDate(r.signingDate());
        e.setExpiryDate(r.expiryDate());
        e.setTotalExpiryDate(r.totalExpiryDate());
        e.setContactPerson(r.contactPerson());
        e.setContactInfo(r.contactInfo());
        e.setTerritory(r.territory());
        e.setCustomerAddress(r.customerAddress());
        e.setXiyuProjectManager(r.xiyuProjectManager());
        e.setMallWebsiteUrl(r.mallWebsiteUrl());
        e.setHasBidNotice(r.hasBidNotice());
        e.setRemarks(r.remarks());
        if (e.getCreatedAt() == null) e.setCreatedAt(r.createdAt());
        e.setUpdatedAt(r.updatedAt());
    }

    private PerformanceRecordEntity toEntity(PerformanceRecord r) {
        var e = new PerformanceRecordEntity();
        if (r.id() != null) e.setId(r.id());
        updateEntityFields(e, r);
        return e;
    }

    private PerformanceRecord toDomain(PerformanceRecordEntity e) {
        var atts = attRepository.findByPerformanceId(e.getId()).stream()
                .map(a -> new PerformanceRecord.AttachmentEntry(
                        a.getId(), a.getFileName(), a.getFileUrl(), a.getFileType())).toList();
        return new PerformanceRecord(
                e.getId(), e.getContractName(), e.getSigningEntity(), e.getGroupCompany(),
                e.getCustomerType(), e.getIndustry(), e.getProjectType(), e.getDockingMethod(),
                e.getCustomerLevel(), e.getSigningDate(), e.getExpiryDate(), e.getTotalExpiryDate(),
                e.getContactPerson(), e.getContactInfo(), e.getTerritory(), e.getCustomerAddress(),
                e.getXiyuProjectManager(), e.getMallWebsiteUrl(), e.isHasBidNotice(), e.getRemarks(),
                atts, e.getCreatedAt(), e.getUpdatedAt());
    }

    private PerformanceAttachmentEntity toAttEntity(PerformanceRecord.AttachmentEntry a, Long perfId) {
        var e = new PerformanceAttachmentEntity();
        if (a.id() != null) e.setId(a.id());
        e.setPerformanceId(perfId);
        e.setFileName(a.fileName());
        e.setFileUrl(a.fileUrl());
        e.setFileType(a.fileType());
        return e;
    }
}
