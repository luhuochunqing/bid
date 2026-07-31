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

    // ---------- filterScopes（US1 创建链路未知 scope 过滤） ----------

    @Test
    void filterScopes_dropsUnknownScopeKeys() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("project.basic", Map.of("budgetLevel", "重点"));
        input.put("evil.scope", Map.of("x", 1));

        Map<String, Object> result =
                codec.filterScopes(input, java.util.Set.of("project.basic", "project.detail"));

        assertEquals(Map.of("project.basic", Map.of("budgetLevel", "重点")), result);
    }

    @Test
    void filterScopes_nullOrEmpty_passthrough() {
        assertNull(codec.filterScopes(null, java.util.Set.of("project.basic")));
        assertTrue(codec.filterScopes(Map.of(), java.util.Set.of("project.basic")).isEmpty());
    }

    // ---------- replaceScope（US1 立项链路按 scope 键整体替换） ----------

    @Test
    void replaceScope_replacesOnlyTargetScope_preservesOthers() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("project.basic", Map.of("budgetLevel", "重点"));
        existing.put("project.initiation", Map.of("oldKey", "旧值"));

        Map<String, Object> merged =
                codec.replaceScope(existing, "project.initiation", Map.of("newKey", "新值"));

        assertEquals(Map.of("budgetLevel", "重点"), merged.get("project.basic"));
        assertEquals(Map.of("newKey", "新值"), merged.get("project.initiation"));
    }

    @Test
    void replaceScope_nullOrEmptyGroup_removesScopeKey() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("project.initiation", Map.of("oldKey", "旧值"));

        assertTrue(codec.replaceScope(existing, "project.initiation", null).isEmpty());
        assertTrue(codec.replaceScope(existing, "project.initiation", Map.of()).isEmpty());
    }

    @Test
    void replaceScope_nonMapGroup_keepsExisting() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("project.initiation", Map.of("oldKey", "旧值"));

        Map<String, Object> merged =
                codec.replaceScope(existing, "project.initiation", "dirty-string-value");

        assertEquals(Map.of("oldKey", "旧值"), merged.get("project.initiation"));
    }

    @Test
    void replaceScope_nullExisting_treatedAsEmpty() {
        Map<String, Object> merged =
                codec.replaceScope(null, "project.initiation", Map.of("k", "v"));

        assertEquals(Map.of("project.initiation", Map.of("k", "v")), merged);
    }
}
