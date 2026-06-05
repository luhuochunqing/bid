package com.xiyu.bid.brandauth.application.mapper;

import com.xiyu.bid.brandauth.application.dto.BrandAuthorizationDTO;
import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import org.springframework.stereotype.Component;

@Component
public class BrandAuthMapper {

    public BrandAuthorizationDTO toDTO(BrandAuthorization a) {
        if (a == null) return null;
        return new BrandAuthorizationDTO(
                a.id(), a.brandName(), a.supplierName(),
                a.scope(), a.scopeDetail(), a.startDate(), a.endDate(),
                a.status(), a.authorizationDocUrl(), a.remarks(),
                a.createdAt(), a.updatedAt()
        );
    }
}
