package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BidResultCurrentUserResolver {

    private final AuthService authService;

    User resolve(UserDetails userDetails) {
        try {
            return authService.resolveUserByUsername(userDetails.getUsername());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new IllegalStateException("Authenticated user not found", ex);
        }
    }
}

