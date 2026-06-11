package com.xiyu.bid.businessqualification.domain.port;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationPage;

import java.util.List;
import java.util.Optional;

public interface BusinessQualificationRepository {
    BusinessQualification save(BusinessQualification qualification);

    Optional<BusinessQualification> findById(Long id);

    /**
     * CO-155 fix: paginated query. Returns domain-layer QualificationPage
     * (NOT Spring Data Page) to keep the domain package free of framework
     * dependencies (enforced by FPJavaArchitectureTest).
     */
    QualificationPage<BusinessQualification> findAll(QualificationListCriteria criteria, int page, int size);

    /**
     * Backward-compatible query without pagination.
     */
    List<BusinessQualification> findAll(QualificationListCriteria criteria);

    List<BusinessQualification> findExpiringWithinDays(int days);

    void deleteById(Long id);

    boolean existsById(Long id);

    boolean existsByCertificateNo(String certificateNo);

    List<String> findAllLevels();
}
