package com.xiyu.bid.bootstrap;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalDevAccountInitializerTest {

    @Test
    void seedLocalAccountsShouldCreateStaffAndManagerLoginUsers() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleProfileRepository roleProfileRepository = mock(RoleProfileRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LocalDevAccountInitializer initializer =
                new LocalDevAccountInitializer(userRepository, roleProfileRepository, passwordEncoder);

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleProfileRepository.findByCodeIgnoreCase("staff"))
                .thenReturn(Optional.of(RoleProfile.builder().id(3L).code("staff").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("manager"))
                .thenReturn(Optional.of(RoleProfile.builder().id(2L).code("manager").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("auditor"))
                .thenReturn(Optional.of(RoleProfile.builder().id(4L).code("auditor").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("bid_admin"))
                .thenReturn(Optional.of(RoleProfile.builder().id(5L).code("bid_admin").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("bid_lead"))
                .thenReturn(Optional.of(RoleProfile.builder().id(6L).code("bid_lead").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("sales"))
                .thenReturn(Optional.of(RoleProfile.builder().id(7L).code("sales").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("task_executor"))
                .thenReturn(Optional.of(RoleProfile.builder().id(8L).code("task_executor").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("bid_specialist"))
                .thenReturn(Optional.of(RoleProfile.builder().id(9L).code("bid_specialist").build()));
        when(roleProfileRepository.findByCodeIgnoreCase("admin_staff"))
                .thenReturn(Optional.of(RoleProfile.builder().id(10L).code("admin_staff").build()));
        when(passwordEncoder.encode(LocalDevAccountInitializer.LOCAL_TEST_PASSWORD)).thenReturn("encoded-test-password");

        initializer.seedLocalAccounts();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(9)).save(userCaptor.capture());
        verify(passwordEncoder, times(9)).encode(LocalDevAccountInitializer.LOCAL_TEST_PASSWORD);

        List<User> savedUsers = userCaptor.getAllValues();
        assertThat(savedUsers)
                .extracting(User::getUsername)
                .containsExactly("staff", "manager", "auditor", "bid_admin", "bid_lead", "sales", "task_executor", "bid_specialist", "admin_staff");
        assertThat(savedUsers)
                .extracting(User::getFullName)
                .containsExactly("小王", "张经理", "赵审计", "陈投标管理", "刘投标组长", "张销售", "吴执行", "周投标专员", "郑行政");
        assertThat(savedUsers)
                .extracting(User::getRole)
                .containsExactly(User.Role.STAFF, User.Role.MANAGER, User.Role.STAFF, User.Role.MANAGER, User.Role.MANAGER, User.Role.MANAGER, User.Role.STAFF, User.Role.STAFF, User.Role.STAFF);
        assertThat(savedUsers)
                .extracting(User::getEnabled)
                .containsOnly(true);
        assertThat(savedUsers)
                .extracting(User::getPassword)
                .containsOnly("encoded-test-password");
    }
}
