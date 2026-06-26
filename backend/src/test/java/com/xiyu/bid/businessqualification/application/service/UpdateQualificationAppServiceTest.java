package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationAttachment;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateQualificationAppServiceTest {

    @Mock
    private BusinessQualificationRepository repository;

    @InjectMocks
    private UpdateQualificationAppService appService;

    @Test
    @DisplayName("下架资质 - 应直接设置 retired=true 和 retireReason，保留原附件不经序列化往返")
    void retire_ShouldSetRetiredAndReason_PreservingOriginalAttachments() {
        // Given: 一个有效资质（带附件，附件 uploadedAt 是原始 LocalDateTime）
        QualificationAttachment attachment = new QualificationAttachment(
                100L, "cert.pdf", "/files/cert.pdf", LocalDateTime.of(2026, 6, 25, 15, 30, 0));
        BusinessQualification existing = sampleQualification(1L, false, null, List.of(attachment));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(BusinessQualification.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        BusinessQualification result = appService.retire(1L, "证书过期不再使用");

        // Then: retired=true + reason 设置正确
        assertThat(result.retired()).isTrue();
        assertThat(result.retireReason()).isEqualTo("证书过期不再使用");
        // 关键: 附件原样保留，未经 DTO 序列化往返（uploadedAt 仍是原始 LocalDateTime）
        assertThat(result.attachments()).isEqualTo(existing.attachments());
        assertThat(result.attachments().get(0).getUploadedAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 25, 15, 30, 0));
        // 其他字段保持不变
        assertThat(result.name()).isEqualTo(existing.name());
        assertThat(result.certificateNo()).isEqualTo(existing.certificateNo());
        verify(repository).save(any(BusinessQualification.class));
    }

    @Test
    @DisplayName("下架资质 - 资质不存在时抛 ResourceNotFoundException，不调用 save")
    void retire_WhenNotFound_ShouldThrowResourceNotFoundExceptionAndNeverSave() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.retire(999L, "测试原因"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("下架资质 - 不应触发证书编号重复校验（retired 只改状态，不改证书编号）")
    void retire_ShouldNotCheckCertificateNoDuplication() {
        BusinessQualification existing = sampleQualification(1L, false, null, List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(BusinessQualification.class))).thenAnswer(inv -> inv.getArgument(0));

        appService.retire(1L, "下架原因");

        // 关键: retire 不应调用 existsByCertificateNo（避免下架时误报重复）
        verify(repository, never()).existsByCertificateNo(any());
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
                "审核备注",
                "西域科技",
                new ValidityPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                new ReminderPolicy(true, 30, null),
                "/files/cert.pdf",
                retireReason,
                retired,
                attachments
        );
    }
}
