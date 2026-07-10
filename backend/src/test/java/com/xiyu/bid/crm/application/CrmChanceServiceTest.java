package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmChanceServiceTest {

    private static final String TEST_USERNAME = "testUser";

    @Mock
    private CrmHttpClient httpClient;

    @Mock
    private CrmAuthService authService;

    private CrmProperties properties;

    private CrmChanceService service;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setBaseUrl("http://crm.example.com");
        service = new CrmChanceService(httpClient, authService, properties);
        when(authService.getValidTokenForUser(anyString())).thenReturn("token");
    }

    @Test
    void pageList_tokenAcquisitionFailure_returnsEmptyResult() {
        when(authService.getValidTokenForUser(anyString())).thenThrow(new IllegalStateException("OSS applyToken failed"));
        CustomerChancePageRequest request = new CustomerChancePageRequest(1, 10, selectAllBody());

        CrmChancePageResult result = service.pageList(request, TEST_USERNAME);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.pageSize()).isZero();
        assertThat(result.pageIndex()).isZero();
        verify(httpClient, never()).post(any(), any(), any(), any(CustomerChancePageRequest.class));
    }

    @Test
    void searchByTender_tokenAcquisitionFailure_returnsEmptyResult() {
        when(authService.getValidTokenForUser(anyString())).thenThrow(new IllegalStateException("CRM generateToken failed"));
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "山东海化集团有限公司", "2026-06-03 23:59:00", "2026-06-04 23:59:00", 1, 10);

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.pageSize()).isZero();
        assertThat(result.pageIndex()).isZero();
        verify(httpClient, never()).post(any(), any(), any(), any(CustomerChancePageRequest.class));
    }

    @Test
    void pageList_unauthorizedThenTokenRefreshFailure_returnsEmptyResult() {
        when(authService.getValidTokenForUser(anyString()))
                .thenReturn("expired-token")
                .thenThrow(new IllegalStateException("CRM generateToken failed"));
        when(httpClient.post(any(), any(), eq("expired-token"), any(CustomerChancePageRequest.class)))
                .thenReturn(unauthorizedCrmResponse());
        CustomerChancePageRequest request = new CustomerChancePageRequest(1, 10, selectAllBody());

        CrmChancePageResult result = service.pageList(request, TEST_USERNAME);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.pageSize()).isZero();
        assertThat(result.pageIndex()).isZero();
        verify(authService).handleUnauthorizedForUser(TEST_USERNAME);
        verify(httpClient, times(1)).post(any(), any(), eq("expired-token"), any(CustomerChancePageRequest.class));
    }

    @Test
    void searchByTender_unauthorizedThenTokenRefreshFailure_returnsEmptyResult() {
        when(authService.getValidTokenForUser(anyString()))
                .thenReturn("expired-token")
                .thenThrow(new IllegalStateException("CRM generateToken failed"));
        when(httpClient.post(any(), any(), eq("expired-token"), any(CustomerChancePageRequest.class)))
                .thenReturn(unauthorizedCrmResponse());
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "山东海化集团有限公司", "2026-06-03 23:59:00", "2026-06-04 23:59:00", 1, 10);

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.pageSize()).isZero();
        assertThat(result.pageIndex()).isZero();
        verify(authService).handleUnauthorizedForUser(TEST_USERNAME);
        verify(httpClient, times(1)).post(any(), any(), eq("expired-token"), any(CustomerChancePageRequest.class));
    }

    @Test
    void searchByTender_withoutTenderer_defaultsToSelectAll() {
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(emptyCrmResponse());

        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "", "2026-08-08 23:59:00", "2026-09-18 23:59:00", 1, 10);

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isZero();
        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(1)).post(any(), any(), eq("token"), captor.capture());
        assertThat(captor.getValue().body().selectAll()).isTrue();
    }

    @Test
    void searchByTender_groupStrategy_queriesGroupNameAndFallsBackToAll() {
        String tenderer = "山东海化集团有限公司";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, "2026-06-03 23:59:00", "2026-06-04 23:59:00", 1, 10);

        String groupEmptyBody = "{\"code\":0,\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1,\"dataList\":[]}";
        String allBody = """
                {"code":0,"totalCount":1,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":2,"code":"CC2","name":"兜底商机","groupName":"其他集团"}\
                ]}
                """;
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(groupEmptyBody))
                .thenReturn(CrmResponseHandler.parse(allBody));

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).code()).isEqualTo("CC2");

        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(2)).post(any(), any(), eq("token"), captor.capture());
        assertThat(captor.getAllValues().get(0).body().groupName()).containsExactly(tenderer);
        assertThat(captor.getAllValues().get(1).body().selectAll()).isTrue();
    }

    @Test
    void searchByTender_exactStrategy_queriesEvaluationDatesAndFallsBackToGroupThenAll() {
        properties.setMatchingStrategy(CrmProperties.MatchingStrategy.EXACT);
        String tenderer = "山东海化集团有限公司";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, "2026-06-03 23:59:00", "2026-06-04 23:59:00", 1, 10);

        String emptyBody = "{\"code\":0,\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1,\"dataList\":[]}";
        String groupBody = """
                {"code":0,"totalCount":1,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":3,"code":"CC3","name":"集团商机","groupName":"山东海化集团有限公司"}\
                ]}
                """;
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(emptyBody))
                .thenReturn(CrmResponseHandler.parse(emptyBody))
                .thenReturn(CrmResponseHandler.parse(groupBody));

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).code()).isEqualTo("CC3");

        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(3)).post(any(), any(), eq("token"), captor.capture());
        // 第一次/第二次按日期精确查询
        assertThat(captor.getAllValues().get(0).body().evaluationStartTime()).startsWith("2026-06-03");
        assertThat(captor.getAllValues().get(1).body().evaluationStartTime()).startsWith("2026-06-04");
        // 第三次按 groupName 兜底
        assertThat(captor.getAllValues().get(2).body().groupName()).containsExactly(tenderer);
    }

    @Test
    void searchByTender_exactStrategy_deduplicatesById() {
        properties.setMatchingStrategy(CrmProperties.MatchingStrategy.EXACT);
        String tenderer = "山东海化集团有限公司";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, "2026-06-03 23:59:00", "2026-06-03 23:59:00", 1, 10);

        String responseBody = """
                {"code":0,"totalCount":2,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":1,"code":"CC1","name":"商机1","groupName":"山东海化集团有限公司","evaluationTime":"2026-06-03"},\
                {"id":1,"code":"CC1","name":"商机1","groupName":"山东海化集团有限公司","evaluationTime":"2026-06-03"}\
                ]}
                """;
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(responseBody));

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).hasSize(1);
        verify(httpClient, times(1)).post(any(), any(), any(), any(CustomerChancePageRequest.class));
    }

    @Test
    void searchByTender_allStrategy_returnsSelectAll() {
        properties.setMatchingStrategy(CrmProperties.MatchingStrategy.ALL);
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                "山东海化集团有限公司", "2026-06-03 23:59:00", "2026-06-04 23:59:00", 1, 10);

        String allBody = """
                {"code":0,"totalCount":1,"pageSize":10,"pageIndex":1,"dataList":[\
                {"id":4,"code":"CC4","name":"全量商机","groupName":"任意集团"}\
                ]}
                """;
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(allBody));

        CrmChancePageResult result = service.searchByTender(request, TEST_USERNAME);

        assertThat(result.list()).hasSize(1);
        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(1)).post(any(), any(), eq("token"), captor.capture());
        assertThat(captor.getValue().body().selectAll()).isTrue();
    }

    @Test
    void searchByTender_parsesIsoDateWithOffset() {
        properties.setMatchingStrategy(CrmProperties.MatchingStrategy.EXACT);
        String tenderer = "山东海化集团有限公司";
        CustomerChanceSearchByTenderRequest request = new CustomerChanceSearchByTenderRequest(
                tenderer, "2026-06-03T23:59:00+08:00", "2026-06-04T10:00:00.000Z", 1, 10);

        String emptyBody = "{\"code\":0,\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1,\"dataList\":[]}";
        String groupBody = "{\"code\":0,\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1,\"dataList\":[]}";
        String allBody = "{\"code\":0,\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1,\"dataList\":[]}";
        when(httpClient.post(any(), any(), any(), any(CustomerChancePageRequest.class)))
                .thenReturn(CrmResponseHandler.parse(emptyBody))
                .thenReturn(CrmResponseHandler.parse(emptyBody))
                .thenReturn(CrmResponseHandler.parse(groupBody))
                .thenReturn(CrmResponseHandler.parse(allBody));

        service.searchByTender(request, TEST_USERNAME);

        ArgumentCaptor<CustomerChancePageRequest> captor = ArgumentCaptor.forClass(CustomerChancePageRequest.class);
        verify(httpClient, times(4)).post(any(), any(), eq("token"), captor.capture());
        // 2026-06-03T23:59:00+08:00 对应 UTC 2026-06-03 15:59，日期部分仍是 2026-06-03
        assertThat(captor.getAllValues().get(0).body().evaluationStartTime()).startsWith("2026-06-03");
        // 2026-06-04T10:00:00.000Z 对应 UTC 2026-06-04 10:00，日期部分是 2026-06-04
        assertThat(captor.getAllValues().get(1).body().evaluationStartTime()).startsWith("2026-06-04");
    }


    private CrmResponseHandler.CrmApiResponse unauthorizedCrmResponse() {
        return CrmResponseHandler.parse("{\"code\":401,\"msg\":\"unauthorized\",\"data\":null}");
    }

    private CrmResponseHandler.CrmApiResponse emptyCrmResponse() {
        return CrmResponseHandler.parse("{\"code\":200,\"msg\":\"success\",\"data\":{\"list\":[],\"totalCount\":0,\"pageSize\":10,\"pageIndex\":1}}");
    }

    private CustomerChanceDTO selectAllBody() {
        return new CustomerChanceDTO(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, null, null, null);
    }
}
