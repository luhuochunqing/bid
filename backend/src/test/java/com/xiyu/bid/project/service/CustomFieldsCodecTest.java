// Input: Map<String, Object> 自定义字段值 / 列存 JSON String
// Output: CustomFieldsCodec 序列化/反序列化断言 — 含非法输入降级空 Map（Constitution VII）
// Pos: backend test source - pure JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomFieldsCodecTest {

    private final CustomFieldsCodec codec = new CustomFieldsCodec(new ObjectMapper());

    @Test
    void toJson_nullInput_returnsNull() {
        assertNull(codec.toJson(null));
    }

    @Test
    void toJson_emptyMap_returnsNull() {
        assertNull(codec.toJson(Map.of()));
    }

    @Test
    void toJson_scopeGroupedMap_serializesToJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("project.basic", Map.of("budgetLevel", "重点客户"));
        map.put("project.detail", Map.of("siteVisitDone", true));

        String json = codec.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("project.basic"));
        assertTrue(json.contains("budgetLevel"));
    }

    @Test
    void fromJson_nullOrBlank_returnsEmptyMap() {
        assertTrue(codec.fromJson(null).isEmpty());
        assertTrue(codec.fromJson("").isEmpty());
        assertTrue(codec.fromJson("   ").isEmpty());
    }

    @Test
    void fromJson_validJson_returnsMap() {
        Map<String, Object> result =
                codec.fromJson("{\"project.basic\":{\"budgetLevel\":\"重点客户\"}}");

        assertEquals(Map.of("budgetLevel", "重点客户"), result.get("project.basic"));
    }

    @Test
    void fromJson_illegalJson_degradesToEmptyMap() {
        assertTrue(codec.fromJson("{not-a-json").isEmpty());
    }

    @Test
    void fromJson_nonObjectJson_degradesToEmptyMap() {
        assertTrue(codec.fromJson("[1,2,3]").isEmpty());
        assertTrue(codec.fromJson("\"plain-string\"").isEmpty());
    }

    @Test
    void roundtrip_preservesValues() {
        Map<String, Object> map = Map.of(
                "project.detail", Map.of("siteVisitDone", true, "visitCount", 3));

        assertEquals(map, codec.fromJson(codec.toJson(map)));
    }
}
