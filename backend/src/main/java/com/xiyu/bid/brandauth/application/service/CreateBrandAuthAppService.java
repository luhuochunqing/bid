package com.xiyu.bid.brandauth.application.service;

import com.xiyu.bid.brandauth.application.command.BrandAuthUpsertCommand;
import com.xiyu.bid.brandauth.application.dto.BrandAuthorizationDTO;
import com.xiyu.bid.brandauth.application.mapper.BrandAuthMapper;
import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateBrandAuthAppService {

    private final BrandAuthorizationRepository repository;
    private final BrandAuthMapper mapper;

    @Transactional
    public BrandAuthorizationDTO create(BrandAuthUpsertCommand command) {
        BrandAuthorization auth = BrandAuthorization.create(
                null, command.brandName(), command.supplierName(),
                command.scope(), command.scopeDetail(),
                command.startDate(), command.endDate(),
                command.authorizationDocUrl(), command.remarks()
        );
        return mapper.toDTO(repository.save(auth));
    }
}
