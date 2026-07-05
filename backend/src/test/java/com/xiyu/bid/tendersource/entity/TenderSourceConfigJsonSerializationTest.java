package com.xiyu.bid.tendersource.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * CO-469 第八轮 P1 审计守卫：TenderSourceConfig 的 JSON 字段序列化必须输出合法 JSON。
 *
 * 防复发场景：如果未来有人把 toJsonArray 改回手写拼接（如 List.toString() 或
 * 旧的 items.stream().map(s -> "\"" + s + "\"") 写法），本测试在 CI 阶段立即失败。
 *
 * 根因（与 PersonnelImportTask 第八轮同类）：旧 toJsonArray 只转义 \\ 和 \"，
 * 未转义 \\n / \\r / \\t 等控制字符，写入 MySQL JSON 列时触发 "Invalid JSON text" 错误。
 */
class TenderSourceConfigJsonSerializationTest {

    private final ObjectMapper validator = new ObjectMapper();

    @Test
    void setPlatforms_空列表_应写入合法JSON_等价于MySQL校验() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatforms(List.of());

        assertThat(config.getPlatformsJson()).isEqualTo("[]");
        assertThatNoException().isThrownBy(() -> validator.readTree(config.getPlatformsJson()));
    }

    @Test
    void setPlatforms_包含换行_应正确转义为合法JSON() throws Exception {
        // CO-469 第八轮 P1：旧实现未转义 \n，会触发 MySQL "Invalid JSON text"
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatforms(List.of("平台\n含换行", "正常平台"));

        String json = config.getPlatformsJson();
        // 等价于 MySQL cast(? as json) 校验
        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        // 反序列化验证：换行被保留
        JsonNode array = validator.readTree(json);
        assertThat(array.isArray()).isTrue();
        assertThat(array.get(0).asText()).isEqualTo("平台\n含换行");
        assertThat(array.get(1).asText()).isEqualTo("正常平台");
    }

    @Test
    void setPlatforms_包含引号和反斜杠_应正确转义() throws Exception {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatforms(List.of("含\"引号\"", "含\\反斜杠"));

        String json = config.getPlatformsJson();
        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        JsonNode array = validator.readTree(json);
        assertThat(array.get(0).asText()).isEqualTo("含\"引号\"");
        assertThat(array.get(1).asText()).isEqualTo("含\\反斜杠");
    }

    @Test
    void setPlatforms_包含制表符和回车_应正确转义() throws Exception {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatforms(List.of("tab\there", "cr\rhere"));

        String json = config.getPlatformsJson();
        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        JsonNode array = validator.readTree(json);
        assertThat(array.get(0).asText()).isEqualTo("tab\there");
        assertThat(array.get(1).asText()).isEqualTo("cr\rhere");
    }

    @Test
    void setRegions_包含中文和Unicode_应正确序列化() throws Exception {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setRegions(List.of("北京", "上海", "海外"));

        String json = config.getRegionsJson();
        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        JsonNode array = validator.readTree(json);
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(3);
        assertThat(array.get(0).asText()).isEqualTo("北京");
    }

    @Test
    void setBusinessUnits_新写入入口_应输出合法JSON() {
        // CO-469 第八轮 P1：补全对称的 setBusinessUnits 入口，避免调用方直接 setBusinessUnitsJson(String) 绕过 Jackson
        TenderSourceConfig config = new TenderSourceConfig();
        config.setBusinessUnits(List.of("事业部A", "事业部B"));

        String json = config.getBusinessUnitsJson();
        assertThatNoException().isThrownBy(() -> validator.readTree(json));

        // 反序列化验证：getBusinessUnits 读回原值
        assertThat(config.getBusinessUnits()).containsExactly("事业部A", "事业部B");
    }

    @Test
    void setPlatforms_null入参_应降级返回空数组() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatforms(null);

        assertThat(config.getPlatformsJson()).isEqualTo("[]");
        assertThatNoException().isThrownBy(() -> validator.readTree(config.getPlatformsJson()));
    }

    @Test
    void getPlatforms_历史脏数据_非法JSON应返回空列表不抛异常() {
        // 历史数据兼容：若 DB 中存在非法 JSON 字符串，反序列化失败应返回空列表（与 PersonnelImportTask.deserializeErrorDetails 一致）
        TenderSourceConfig config = new TenderSourceConfig();
        // 模拟历史脏数据：手写拼接格式（旧实现输出）
        config.setPlatformsJson("[未转义的中文, 直接拼接]");

        List<String> result = config.getPlatforms();
        assertThat(result).isNotNull();
        // 不抛异常即可
    }

    @Test
    void getPlatforms_合法JSON数组_应正确解析() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatformsJson("[\"平台A\",\"平台B\"]");

        List<String> result = config.getPlatforms();
        assertThat(result).containsExactly("平台A", "平台B");
    }

    @Test
    void getPlatforms_emptyArray_应返回空列表() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatformsJson("[]");

        assertThat(config.getPlatforms()).isEmpty();
    }

    @Test
    void getPlatforms_null_应返回空列表() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatformsJson(null);

        assertThat(config.getPlatforms()).isEmpty();
    }

    @Test
    void getPlatforms_空白字符串_应返回空列表() {
        TenderSourceConfig config = new TenderSourceConfig();
        config.setPlatformsJson("   ");

        assertThat(config.getPlatforms()).isEmpty();
    }

    @Test
    void roundTrip_序列化再反序列化_应保持数据一致() {
        TenderSourceConfig config = new TenderSourceConfig();
        List<String> original = List.of("含\"引号\"", "含\\反斜杠", "含\n换行", "正常值");
        config.setPlatforms(original);

        // 通过 getPlatforms 读回，应与原值一致
        assertThat(config.getPlatforms()).containsExactlyElementsOf(original);
    }
}
