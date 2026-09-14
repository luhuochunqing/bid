package com.xiyu.bid.resources.application;

import com.xiyu.bid.compliance.domain.policy.CaCertificateEvidence;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaCertificateEvidenceQueryAdapterTest {

    @Test
    @DisplayName("findAvailableCertificates 排除 INACTIVE 并正确映射领域模型")
    void testFindAvailableCertificates_MapsFieldsAndExcludesInactive() {
        CaCertificateRepository repo = mock(CaCertificateRepository.class);
        CaCertificateEntity activeEntity = CaCertificateEntity.builder()
                .id(101L)
                .caType("ENTITY_CA")
                .sealType("公章,法人章")
                .issuer("CFCA")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .relatedPlatforms("国家电网新一代电子商务平台")
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .custodianName("李保管")
                .holderName("张持有人")
                .build();

        when(repo.findByStatusNot("INACTIVE")).thenReturn(List.of(activeEntity));

        CaCertificateEvidenceQueryAdapter adapter = new CaCertificateEvidenceQueryAdapter(repo);
        List<CaCertificateEvidence> results = adapter.findAvailableCertificates();

        assertThat(results).hasSize(1);
        CaCertificateEvidence ev = results.get(0);
        assertThat(ev.id()).isEqualTo(101L);
        assertThat(ev.caType()).isEqualTo("ENTITY_CA");
        assertThat(ev.sealType()).isEqualTo("公章,法人章");
        assertThat(ev.issuer()).isEqualTo("CFCA");
        assertThat(ev.expiryDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(ev.relatedPlatforms()).isEqualTo("国家电网新一代电子商务平台");
        assertThat(ev.borrowStatus()).isEqualTo("IN_STOCK");
        assertThat(ev.status()).isEqualTo("ACTIVE");
        assertThat(ev.custodianName()).isEqualTo("李保管");
        assertThat(ev.holderName()).isEqualTo("张持有人");
    }

    @Test
    @DisplayName("findCaMatches 根据交易平台关键词匹配并生成 KnowledgeMatch")
    void testFindCaMatches_FiltersByPlatformAndLimits() {
        CaCertificateRepository repo = mock(CaCertificateRepository.class);
        CaCertificateEntity e1 = CaCertificateEntity.builder()
                .id(1L)
                .caType("ENTITY_CA")
                .sealType("公章")
                .expiryDate(LocalDate.of(2027, 1, 1))
                .relatedPlatforms("国家电网新一代电子商务平台")
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .build();
        CaCertificateEntity e2 = CaCertificateEntity.builder()
                .id(2L)
                .caType("ELECTRONIC_CA")
                .sealType("公章")
                .expiryDate(LocalDate.of(2027, 1, 1))
                .relatedPlatforms("南方电网供应链统一服务平台")
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .build();

        when(repo.findByStatusNot("INACTIVE")).thenReturn(List.of(e1, e2));

        CaCertificateEvidenceQueryAdapter adapter = new CaCertificateEvidenceQueryAdapter(repo);
        List<KnowledgeMatch> matches = adapter.findCaMatches("国家电网", 10);

        assertThat(matches).hasSize(1);
        KnowledgeMatch m = matches.get(0);
        assertThat(m.knowledgeSource()).isEqualTo("ca-certificate");
        assertThat(m.recordId()).isEqualTo("1");
        assertThat(m.content()).contains("国家电网");
        assertThat(m.matched()).isTrue();
    }

    @Test
    @DisplayName("仓储为 null 时安全降级为空列表")
    void testNullRepository_ReturnsEmptyList() {
        CaCertificateEvidenceQueryAdapter adapter = new CaCertificateEvidenceQueryAdapter(null);
        assertThat(adapter.findAvailableCertificates()).isEmpty();
        assertThat(adapter.findCaMatches("国网", 10)).isEmpty();
    }
}
