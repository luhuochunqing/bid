package com.xiyu.bid.bootstrap;

import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the fail-fast password validation in
 * {@link DefaultAdminInitializer#validateAdminPasswordOnStartup()}.
 *
 * <p>The validation must always fire at startup ({@code @PostConstruct}),
 * regardless of whether the seed path is taken. This guards against an empty
 * {@code ADMIN_PASSWORD} env var silently sliding past on a pre-seeded DB.
 */
class DefaultAdminInitializerTest {

    private DefaultAdminInitializer build(String password) {
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(
                mock(UserRepository.class),
                mock(RoleProfileRepository.class),
                mock(PasswordEncoder.class)
        );
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@xiyu-local");
        ReflectionTestUtils.setField(initializer, "adminFullName", "系统管理员");
        return initializer;
    }

    @Test
    void emptyPasswordThrowsAtStartup() {
        DefaultAdminInitializer initializer = build("");
        assertThatThrownBy(initializer::validateAdminPasswordOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least " + DefaultAdminInitializer.MIN_ADMIN_PASSWORD_LENGTH);
    }

    @Test
    void nullPasswordThrowsAtStartup() {
        DefaultAdminInitializer initializer = build(null);
        assertThatThrownBy(initializer::validateAdminPasswordOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void elevenCharPasswordThrowsAtStartup() {
        DefaultAdminInitializer initializer = build("12345678901"); // 11 chars
        assertThatThrownBy(initializer::validateAdminPasswordOnStartup)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void twelveCharPasswordPassesValidation() {
        DefaultAdminInitializer initializer = build("XiyuAdmin202!"); // 13 chars, well-formed
        assertThatCode(initializer::validateAdminPasswordOnStartup).doesNotThrowAnyException();
    }

    @Test
    void minimumLengthBoundaryIsTwelve() {
        // Exactly 12 chars must pass (boundary).
        DefaultAdminInitializer initializer = build("123456789012");
        assertThatCode(initializer::validateAdminPasswordOnStartup).doesNotThrowAnyException();
    }

    @Test
    void minimumLengthIsExposedAsTwelve() {
        // Sanity: the public-ish constant is 12 — codifies the contract for callers/tests.
        assertThat(DefaultAdminInitializer.MIN_ADMIN_PASSWORD_LENGTH).isEqualTo(12);
    }
}
