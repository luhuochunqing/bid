package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CrmOpportunityCodeResolver 单元测试。
 * <p>覆盖：code 原样返回、纯数字 id 反查 code、username 透传、externalId 兜底反查。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrmOpportunityCodeResolver — CRM 商机编号解析")
class CrmOpportunityCodeResolverTest {

    private static final String USERNAME = "06234";

    @Mock
    private CrmProjectLeaderService crmProjectLeaderService;

    private CrmOpportunityCodeResolver resolver() {
        return new CrmOpportunityCodeResolver(crmProjectLeaderService);
    }

    @Test
    @DisplayName("已是 CC 前缀 code → 直接返回，不调 CRM")
    void alreadyCodeFormat_returnsAsIs() {
        String code = resolver().resolve("CC2026070932", USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    @DisplayName("空值/空白 → 返回空字符串")
    void blankInput_returnsEmpty() {
        assertThat(resolver().resolve(null, USERNAME)).isEmpty();
        assertThat(resolver().resolve("", USERNAME)).isEmpty();
        assertThat(resolver().resolve("  ", USERNAME)).isEmpty();
    }

    @Test
    @DisplayName("纯数字 id → 用 username 调 CRM 反查 code")
    void numericId_looksUpCodeWithUsername() {
        CrmOpportunityCodeResolver resolver = resolver();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(22L, USERNAME))
                .thenReturn(new CrmProjectLeaderService.ProjectLeaderResult(
                        "负责人", "L001", "西域测试商机", "CC2026070932"));

        String code = resolver.resolve("22", USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(22L, USERNAME);
    }

    @Test
    @DisplayName("纯数字 id 反查失败 → 降级返回原数字 id")
    void numericId_lookupFails_returnsRawId() {
        when(crmProjectLeaderService.findProjectLeaderByChanceId(22L, USERNAME))
                .thenThrow(new RuntimeException("token unavailable"));

        String code = resolver().resolve("22", USERNAME);

        assertThat(code).isEqualTo("22");
    }

    @Test
    @DisplayName("resolve(crmOpportunityId) 无 username 兼容旧调用")
    void legacyResolve_withoutUsername_delegates() {
        when(crmProjectLeaderService.findProjectLeaderByChanceId(22L, null))
                .thenReturn(new CrmProjectLeaderService.ProjectLeaderResult(
                        "负责人", "L001", "西域测试商机", "CC2026070932"));

        String code = resolver().resolve("22");

        assertThat(code).isEqualTo("CC2026070932");
    }

    @Test
    @DisplayName("tender.crm_opportunity_id 为空但 externalId=CRM:sourceId → 用 sourceId 兜底反查")
    void resolveFromTender_emptyCrmId_withCrmExternalId_looksUpBySourceId() {
        Tender tender = new Tender();
        tender.setId(24L);
        tender.setExternalId("CRM:17");
        tender.setCrmOpportunityId(null);

        when(crmProjectLeaderService.findProjectLeaderByChanceId(17L, USERNAME))
                .thenReturn(new CrmProjectLeaderService.ProjectLeaderResult(
                        "负责人", "L001", "0710生产测试12", "CC2026070932"));

        String code = resolver().resolveFromTender(tender, USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
    }

    @Test
    @DisplayName("tender.crm_opportunity_id 非空 → 优先使用，不读 externalId")
    void resolveFromTender_crmIdPresent_usesCrmId() {
        Tender tender = new Tender();
        tender.setId(24L);
        tender.setExternalId("CRM:17");
        tender.setCrmOpportunityId("CC2026070932");

        String code = resolver().resolveFromTender(tender, USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    @DisplayName("tender.crm_opportunity_id 为空且 externalId 不是 CRM: → 返回空字符串")
    void resolveFromTender_noCrmExternalId_returnsEmpty() {
        Tender tender = new Tender();
        tender.setId(24L);
        tender.setExternalId("EHSY:123");
        tender.setCrmOpportunityId(null);

        String code = resolver().resolveFromTender(tender, USERNAME);

        assertThat(code).isEmpty();
    }

    @Test
    @DisplayName("externalId 的 sourceId 不是纯数字 → 返回空字符串")
    void resolveFromTender_externalIdNotNumeric_returnsEmpty() {
        Tender tender = new Tender();
        tender.setId(24L);
        tender.setExternalId("CRM:CC2026070932");
        tender.setCrmOpportunityId(null);

        String code = resolver().resolveFromTender(tender, USERNAME);

        assertThat(code).isEmpty();
    }
}
