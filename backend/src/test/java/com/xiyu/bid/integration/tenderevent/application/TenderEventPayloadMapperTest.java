package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
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

    @Test
    @DisplayName("有 externalId → data 含 tenderId 和 externalId")
    void toJson_withExternalId() {
        assertThat(mapper.toJson(new TenderEventPayload(123L, "ext-1")))
                .isEqualTo("{\"tenderId\":123,\"externalId\":\"ext-1\"}");
    }

    @Test
    @DisplayName("externalId 为 null → data 只含 tenderId")
    void toJson_nullExternalId_omits() {
        assertThat(mapper.toJson(new TenderEventPayload(123L, null)))
                .isEqualTo("{\"tenderId\":123}");
    }

    @Test
    @DisplayName("externalId 为空白 → data 只含 tenderId")
    void toJson_blankExternalId_omits() {
        assertThat(mapper.toJson(new TenderEventPayload(123L, "  ")))
                .isEqualTo("{\"tenderId\":123}");
    }
}