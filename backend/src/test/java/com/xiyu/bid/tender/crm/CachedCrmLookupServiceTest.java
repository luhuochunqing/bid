package com.xiyu.bid.tender.crm;

import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CustomerManagerResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 031 R-007：批次内 CRM 缓存 — 同 key 只查一次，empty 也缓存。
 */
@ExtendWith(MockitoExtension.class)
class CachedCrmLookupServiceTest {

    @Mock
    private CrmCompanySearchService companySearchService;

    @Mock
    private CrmCustomerManagerLookupService customerManagerLookupService;

    private CachedCrmLookupService service;

    @BeforeEach
    void setUp() {
        service = new CachedCrmLookupService(companySearchService, customerManagerLookupService);
    }

    @AfterEach
    void tearDown() {
        service.closeBatch();
    }

    @Test
    @DisplayName("无 openBatch：透传底层，每次都调 CRM")
    void withoutBatch_passesThroughEachTime() {
        when(companySearchService.searchByName("甲公司", "u1"))
                .thenReturn(Optional.of(new CompanySearchResult(1L, "甲公司", "G")));

        service.searchByName("甲公司", "u1");
        service.searchByName("甲公司", "u1");

        verify(companySearchService, times(2)).searchByName("甲公司", "u1");
        assertThat(service.isBatchOpen()).isFalse();
    }

    @Test
    @DisplayName("openBatch：相同公司名只查一次 CRM")
    void withBatch_sameNameHitsOnce() {
        when(companySearchService.searchByName("甲公司", "u1"))
                .thenReturn(Optional.of(new CompanySearchResult(1L, "甲公司", "G")));

        service.openBatch();
        Optional<CompanySearchResult> a = service.searchByName("甲公司", "u1");
        Optional<CompanySearchResult> b = service.searchByName("甲公司", "u1");

        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.get().id()).isEqualTo(1L);
        verify(companySearchService, times(1)).searchByName("甲公司", "u1");
    }

    @Test
    @DisplayName("openBatch：empty 结果也缓存，不重复打空查询")
    void withBatch_emptyIsCached() {
        when(companySearchService.searchByName("不存在", "u1"))
                .thenReturn(Optional.empty());

        service.openBatch();
        assertThat(service.searchByName("不存在", "u1")).isEmpty();
        assertThat(service.searchByName("不存在", "u1")).isEmpty();

        verify(companySearchService, times(1)).searchByName("不存在", "u1");
    }

    @Test
    @DisplayName("openBatch：相同 companyId 的经理查询只打一次")
    void withBatch_managerByCompanyIdHitsOnce() {
        when(customerManagerLookupService.findByCompanyId(10L, "u1"))
                .thenReturn(Optional.of(new CustomerManagerResult("01097", 19, "集团项目经理")));

        service.openBatch();
        service.findByCompanyId(10L, "u1");
        service.findByCompanyId(10L, "u1");

        verify(customerManagerLookupService, times(1)).findByCompanyId(10L, "u1");
    }

    @Test
    @DisplayName("closeBatch 后恢复透传")
    void afterClose_passesThroughAgain() {
        when(companySearchService.searchByName("甲公司", "u1"))
                .thenReturn(Optional.of(new CompanySearchResult(1L, "甲公司", "G")));

        service.openBatch();
        service.searchByName("甲公司", "u1");
        service.closeBatch();
        service.searchByName("甲公司", "u1");

        verify(companySearchService, times(2)).searchByName("甲公司", "u1");
    }
}
