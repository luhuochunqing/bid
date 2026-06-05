package com.xiyu.bid.businessqualification.domain.port;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;

import java.util.List;
import java.util.Optional;

public interface BusinessQualificationRepository {
    BusinessQualification save(BusinessQualification qualification);

    Optional<BusinessQualification> findById(Long id);

    List<BusinessQualification> findAll(QualificationListCriteria criteria);

    List<BusinessQualification> findExpiringWithinDays(int days);

    void deleteById(Long id);

    boolean existsById(Long id);
}
