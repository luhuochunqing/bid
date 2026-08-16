package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelCertificateEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelJpaRepository;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PersonMatchService - 人员匹配（FR-010）")
class PersonMatchServiceTest {

    @Mock
    private PersonnelJpaRepository personnelRepository;

    @Mock
    private PersonnelCertificateJpaRepository certificateRepository;

    private PersonMatchService service;

    @BeforeEach
    void setUp() {
        service = new PersonMatchService(personnelRepository, certificateRepository);
    }

    private PersonnelEntity person(Long id, String name, String title) {
        return PersonnelEntity.builder()
                .id(id).name(name).employeeNumber("E" + id).technicalTitle(title)
                .status(PersonnelStatus.ACTIVE).build();
    }

    private PersonnelCertificateEntity certificate(Long personnelId, String name, LocalDate expiry) {
        return PersonnelCertificateEntity.builder()
                .personnelId(personnelId).certificateName(name).expiryDate(expiry).build();
    }

    @Test
    @DisplayName("比例计算：3/5 符合 → PARTIAL/60")
    void ratio_threeOfFive() {
        List<PersonnelEntity> people = List.of(
                person(1L, "张三", "项目经理"), person(2L, "李四", "项目经理"), person(3L, "王五", "项目经理"));
        when(personnelRepository.findAll(any(Specification.class))).thenReturn(people);
        people.forEach(p -> when(certificateRepository.findByPersonnelId(p.getId()))
                .thenReturn(List.of(certificate(p.getId(), "PMP", LocalDate.now().plusYears(1)))));
        PersonMatchRequest request = new PersonMatchRequest(List.of("项目经理"), List.of("PMP"), 5);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchRatio()).isEqualTo(60);
        assertThat(result.matched()).hasSize(3);
    }

    @Test
    @DisplayName("证书软删除过滤：deleted_at 非空的证书不算命中，人员被剔除")
    void deletedCertificate_filtered() {
        when(personnelRepository.findAll(any(Specification.class))).thenReturn(List.of(
                person(1L, "张三", "项目经理"), person(2L, "李四", "项目经理")));
        PersonnelCertificateEntity deleted = certificate(1L, "PMP", LocalDate.now().plusYears(1));
        deleted.setDeletedAt(LocalDate.now().atStartOfDay());
        when(certificateRepository.findByPersonnelId(1L)).thenReturn(List.of(deleted));
        when(certificateRepository.findByPersonnelId(2L)).thenReturn(List.of(
                certificate(2L, "PMP", LocalDate.now().plusYears(1))));
        PersonMatchRequest request = new PersonMatchRequest(List.of("项目经理"), List.of("PMP"), 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(((PersonMatchedItem) result.matched().get(0)).name()).isEqualTo("李四");
    }

    @Test
    @DisplayName("单人多证计一次：1 人 2 张命中证书 → matched 1 条、hitCertificates 2 项")
    void multiCert_countOnce() {
        when(personnelRepository.findAll(any(Specification.class))).thenReturn(List.of(
                person(1L, "张三", "项目经理")));
        when(certificateRepository.findByPersonnelId(1L)).thenReturn(List.of(
                certificate(1L, "PMP", LocalDate.now().plusYears(1)),
                certificate(1L, "软考高级项目经理", null)));
        PersonMatchRequest request = new PersonMatchRequest(List.of("项目经理"), List.of("PMP", "软考"), 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        PersonMatchedItem item = (PersonMatchedItem) result.matched().get(0);
        assertThat(item.hitCertificates()).containsExactly("PMP", "软考高级项目经理");
        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
    }

    @Test
    @DisplayName("证书有效期过滤：过期证书不算命中，唯一证书过期则人员剔除")
    void expiredCertificate_notHit() {
        when(personnelRepository.findAll(any(Specification.class))).thenReturn(List.of(
                person(1L, "张三", "项目经理"), person(2L, "李四", "项目经理")));
        when(certificateRepository.findByPersonnelId(1L)).thenReturn(List.of(
                certificate(1L, "PMP", LocalDate.now().minusDays(1))));
        when(certificateRepository.findByPersonnelId(2L)).thenReturn(List.of(
                certificate(2L, "PMP", null)));
        PersonMatchRequest request = new PersonMatchRequest(List.of("项目经理"), List.of("PMP"), 2);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(((PersonMatchedItem) result.matched().get(0)).name()).isEqualTo("李四");
    }

    @Test
    @DisplayName("空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(personnelRepository.findAll(any(Specification.class))).thenReturn(List.of());
        PersonMatchRequest request = new PersonMatchRequest(List.of("项目经理"), List.of("PMP"), 3);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }
}
