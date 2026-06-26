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
