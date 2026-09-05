package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import java.time.LocalDateTime;
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

    /** 仅 tenderId + externalId 非空，其余 18 字段 null（验证 null 省略）。 */
    private TenderEventPayload minimal(String externalId) {
        return new TenderEventPayload(123L, externalId, null, null, null, null, null, null, null, null,
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
    @DisplayName("有 externalId、其余字段 null → data 只含 tenderId 和 externalId")
    void toJson_minimal() {
        assertThat(mapper.toJson(minimal("ext-1")))
                .isEqualTo("{\"tenderId\":123,\"externalId\":\"ext-1\"}");
    }

    @Test
    @DisplayName("externalId 为 null → data 只含 tenderId")
    void toJson_nullExternalId_omits() {
        assertThat(mapper.toJson(minimal(null))).isEqualTo("{\"tenderId\":123}");
    }

    @Test
    @DisplayName("externalId 为空白 → data 只含 tenderId")
    void toJson_blankExternalId_omits() {
        assertThat(mapper.toJson(minimal("  "))).isEqualTo("{\"tenderId\":123}");
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
