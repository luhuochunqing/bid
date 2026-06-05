package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteQualificationAppService {

    private final BusinessQualificationRepository repository;

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("BusinessQualification", String.valueOf(id));
        }
        repository.deleteById(id);
    }
}
