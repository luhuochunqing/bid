package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity.BorrowStatus;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity.CaBorrowStatus;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CO-466: {@link CaBorrowApplicationNameEnricher} 单元测试.
 *
 * <p>历史缺陷：CaBorrowApplicationDTO 没返回 caName 字段，前端 normalize
 * 拿到空字符串，模板 fallback 到 {@code CA#${caCertificateId}}（如 "CA#5"），
 * 而不是用户期望的"持有人 / 关联平台 / 印章"拼接。
 *
 * <p>修复契约：enricher 拼装规则与前端 CABorrowDialog.vue caLabel 一致：
 * <pre>
 * caName = [holderName, relatedPlatforms, sealTypeLabel].filter(nonEmpty).join(' / ')
 * </pre>
 *
 * <p>CO-566: 关联平台由关联表 ID 反查改为直接读取 relatedPlatforms 文本，
 * enricher 不再依赖 PlatformAccount / CaCertificatePlatformRepository。
 *
 * <p>测试策略：直接构造 Enricher 组件，mock certificateRepository，验证 caName 拼装。
 */
@ExtendWith(MockitoExtension.class)
class CaBorrowApplicationNameEnricherTest {

    @Mock private CaCertificateRepository certificateRepository;

    // ── caName 必须拼装"持有人 / 关联平台 / 印章" ──

    @Test
    void enrich_caName_shouldIncludeHolderPlatformsSeal() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        // CO-566: 关联平台直接用 relatedPlatforms 文本
        CaCertificateEntity cert = cert(1L, "张三", "OFFICIAL_SEAL", "政采云, 国铁采购");
        CaBorrowApplicationEntity app = app(501L, 1L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaName()).isEqualTo("张三 / 政采云, 国铁采购 / 公章");
    }

    // ── holder 为空时 caName 只显示"关联平台 / 印章" ──

    @Test
    void enrich_holderEmpty_shouldOnlyShowPlatformsAndSeal() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaCertificateEntity cert = cert(2L, "", "LEGAL_PERSON_SEAL", "政采云");
        CaBorrowApplicationEntity app = app(502L, 2L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        assertThat(result.get(0).getCaName()).isEqualTo("政采云 / 法人章");
    }

    // ── relatedPlatforms 为空时 caName 只显示"持有人 / 印章" ──

    @Test
    void enrich_emptyRelatedPlatforms_shouldOnlyShowHolderAndSeal() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaCertificateEntity cert = cert(3L, "李四", "LEGAL_SIGN", null);
        CaBorrowApplicationEntity app = app(503L, 3L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        assertThat(result.get(0).getCaName()).isEqualTo("李四 / 法人签字");
    }

    // ── 印章类型映射覆盖所有 4 种 ──

    @Test
    void enrich_sealTypeLabel_shouldMapAllFourTypes() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();

        CaCertificateEntity official = cert(11L, "甲", "OFFICIAL_SEAL", null);
        CaCertificateEntity legal = cert(12L, "乙", "LEGAL_PERSON_SEAL", null);
        CaCertificateEntity legalSign = cert(13L, "丙", "LEGAL_SIGN", null);
        CaCertificateEntity contactSign = cert(14L, "丁", "CONTACT_SIGN", null);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(official, legal, legalSign, contactSign));

        List<CaBorrowApplicationEntity> apps = List.of(
                app(1L, 11L), app(2L, 12L), app(3L, 13L), app(4L, 14L)
        );
        List<CaBorrowApplicationDTO> result = enricher.enrich(apps);

        assertThat(result.get(0).getCaName()).isEqualTo("甲 / 公章");
        assertThat(result.get(1).getCaName()).isEqualTo("乙 / 法人章");
        assertThat(result.get(2).getCaName()).isEqualTo("丙 / 法人签字");
        assertThat(result.get(3).getCaName()).isEqualTo("丁 / 联系人签字");
    }

    // ── 多个申请关联同一个 CA 时，caName 应正确填到每条 ──

    @Test
    void enrich_multipleApplicationsSameCa_shouldEnrichAll() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaCertificateEntity cert = cert(1L, "张三", "OFFICIAL_SEAL", null);
        CaBorrowApplicationEntity app1 = app(901L, 1L);
        CaBorrowApplicationEntity app2 = app(902L, 1L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app1, app2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCaName()).isEqualTo("张三 / 公章");
        assertThat(result.get(1).getCaName()).isEqualTo("张三 / 公章");
    }

    // ── 多个申请关联不同 CA 时，按 caId 批量查应正确分配 ──

    @Test
    void enrich_multipleApplicationsDifferentCas_shouldEnrichRespectively() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaCertificateEntity cert1 = cert(1L, "张三", "OFFICIAL_SEAL", null);
        CaCertificateEntity cert2 = cert(2L, "李四", "LEGAL_SIGN", null);
        CaBorrowApplicationEntity app1 = app(911L, 1L);
        CaBorrowApplicationEntity app2 = app(912L, 2L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert1, cert2));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app1, app2));

        assertThat(result.get(0).getCaName()).isEqualTo("张三 / 公章");
        assertThat(result.get(1).getCaName()).isEqualTo("李四 / 法人签字");
    }

    // ── CA 已被删除（findAllById 返回空）时 caName 应为 null，不应抛异常 ──

    @Test
    void enrich_certificateDeleted_shouldReturnNullCaName_notThrow() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaBorrowApplicationEntity app = app(921L, 999L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of());  // CA 已删

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        // CA 已删除时 caName 为 null（前端会 fallback 到 CA#${caCertificateId}）
        assertThat(result.get(0).getCaName()).isNull();
    }

    // ── 空列表应直接返回空列表，不触发任何查询 ──

    @Test
    void enrich_emptyApplications_shouldReturnEmptyList() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of());

        assertThat(result).isEmpty();
    }

    // ── null 输入也应安全返回空列表 ──

    @Test
    void enrich_nullApplications_shouldReturnEmptyList() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();

        List<CaBorrowApplicationDTO> result = enricher.enrich(null);

        assertThat(result).isEmpty();
    }

    // ── 全字段都为空时 caName 应为 null（前端 fallback 到 CA#${id}） ──

    @Test
    void enrich_allFieldsEmpty_shouldReturnNullCaName() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        // holderName=null, sealType=null, relatedPlatforms=null
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L).caType("ENTITY_CA")
                .expiryDate(LocalDate.now().plusDays(30))
                .custodianId(99L).custodianName("保管员")
                .borrowStatus(CaBorrowStatus.IN_STOCK.name()).status("ACTIVE").build();
        CaBorrowApplicationEntity app = app(931L, 1L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        // 所有字段都空时 caName 为 null（前端会 fallback 到 CA#${caCertificateId}）
        assertThat(result.get(0).getCaName()).isNull();
    }

    // ── 未知 sealType 应回退原值（不丢失信息） ──

    @Test
    void enrich_unknownSealType_shouldFallbackToRawValue() {
        CaBorrowApplicationNameEnricher enricher = newEnricher();
        CaCertificateEntity cert = cert(1L, "张三", "CUSTOM_SEAL_TYPE", null);
        CaBorrowApplicationEntity app = app(1L, 1L);

        when(certificateRepository.findAllById(any())).thenReturn(List.of(cert));

        List<CaBorrowApplicationDTO> result = enricher.enrich(List.of(app));

        // 未知 sealType 应回退原值，不丢失信息
        assertThat(result.get(0).getCaName()).isEqualTo("张三 / CUSTOM_SEAL_TYPE");
    }

    // ── helpers ──

    private CaBorrowApplicationNameEnricher newEnricher() {
        return new CaBorrowApplicationNameEnricher(certificateRepository);
    }

    private CaCertificateEntity cert(Long id, String holderName, String sealType, String relatedPlatforms) {
        return CaCertificateEntity.builder()
                .id(id).caType("ENTITY_CA").sealType(sealType)
                .holderName(holderName).relatedPlatforms(relatedPlatforms)
                .expiryDate(LocalDate.now().plusDays(30))
                .custodianId(99L).custodianName("保管员")
                .borrowStatus(CaBorrowStatus.IN_STOCK.name()).status("ACTIVE").build();
    }

    private CaBorrowApplicationEntity app(Long id, Long caCertificateId) {
        return CaBorrowApplicationEntity.builder()
                .id(id).caCertificateId(caCertificateId).applicantId(10L).applicantName("王五")
                .status(BorrowStatus.PENDING_APPROVAL.name()).build();
    }
}
