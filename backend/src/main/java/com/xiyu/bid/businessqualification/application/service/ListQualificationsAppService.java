package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    /**
     * CO-155 fix: paginated query entry. Repository filters and counts at SQL
     * layer and returns a domain QualificationPage which is then mapped to
     * Spring's Page for the controller layer.
     */
    @Transactional(readOnly = true)
    public Page<BusinessQualification> list(QualificationListCriteria criteria, int page, int size) {
        var domainPage = repository.findAll(criteria, page, size);
        // Map domain page back to Spring Page (controller layer convenience)
        return new org.springframework.data.domain.PageImpl<>(
                domainPage.content(),
                org.springframework.data.domain.PageRequest.of(page, size),
                domainPage.totalElements()
        );
    }

    @Transactional(readOnly = true)
    public BusinessQualification get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(id)));
    }
}
