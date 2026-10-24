// Input: QualificationRepository / BusinessQualificationRepository mock
// Output: 断言 QualificationQueryDelegate 优先新库、回退旧库、空仓储异常等行为
// Pos: compliance/service/ - 企业资质库查询委托单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.repository.QualificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualificationQueryDelegateTest {

    @Test
    @DisplayName("项目1117：组合ISO要求逐项关联三张对应证书")
    void matchQualifications_combinedIsoRequirement_matchesEachStandard() {
        QualificationRepository repo = mock(QualificationRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                Qualification.builder().id(1L).name("ISO9001质量管理体系认证证书").build(),
                Qualification.builder().id(2L).name("ISO14001环境管理体系认证证书").build(),
                Qualification.builder().id(3L).name("ISO45001职业健康安全管理体系认证证书").build()));
        QualificationQueryDelegate delegate = new QualificationQueryDelegate(repo);

        List<KnowledgeMatch> matches = delegate.matchQualifications(List.of(
                "具有有效的ISO9001质量管理体系、ISO14001环境管理体系及ISO45001职业健康安全管理体系认证证书"));

        assertThat(matches).extracting(KnowledgeMatch::recordId).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("知识库联动不关联已过期证书")
    void matchQualifications_expiredCertificate_isIgnored() {
        QualificationRepository repo = mock(QualificationRepository.class);
        when(repo.findAll()).thenReturn(List.of(Qualification.builder().id(1L)
                .name("ISO9001质量管理体系认证证书").expiryDate(LocalDate.of(2020, 1, 1)).build()));

        assertThat(new QualificationQueryDelegate(repo).matchQualifications(List.of("ISO9001"))).isEmpty();
    }

    @Test
    @DisplayName("双仓储均缺失时调用匹配抛出 IllegalStateException")
    void testMatchQualifications_WhenBothRepositoriesNull_ThrowsIllegalStateException() {
        QualificationQueryDelegate delegate = new QualificationQueryDelegate();
        assertThatThrownBy(() -> delegate.matchQualifications(List.of("ISO9001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("企业资质库仓储不可用");
    }

    @Test
    @DisplayName("传入关键词为空或空列表时直接返回空列表")
    void testMatchQualifications_WhenKeywordsEmpty_ReturnsEmptyList() {
        QualificationRepository repo = mock(QualificationRepository.class);
        QualificationQueryDelegate delegate = new QualificationQueryDelegate(repo);
        assertThat(delegate.matchQualifications(null)).isEmpty();
        assertThat(delegate.matchQualifications(List.of())).isEmpty();
    }

    @Test
    @DisplayName("优先业务资质库：匹配有效资质并正确映射字段")
    void testMatchQualifications_PrefersBusinessQualificationRepository() {
        BusinessQualificationRepository bqRepo = mock(BusinessQualificationRepository.class);
        BusinessQualification bq = BusinessQualification.create(
                10L, "信息安全管理体系认证", "一级",
                null, null, "SEC-001", "认证机构", null, null,
                "安全服务", null, "西域",
                new ValidityPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1)),
                null, "http://file", null, null, List.of()
        );
        when(bqRepo.findAll(any(QualificationListCriteria.class))).thenReturn(List.of(bq));

        QualificationQueryDelegate delegate = new QualificationQueryDelegate(null, bqRepo);
        List<KnowledgeMatch> matches = delegate.matchQualifications(List.of("ISO27001", "信息安全管理体系认证"));

        assertThat(matches).hasSize(1);
        KnowledgeMatch match = matches.getFirst();
        assertThat(match.knowledgeSource()).isEqualTo(KnowledgeMatch.SOURCE_QUALIFICATION);
        assertThat(match.recordId()).isEqualTo("10");
        assertThat(match.content()).contains("信息安全管理体系认证");
    }

    @Test
    @DisplayName("业务资质库无数据时回退旧资质库")
    void testMatchQualifications_FallbackToLegacyRepository() {
        QualificationRepository legacyRepo = mock(QualificationRepository.class);
        BusinessQualificationRepository bqRepo = mock(BusinessQualificationRepository.class);
        when(bqRepo.findAll(any(QualificationListCriteria.class))).thenReturn(Collections.emptyList());

        Qualification q = Qualification.builder().id(99L).name("CMMI3级认证").build();
        when(legacyRepo.findAll()).thenReturn(List.of(q));

        QualificationQueryDelegate delegate = new QualificationQueryDelegate(legacyRepo, bqRepo);
        List<KnowledgeMatch> matches = delegate.matchQualifications(List.of("CMMI3"));

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().recordId()).isEqualTo("99");
    }

    @Test
    @DisplayName("findAllQualifications 异常时安全捕获并返回空列表")
    void testFindAllQualifications_OnException_ReturnsEmptyList() {
        QualificationRepository legacyRepo = mock(QualificationRepository.class);
        when(legacyRepo.findAll()).thenThrow(new RuntimeException("DB down"));

        QualificationQueryDelegate delegate = new QualificationQueryDelegate(legacyRepo);
        List<Qualification> results = delegate.findAllQualifications();

        assertThat(results).isEmpty();
    }
}
