// Input: PlatformAccountAuditRecorder + mock IAuditLogService
// Output: 字段级 diff 审计日志写入验证
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.audit;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CO-522: 验证 {@link PlatformAccountAuditRecorder} 的字段级 diff 与密码 mask 逻辑。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAccountAuditRecorderTest {

    @Mock
    private IAuditLogService auditLogService;

    private PlatformAccountAuditRecorder recorder() {
        return new PlatformAccountAuditRecorder(auditLogService);
    }

    private static final User OPERATOR = User.builder().id(7L).username("00888").build();

    @Test
    @DisplayName("仅普通字段变更 → 写一条 UPDATE 日志，description 含变更字段与前后值")
    void recordUpdate_normalFieldChange_logsUpdateWithDiff() {
        PlatformAccount before = PlatformAccount.builder()
                .id(1L).accountName("旧平台").username("olduser")
                .platformType(PlatformType.GOV_PROCUREMENT).password("enc:old").build();
        PlatformAccount after = PlatformAccount.builder()
                .id(1L).accountName("新平台").username("olduser")
                .platformType(PlatformType.GOV_PROCUREMENT).password("enc:old").build();

        recorder().recordUpdate(before, after, OPERATOR, 0);

        ArgumentCaptor<AuditLogService.AuditLogEntry> captor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());

        AuditLogService.AuditLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("UPDATE");
        assertThat(entry.getEntityType()).isEqualTo("PlatformAccount");
        assertThat(entry.getEntityId()).isEqualTo("1");
        assertThat(entry.getDescription()).contains("平台名称：旧平台 → 新平台");
        assertThat(entry.getDescription()).doesNotContain("密码");  // 密码未变，不应出现
    }

    @Test
    @DisplayName("密码字段变更 → description 含「密码：已更新」，绝不写明文")
    void recordUpdate_passwordChange_masksPasswordInDiff() {
        PlatformAccount before = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .password("enc:old_secret_value").build();
        PlatformAccount after = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .password("enc:new_secret_value").build();

        recorder().recordUpdate(before, after, OPERATOR, 0);

        ArgumentCaptor<AuditLogService.AuditLogEntry> captor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());

        String description = captor.getValue().getDescription();
        assertThat(description).contains("密码：已更新");
        assertThat(description).doesNotContain("enc:old_secret_value");
        assertThat(description).doesNotContain("enc:new_secret_value");
    }

    @Test
    @DisplayName("绑定联系人变更 → 写两条日志（TRANSFER_CONTACT + UPDATE），TRANSFER 含转派待审批数")
    void recordUpdate_contactPersonChange_logsTransferAndUpdate() {
        PlatformAccount before = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .contactPerson(10L).password("enc:same").build();
        PlatformAccount after = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .contactPerson(20L).password("enc:same").build();

        recorder().recordUpdate(before, after, OPERATOR, 3);

        ArgumentCaptor<AuditLogService.AuditLogEntry> captor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService, times(2)).log(captor.capture());

        // 第一条：TRANSFER_CONTACT
        AuditLogService.AuditLogEntry transferEntry = captor.getAllValues().get(0);
        assertThat(transferEntry.getAction()).isEqualTo("TRANSFER_CONTACT");
        assertThat(transferEntry.getDescription())
                .contains("更换绑定联系人")
                .contains("用户(10) → 用户(20)")
                .contains("待审批申请数：3");
        // 第二条：UPDATE diff
        AuditLogService.AuditLogEntry updateEntry = captor.getAllValues().get(1);
        assertThat(updateEntry.getAction()).isEqualTo("UPDATE");
        assertThat(updateEntry.getDescription()).contains("绑定联系人：用户(10) → 用户(20)");
    }

    @Test
    @DisplayName("入参含 null → 跳过审计写入，不抛异常")
    void recordUpdate_nullArgs_skipsSafely() {
        recorder().recordUpdate(null, new PlatformAccount(), OPERATOR, 0);
        recorder().recordUpdate(new PlatformAccount(), null, OPERATOR, 0);
        recorder().recordUpdate(new PlatformAccount(), new PlatformAccount(), null, 0);
        verify(auditLogService, times(0)).log(any());
    }

    @Test
    @DisplayName("无任何字段变更 → 写一条 UPDATE，description 为默认文案")
    void recordUpdate_noChanges_logsDefaultDescription() {
        PlatformAccount before = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .password("enc:same").build();
        PlatformAccount after = PlatformAccount.builder()
                .id(1L).accountName("测试").username("u")
                .password("enc:same").build();

        recorder().recordUpdate(before, after, OPERATOR, 0);

        ArgumentCaptor<AuditLogService.AuditLogEntry> captor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("编辑平台账号");
    }
}
