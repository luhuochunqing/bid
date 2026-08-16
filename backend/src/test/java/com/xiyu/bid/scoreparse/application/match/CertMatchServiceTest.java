package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CertMatchService - 资质证书匹配（FR-009）")
class CertMatchServiceTest {

    @Mock
    private BusinessQualificationJpaRepository repository;

    private CertMatchService service;

    @BeforeEach
    void setUp() {
        service = new CertMatchService(repository);
    }

    private BusinessQualificationEntity cert(Long id, String name, String level,
                                             LocalDate expiry, QualificationStatus status) {
        return BusinessQualificationEntity.builder()
                .id(id).name(name).level(level).expiryDate(expiry).status(status)
                .build();
    }

    @Test
    @DisplayName("完全匹配：1/1 有效证书 → FULL/100")
    void fullMatch_singleValidCert() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                cert(1L, "ISO9001 质量管理体系", "三级", LocalDate.now().plusYears(1), QualificationStatus.IN_STOCK)));
        CertMatchRequest request = new CertMatchRequest(List.of("ISO9001"), "三级",
                LocalDate.now().plusMonths(6), 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(result.matched()).hasSize(1);
        assertThat(((CertMatchedItem) result.matched().get(0)).expired()).isFalse();
    }

    @Test
    @DisplayName("部分匹配：2/3 → PARTIAL/67")
    void partialMatch_twoOfThree() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                cert(1L, "ISO9001", null, null, QualificationStatus.IN_STOCK),
                cert(2L, "ISO14001", null, null, QualificationStatus.IN_STOCK)));
        CertMatchRequest request = new CertMatchRequest(List.of("ISO"), null, null, 3);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchRatio()).isEqualTo(67);
    }

    @Test
    @DisplayName("未匹配：空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        CertMatchRequest request = new CertMatchRequest(List.of("不存在"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }

    @Test
    @DisplayName("过期证书：status=EXPIRED → 算命中但 expired=true，tier=PARTIAL")
    void expiredCert_flaggedAndPartial() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                cert(1L, "ISO9001", null, LocalDate.now().plusYears(1), QualificationStatus.EXPIRED)));
        CertMatchRequest request = new CertMatchRequest(List.of("ISO9001"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(((CertMatchedItem) result.matched().get(0)).expired()).isTrue();
        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(result.matchDetail()).contains("过期");
    }

    @Test
    @DisplayName("按日期过期：expiry_date < 今天 → expired=true")
    void expiredByDate_flagged() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                cert(1L, "ISO9001", null, LocalDate.now().minusDays(1), QualificationStatus.IN_STOCK)));
        CertMatchRequest request = new CertMatchRequest(List.of("ISO9001"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(((CertMatchedItem) result.matched().get(0)).expired()).isTrue();
        assertThat(result.tier()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("等级忽略：requiredLevel 为空 → 不同等级行均放行")
    void levelIgnored_whenRequiredLevelNull() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                cert(1L, "ISO9001", "一级", null, QualificationStatus.IN_STOCK),
                cert(2L, "ISO9001", "三级", null, QualificationStatus.IN_STOCK)));
        CertMatchRequest request = new CertMatchRequest(List.of("ISO9001"), null, null, 2);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(2);
        assertThat(result.tier()).isEqualTo("FULL");
    }
}
