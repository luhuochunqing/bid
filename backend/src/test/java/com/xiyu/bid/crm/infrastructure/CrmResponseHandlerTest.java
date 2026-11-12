package com.xiyu.bid.crm.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSS 统一认证响应体解析测试：兼容 msg 与 message 字段。
 */
class CrmResponseHandlerTest {

    @Test
    void parse_shouldReadMessageFieldUsedByOssLogin() {
        CrmResponseHandler.CrmApiResponse response = CrmResponseHandler.parse(
                "{\"code\":501,\"message\":\"没有权限访问该系统\",\"success\":false}");

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo(501);
        assertThat(response.msg()).isEqualTo("没有权限访问该系统");
    }

    @Test
    void parse_shouldKeepMsgFieldForLegacyCrmApis() {
        CrmResponseHandler.CrmApiResponse response = CrmResponseHandler.parse(
                "{\"code\":0,\"msg\":\"ok\",\"data\":{}}");

        assertThat(response.success()).isTrue();
        assertThat(response.msg()).isEqualTo("ok");
    }
}
