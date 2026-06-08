package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessQualificationRepositoryAdapterTest {

    @Mock private BusinessQualificationJpaRepository qualificationJpaRepository;
    @Mock private QualificationAttachmentJpaRepository attachmentJpaRepository;

    @Test
    @DisplayName("列表过滤 - 支持多选状态")
    void findAll_ShouldFilterByMultipleStatuses() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        LocalDate today = LocalDate.now();
        when(qualificationJpaRepository.findAll()).thenReturn(List.of(
                entityWithExpiryAndStatus(1L, "资质A", today.plusYears(1), QualificationStatus.VALID),   // valid
                entityWithExpiryAndStatus(2L, "资质B", today.plusDays(10), QualificationStatus.EXPIRING), // expiring
                entityWithExpiryAndStatus(3L, "资质C", today.minusDays(5), QualificationStatus.EXPIRED)   // expired
        ));

        List<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .status(List.of("valid", "expiring"))
                        .build()
        );

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(BusinessQualification::id).toList()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("列表过滤 - 支持有效期范围")
    void findAll_ShouldFilterByExpiryDateRange() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        LocalDate today = LocalDate.now();
        when(qualificationJpaRepository.findAll()).thenReturn(List.of(
                entityWithExpiry(1L, today.minusDays(5)),
                entityWithExpiry(2L, today.plusDays(10)),
                entityWithExpiry(3L, today.plusDays(40))
        ));

        List<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .expiringFrom(today)
                        .expiringTo(today.plusDays(30))
                        .build()
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("列表过滤 - 支持认证机构模糊匹配")
    void findAll_ShouldFilterByIssuerFuzzyMatch() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        when(qualificationJpaRepository.findAll()).thenReturn(List.of(
                entityWithIssuer(1L, "北京市科学技术委员会"),
                entityWithIssuer(2L, "上海市住房和城乡建设管理委员会"),
                entityWithIssuer(3L, "国家知识产权局")
        ));

        List<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .issuer("北京")
                        .build()
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    private BusinessQualificationRepositoryAdapter newAdapter() {
        return new BusinessQualificationRepositoryAdapter(qualificationJpaRepository, attachmentJpaRepository);
    }

    private BusinessQualificationEntity entity(Long id, String name, QualificationStatus status) {
        return entityBuilder(id, name)
                .status(status)
                .build();
    }

    private BusinessQualificationEntity entityWithExpiry(Long id, LocalDate expiryDate) {
        return entityBuilder(id, "资质" + id)
                .status(QualificationStatus.VALID)
                .issueDate(LocalDate.now().minusYears(1))
                .expiryDate(expiryDate)
                .build();
    }

    private BusinessQualificationEntity entityWithIssuer(Long id, String issuer) {
        return entityBuilder(id, "资质" + id)
                .status(QualificationStatus.VALID)
                .issuer(issuer)
                .build();
    }

    private BusinessQualificationEntity entityWithExpiryAndStatus(Long id, String name, LocalDate expiryDate, QualificationStatus status) {
        return entityBuilder(id, name)
                .status(status)
                .issueDate(expiryDate.minusYears(1))
                .expiryDate(expiryDate)
                .build();
    }

    private BusinessQualificationEntity.BusinessQualificationEntityBuilder entityBuilder(Long id, String name) {
        return BusinessQualificationEntity.builder()
                .id(id)
                .name(name)
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域科技")
                .category(QualificationCategory.PRODUCT)
                .certificateNo("NO-" + id)
                .issuer("科技局")
                .issueDate(LocalDate.now().minusYears(1))
                .expiryDate(LocalDate.now().plusYears(1))
                .status(QualificationStatus.VALID)
                .reminderEnabled(true)
                .reminderDays(30);
    }
}
