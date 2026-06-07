package com.xiyu.bid.brandauth.domain.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AuthorizationExpiryPolicy {

    public List<BrandAuthorization> findExpiringSoon(List<BrandAuthorization> auths, int warningDays) {
        return auths.stream()
                .filter(a -> a.isExpiringSoon(warningDays))
                .toList();
    }

    public AuthorizationStatus evaluate(BrandAuthorization auth) {
        return auth.computeRealtimeStatus();
    }
}
