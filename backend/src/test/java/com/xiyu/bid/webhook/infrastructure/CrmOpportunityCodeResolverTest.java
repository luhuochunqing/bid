package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
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
 * <p>覆盖：code 原样返回、纯数字 id 反查 code、username 透传、反查失败不降级为数字 id。
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
    @DisplayName("纯数字 id 反查失败 → 返回空字符串，避免 CRM 伪成功")
    void numericId_lookupFails_returnsEmpty() {
        when(crmProjectLeaderService.findProjectLeaderByChanceId(22L, USERNAME))
                .thenThrow(new RuntimeException("token unavailable"));

        String code = resolver().resolve("22", USERNAME);

        assertThat(code).isEmpty();
    }

    @Test
    @DisplayName("CRM 返回空 code → 返回空字符串")
    void numericId_lookupReturnsBlankCode_returnsEmpty() {
        when(crmProjectLeaderService.findProjectLeaderByChanceId(22L, USERNAME))
                .thenReturn(new CrmProjectLeaderService.ProjectLeaderResult(
                        "负责人", "L001", "西域测试商机", ""));

        String code = resolver().resolve("22", USERNAME);

        assertThat(code).isEmpty();
    }
}
