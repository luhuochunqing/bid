package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.audit.service.AuditLogService.AuditLogEntry;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.xiyu.bid.businessqualification.domain.service.QualificationCreationPolicy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateQualificationAppServiceTest {

    @Mock
    private BusinessQualificationRepository repository;

    @Mock
    private IAuditLogService auditLogService;

    @Spy
    private QualificationCreationPolicy creationPolicy = new QualificationCreationPolicy();

    @InjectMocks
    private UpdateQualificationAppService appService;

    @Test
    @DisplayName("下架资质 - 应调用 updateRetiredStatus 部分更新，不触发全量 save 和附件删除重插")
    void retire_ShouldCallUpdateRetiredStatus_NotFullSave() {
        // Given: 一个有效资质（带附件）
        QualificationAttachment attachment = new QualificationAttachment(
                100L, "cert.pdf", "/files/cert.pdf", LocalDateTime.of(2026, 6, 25, 15, 30, 0));
        BusinessQualification existing = sampleQualification(1L, false, null, List.of(attachment));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.updateRetiredStatus(1L, true, "证书过期不再使用"))
                .thenReturn(sampleQualification(1L, true, "证书过期不再使用", List.of(attachment)));

        // When
        BusinessQualification result = appService.retire(1L, "证书过期不再使用");

        // Then: 调用了部分更新方法，而非全量 save
        assertThat(result.retired()).isTrue();
        assertThat(result.retireReason()).isEqualTo("证书过期不再使用");
        verify(repository).updateRetiredStatus(1L, true, "证书过期不再使用");
        verify(repository, never()).save(any(BusinessQualification.class));
    }

    @Test
    @DisplayName("下架资质 - 资质不存在时抛 ResourceNotFoundException，不调用 updateRetiredStatus")
    void retire_WhenNotFound_ShouldThrowResourceNotFoundExceptionAndNeverUpdateRetired() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.retire(999L, "测试原因"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).updateRetiredStatus(anyLong(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("下架资质 - 不应触发证书编号重复校验（retired 只改状态，不改证书编号）")
    void retire_ShouldNotCheckCertificateNoDuplication() {
        BusinessQualification existing = sampleQualification(1L, false, null, List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.updateRetiredStatus(1L, true, "下架原因"))
                .thenReturn(sampleQualification(1L, true, "下架原因", List.of()));

        appService.retire(1L, "下架原因");

        // 关键: retire 不应调用 existsByCertificateNo（避免下架时误报重复）
        verify(repository, never()).existsByCertificateNo(any());
    }

    // ==================== CO-368: 资质附件删除 - 三元短路修复 ====================

    @Test
    @DisplayName("CO-368: 显式清空 fileUrl (fileUrlExplicitlySet=true + fileUrl=null) 应清空附件并写审计")
    void update_WithExplicitClearFlag_ShouldClearFileUrlAndWriteAudit() {
        BusinessQualification existing = givenExistingQualification();
        QualificationUpsertCommand command = buildCommandFromExisting(existing, null, true);

        BusinessQualification result = appService.update(1L, command);

        // Then: fileUrl 被清空
        assertThat(result.fileUrl()).isNull();
        // 审计被调用一次，action="ATTACHMENT_CHANGE"，description="删除资质证书附件"
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("ATTACHMENT_CHANGE");
        assertThat(entry.getDescription()).isEqualTo("删除资质证书附件");
    }

    @Test
    @DisplayName("CO-368: 未传 fileUrlExplicitlySet (null) 应保留 existing.fileUrl（向后兼容）")
    void update_WithoutExplicitFlag_ShouldKeepExistingFileUrl() {
        BusinessQualification existing = givenExistingQualification();
        // 老式调用：fileUrl=null 表示"未传"，fileUrlExplicitlySet=null 表示未传 flag
        QualificationUpsertCommand command = buildCommandFromExisting(existing, null, null);

        BusinessQualification result = appService.update(1L, command);

        // Then: fileUrl 保留 existing 的旧值
        assertThat(result.fileUrl()).isEqualTo("/files/cert.pdf");
        // 审计不应被调用（因为 urlChanged=false）
        verify(auditLogService, never()).log(any());
    }

    @Test
    @DisplayName("CO-368: 显式替换 fileUrl (fileUrlExplicitlySet=true + fileUrl=新值) 应写入新 fileUrl（替换不回归）")
    void update_WithExplicitFlagAndNewUrl_ShouldReplaceFileUrl() {
        BusinessQualification existing = givenExistingQualification();
        QualificationUpsertCommand command = buildCommandFromExisting(existing, "/files/new.pdf", true);

        BusinessQualification result = appService.update(1L, command);

        // Then: fileUrl 被替换为新值
        assertThat(result.fileUrl()).isEqualTo("/files/new.pdf");
        // 审计被调用一次，action="ATTACHMENT_CHANGE"，description="替换资质证书附件"
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("ATTACHMENT_CHANGE");
        assertThat(entry.getDescription()).isEqualTo("替换资质证书附件");
    }

    @Test
    @DisplayName("CO-368: 显式传 fileUrlExplicitlySet=false 应等同于未传，保留 existing.fileUrl")
    void update_WithExplicitFalseFlag_ShouldKeepExistingFileUrl() {
        BusinessQualification existing = givenExistingQualification();
        // 显式传 false（而非未传），应等同于 null，保留 existing
        QualificationUpsertCommand command = buildCommandFromExisting(existing, null, false);

        BusinessQualification result = appService.update(1L, command);

        // Then: fileUrl 保留 existing 的旧值（Boolean.TRUE.equals(false) == false → 走保留分支）
        assertThat(result.fileUrl()).isEqualTo("/files/cert.pdf");
        verify(auditLogService, never()).log(any());
    }

    @Test
    @DisplayName("CO-525: 更新时传入空白 agencyContact 应被校验拦截，不得落库")
    void update_WithBlankAgencyContact_ShouldThrowInvalidArgumentException() {
        BusinessQualification existing = sampleQualification(1L, false, null, List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        QualificationUpsertCommand command = buildCommandFromExisting(existing, null, null).toBuilder()
                .agencyContact("   ")
                .build();

        assertThatThrownBy(() -> appService.update(1L, command))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("代理机构联系人不能为空");

        verify(repository, never()).save(any(BusinessQualification.class));
    }

    /**
     * CO-368 测试公用 Given：构造 existing 资质（带附件 + fileUrl="/files/cert.pdf"），
     * 并 stub repository.findById/save。
     */
    private BusinessQualification givenExistingQualification() {
        QualificationAttachment attachment = new QualificationAttachment(
                100L, "old.pdf", "/files/old.pdf", LocalDateTime.of(2026, 6, 25, 15, 30, 0));
        BusinessQualification existing = sampleQualification(1L, false, null, List.of(attachment));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(BusinessQualification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return existing;
    }

    /**
     * CO-368 测试公用 When：基于 existing 构造 upsert command，仅 fileUrl 和 fileUrlExplicitlySet 变化。
     * 其他字段全部沿用 existing，避免每个测试重复 14 行 builder。
     */
    private QualificationUpsertCommand buildCommandFromExisting(BusinessQualification existing,
                                                                String fileUrl,
                                                                Boolean fileUrlExplicitlySet) {
        return QualificationUpsertCommand.builder()
                .name(existing.name())
                .subjectType(existing.subject().getType())
                .subjectName(existing.subject().getName())
                .category(existing.category())
                .certificateNo(existing.certificateNo())
                .issuer(existing.issuer())
                .holderName(existing.holderName())
                .issueDate(existing.validityPeriod().getIssueDate())
                .expiryDate(existing.validityPeriod().getExpiryDate())
                .reminderEnabled(existing.reminderPolicy().isEnabled())
                .reminderDays(existing.reminderPolicy().getReminderDays())
                .fileUrl(fileUrl)
                .fileUrlExplicitlySet(fileUrlExplicitlySet)
                .retired(false)
                .attachments(existing.attachments())
                .build();
    }

    /**
     * 构造一个最小可用的 BusinessQualification 样本。
     * 字段顺序对齐 BusinessQualification.createWithRetired(...)。
     */
    private BusinessQualification sampleQualification(Long id, boolean retired, String retireReason,
                                                       List<QualificationAttachment> attachments) {
        return BusinessQualification.createWithRetired(
                id,
                "ISO 9001",
                "AAA",
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域"),
                QualificationCategory.LICENSE,
                "NO-" + id,
                "认证机构",
                "代理机构",
                "13800138000",
                "范围",
                null,
                "西域科技",
                new ValidityPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                new ReminderPolicy(true, 30, null),
                "/files/cert.pdf",
                null,
                retireReason,
                retired,
                attachments
        );
    }
}
