package com.xiyu.bid.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * JsonUtils 工具类单元测试。
 *
 * <p>CO-469 第八轮 P1 审计后提取：全仓 JSON 字段序列化统一入口，
 * 替代各实体/适配器内各自 new ObjectMapper() 的重复实现。
 *
 * <p>防复发场景：如果未来有人改了 JsonUtils 的序列化逻辑（比如又换回手写拼接），
 * 本测试立即失败。
 */
class JsonUtilsTest {

    private final ObjectMapper validator = new ObjectMapper();

    // ── toStringListJson / fromStringListJson ────────

    @Test
    void toStringListJson_空列表_输出合法JSON数组() {
        String json = JsonUtils.toStringListJson(List.of());
        assertThat(json).isEqualTo("[]");
        assertThatNoException().isThrownBy(() -> validator.readTree(json));
    }

    @Test
    void toStringListJson_null入参_降级返回空数组() {
        String json = JsonUtils.toStringListJson(null);
        assertThat(json).isEqualTo("[]");
    }

    @Test
    void toStringListJson_包含特殊字符_正确转义() throws Exception {
        List<String> input = List.of("含\"引号\"", "含\\反斜杠", "含\n换行", "tab\there", "cr\r回车");
        String json = JsonUtils.toStringListJson(input);

        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        JsonNode array = validator.readTree(json);
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(5);
        assertThat(array.get(0).asText()).isEqualTo("含\"引号\"");
        assertThat(array.get(1).asText()).isEqualTo("含\\反斜杠");
        assertThat(array.get(2).asText()).isEqualTo("含\n换行");
        assertThat(array.get(3).asText()).isEqualTo("tab\there");
        assertThat(array.get(4).asText()).isEqualTo("cr\r回车");
    }

    @Test
    void fromStringListJson_合法JSON_正确解析() {
        List<String> result = JsonUtils.fromStringListJson("[\"北京\",\"上海\",\"广州\"]");
        assertThat(result).containsExactly("北京", "上海", "广州");
    }

    @Test
    void fromStringListJson_null_返回空列表() {
        assertThat(JsonUtils.fromStringListJson(null)).isEmpty();
    }

    @Test
    void fromStringListJson_空白字符串_返回空列表() {
        assertThat(JsonUtils.fromStringListJson("   ")).isEmpty();
    }

    @Test
    void fromStringListJson_空数组_返回空列表() {
        assertThat(JsonUtils.fromStringListJson("[]")).isEmpty();
    }

    @Test
    void fromStringListJson_非法JSON_降级返回空列表不抛异常() {
        // 模拟历史脏数据：List.toString() 格式
        List<String> result = JsonUtils.fromStringListJson("[未转义中文, 直接拼接]");
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void stringList_roundTrip_序列化再反序列化_数据一致() {
        List<String> original = List.of("含\"引号\"", "含\\反斜杠", "含\n换行", "正常值");
        String json = JsonUtils.toStringListJson(original);
        List<String> parsed = JsonUtils.fromStringListJson(json);
        assertThat(parsed).containsExactlyElementsOf(original);
    }

    // ── toJson / fromJson（通用） ───────────────────

    static class TestBean {
        public String name;
        public int age;
    }

    @Test
    void toJson_fromJson_通用对象_roundTrip() {
        TestBean bean = new TestBean();
        bean.name = "test";
        bean.age = 25;

        String json = JsonUtils.toJson(bean);
        assertThat(json).isNotNull();

        TestBean parsed = JsonUtils.fromJson(json, TestBean.class);
        assertThat(parsed).isNotNull();
        assertThat(parsed.name).isEqualTo("test");
        assertThat(parsed.age).isEqualTo(25);
    }

    @Test
    void toJson_null_返回null() {
        assertThat(JsonUtils.toJson(null)).isNull();
    }

    @Test
    void fromJson_null_返回null() {
        assertThat(JsonUtils.fromJson(null, TestBean.class)).isNull();
    }

    @Test
    void fromJson_非法JSON_返回null不抛异常() {
        assertThat(JsonUtils.fromJson("not valid json", TestBean.class)).isNull();
    }

    // ── fromJsonArray ──────────────────────────────

    @Test
    void fromJsonArray_对象数组_正确解析() {
        String json = "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]";
        List<TestBean> result = JsonUtils.fromJsonArray(json, TestBean.class);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name).isEqualTo("a");
        assertThat(result.get(1).age).isEqualTo(2);
    }

    @Test
    void fromJsonArray_空数组_返回空列表() {
        assertThat(JsonUtils.fromJsonArray("[]", TestBean.class)).isEmpty();
    }

    @Test
    void fromJsonArray_null_返回空列表() {
        assertThat(JsonUtils.fromJsonArray(null, TestBean.class)).isEmpty();
    }

    @Test
    void fromJsonArray_非法JSON_返回空列表() {
        assertThat(JsonUtils.fromJsonArray("not json", TestBean.class)).isEmpty();
    }
}
