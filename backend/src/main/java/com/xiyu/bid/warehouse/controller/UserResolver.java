package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 统一的当前用户解析器，供 warehouse 模块各 Controller 复用。
 */
@Component
public class UserResolver {

    private final UserRepository userRepository;

    public UserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    public Long resolveCurrentUserId() {
        User user = resolveCurrentUser();
        return user != null ? user.getId() : null;
    }
}
