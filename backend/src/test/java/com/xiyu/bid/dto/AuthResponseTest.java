package com.xiyu.bid.dto;

import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * IJTHPA 修复后，{@link AuthResponse} 必须透出当前用户的手机号，
 * 前端用它在新增平台账号表单里自动带入「绑定手机」字段。
 */
@DisplayName("AuthResponse includes phone for frontend autofill")
class AuthResponseTest {

    @Test
    @DisplayName("from() copies user.phone into the response payload")
    void from_copiesPhone() {
        User user = User.builder()
                .id(42L)
                .username("alice")
                .email("alice@example.com")
                .phone("17712345678")
                .fullName("Alice")
                .role(User.Role.ADMIN)
                .build();

        AuthResponse response = AuthResponse.from("token-abc", user, List.of(1L, 2L), List.of("BID"), List.of("all"));

        assertNotNull(response);
        assertEquals("17712345678", response.getPhone());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("Alice", response.getFullName());
        assertEquals("token-abc", response.getToken());
    }

    @Test
    @DisplayName("from() tolerates a user with no phone (older accounts)")
    void from_toleratesMissingPhone() {
        User user = User.builder()
                .id(43L)
                .username("bob")
                .email("bob@example.com")
                .role(User.Role.STAFF)
                .build();

        AuthResponse response = AuthResponse.from("token", user);

        assertNull(response.getPhone());
        assertEquals("bob@example.com", response.getEmail());
    }
}
