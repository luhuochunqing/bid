package com.xiyu.bid.testsupport.integration;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;

public final class IntegrationUserSupport {

    private final UserRepository userRepository;

    public IntegrationUserSupport(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createAdminUser() {
        return createAdminUser("bid-admin", "bid-admin@example.com", "闭环管理员");
    }

    public User createAdminUser(String username, String email, String fullName) {
        return userRepository.save(User.builder()
                .username(username)
                .password("XiyuDemo!2026")
                .email(email)
                .fullName(fullName)
                .role(User.Role.ADMIN)
                .enabled(true)
                .build());
    }
}
