// Input: PlatformAccountRepository, PasswordEncryptionUtil mocks
// Output: PlatformAccountService unit tests — CRUD, borrow/return, statistics, password view
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.dto.ReturnAccountRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAccountServiceTest {

    @Mock
    private PlatformAccountRepository repository;

    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    private PlatformAccountService service;

    private static final String ENCRYPTED_PWD = "encrypted:secret123";
    private static final User ADMIN_USER = User.builder().id(1L).role(User.Role.ADMIN).build();
    private static final User STAFF_USER = User.builder().id(2L).role(User.Role.STAFF).build();

    @BeforeEach
    void setUp() {
        service = new PlatformAccountService(repository, passwordEncryptionUtil);
    }

    // ── 创建 ──

    @Test
    @DisplayName("创建平台账号成功 — 密码加密、字段正确")
    void createAccount_success() {
        PlatformAccountCreateRequest req = validRequest();
        PlatformAccount saved = accountWithId(1L);
        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAccountName()).isEqualTo("测试平台");
        assertThat(result.getPlatformType()).isEqualTo(PlatformType.GOV_PROCUREMENT);
        verify(passwordEncryptionUtil).encrypt("secret123");
    }

    @Test
    @DisplayName("创建时用户名已存在抛出异常")
    void createAccount_duplicateUsername_throws() {
        when(repository.findByUsername("testuser")).thenReturn(Optional.of(new PlatformAccount()));

        assertThatThrownBy(() -> service.createAccount(validRequest(), ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("创建时用户名为空抛出异常")
    void createAccount_nullUsername_throws() {
        PlatformAccountCreateRequest req = validRequest();
        req.setUsername(null);
        assertThatThrownBy(() -> service.createAccount(req, ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null");
    }

    @Test
    @DisplayName("创建时密码为空抛出异常")
    void createAccount_nullPassword_throws() {
        PlatformAccountCreateRequest req = validRequest();
        req.setPassword(null);
        assertThatThrownBy(() -> service.createAccount(req, ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password cannot be null");
    }

    @Test
    @DisplayName("创建时平台名称为空抛出异常")
    void createAccount_nullAccountName_throws() {
        PlatformAccountCreateRequest req = validRequest();
        req.setAccountName(null);
        assertThatThrownBy(() -> service.createAccount(req, ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account name cannot be null");
    }

    // ── 查询 ──

    @Test
    @DisplayName("根据 ID 查询成功")
    void getAccountById_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(accountWithId(1L)));
        PlatformAccountDTO result = service.getAccountById(1L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("根据 ID 查询不存在时抛出异常")
    void getAccountById_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAccountById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("查询所有账号")
    void getAllAccounts_success() {
        when(repository.findAll()).thenReturn(List.of(accountWithId(1L), accountWithId(2L)));
        List<PlatformAccountDTO> result = service.getAllAccounts();
        assertThat(result).hasSize(2);
    }

    // ── 更新 ──

    @Test
    @DisplayName("更新账号成功 — 部分字段更新")
    void updateAccount_success() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest req = validRequest();
        req.setAccountName("更新后的平台");
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        PlatformAccountDTO result = service.updateAccount(1L, req, ADMIN_USER);
        assertThat(result.getId()).isEqualTo(1L);
        verify(repository).save(any());
    }

    // ── 删除 ──

    @Test
    @DisplayName("删除账号成功")
    void deleteAccount_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(accountWithId(1L)));
        service.deleteAccount(1L, ADMIN_USER);
        verify(repository).delete(any());
    }

    @Test
    @DisplayName("删除不存在的账号抛出异常")
    void deleteAccount_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteAccount(99L, ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 借用/归还 ──

    @Test
    @DisplayName("借用账号成功")
    void borrowAccount_success() {
        PlatformAccount account = accountWithId(1L);
        BorrowAccountRequest req = BorrowAccountRequest.builder().borrowedBy(10L).dueHours(72).build();
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(repository.save(any())).thenReturn(account);

        PlatformAccountDTO result = service.borrowAccount(1L, req, ADMIN_USER);
        assertThat(result.getBorrowedBy()).isEqualTo(10L);
    }

    @Test
    @DisplayName("归还账号成功（带密码变更）")
    void returnAccount_withPassword_success() {
        PlatformAccount account = accountWithId(1L);
        ReturnAccountRequest req = ReturnAccountRequest.builder().newPassword("newSecret").build();
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.encrypt("newSecret")).thenReturn("encrypted:newSecret");
        when(repository.save(any())).thenReturn(account);

        PlatformAccountDTO result = service.returnAccount(1L, req, ADMIN_USER);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.AVAILABLE);
    }

    // ── 统计 ──

    @Test
    @DisplayName("统计信息正确")
    void getStatistics_success() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByStatus(AccountStatus.AVAILABLE)).thenReturn(5L);
        when(repository.countByStatus(AccountStatus.IN_USE)).thenReturn(3L);
        when(repository.countByStatus(AccountStatus.MAINTENANCE)).thenReturn(1L);
        when(repository.countByStatus(AccountStatus.DISABLED)).thenReturn(1L);

        PlatformAccountStatisticsDTO stats = service.getStatistics();
        assertThat(stats.getTotalAccounts()).isEqualTo(10L);
        assertThat(stats.getAvailableAccounts()).isEqualTo(5L);
        assertThat(stats.getInUseAccounts()).isEqualTo(3L);
        assertThat(stats.getMaintenanceAccounts()).isEqualTo(1L);
        assertThat(stats.getDisabledAccounts()).isEqualTo(1L);
    }

    // ── 密码查看 ──

    @Test
    @DisplayName("ADMIN 可以查看密码")
    void getPassword_admin_success() {
        PlatformAccount account = accountWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.decrypt(ENCRYPTED_PWD)).thenReturn("secret123");

        String pwd = service.getPassword(1L, ADMIN_USER);
        assertThat(pwd).isEqualTo("secret123");
    }

    @Test
    @DisplayName("非 ADMIN 查看密码抛出异常")
    void getPassword_nonAdmin_throws() {
        assertThatThrownBy(() -> service.getPassword(1L, STAFF_USER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only administrators");
    }

    // ── 到期查询 ──

    @Test
    @DisplayName("查询逾期账号")
    void findOverdueAccounts_success() {
        when(repository.findOverdueAccounts(eq(AccountStatus.IN_USE), any(LocalDateTime.class)))
                .thenReturn(List.of(accountWithId(1L)));
        List<PlatformAccountDTO> result = service.findOverdueAccounts();
        assertThat(result).hasSize(1);
    }

    // ── helpers ──

    private static PlatformAccountCreateRequest validRequest() {
        return PlatformAccountCreateRequest.builder()
                .username("testuser")
                .password("secret123")
                .accountName("测试平台")
                .platformType(PlatformType.GOV_PROCUREMENT)
                .build();
    }

    private static PlatformAccount accountWithId(Long id) {
        return PlatformAccount.builder()
                .id(id)
                .username("testuser")
                .password(ENCRYPTED_PWD)
                .accountName("测试平台")
                .platformType(PlatformType.GOV_PROCUREMENT)
                .status(AccountStatus.AVAILABLE)
                .build();
    }
}
