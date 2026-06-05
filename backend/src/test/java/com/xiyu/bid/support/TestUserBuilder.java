package com.xiyu.bid.support;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class TestUserBuilder {

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private RoleProfile defaultProfile;

    public TestUserBuilder(UserRepository userRepository, RoleProfileRepository roleProfileRepository) {
        this.userRepository = userRepository;
        this.roleProfileRepository = roleProfileRepository;
    }

    public RoleProfile getOrCreateDefaultProfile() {
        if (defaultProfile == null) {
            defaultProfile = roleProfileRepository.findByCodeIgnoreCase("TEST_DEFAULT")
                    .orElseGet(() -> roleProfileRepository.save(RoleProfile.builder()
                            .code("TEST_DEFAULT")
                            .name("测试默认权限")
                            .dataScope("self")
                            .build()));
        }
        return defaultProfile;
    }

    public User createUser(String username, User.Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .password("password123")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .roleProfile(getOrCreateDefaultProfile())
                .enabled(true)
                .build());
    }

    public User.UserBuilder createRawBuilder(String username, User.Role role) {
        return User.builder()
                .username(username)
                .password("password123")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .roleProfile(getOrCreateDefaultProfile())
                .enabled(true);
    }
}
