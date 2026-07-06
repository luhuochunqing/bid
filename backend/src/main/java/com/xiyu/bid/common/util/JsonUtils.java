package com.xiyu.bid.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * JSON 序列化统一工具类。
 *
 * <p>CO-469 第八轮 P1 审计后提取：全仓 6 个实体 9 个 JSON 字段各自实现序列化逻辑，
 * 存在重复造轮子、ObjectMapper 配置不一致、异常降级策略不统一等问题。
 *
 * <p>设计原则：
 * <ul>
 *   <li>序列化失败不抛异常，降级返回空（保证业务流程不中断）</li>
 *   <li>反序列化失败不抛异常，降级返回空集合/null（兼容历史脏数据）</li>
 *   <li>使用静态 ObjectMapper，配置与 Spring 容器一致（JavaTimeModule 等）</li>
 *   <li>所有 JSON 字段读写必须走本类，禁止业务代码直接 new ObjectMapper()</li>
 * </ul>
 *
 * <p>使用场景：
 * <pre>{@code
 *   // 序列化 List<String> -> JSON 数组字符串
 *   String json = JsonUtils.toStringListJson(list);
 *
 *   // 反序列化 JSON 数组字符串 -> List<String>
 *   List<String> list = JsonUtils.fromStringListJson(json);
 *
 *   // 通用序列化（复杂对象）
 *   String json = JsonUtils.toJson(obj);
 *
 *   // 通用反序列化（复杂对象）
 *   MyType obj = JsonUtils.fromJson(json, MyType.class);
 *   MyType obj = JsonUtils.fromJson(json, new TypeReference<MyType>() {});
 * }</pre>
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMPTY_ARRAY = "[]";

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private JsonUtils() {}

    // ── List<String> 专用 ──────────────────────────────

    public static String toStringListJson(List<String> items) {
        if (items == null || items.isEmpty()) {
            return EMPTY_ARRAY;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 序列化 List<String> 失败，降级返回 []: {}", e.getMessage());
            return EMPTY_ARRAY;
        }
    }

    public static List<String> fromStringListJson(String json) {
        if (json == null || json.isBlank() || EMPTY_ARRAY.equals(json)) {
            return List.of();
        }
        try {
            List<String> result = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return result != null ? result : List.of();
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 反序列化 List<String> 失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    // ── 通用序列化/反序列化 ────────────────────────────

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 序列化对象失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 反序列化对象失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 反序列化对象失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化数组为 List（避免泛型擦除问题）。
     */
    public static <T> List<T> fromJsonArray(String json, Class<T> elementClass) {
        if (json == null || json.isBlank() || EMPTY_ARRAY.equals(json)) {
            return List.of();
        }
        try {
            T[] array = OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructArrayType(elementClass));
            return array != null ? List.of(array) : List.of();
        } catch (JsonProcessingException e) {
            log.warn("JsonUtils: 反序列化数组失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 暴露 ObjectMapper 供需要高级配置的场景使用（如自定义序列化器）。
     * 注意：返回的是共享实例，请勿修改其配置！
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
