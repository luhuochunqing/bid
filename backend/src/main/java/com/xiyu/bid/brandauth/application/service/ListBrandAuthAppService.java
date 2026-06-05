package com.xiyu.bid.brandauth.application.service;

import com.xiyu.bid.brandauth.application.dto.BrandAuthorizationDTO;
import com.xiyu.bid.brandauth.application.mapper.BrandAuthMapper;
import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListBrandAuthAppService {

    private final BrandAuthorizationRepository repository;
    private final BrandAuthMapper mapper;

    @Transactional(readOnly = true)
    public List<BrandAuthorizationDTO> list() {
        return repository.findAll().stream().map(mapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public BrandAuthorizationDTO get(Long id) {
        BrandAuthorization a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BrandAuthorization", String.valueOf(id)));
        return mapper.toDTO(a);
    }

    @Transactional(readOnly = true)
    public List<BrandAuthorizationDTO> byBrand(String brandName) {
        return repository.findByBrandName(brandName).stream().map(mapper::toDTO).toList();
    }
}
