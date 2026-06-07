package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.bidresult.service.BidResultCurrentUserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BidResultCurrentUserResolver {

    private final BidResultCurrentUserLookupService currentUserLookupService;

    User resolve(UserDetails userDetails) {
        return currentUserLookupService.requireUser(userDetails);
    }
}
