package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidResultCurrentUserLookupService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new AuthenticationServiceException("UserDetails cannot be null");
        }
        String username = userDetails.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationServiceException("Username cannot be null or empty");
        }
        return userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationServiceException("Authenticated user not found: " + username));
    }
}
