package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CrmCustomerManagerLookupService 单元测试.
 *
 * <p>覆盖 CO-302 反查路径第二步：按 companyId 查询客户负责人列表。
 * 注意：接口 25259 返回的 code 是 string 类型 "0"，与 25338 的 integer 0 不同。
 */
@ExtendWith(MockitoExtension.class)
class CrmCustomerManagerLookupServiceTest {

    @Mock
    private CrmHttpClient httpClient;
    @Mock
    private CrmAuthService authService;
    @Mock
    private CrmProperties properties;

    private CrmCustomerManagerLookupService service;

    @BeforeEach
    void setUp() {
        service = new CrmCustomerManagerLookupService(httpClient, authService, properties);
        // 用 lenient：null companyId 短路用例不会触达这些 stub
        lenient().when(authService.getValidTokenForCaller(any())).thenReturn("fake-token");
        lenient().when(properties.getEffectiveCacBaseUrl()).thenReturn("https://cac-test.ehsy.com");
        lenient().when(properties.getCustomer()).thenReturn(new CrmProperties.CrmCustomerPaths());
    }

    @Test
    @DisplayName("code='0'(string)_查到1个集团项目经理(saleType=19)_返回该负责人")
    void findByCompanyId_StringCodeOk_OneGroupProjectManager_ShouldReturn() {
        String responseJson = """
            {
              "code": "0",
              "msg": "查询成功",
              "totalCount": 1,
              "dataList": [
                {"id": 63, "companyId": 81417644, "saleNo": "01097", "saleType": 19, "saleTypeText": "集团项目经理"}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isPresent();
        assertThat(result.get().saleNo()).isEqualTo("01097");
        assertThat(result.get().saleType()).isEqualTo(19);
        assertThat(result.get().saleTypeText()).isEqualTo("集团项目经理");
    }

    @Test
    @DisplayName("查到多个负责人_仅返回集团项目经理(saleType=19)_跳过其他角色")
    void findByCompanyId_MultipleManagers_ShouldReturnOnlyGroupProjectManager() {
        String responseJson = """
            {
              "code": "0",
              "msg": "查询成功",
              "totalCount": 3,
              "dataList": [
                {"id": 63, "companyId": 81417644, "saleNo": "01097", "saleType": 2, "saleTypeText": "对账开票专员"},
                {"id": 96, "companyId": 81417644, "saleNo": "01989", "saleType": 19, "saleTypeText": "集团项目经理"},
                {"id": 98, "companyId": 81417644, "saleNo": "02180", "saleType": 20, "saleTypeText": null}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isPresent();
        assertThat(result.get().saleNo()).isEqualTo("01989");
        assertThat(result.get().saleType()).isEqualTo(19);
    }

    @Test
    @DisplayName("无集团项目经理(saleType=19)_返回empty_不fallback到其他角色")
    void findByCompanyId_NoGroupProjectManager_ShouldReturnEmptyWithoutFallback() {
        String responseJson = """
            {
              "code": "0",
              "msg": "查询成功",
              "totalCount": 2,
              "dataList": [
                {"id": 63, "companyId": 81417644, "saleNo": "01097", "saleType": 2, "saleTypeText": "对账开票专员"},
                {"id": 96, "companyId": 81417644, "saleNo": "01989", "saleType": 16, "saleTypeText": "百大项目负责人"}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查到0条_返回empty")
    void findByCompanyId_NoManager_ShouldReturnEmpty() {
        String responseJson = """
            {"code": "0", "msg": "查询成功", "totalCount": 0, "dataList": []}
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("companyId为null_返回empty_不调HTTP")
    void findByCompanyId_NullCompanyId_ShouldReturnEmptyWithoutHttpCall() {
        Optional<CustomerManagerResult> result = service.findByCompanyId(null, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("code='0'(string)_dataList为null_返回empty")
    void findByCompanyId_CodeOkButDataListNull_ShouldReturnEmpty() {
        String responseJson = """
            {"code": "0", "msg": "查询成功", "totalCount": 0, "dataList": null}
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("接口异常_返回empty_不抛")
    void findByCompanyId_HttpError_ShouldReturnEmpty() {
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.CrmApiResponse.parseError("connection timeout"));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("集团项目经理(saleType=19)无saleNo_跳过_取下一个同角色的")
    void findByCompanyId_GroupProjectManagerNoSaleNo_ShouldSkipToNext() {
        String responseJson = """
            {
              "code": "0",
              "msg": "查询成功",
              "totalCount": 2,
              "dataList": [
                {"id": 63, "companyId": 81417644, "saleNo": null, "saleType": 19, "saleTypeText": "集团项目经理"},
                {"id": 96, "companyId": 81417644, "saleNo": "01989", "saleType": 19, "saleTypeText": "集团项目经理"}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CustomerManagerResult> result = service.findByCompanyId(81417644L, "testuser");

        assertThat(result).isPresent();
        assertThat(result.get().saleNo()).isEqualTo("01989");
    }
}
