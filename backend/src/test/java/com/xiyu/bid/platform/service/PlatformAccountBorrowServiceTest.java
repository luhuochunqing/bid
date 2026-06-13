// Input: AccountBorrowApplicationRepository, PlatformAccountRepository mocks
// Output: PlatformAccountBorrowService unit tests — borrow application lifecycle
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private PlatformAccountBorrowService service;

    private static final User USER = User.builder().id(10L).build();

    @BeforeEach
    void setUp() {
        service = new PlatformAccountBorrowService(applicationRepository, accountRepository);
    }

    @Test
    @DisplayName("提交借用申请成功 — 账号标记审批中")
    void submitApplication_success() {
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.AVAILABLE).build();
        BorrowApplicationRequest req = BorrowApplicationRequest.builder()
                .accountId(1L).custodianId(20L).purpose("投标使用").build();
        AccountBorrowApplication savedApp = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).applicantId(10L).custodianId(20L).purpose("投标使用")
                .status(BorrowStatus.PENDING_APPROVAL).build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(savedApp);

        BorrowApplicationDTO result = service.submitApplication(req, USER);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        verify(accountRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("提交借用申请时账号不存在抛出异常")
    void submitApplication_accountNotFound_throws() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        BorrowApplicationRequest req = BorrowApplicationRequest.builder().accountId(99L).build();

        assertThatThrownBy(() -> service.submitApplication(req, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("账号不存在");
    }

    @Test
    @DisplayName("审批通过申请成功")
    void approveApplication_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).status(BorrowStatus.PENDING_APPROVAL).build();
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenReturn(app);

        BorrowApplicationDTO result = service.approveApplication(100L, USER);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("审批不存在的申请抛出异常")
    void approveApplication_notFound_throws() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approveApplication(99L, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申请不存在");
    }

    @Test
    @DisplayName("拒绝申请成功 — 填写拒绝原因")
    void rejectApplication_withReason_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).status(BorrowStatus.PENDING_APPROVAL).build();
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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("拒绝时必须填写原因");
    }

    @Test
    @DisplayName("取消申请成功 — 释放账号")
    void cancelApplication_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).status(BorrowStatus.PENDING_APPROVAL).build();
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
    @DisplayName("归还账号成功 — 账号状态恢复可用")
    void returnAccount_success() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(1L).status(BorrowStatus.APPROVED).build();
        PlatformAccount account = PlatformAccount.builder().id(1L).status(AccountStatus.IN_USE).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(applicationRepository.save(any())).thenReturn(app);

        BorrowApplicationDTO result = service.returnAccount(100L, "newSecret", USER);

        assertThat(result.getStatus()).isEqualTo("RETURNED");
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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申请不存在");
    }
}
