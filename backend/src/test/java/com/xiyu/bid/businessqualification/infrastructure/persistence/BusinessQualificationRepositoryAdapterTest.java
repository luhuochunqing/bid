package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationPage;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessQualificationRepositoryAdapterTest {

    @Mock private BusinessQualificationJpaRepository qualificationJpaRepository;
    @Mock private QualificationAttachmentJpaRepository attachmentJpaRepository;

    @Test
    @DisplayName("列表过滤 - 支持多选状态")
    @SuppressWarnings("unchecked")
    void findAll_ShouldFilterByMultipleStatuses() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        LocalDate today = LocalDate.now();
        Pageable springPageable = PageRequest.of(0, 15);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(List.of(
                entityWithExpiryAndStatus(1L, "资质A", today.plusYears(1), QualificationStatus.VALID),
                entityWithExpiryAndStatus(2L, "资质B", today.plusDays(10), QualificationStatus.EXPIRING),
                entityWithExpiryAndStatus(3L, "资质C", today.minusDays(5), QualificationStatus.EXPIRED)
        ), springPageable, 3);
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        // CO-155 fix: domain-layer QualificationPage (port boundary free of Spring Page)
        QualificationPage<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .status(List.of("valid", "expiring"))
                        .build(),
                0, 15
        );

        assertThat(result.content()).hasSize(3);
        assertThat(result.totalElements()).isEqualTo(3L);
    }

    @Test
    @DisplayName("列表过滤 - 支持有效期范围")
    @SuppressWarnings("unchecked")
    void findAll_ShouldFilterByExpiryDateRange() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        LocalDate today = LocalDate.now();
        Pageable springPageable = PageRequest.of(0, 15);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(List.of(
                entityWithExpiry(2L, today.plusDays(10))
        ), springPageable, 1);
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        QualificationPage<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .expiringFrom(today)
                        .expiringTo(today.plusDays(30))
                        .build(),
                0, 15
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("列表过滤 - 支持认证机构模糊匹配")
    @SuppressWarnings("unchecked")
    void findAll_ShouldFilterByIssuerFuzzyMatch() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        Pageable springPageable = PageRequest.of(0, 15);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(List.of(
                entityWithIssuer(1L, "北京市科学技术委员会")
        ), springPageable, 1);
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        QualificationPage<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder()
                        .issuer("北京")
                        .build(),
                0, 15
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
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

    // ===== CO-155 fix: pagination tests =====

    @Test
    @DisplayName("分页查询 - 总数正确转换到 domain QualificationPage")
    @SuppressWarnings("unchecked")
    void findAllPageable_ShouldReturnDomainPageWithTotalElements() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        Pageable springPageable = PageRequest.of(0, 15);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(
                List.of(
                        entityWithExpiryAndStatus(1L, "A", LocalDate.now().plusYears(1), QualificationStatus.VALID),
                        entityWithExpiryAndStatus(2L, "B", LocalDate.now().plusYears(1), QualificationStatus.VALID)
                ),
                springPageable,
                42  // totalElements
        );
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        QualificationPage<BusinessQualification> result = adapter.findAll(
                QualificationListCriteria.builder().build(),
                0, 15
        );

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(42L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(15);
    }

    @Test
    @DisplayName("分页查询 - category 过滤通过 Specification 推到 SQL（不再走内存过滤）")
    @SuppressWarnings("unchecked")
    void findAllPageable_ShouldPushCategoryFilterToJpaSpecification() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        Pageable springPageable = PageRequest.of(0, 10);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(List.of(), springPageable, 0);
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        adapter.findAll(
                QualificationListCriteria.builder()
                        .category("LICENSE")
                        .keyword("ISO")
                        .build(),
                0, 10
        );

        // 验证 JpaRepository.findAll(Specification, Pageable) 被调用过一次（不是 findAll() 全表）
        ArgumentCaptor<Specification<BusinessQualificationEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(qualificationJpaRepository).findAll(specCaptor.capture(), pageableCaptor.capture());

        assertThat(specCaptor.getValue()).isNotNull();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("分页查询 - 永不调用 jpaRepository.findAll() 全表方法")
    @SuppressWarnings("unchecked")
    void findAllPageable_ShouldNeverCallFullTableFindAll() {
        BusinessQualificationRepositoryAdapter adapter = newAdapter();
        Pageable springPageable = PageRequest.of(0, 15);
        org.springframework.data.domain.Page<BusinessQualificationEntity> mockSpringPage = new PageImpl<>(List.of(), springPageable, 0);
        when(qualificationJpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockSpringPage);

        adapter.findAll(QualificationListCriteria.builder().build(), 0, 15);

        // 关键：paginated path 不能 fallback 到内存过滤
        verify(qualificationJpaRepository, org.mockito.Mockito.never()).findAll();
    }
}
