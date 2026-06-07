package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListQualificationsAppService {

    private final BusinessQualificationRepository repository;

    @Transactional(readOnly = true)
    public List<BusinessQualification> list(QualificationListCriteria criteria) {
        return repository.findAll(criteria);
    }

    @Transactional(readOnly = true)
    public BusinessQualification get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(id)));
    }
}
