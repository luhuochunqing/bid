package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ExternalSystemPrefix;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenderCrmOpportunityCodeResolver 单元测试。
 * <p>覆盖：优先用 crm_opportunity_id、externalId CRM:sourceId 兜底、非数字 sourceId、非 CRM 前缀。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderCrmOpportunityCodeResolver — 标讯 CRM 商机编号解析")
class TenderCrmOpportunityCodeResolverTest {

    private static final String USERNAME = "06234";

    @Mock
    private CrmOpportunityCodeResolver crmOpportunityCodeResolver;

    private TenderCrmOpportunityCodeResolver resolver() {
        return new TenderCrmOpportunityCodeResolver(crmOpportunityCodeResolver);
    }

    @Test
    @DisplayName("crm_opportunity_id 非空 → 直接委托基础设施层解析")
    void crmOpportunityIdPresent_delegatesToInfrastructure() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId("CC2026070932");
        when(crmOpportunityCodeResolver.resolve("CC2026070932", USERNAME)).thenReturn("CC2026070932");

        String code = resolver().resolveForTender(tender, USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
        verify(crmOpportunityCodeResolver).resolve("CC2026070932", USERNAME);
    }

    @Test
    @DisplayName("crm_opportunity_id 为空但 externalId=CRM:sourceId → 用 sourceId 兜底反查")
    void emptyCrmId_withCrmExternalId_looksUpBySourceId() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId(null);
        tender.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("17"));
        when(crmOpportunityCodeResolver.resolve("17", USERNAME)).thenReturn("CC2026070932");

        String code = resolver().resolveForTender(tender, USERNAME);

        assertThat(code).isEqualTo("CC2026070932");
        verify(crmOpportunityCodeResolver).resolve("17", USERNAME);
    }

    @Test
    @DisplayName("crm_opportunity_id 非空时，即使 externalId 是 CRM: 也不读 externalId")
    void crmIdPresent_ignoresExternalId() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId("CC2026070932");
        tender.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("17"));
        when(crmOpportunityCodeResolver.resolve("CC2026070932", USERNAME)).thenReturn("CC2026070932");

        resolver().resolveForTender(tender, USERNAME);

        verify(crmOpportunityCodeResolver, never()).resolve(eq("17"), any());
    }

    @Test
    @DisplayName("externalId 不是 CRM: 前缀 → 返回空字符串")
    void nonCrmExternalId_returnsEmpty() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId(null);
        tender.setExternalId("EHSY:123");

        String code = resolver().resolveForTender(tender, USERNAME);

        assertThat(code).isEmpty();
        verify(crmOpportunityCodeResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("externalId 的 sourceId 不是纯数字 → 返回空字符串")
    void crmExternalIdNonNumeric_returnsEmpty() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId(null);
        tender.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("CC2026070932"));

        String code = resolver().resolveForTender(tender, USERNAME);

        assertThat(code).isEmpty();
        verify(crmOpportunityCodeResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("tender 为 null → 返回空字符串")
    void nullTender_returnsEmpty() {
        assertThat(resolver().resolveForTender(null, USERNAME)).isEmpty();
    }

    @Test
    @DisplayName("无 crm_opportunity_id 且无 externalId → 返回空字符串")
    void noCrmIdNoExternalId_returnsEmpty() {
        Tender tender = new Tender();
        tender.setCrmOpportunityId(null);
        tender.setExternalId(null);

        String code = resolver().resolveForTender(tender, USERNAME);

        assertThat(code).isEmpty();
        verify(crmOpportunityCodeResolver, never()).resolve(any(), any());
    }
}
