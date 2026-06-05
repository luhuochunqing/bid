package com.xiyu.bid.brandauth.application.service;

import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteBrandAuthAppService {

    private final BrandAuthorizationRepository repository;

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("BrandAuthorization", String.valueOf(id));
        }
        repository.deleteById(id);
    }
}
