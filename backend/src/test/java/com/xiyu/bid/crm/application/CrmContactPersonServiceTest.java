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

    // ---- CO-329 遗留：客户信息矩阵仍未带入的兜底覆盖 ----
    // 文档契约（SingleResponseList）对接人数组在 data，但实际环境/版本可能用 list/rows/嵌套；
    // 且部分环境成功返回时 code 非 0。下列场景此前都会把"已返回的对接人"解析成空 → 矩阵空。

    @Test
    void pageList_parsesListField() {
        // 数组在 list 字段（非 data/dataList），常见分页命名。
        String body = "{\"code\":0,\"list\":[{\"id\":4,\"name\":\"赵六\"}]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("赵六");
    }

    @Test
    void pageList_parsesRowsField() {
        String body = "{\"code\":0,\"rows\":[{\"id\":5,\"name\":\"孙七\"}]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("孙七");
    }

    @Test
    void pageList_parsesNestedDataList() {
        // 嵌套结构 {code, data:{list:[...]}}。
        String body = "{\"code\":0,\"data\":{\"list\":[{\"id\":6,\"name\":\"周八\"}]}}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("周八");
    }

    @Test
    void pageList_returnsDataEvenWhenCodeNotZero() {
        // 放宽 success：部分 CRM 环境对接人接口成功返回时 code 非 0（如 200），但 data 已带对接人。
        // 死卡 code==0 会把已返回的对接人误判失败 → 客户信息矩阵带不过来。
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":[{\"id\":7,\"name\":\"吴九\"}]}";

        List<ContactPersonInfoVO> result = serviceWith(body).pageList(21045L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("吴九");
    }
}
