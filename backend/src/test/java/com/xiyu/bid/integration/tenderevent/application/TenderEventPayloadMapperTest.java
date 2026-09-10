package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderEventPayloadMapper - 事件消息体 data 序列化")
class TenderEventPayloadMapperTest {

    private TenderEventPayloadMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TenderEventPayloadMapper(new ObjectMapper());
    }

    /** 复现 tenderId=4173：仅 tenderId、externalId、title 有值。 */
    private TenderEventPayload minimal(String externalId) {
        return new TenderEventPayload(4173L, externalId, "SDK事件联调-PR2603-自动注册-20260910T083236",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** 全字段填充（验证中文映射 + 时间格式化 + 联系人性质）。 */
    private TenderEventPayload full() {
        return new TenderEventPayload(
                123L, "ext-1", "测试标讯", "招标主体X", "上海",
                LocalDateTime.of(2026, 9, 10, 17, 0),
                LocalDateTime.of(2026, 9, 15, 9, 30),
                "LOCAL_SOE", "A", "OFFICE",
                "张三", "13800000000", "021-12345678", "zhangsan@example.com", "客户",
                "李四", "13900000000", "021-87654321", "lisi@example.com", "代理");
    }

    @Test
    @DisplayName("4173 样本其余字段 null → 20 个约定字段齐全且空值为空字符串")
    void toJson_minimal_includesAllContractFields() throws Exception {
        String json = mapper.toJson(minimal("E2E-PR2603-AUTO-20260910T083236"));

        Map<String, Object> data = new ObjectMapper().readValue(json, new TypeReference<>() {});
        assertThat(data).hasSize(20)
                .containsEntry("tenderId", 4173)
                .containsEntry("externalId", "E2E-PR2603-AUTO-20260910T083236")
                .containsEntry("title", "SDK事件联调-PR2603-自动注册-20260910T083236")
                .containsEntry("purchaserName", "")
                .containsEntry("region", "")
                .containsEntry("registrationDeadline", "")
                .containsEntry("bidOpeningTime", "")
                .containsEntry("customerType", "")
                .containsEntry("priority", "")
                .containsEntry("projectType", "")
                .containsEntry("contactName", "")
                .containsEntry("contactPhone", "")
                .containsEntry("contactTel", "")
                .containsEntry("contactMail", "")
                .containsEntry("contactNature", "")
                .containsEntry("contactName2", "")
                .containsEntry("contactPhone2", "")
                .containsEntry("contactTel2", "")
                .containsEntry("contactMail2", "")
                .containsEntry("contactNature2", "");
    }

    @Test
    @DisplayName("externalId 为 null → 字段保留并输出空字符串")
    void toJson_nullExternalId_becomesEmptyString() throws Exception {
        Map<String, Object> data = new ObjectMapper().readValue(
                mapper.toJson(minimal(null)), new TypeReference<>() {});
        assertThat(data).containsEntry("externalId", "");
    }

    @Test
    @DisplayName("externalId 为空白 → 字段保留并规范为空字符串")
    void toJson_blankExternalId_becomesEmptyString() throws Exception {
        Map<String, Object> data = new ObjectMapper().readValue(
                mapper.toJson(minimal("  ")), new TypeReference<>() {});
        assertThat(data).containsEntry("externalId", "");
    }

    @Test
    @DisplayName("全字段 → 枚举转中文、时间格式化 yyyy-MM-dd HH:mm、含联系人性质")
    void toJson_full_mapsLabelsAndFormatsDates() {
        String json = mapper.toJson(full());
        assertThat(json)
                .contains("\"title\":\"测试标讯\"")
                .contains("\"purchaserName\":\"招标主体X\"")
                .contains("\"region\":\"上海\"")
                .contains("\"registrationDeadline\":\"2026-09-10 17:00\"")
                .contains("\"bidOpeningTime\":\"2026-09-15 09:30\"")
                .contains("\"customerType\":\"地方国企\"")
                .contains("\"priority\":\"A级\"")
                .contains("\"projectType\":\"办公\"")
                .contains("\"contactName\":\"张三\"")
                .contains("\"contactNature\":\"客户\"")
                .contains("\"contactName2\":\"李四\"")
                .contains("\"contactNature2\":\"代理\"");
    }

    @Test
    @DisplayName("未知枚举值 → fallback 原值，不丢失数据")
    void toJson_unknownEnum_fallbackOriginal() {
        TenderEventPayload p = new TenderEventPayload(1L, null, null, null, null, null, null,
                "UNKNOWN_TYPE", "X", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(mapper.toJson(p))
                .contains("\"customerType\":\"UNKNOWN_TYPE\"")
                .contains("\"priority\":\"X\"");
    }
}
