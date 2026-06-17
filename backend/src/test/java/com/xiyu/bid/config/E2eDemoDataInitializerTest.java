package com.xiyu.bid.config;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.entity.TaskStatusDict;
import com.xiyu.bid.task.repository.TaskStatusDictRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class E2eDemoDataInitializerTest {

    @Test
    void seedDemoUsers_ShouldCreateExpectedUsersWithEncodedPasswords() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RoleProfileRepository roleProfileRepository = mock(RoleProfileRepository.class);
        TaskStatusDictRepository taskStatusDictRepository = mock(TaskStatusDictRepository.class);
        E2eDemoDataInitializer initializer = new E2eDemoDataInitializer(
                userRepository, roleProfileRepository, taskStatusDictRepository, passwordEncoder);

        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase(anyString()))
                .thenReturn(Optional.of(RoleProfile.builder().id(1L).code("TEST").build()));
        when(passwordEncoder.encode("123456")).thenReturn("encoded-123456");

        initializer.seedDemoUsers();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(7)).save(userCaptor.capture());
        verify(passwordEncoder, times(7)).encode("123456");

        List<User> savedUsers = userCaptor.getAllValues();
        assertThat(savedUsers)
                .extracting(User::getUsername)
                .containsExactly("lizong", "xiaowang", "xiaochen", "xiaoliu", "xiaozhang", "xiaozhou", "xiaozheng");
        assertThat(savedUsers)
                .extracting(User::getPassword)
                .containsOnly("encoded-123456");
        assertThat(savedUsers)
                .extracting(User::getEnabled)
                .containsOnly(true);
    }

    @Test
    void seedTaskStatuses_ShouldCreateDefaultKanbanColumns() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RoleProfileRepository roleProfileRepository = mock(RoleProfileRepository.class);
        TaskStatusDictRepository taskStatusDictRepository = mock(TaskStatusDictRepository.class);
        E2eDemoDataInitializer initializer = new E2eDemoDataInitializer(
                userRepository, roleProfileRepository, taskStatusDictRepository, passwordEncoder);

        when(taskStatusDictRepository.findById(anyString())).thenReturn(Optional.empty());

        initializer.seedTaskStatuses();

        ArgumentCaptor<TaskStatusDict> statusCaptor = ArgumentCaptor.forClass(TaskStatusDict.class);
        verify(taskStatusDictRepository, times(4)).save(statusCaptor.capture());
        assertThat(statusCaptor.getAllValues())
                .extracting(TaskStatusDict::getCode)
                .containsExactly("TODO", "IN_PROGRESS", "REVIEW", "COMPLETED");
        assertThat(statusCaptor.getAllValues())
                .extracting(TaskStatusDict::getEnabled)
                .containsOnly(true);
    }
}
