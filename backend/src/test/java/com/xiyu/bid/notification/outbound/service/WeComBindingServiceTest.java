package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComBindingService — admin bind/unbind orchestration")
class WeComBindingServiceTest {

    @Mock private UserRepository userRepository;

    private WeComBindingService service;

    @BeforeEach
    void setUp() {
        service = new WeComBindingService(userRepository);
    }

    private static User user(Long id, String wecomUserId) {
        return User.builder().id(id).username("u" + id).email("u" + id + "@x.com")
            .password("p").fullName("User " + id).role(User.Role.STAFF)
            .wecomUserId(wecomUserId).build();
    }

    @Test
    @DisplayName("bind sets wecomUserId and saves user")
    void bind_SavesUser() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, null)));

        service.bind(7L, "wc_007");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("bind missing user throws NoSuchElementException")
    void bind_MissingUser_Throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(99L, "wc_new"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("bind rewraps DataIntegrityViolationException as IllegalArgumentException")
    void bind_DuplicateWecomId_RewrapsError() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, null)));
        when(userRepository.save(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.bind(7L, "wc_dup"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已绑定其他用户");
    }

    @Test
    @DisplayName("unbind clears wecomUserId and saves user")
    void unbind_SavesUser() {
        User existing = user(7L, "wc_007");
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.unbind(7L);

        assertThat(existing.getWecomUserId()).isNull();
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("unbind missing user throws")
    void unbind_MissingUser_Throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unbind(99L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("currentBinding returns stored wecomUserId")
    void currentBinding_ReturnsValue() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "wc_007")));

        assertThat(service.currentBinding(7L)).isEqualTo("wc_007");
    }

    @Test
    @DisplayName("currentBinding returns null when never bound")
    void currentBinding_ReturnsNullWhenUnbound() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, null)));

        assertThat(service.currentBinding(7L)).isNull();
    }
}
