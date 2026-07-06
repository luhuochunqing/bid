// Input: PlatformAccountRepository, PasswordEncryptionUtil mocks
// Output: PlatformAccountService unit tests — CRUD, borrow/return, statistics, password view
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.dto.PlatformAccountSummaryDTO;
import com.xiyu.bid.platform.dto.ReturnAccountRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.audit.PlatformAccountAuditRecorder;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAccountServiceTest {

    @Mock
    private PlatformAccountRepository repository;

    @Mock
    private PlatformAccountBorrowService borrowService;

    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountCreationWhitelistStore whitelistStore;

    @Mock
    private PlatformAccountAuditRecorder auditRecorder;

    @Mock
    private AccountBorrowApplicationRepository borrowApplicationRepository;

    private PlatformAccountService service;

    private static final String ENCRYPTED_PWD = "encrypted:secret123";
    private static final User ADMIN_USER = User.builder().id(1L).role(User.Role.ADMIN).build();
    private static final User STAFF_USER = User.builder().id(2L).role(User.Role.MANAGER).build();
    private static final User BID_ADMIN_USER = User.builder().id(3L).role(User.Role.MANAGER).build();
    private static final User BID_LEADER_USER = User.builder().id(4L).role(User.Role.MANAGER).build();
    private static final User BID_TEAM_USER = User.builder().id(5L).fullName("投标专员").employeeNumber("E005").role(User.Role.MANAGER).build();
    private static final User WHITELISTED_BID_TEAM_USER = User.builder().id(6L).username("00444").role(User.Role.MANAGER).build();

    @BeforeEach
    void setUp() {
        // CO-390: service 不再直接依赖 UserRepository，改委托给 PlatformAccountContactLabelEnricher。
        // 测试中用真实 enricher + mock UserRepository，覆盖 service → enricher 协作链路。
        // CO-403: 改为委托 BorrowService 同步申请表状态，不再直接操作 AccountBorrowApplicationRepository
        // CO-522: updateApplier 用真实实例（字段应用是纯逻辑，需要真正修改 account 对象），
        //         auditRecorder 用 mock（只验证调用，不验证 diff 本身——后者在 AuditRecorderTest 里覆盖）
        PlatformAccountUpdateApplier realApplier = new PlatformAccountUpdateApplier(repository, passwordEncryptionUtil);
        service = new PlatformAccountService(
                repository, borrowService, passwordEncryptionUtil, effectiveRoleResolver,
                new PlatformAccountContactLabelEnricher(userRepository), whitelistStore,
                auditRecorder, borrowApplicationRepository, realApplier);
        // CO-522: recorder 默认返回入参快照与 0，避免影响编辑断言；具体测试按需覆盖
        lenient().when(auditRecorder.snapshot(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditRecorder.resolvePendingApprovalCount(any(), any(), any())).thenReturn(0);
        // CO-373：默认模拟 LOCAL_USER 解析路径——回退到实体 roleCode
        lenient().when(effectiveRoleResolver.resolveRoleCode(any(User.class)))
                .thenAnswer(inv -> inv.<User>getArgument(0).getRoleCode());
        // CO-389：模拟 OSS 用户角色码解析（覆盖 getPassword 放开后的角色判断）
        lenient().when(effectiveRoleResolver.resolveRoleCode(ADMIN_USER)).thenReturn("admin");
        lenient().when(effectiveRoleResolver.resolveRoleCode(BID_ADMIN_USER)).thenReturn("/bidAdmin");
        lenient().when(effectiveRoleResolver.resolveRoleCode(BID_LEADER_USER)).thenReturn("bid-TeamLeader");
        lenient().when(effectiveRoleResolver.resolveRoleCode(BID_TEAM_USER)).thenReturn("bid-Team");
        lenient().when(effectiveRoleResolver.resolveRoleCode(WHITELISTED_BID_TEAM_USER)).thenReturn("bid-Team");
        lenient().when(effectiveRoleResolver.resolveRoleCode(STAFF_USER)).thenReturn("manager");
        // CO-390：默认模拟 UserRepository.findAllById 返回空（contactPerson 无 userId 时不会调用）
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
        // 默认白名单校验放行，具体测试按需覆盖
        lenient().doNothing().when(whitelistStore).checkCreatePermission(any(), any());
    }

    // ── 创建 ──

    @Test
    @DisplayName("创建平台账号成功 — 密码加密、字段正确")
    void createAccount_success() {
        PlatformAccountCreateRequest req = validRequest();
        PlatformAccount saved = accountWithId(1L);
        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenReturn(saved);

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAccountName()).isEqualTo("测试平台");
        verify(passwordEncryptionUtil).encrypt("secret123");
    }

    @Test
    @DisplayName("创建平台账号成功 — contactPerson 接收 Long userId（CO-390 升级）")
    void createAccount_withContactPersonUserId_shouldSucceed() throws Exception {
        PlatformAccountCreateRequest req = validRequest();
        req.setContactPerson(99L);
        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getContactPerson()).isEqualTo(99L);
        // CO-390: custodian / caCustodian / custodianName 字段已物理删除，DTO 不应再声明这些字段
        assertThat(PlatformAccountDTO.class.getDeclaredField("contactPerson").getType())
                .isEqualTo(Long.class);
        assertThat(fieldExists(PlatformAccountDTO.class, "custodian")).isFalse();
        assertThat(fieldExists(PlatformAccountDTO.class, "caCustodian")).isFalse();
        assertThat(fieldExists(PlatformAccountDTO.class, "custodianName")).isFalse();
    }

    private static boolean fieldExists(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Test
    @DisplayName("创建时用户名重复允许创建（已取消平台类型维度唯一校验）")
    void createAccount_duplicateUsernameAllowed_succeeds() {
        PlatformAccountCreateRequest req = validRequest();
        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getUsername()).isEqualTo("testuser");
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

    // ── 创建权限 ──

    @Test
    @DisplayName("白名单内投标专员可创建平台账户")
    void createAccount_whitelistedBidTeamUser_succeeds() {
        // checkCreatePermission 默认放行（doNothing），无需额外 stub
        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenReturn(accountWithId(1L));

        PlatformAccountDTO result = service.createAccount(validRequest(), WHITELISTED_BID_TEAM_USER);

        assertThat(result.getId()).isEqualTo(1L);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("白名单外投标专员不可创建平台账户（403）")
    void createAccount_nonWhitelistedBidTeamUser_denied() {
        doThrow(new org.springframework.security.access.AccessDeniedException("仅管理员或白名单用户可创建平台账户"))
                .when(whitelistStore).checkCreatePermission("bid-Team", BID_TEAM_USER);

        assertThatThrownBy(() -> service.createAccount(validRequest(), BID_TEAM_USER))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("白名单用户");
        verify(repository, never()).save(any());
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

    @Test
    @DisplayName("CO-388：投标专员作为绑定联系人时可查看完整账号信息")
    void getAccountsForViewer_bidTeamContactPerson_receivesFullDto() {
        PlatformAccount ownAccount = accountWithId(1L);
        ownAccount.setContactPerson(BID_TEAM_USER.getId());
        ownAccount.setStatus(AccountStatus.IN_USE);
        PlatformAccount otherAccount = accountWithId(2L);
        otherAccount.setContactPerson(99L);
        when(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(ownAccount, otherAccount));

        List<?> result = service.getAccountsForViewer(BID_TEAM_USER);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(PlatformAccountDTO.class);
        assertThat(result.get(1)).isInstanceOf(PlatformAccountSummaryDTO.class);
        PlatformAccountDTO own = (PlatformAccountDTO) result.get(0);
        assertThat(own.getUsername()).isEqualTo("testuser");
        assertThat(own.getContactPerson()).isEqualTo(BID_TEAM_USER.getId());
        assertThat(own.getStatus()).isEqualTo(AccountStatus.IN_USE);
    }

    @Test
    @DisplayName("CO-388：投标专员非绑定联系人时收到脱敏摘要")
    void getAccountsForViewer_bidTeamNotContactPerson_receivesSummaryDto() {
        PlatformAccount account = accountWithId(1L);
        account.setContactPerson(99L);
        account.setStatus(AccountStatus.AVAILABLE);
        when(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(account));

        List<?> result = service.getAccountsForViewer(BID_TEAM_USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(PlatformAccountSummaryDTO.class);
        PlatformAccountSummaryDTO summary = (PlatformAccountSummaryDTO) result.get(0);
        assertThat(summary.getStatus()).isEqualTo(AccountStatus.AVAILABLE);
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

    @Test
    @DisplayName("更新账号成功 — contactPerson userId 可更新（CO-390）")
    void updateAccount_withContactPersonUserId_shouldUpdate() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest req = validRequest();
        req.setContactPerson(88L);
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.updateAccount(1L, req, ADMIN_USER);
        assertThat(result.getContactPerson()).isEqualTo(88L);
    }

    @Test
    @DisplayName("CO-416: 投标专员作为绑定联系人可编辑账户")
    void updateAccount_bidTeamAsContactPerson_shouldSucceed() {
        PlatformAccount existing = accountWithId(1L);
        existing.setContactPerson(BID_TEAM_USER.getId());
        PlatformAccountCreateRequest req = validRequest();
        req.setAccountName("投标专员更新的平台");
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.updateAccount(1L, req, BID_TEAM_USER);
        assertThat(result.getAccountName()).isEqualTo("投标专员更新的平台");
    }

    @Test
    @DisplayName("CO-416: 投标专员非绑定联系人被拒绝（403）")
    void updateAccount_bidTeamNotContactPerson_shouldBeDenied() {
        PlatformAccount existing = accountWithId(1L);
        existing.setContactPerson(999L); // 不是当前用户
        PlatformAccountCreateRequest req = validRequest();
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateAccount(1L, req, BID_TEAM_USER))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("仅管理员或账户绑定联系人可编辑账户");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("CO-416: 投标管理员可编辑任意账户（特权角色放行）")
    void updateAccount_bidAdmin_shouldSucceed() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest req = validRequest();
        req.setAccountName("管理员更新的平台");
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.updateAccount(1L, req, BID_ADMIN_USER);
        assertThat(result.getAccountName()).isEqualTo("管理员更新的平台");
    }


    @Test
    @DisplayName("CO-390: getAllAccounts 批量填充 contactPersonLabel 为 姓名(工号) 格式")
    void getAllAccounts_shouldEnrichContactPersonLabel() {
        PlatformAccount a1 = accountWithId(1L);
        a1.setContactPerson(99L);
        PlatformAccount a2 = accountWithId(2L);
        a2.setContactPerson(100L);
        when(repository.findAll()).thenReturn(List.of(a1, a2));
        User u99 = User.builder().id(99L).fullName("张三").employeeNumber("0088").build();
        User u100 = User.builder().id(100L).fullName("李四").employeeNumber("0099").build();
        when(userRepository.findAllById(any())).thenReturn(List.of(u99, u100));

        List<PlatformAccountDTO> result = service.getAllAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContactPersonLabel()).isEqualTo("张三（0088）");
        assertThat(result.get(1).getContactPersonLabel()).isEqualTo("李四（0099）");
    }

    @Test
    @DisplayName("CO-390: contactPerson 为 null 时 contactPersonLabel 也为 null")
    void getAllAccounts_withNullContactPerson_shouldKeepLabelNull() {
        PlatformAccount a1 = accountWithId(1L); // contactPerson = null
        when(repository.findAll()).thenReturn(List.of(a1));

        List<PlatformAccountDTO> result = service.getAllAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContactPerson()).isNull();
        assertThat(result.get(0).getContactPersonLabel()).isNull();
        // 不应调用 userRepository
        verify(userRepository, never()).findAllById(any());
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
        account.borrow(10L, LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        ReturnAccountRequest req = ReturnAccountRequest.builder().newPassword("newSecret").build();
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.encrypt("newSecret")).thenReturn("encrypted:newSecret");
        when(repository.save(any())).thenReturn(account);

        PlatformAccountDTO result = service.returnAccount(1L, req, ADMIN_USER);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.AVAILABLE);
    }

    // ── CO-415: returnAccount 联系人豁免（对称于 getPassword 范式） ──

    @Test
    @DisplayName("CO-415：bid-Team 作为绑定联系人可归还账号（无密码变更）")
    void returnAccount_bidTeamAsContactPerson_succeeds() {
        PlatformAccount ownAccount = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("我的平台")
                .contactPerson(BID_TEAM_USER.getId())  // 绑定联系人 = bidTeam 用户
                .build();
        ownAccount.borrow(BID_TEAM_USER.getId(), LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        when(repository.findById(1L)).thenReturn(Optional.of(ownAccount));
        when(repository.save(any())).thenReturn(ownAccount);

        PlatformAccountDTO result = service.returnAccount(1L, BID_TEAM_USER);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.AVAILABLE);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("CO-415：bid-Team 非绑定联系人归还账号抛出异常（无密码变更）")
    void returnAccount_bidTeamNotContactPerson_throws() {
        PlatformAccount othersAccount = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("其他平台")
                .contactPerson(999L)  // NOT BID_TEAM_USER.id (5)
                .build();
        othersAccount.borrow(10L, LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        when(repository.findById(1L)).thenReturn(Optional.of(othersAccount));

        assertThatThrownBy(() -> service.returnAccount(1L, BID_TEAM_USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("contact person");
    }

    @Test
    @DisplayName("CO-415：bid-Team 作为绑定联系人可归还账号（带密码变更）")
    void returnAccount_withPassword_bidTeamAsContactPerson_succeeds() {
        PlatformAccount ownAccount = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("我的平台")
                .contactPerson(BID_TEAM_USER.getId())
                .build();
        ownAccount.borrow(BID_TEAM_USER.getId(), LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        ReturnAccountRequest req = ReturnAccountRequest.builder().newPassword("newSecret").build();
        when(repository.findById(1L)).thenReturn(Optional.of(ownAccount));
        when(passwordEncryptionUtil.encrypt("newSecret")).thenReturn("encrypted:newSecret");
        when(repository.save(any())).thenReturn(ownAccount);

        PlatformAccountDTO result = service.returnAccount(1L, req, BID_TEAM_USER);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.AVAILABLE);
    }

    @Test
    @DisplayName("CO-415：bid-Team 非绑定联系人归还账号抛出异常（带密码变更）")
    void returnAccount_withPassword_bidTeamNotContactPerson_throws() {
        PlatformAccount othersAccount = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("其他平台")
                .contactPerson(999L)
                .build();
        othersAccount.borrow(10L, LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        ReturnAccountRequest req = ReturnAccountRequest.builder().newPassword("newSecret").build();
        when(repository.findById(1L)).thenReturn(Optional.of(othersAccount));

        assertThatThrownBy(() -> service.returnAccount(1L, req, BID_TEAM_USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("contact person");
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
    @DisplayName("CO-389：bidAdmin 可以查看密码（service 层 isPrivilegedViewer 放开）")
    void getPassword_whenBidAdmin_callsucceeds() {
        PlatformAccount account = accountWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.decrypt(ENCRYPTED_PWD)).thenReturn("secret123");

        String pwd = service.getPassword(1L, BID_ADMIN_USER);
        assertThat(pwd).isEqualTo("secret123");
    }

    @Test
    @DisplayName("CO-389：bid-TeamLeader 可以查看密码（service 层 isPrivilegedViewer 放开）")
    void getPassword_whenBidTeamLeader_callsucceeds() {
        PlatformAccount account = accountWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.decrypt(ENCRYPTED_PWD)).thenReturn("secret123");

        String pwd = service.getPassword(1L, BID_LEADER_USER);
        assertThat(pwd).isEqualTo("secret123");
    }

    @Test
    @DisplayName("CO-389：bid-Team（投标专员）非绑定联系人查看密码抛出 AccessDeniedException（CO-507 修复）")
    void getPassword_whenBidTeamNotContactPerson_throwsAccessDeniedException() {
        // Mock account whose contactPerson is NOT the bidTeam user.
        PlatformAccount accountNotOwn = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("其他平台")
                .status(AccountStatus.AVAILABLE)
                .contactPerson(999L)  // NOT BID_TEAM_USER.id (5)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(accountNotOwn));

        assertThatThrownBy(() -> service.getPassword(1L, BID_TEAM_USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators or the account's contact person");
    }

    @Test
    @DisplayName("账户不存在时抛 IllegalArgumentException")
    void getPassword_whenAccountNotFound_throwsIllegalArgumentException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPassword(999L, ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found with id: 999");
    }

    @Test
    @DisplayName("CO-400 四轮：bid-Team 作为绑定联系人查看密码成功")
    void getPassword_whenBidTeamAsContactPerson_succeeds() {
        // Mock account whose contactPerson IS the bidTeam user.
        when(passwordEncryptionUtil.decrypt(ENCRYPTED_PWD)).thenReturn("secret123");
        PlatformAccount ownAccount = PlatformAccount.builder()
                .id(1L).username("testuser").password(ENCRYPTED_PWD)
                .accountName("我的平台")
                .status(AccountStatus.AVAILABLE)
                .contactPerson(BID_TEAM_USER.getId())  // 绑定联系人 = bidTeam 用户
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(ownAccount));

        String result = service.getPassword(1L, BID_TEAM_USER);
        assertThat(result).isEqualTo("secret123");
    }

    @Test
    @DisplayName("非特权非 bid-Team 角色查看密码抛出 AccessDeniedException（CO-507 修复，权限下沉 Policy）")
    void getPassword_nonPrivilegedNonBidTeamRole_throwsAccessDeniedException() {
        // 新架构：Service 先 findById 再调 Policy.checkCanViewPassword
        // 非特权非投标专员角色会被 Policy 拒绝，但需要 mock findById（Service 先加载账户）
        PlatformAccount account = accountWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getPassword(1L, STAFF_USER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators or the account's contact person");
    }

    @Test
    @DisplayName("currentUser == null 防御性校验抛出 AccessDeniedException（CO-507 修复，权限下沉 Policy）")
    void getPassword_whenCurrentUserNull_throwsAccessDeniedException() {
        // 新架构：Policy.canViewPassword 第一行 if (currentUser == null) return false
        // Service 先 findById 再调 Policy，所以需要 mock findById
        PlatformAccount account = accountWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getPassword(1L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators or the account's contact person");
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

    // ── CO-447: 列表按 createdAt 倒序 ──

    @Test
    @DisplayName("CO-447: getAccountsForViewer 按 createdAt 倒序返回")
    void getAccountsForViewer_shouldSortByCreatedAtDesc() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 7, 1, 10, 0);
        PlatformAccount a1 = accountWithId(1L); a1.setCreatedAt(t1);
        PlatformAccount a2 = accountWithId(2L); a2.setCreatedAt(t2);
        PlatformAccount a3 = accountWithId(3L); a3.setCreatedAt(t3);
        // mock findAll(Sort) 返回倒序（最新在前）
        when(repository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .thenReturn(List.of(a3, a2, a1));

        List<?> result = service.getAccountsForViewer(ADMIN_USER);

        assertThat(result).hasSize(3);
        PlatformAccountDTO first = (PlatformAccountDTO) result.get(0);
        assertThat(first.getId()).isEqualTo(3L); // 最新创建的排第一
        PlatformAccountDTO last = (PlatformAccountDTO) result.get(2);
        assertThat(last.getId()).isEqualTo(1L);  // 最早创建的排最后
    }

    // ── CO-474: 注册人/注册手机/注册邮箱字段持久化 ──

    @Test
    @DisplayName("CO-474: 创建账号持久化 registrant/registerPhone/registerEmail")
    void CO474_createAccount_persistsRegistrantRegisterPhoneRegisterEmail() {
        PlatformAccountCreateRequest req = validRequest();
        req.setRegistrant("张三");
        req.setRegisterPhone("13900000001");
        req.setRegisterEmail("zhangsan@example.com");

        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getRegistrant()).isEqualTo("张三");
        assertThat(result.getRegisterPhone()).isEqualTo("13900000001");
        assertThat(result.getRegisterEmail()).isEqualTo("zhangsan@example.com");
    }

    @Test
    @DisplayName("CO-474: 新字段未提供时默认为 null")
    void CO474_createAccount_newFieldsDefaultToNullWhenNotProvided() {
        PlatformAccountCreateRequest req = validRequest();
        req.setRegistrant(null);
        req.setRegisterPhone(null);
        req.setRegisterEmail(null);

        when(passwordEncryptionUtil.encrypt("secret123")).thenReturn(ENCRYPTED_PWD);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.createAccount(req, ADMIN_USER);

        assertThat(result.getRegistrant()).isNull();
        assertThat(result.getRegisterPhone()).isNull();
        assertThat(result.getRegisterEmail()).isNull();
    }

    @Test
    @DisplayName("CO-474: 更新账号持久化新字段")
    void CO474_updateAccount_persistsNewFields() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest update = validRequest();
        update.setPassword(null);
        update.setRegistrant("李注册");
        update.setRegisterPhone("13800138000");
        update.setRegisterEmail("lizhuce@example.com");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformAccountDTO result = service.updateAccount(1L, update, ADMIN_USER);

        assertThat(result.getRegistrant()).isEqualTo("李注册");
        assertThat(result.getRegisterPhone()).isEqualTo("13800138000");
        assertThat(result.getRegisterEmail()).isEqualTo("lizhuce@example.com");
    }

    @Test
    @DisplayName("CO-474: DTO 不再包含 contactPhone/contactEmail 字段")
    void CO474_dto_noLongerHasContactPhoneContactEmail() {
        assertThat(fieldExists(PlatformAccountDTO.class, "contactPhone")).isFalse();
        assertThat(fieldExists(PlatformAccountDTO.class, "contactEmail")).isFalse();
        assertThat(fieldExists(PlatformAccountDTO.class, "registrant")).isTrue();
        assertThat(fieldExists(PlatformAccountDTO.class, "registerPhone")).isTrue();
        assertThat(fieldExists(PlatformAccountDTO.class, "registerEmail")).isTrue();
        PlatformAccountDTO dto = PlatformAccountDTO.builder().build();
        assertThat(dto.getRegistrant()).isNull();
        assertThat(dto.getRegisterPhone()).isNull();
        assertThat(dto.getRegisterEmail()).isNull();
    }

    // ── CO-522: 编辑操作触发字段级 diff 审计 ──

    @Test
    @DisplayName("CO-522: 编辑账号成功后调用 auditRecorder 记录 diff")
    void updateAccount_shouldInvokeAuditRecorder() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest req = validRequest();
        req.setAccountName("更新后的平台");
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.updateAccount(1L, req, ADMIN_USER);

        // 验证 recorder.recordUpdate 被调用，操作人正确，pendingApprovalCount 来自 recorder 解析
        verify(auditRecorder).recordUpdate(any(PlatformAccount.class), any(PlatformAccount.class),
                eq(ADMIN_USER), anyInt());
    }

    @Test
    @DisplayName("CO-522: 编辑变更绑定联系人时，调用 recorder 解析转派待审批数（透传 borrowApplicationRepository）")
    void updateAccount_whenContactPersonChanges_callsResolvePendingApprovalCount() {
        PlatformAccount existing = accountWithId(1L);
        existing.setContactPerson(10L);
        PlatformAccountCreateRequest req = validRequest();
        req.setContactPerson(20L);  // 变更联系人
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.updateAccount(1L, req, ADMIN_USER);

        // 验证 recorder 透传 borrowApplicationRepository 与新联系人 ID 给 resolvePendingApprovalCount
        verify(auditRecorder).resolvePendingApprovalCount(
                eq(existing), eq(20L), eq(borrowApplicationRepository));
    }

    @Test
    @DisplayName("CO-522: 编辑前先调用 recorder.snapshot 捕获更新前快照")
    void updateAccount_callsSnapshotBeforeApplyingChanges() {
        PlatformAccount existing = accountWithId(1L);
        PlatformAccountCreateRequest req = validRequest();
        req.setPassword(null);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.updateAccount(1L, req, ADMIN_USER);

        verify(auditRecorder).snapshot(existing);
    }

    // ── helpers ──

    private static PlatformAccountCreateRequest validRequest() {
        return PlatformAccountCreateRequest.builder()
                .username("testuser")
                .password("secret123")
                .accountName("测试平台")
                .build();
    }

    private static PlatformAccount accountWithId(Long id) {
        return PlatformAccount.builder()
                .id(id)
                .username("testuser")
                .password(ENCRYPTED_PWD)
                .accountName("测试平台")
                .status(AccountStatus.AVAILABLE)
                .build();
    }
}
