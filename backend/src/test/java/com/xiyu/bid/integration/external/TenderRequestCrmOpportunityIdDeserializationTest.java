package com.xiyu.bid.integration.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-276: 验证 TenderUpdateRequest / TenderPushRequest 能正确反序列化 CRM 文档字段名
 * crmOpportunityId / crmOpportunityName。
 *
 * <p>根因：CRM 通过 PUT/POST 推送时使用 crmOpportunityId 字段名，但代码 DTO 历史字段名为 crmId，
 * Jackson 反序列化时未知字段被丢弃（Spring Boot 默认 FAIL_ON_UNKNOWN_PROPERTIES=false），
 * 导致商机编号丢失、商机未关联、放弃/中标状态无法回传 CRM（tender 273 案例）。
 *
 * <p>修复：DTO 新增 crmOpportunityId / crmOpportunityName 公开别名字段，业务侧用
 * firstNonBlank(crmOpportunityId, crmId) 合并取值。
 */
class TenderRequestCrmOpportunityIdDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("TenderUpdateRequest: 仅传 crmOpportunityId 应被正确反序列化")
    void tenderUpdateRequest_crmOpportunityId_deserialized() throws Exception {
        String json = "{\"crmOpportunityId\":\"CC20260619283\",\"crmOpportunityName\":\"测试商机\"}";

        TenderUpdateRequest request = mapper.readValue(json, TenderUpdateRequest.class);

        assertThat(request.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(request.getCrmOpportunityName()).isEqualTo("测试商机");
        // 历史字段 crmId 应为 null（CRM 未传该字段名）
        assertThat(request.getCrmId()).isNull();
    }

    @Test
    @DisplayName("TenderUpdateRequest: 兼容历史字段名 crmId")
    void tenderUpdateRequest_legacyCrmId_stillSupported() throws Exception {
        String json = "{\"crmId\":\"CC-LEGACY\"}";

        TenderUpdateRequest request = mapper.readValue(json, TenderUpdateRequest.class);

        assertThat(request.getCrmId()).isEqualTo("CC-LEGACY");
        assertThat(request.getCrmOpportunityId()).isNull();
    }

    @Test
    @DisplayName("TenderPushRequest: 仅传 crmOpportunityId 应被正确反序列化")
    void tenderPushRequest_crmOpportunityId_deserialized() throws Exception {
        String json = "{\"sourceSystem\":\"CRM\",\"sourceId\":\"243\","
                + "\"crmOpportunityId\":\"CC20260619283\",\"crmOpportunityName\":\"测试商机\"}";

        TenderPushRequest request = mapper.readValue(json, TenderPushRequest.class);

        assertThat(request.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(request.getCrmOpportunityName()).isEqualTo("测试商机");
        assertThat(request.getCrmId()).isNull();
    }
}
