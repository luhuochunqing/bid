package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * CrmCompanySearchService 单元测试.
 *
 * <p>覆盖 CO-302 反查路径第一步：按招标主体名称查询 CRM 公司，精确匹配优先。
 */
@ExtendWith(MockitoExtension.class)
class CrmCompanySearchServiceTest {

    @Mock
    private CrmHttpClient httpClient;
    @Mock
    private CrmAuthService authService;
    @Mock
    private CrmProperties properties;

    private CrmCompanySearchService service;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CrmCompanySearchService(httpClient, authService, properties);
        // 用 lenient：null/blank 短路用例不会触达这些 stub
        lenient().when(authService.getValidTokenForUser(any())).thenReturn("fake-token");
        lenient().when(properties.getEffectiveCacBaseUrl()).thenReturn("https://cac-test.ehsy.com");
        lenient().when(properties.getCustomer()).thenReturn(new CrmProperties.CrmCustomerPaths());
    }

    @Test
    @DisplayName("模糊查询命中多条_第一条与输入精确匹配_返回第一条")
    void searchByName_MultipleHitsWithExactFirstMatch_ShouldReturnFirst() throws Exception {
        String responseJson = """
            {
              "code": 0,
              "msg": "success",
              "totalCount": 2,
              "dataList": [
                {"id": 100, "name": "上海西域有限公司", "groupName": "西域集团", "isAccurate": "1", "virtualFlag": "0"},
                {"id": 200, "name": "上海西域分公司", "groupName": "西域集团", "isAccurate": "0", "virtualFlag": "0"}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CompanySearchResult> result = service.searchByName("上海西域有限公司", "testuser");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(100L);
        assertThat(result.get().name()).isEqualTo("上海西域有限公司");
        assertThat(result.get().groupName()).isEqualTo("西域集团");
    }

    @Test
    @DisplayName("模糊查询命中多条_无精确匹配_返回empty（严格精确匹配）")
    void searchByName_MultipleHitsNoExactMatch_ShouldReturnEmpty() throws Exception {
        String responseJson = """
            {
              "code": 0,
              "msg": "success",
              "totalCount": 2,
              "dataList": [
                {"id": 100, "name": "上海西域分公司", "groupName": "西域集团", "isAccurate": "0", "virtualFlag": "0"},
                {"id": 200, "name": "上海西域子公司", "groupName": "西域集团", "isAccurate": "0", "virtualFlag": "0"}
              ]
            }
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CompanySearchResult> result = service.searchByName("上海西域有限公司", "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("模糊查询命中0条_返回empty")
    void searchByName_NoHit_ShouldReturnEmpty() throws Exception {
        String responseJson = """
            {"code": 0, "msg": "success", "totalCount": 0, "dataList": []}
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CompanySearchResult> result = service.searchByName("不存在的公司", "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("name为空_返回empty_不调HTTP")
    void searchByName_BlankName_ShouldReturnEmptyWithoutHttpCall() {
        Optional<CompanySearchResult> result = service.searchByName("", "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("name为null_返回empty")
    void searchByName_NullName_ShouldReturnEmpty() {
        Optional<CompanySearchResult> result = service.searchByName(null, "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("code=0但dataList为null_返回empty")
    void searchByName_CodeOkButDataListNull_ShouldReturnEmpty() throws Exception {
        String responseJson = """
            {"code": 0, "msg": "success", "totalCount": 0, "dataList": null}
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CompanySearchResult> result = service.searchByName("某公司", "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("接口异常（parseError）_返回empty_不抛")
    void searchByName_HttpError_ShouldReturnEmpty() {
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.CrmApiResponse.parseError("connection timeout"));

        Optional<CompanySearchResult> result = service.searchByName("某公司", "testuser");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("code非0_返回empty")
    void searchByName_NonZeroCode_ShouldReturnEmpty() throws Exception {
        String responseJson = """
            {"code": 500, "msg": "internal error", "totalCount": 0, "dataList": []}
            """;
        when(httpClient.post(anyString(), anyString(), anyString(), any()))
                .thenReturn(CrmResponseHandler.parse(responseJson));

        Optional<CompanySearchResult> result = service.searchByName("某公司", "testuser");

        assertThat(result).isEmpty();
    }
}
