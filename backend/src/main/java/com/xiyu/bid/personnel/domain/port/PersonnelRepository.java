package com.xiyu.bid.personnel.domain.port;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;

import java.util.List;
import java.util.Optional;

public interface PersonnelRepository {

    Personnel save(Personnel personnel);

    Optional<Personnel> findById(Long id);

    List<Personnel> findAll(PersonnelListCriteria criteria);

    PagedResult<Personnel> findAllPageable(PersonnelListCriteria criteria, int pageNumber, int pageSize);

    List<Personnel> findByEmployeeNumber(String employeeNumber);

    boolean existsByEmployeeNumber(String employeeNumber, Long excludeId);

    void deleteById(Long id);

    // Certificate-level operations
    Personnel addCertificate(Long personnelId, Certificate certificate);

    Personnel removeCertificate(Long personnelId, Long certificateId);

    List<Certificate> findExpiringCertificates(int warningDays);

    long count();
}
