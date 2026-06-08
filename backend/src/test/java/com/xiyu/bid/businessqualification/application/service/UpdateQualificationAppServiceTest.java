package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateQualificationAppServiceTest {

    @Mock private BusinessQualificationRepository repository;
    @Mock private IAuditLogService auditLogService;

    @Test
    @DisplayName("更新资质 - 应持久化下架原因")
    void update_ShouldPersistRetireReason() {
        UpdateQualificationAppService appService = new UpdateQualificationAppService(repository, auditLogService);
        when(repository.findById(1L)).thenReturn(Optional.of(sampleQualification("旧原因")));
        when(repository.save(org.mockito.ArgumentMatchers.any(BusinessQualification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessQualification updated = appService.update(1L, QualificationUpsertCommand.builder()
                .name("高新技术企业证书")
                .retireReason("证书已过期且不再使用")
                .build());

        assertThat(updated.retireReason()).isEqualTo("证书已过期且不再使用");
    }

    @Test
    @DisplayName("更新资质 - 空字符串应覆盖清空下架原因")
    void update_ShouldAllowClearingRetireReasonWithEmptyString() {
        UpdateQualificationAppService appService = new UpdateQualificationAppService(repository, auditLogService);
        when(repository.findById(1L)).thenReturn(Optional.of(sampleQualification("旧原因")));
        when(repository.save(org.mockito.ArgumentMatchers.any(BusinessQualification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessQualification updated = appService.update(1L, QualificationUpsertCommand.builder()
                .name("高新技术企业证书")
                .retireReason("")
                .build());

        assertThat(updated.retireReason()).isEqualTo("");
    }

    private BusinessQualification sampleQualification(String retireReason) {
        return BusinessQualification.create(
                1L,
                "高新技术企业证书",
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域科技"),
                QualificationCategory.PRODUCT,
                "GX-001",
                "科技局",
                null,
                null,
                null,
                null,
                null,
                new ValidityPeriod(LocalDate.now().minusYears(1), LocalDate.now().plusYears(1)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                retireReason,
                List.of()
        );
    }
}
