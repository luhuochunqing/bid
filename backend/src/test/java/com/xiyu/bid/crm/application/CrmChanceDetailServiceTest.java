package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmChanceDetailService} 单元测试。
 * <p>覆盖 CO-275：按商机主键 id 查询商机详情（detail 接口）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmChanceDetailServiceTest {

    @Mock
    private CrmHttpClient httpClient;

    @Mock
    private CrmAuthService authService;

    private CrmProperties properties;

    private CrmChanceDetailService service;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        properties.setBaseUrl("http://crm.example.com");
        service = new CrmChanceDetailService(httpClient, authService, properties);
        when(authService.getValidToken()).thenReturn("token");
    }

    @Test
    void getDetailById_nullId_returnsNullNoCall() {
        assertThat(service.getDetailById(null)).isNull();
        verify(httpClient, never()).post(any(), any(), any(), any());
    }

    @Test
    void getDetailById_success_returnsVo() {
        // CRM detail 接口返回 SingleResponse 结构：{"code":0,"data":{...}}
        String body = "{\"code\":0,\"data\":{\"id\":243,\"code\":\"CC20260619283\","
                + "\"name\":\"商机A\",\"projectLeaderName\":\"张三\",\"projectLeaderNo\":\"EMP001\"}}";
        when(httpClient.post(any(), any(), eq("token"), isNull()))
                .thenReturn(CrmResponseHandler.parse(body));

        CustomerChanceVO vo = service.getDetailById(243L);

        assertThat(vo).isNotNull();
        assertThat(vo.id()).isEqualTo(243L);
        assertThat(vo.code()).isEqualTo("CC20260619283");
        assertThat(vo.name()).isEqualTo("商机A");
        // 验证 path 拼接了 ?id=243
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(any(), pathCaptor.capture(), eq("token"), isNull());
        assertThat(pathCaptor.getValue()).isEqualTo("/customer-chance/detail?id=243");
    }

    @Test
    void getDetailById_crmFailure_returnsNull() {
        when(httpClient.post(any(), any(), eq("token"), isNull()))
                .thenReturn(CrmResponseHandler.parse("{\"code\":1,\"msg\":\"商机不存在\"}"));

        assertThat(service.getDetailById(999L)).isNull();
    }
}
