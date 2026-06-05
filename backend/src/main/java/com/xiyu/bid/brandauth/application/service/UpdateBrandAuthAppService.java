package com.xiyu.bid.brandauth.application.service;

import com.xiyu.bid.brandauth.application.command.BrandAuthUpsertCommand;
import com.xiyu.bid.brandauth.application.dto.BrandAuthorizationDTO;
import com.xiyu.bid.brandauth.application.mapper.BrandAuthMapper;
import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateBrandAuthAppService {

    private final BrandAuthorizationRepository repository;
    private final BrandAuthMapper mapper;

    @Transactional
    public BrandAuthorizationDTO update(Long id, BrandAuthUpsertCommand command) {
        BrandAuthorization existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BrandAuthorization", String.valueOf(id)));

        BrandAuthorization updated = BrandAuthorization.create(
                existing.id(), command.brandName(), command.supplierName(),
                command.scope(), command.scopeDetail(),
                command.startDate(), command.endDate(),
                command.authorizationDocUrl(), command.remarks()
        );
        return mapper.toDTO(repository.save(updated));
    }
}
