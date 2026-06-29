// Input: AccountBorrowApplicationRepository, PlatformAccountRepository mocks
// Output: PlatformAccountBorrowService unit tests — borrow application lifecycle
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
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
class PlatformAccountBorrowServiceTest {

    @Mock
    private AccountBorrowApplicationRepository applicationRepository;
    @Mock
    private PlatformAccountRepository accountRepository;
    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    private PlatformAccountBorrowService service;

    private static final User USER = User.builder().id(10L).build();
    private static final User CUSTODIAN = User.builder().id(20L).build();

    @BeforeEach
    void setUp() {
        service = new PlatformAccountBorrowService(applicationRepository, accountRepository, passwordEncryptionUtil);
    }

    @Test
    @DisplayName("提交借用申请成功 — 账号标记审批中并记录 projectId")
    void submitApplication_success() {
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.AVAILABLE).contactPerson(20L).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).custodianId(20L).purpose("投标使用").projectId(5L).expectedReturnAt("2026-07-10T18:00:00").build();
        AccountBorrowApplication savedApp = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(10L).custodianId(20L).purpose("投标使用")
                .projectId(5L).status(BorrowStatus.PENDING_APPROVAL).build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(savedApp);

        BorrowApplicationDTO result = service.submitApplication(req, USER);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getProjectId()).isEqualTo(5L);
        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        verify(accountRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("提交借用申请时账号不存在抛出异常")
    void submitApplication_accountNotFound_throws() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        BorrowApplicationRequest req = BorrowApplicationRequest.builder().accountId(99L).build();

        assertThatThrownBy(() -> service.submitApplication(req, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号不存在");
    }

    @Test
    @DisplayName("审批通过申请成功 — 状态变为已借出并记录审批意见")
    void approveApplication_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(10L).custodianId(10L)
                .status(BorrowStatus.PENDING_APPROVAL)
                .expectedReturnAt(LocalDateTime.of(2026, 7, 10, 18, 0))
                .build();
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.PENDING_APPROVAL).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenReturn(app);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);

        BorrowApplicationDTO result = service.approveApplication(100L, "同意", USER);

        assertThat(result.getStatus()).isEqualTo("BORROWED");
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getApprovalComment()).isEqualTo("同意");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.IN_USE);
        assertThat(account.getBorrowedBy()).isEqualTo(10L);
        assertThat(account.getDueAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 18, 0));
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("审批不存在的申请抛出异常")
    void approveApplication_notFound_throws() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approveApplication(99L, null, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("申请不存在");
    }

    @Test
    @DisplayName("拒绝申请成功 — 填写拒绝原因")
    void rejectApplication_withReason_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).custodianId(10L).status(BorrowStatus.PENDING_APPROVAL).build();
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.PENDING_APPROVAL).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(app);

        BorrowApplicationDTO result = service.rejectApplication(100L, "信息不完整", USER);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(accountRepository).save(any());
    }

    @Test
    @DisplayName("拒绝申请时拒绝原因为空抛出异常")
    void rejectApplication_emptyReason_throws() {
        assertThatThrownBy(() -> service.rejectApplication(100L, "", USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝时必须填写原因");
    }

    @Test
    @DisplayName("取消申请成功 — 释放账号")
    void cancelApplication_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(10L).status(BorrowStatus.PENDING_APPROVAL).build();
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.PENDING_APPROVAL).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(app);

        BorrowApplicationDTO result = service.cancelApplication(100L, USER);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(accountRepository).save(any());
    }

    @Test
    @DisplayName("归还账号成功 — 账号状态恢复可用且密码被加密更新")
    void returnAccount_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).custodianId(10L).status(BorrowStatus.BORROWED).build();
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.IN_USE).password("oldEncrypted").build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncryptionUtil.encrypt("newSecret")).thenReturn("encryptedNewSecret");
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(app);

        BorrowApplicationDTO result = service.returnAccount(100L, "newSecret", LocalDateTime.of(2026, 7, 5, 18, 0), USER);

        assertThat(result.getStatus()).isEqualTo("RETURNED");
        assertThat(result.getReturnedAt()).isEqualTo(LocalDateTime.of(2026, 7, 5, 18, 0));
        assertThat(account.getPassword()).isEqualTo("encryptedNewSecret");
        verify(passwordEncryptionUtil).encrypt("newSecret");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("按状态查询申请列表")
    void getApplications_byStatus() {
        when(applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL))
                .thenReturn(List.of(AccountBorrowApplication.builder().id(1L).status(BorrowStatus.PENDING_APPROVAL).build()));

        List<BorrowApplicationDTO> result = service.getApplications(null, null, "PENDING_APPROVAL");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("按申请人查询申请列表")
    void getApplications_byApplicant() {
        when(applicationRepository.findByApplicantId(10L))
                .thenReturn(List.of(AccountBorrowApplication.builder().id(1L).status(BorrowStatus.PENDING_APPROVAL).build()));

        List<BorrowApplicationDTO> result = service.getApplications(10L, null, null);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("根据 ID 查询申请")
    void getApplication_success() {
        when(applicationRepository.findById(100L))
                .thenReturn(Optional.of(AccountBorrowApplication.builder().id(100L).status(BorrowStatus.PENDING_APPROVAL).build()));

        BorrowApplicationDTO result = service.getApplication(100L);
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("查询不存在的申请抛出异常")
    void getApplication_notFound_throws() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getApplication(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("申请不存在");
    }

    @Test
    @DisplayName("非绑定联系人审批申请抛出异常")
    void approveApplication_notCustodian_throws() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).custodianId(20L).status(BorrowStatus.PENDING_APPROVAL).build();
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.approveApplication(100L, null, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有账号绑定联系人可以操作该申请");
    }

    @Test
    @DisplayName("非申请人撤销申请抛出异常")
    void cancelApplication_notApplicant_throws() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(20L).status(BorrowStatus.PENDING_APPROVAL).build();
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.cancelApplication(100L, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有申请人可以撤销该申请");
    }

    @Test
    @DisplayName("提交申请时保管员与账号不匹配抛出异常")
    void submitApplication_custodianMismatch_throws() {
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.AVAILABLE).contactPerson(99L).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).custodianId(20L).purpose("投标使用").build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.submitApplication(req, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保管员信息不匹配");
    }

    @Test
    @DisplayName("CO-386: 提交申请未传 custodianId 时自动从 account.contactPerson 取值")
    void submitApplication_withoutCustodianId_usesAccountContactPerson() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).status(AccountStatus.AVAILABLE).contactPerson(20L).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).purpose("投标使用")
                .expectedReturnAt("2026-07-10T18:00:00").build();
        AccountBorrowApplication savedApp = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(10L).custodianId(20L)
                .purpose("投标使用").status(BorrowStatus.PENDING_APPROVAL).build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(savedApp);

        BorrowApplicationDTO result = service.submitApplication(req, USER);

        assertThat(result.getCustodianId()).isEqualTo(20L);
        verify(applicationRepository).save(org.mockito.ArgumentMatchers.argThat(
                app -> app != null && app.getCustodianId() != null && app.getCustodianId().equals(20L)));
    }

    @Test
    @DisplayName("CO-386: 提交申请未传 custodianId 且账号未绑定联系人时抛出异常")
    void submitApplication_accountWithoutContactPerson_throws() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).status(AccountStatus.AVAILABLE).contactPerson(null).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).purpose("投标使用").build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.submitApplication(req, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该账户未绑定联系人");
    }

    @Test
    @DisplayName("提交申请时预计归还时间格式非法抛出异常")
    void submitApplication_invalidExpectedReturnAt_throws() {
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.AVAILABLE).contactPerson(20L).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).custodianId(20L).purpose("投标使用")
                .expectedReturnAt("2026/07/10 18:00").build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.submitApplication(req, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预计归还时间格式不正确");
    }

    @Test
    @DisplayName("按非法状态查询申请列表抛出异常")
    void getApplications_invalidStatus_throws() {
        assertThatThrownBy(() -> service.getApplications(null, null, "UNKNOWN_STATUS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的申请状态");
    }

    @Test
    @DisplayName("审批非待审批状态申请抛出异常")
    void approveApplication_invalidState_throws() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).custodianId(10L).status(BorrowStatus.BORROWED).build();
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.approveApplication(100L, "同意", USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能在待审批状态下通过申请");
    }

    @Test
    @DisplayName("归还未借出状态申请抛出异常")
    void returnAccount_invalidState_throws() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).custodianId(10L).status(BorrowStatus.PENDING_APPROVAL).build();
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.returnAccount(100L, "newSecret", LocalDateTime.now(), USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能在已借出状态下归还账号");
    }
}
