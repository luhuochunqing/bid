package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler.CrmApiResponse;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CRM 对接人查询解析测试。
 * <p>核心覆盖：CRM 对接人 page-list 响应为扁平结构 {@code {code,totalCount,dataList:[...]}}（无外层 data），
 * {@code parseListResponse} 必须从 {@code dataList} 取数组，而非仅判 {@code data.isArray()}。
 */
@ExtendWith(MockitoExtension.class)
class CrmContactPersonServiceTest {

    @Mock CrmHttpClient httpClient;
    @Mock CrmAuthService authService;
    @Mock CrmProperties properties;
    @Mock CrmProperties.CrmContactPersonPaths contactPersonPaths;

    private CrmContactPersonService serviceWith(String crmBody) {
        when(authService.getValidToken()).thenReturn("token");
        when(properties.getEffectiveContactPersonBaseUrl()).thenReturn("http://crm");
        when(properties.getContactPerson()).thenReturn(contactPersonPaths);
        when(contactPersonPaths.getPageListPath()).thenReturn("/contact-person-info/page-list");
        CrmApiResponse response = CrmResponseHandler.parse(crmBody);
        when(httpClient.post(anyString(), anyString(), anyString(), any())).thenReturn(response);
        return new CrmContactPersonService(httpClient, authService, properties);
    }

    @Test
    void pageList_extractsDataListFromFlatCrmResponse() {
        // CRM 实际响应：扁平结构，对接人在 dataList 字段（非直接数组）——此前解析成空，本次修复点。
        String body = "{\"code\":0,\"totalCount\":2,\"dataList\":[" +
                "{\"id\":1,\"name\":\"张三\",\"phone\":\"13800000000\",\"contactMethod\":\"电话\",\"preferenceLevel\":\"支持\"}," +
                "{\"id\":2,\"name\":\"李四\",\"phone\":\"13900000000\"}]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("张三");
        assertThat(result.get(0).phone()).isEqualTo("13800000000");
        assertThat(result.get(0).contactMethod()).isEqualTo("电话");
        assertThat(result.get(1).name()).isEqualTo("李四");
    }

    @Test
    void pageList_handlesDirectArrayBackwardCompat() {
        // 兼容：若 CRM 某天返回 data 直接数组，仍可解析。
        String body = "{\"code\":0,\"data\":[{\"id\":3,\"name\":\"王五\"}]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("王五");
    }

    @Test
    void pageList_returnsEmptyWhenNoDataList() {
        String body = "{\"code\":0,\"totalCount\":0,\"dataList\":[]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).isEmpty();
    }
}
