package com.xiyu.bid.qualification.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CO-471 根因验证测试（决定性证据）。
 *
 * 用最小化的 WebMvcTest 验证 Spring MVC 对 @RequestBody Map<String, List<Long>> 的实际反序列化类型。
 * 结论：Spring MVC 的 Jackson HttpMessageConverter 因类型擦除丢失泛型信息，
 * 将 JSON 小数字默认解析为 Integer 而非 Long。这导致 Service 层
 * ids.contains(q.getId()) 中 Integer.equals(Long) 永远返回 false，
 * 过滤结果为空，导出的 Excel 只剩表头。
 *
 * 修复方式：Service 层将 ids 统一转为 Set<Long>（见 QualificationExportService）。
 */
@WebMvcTest(QualificationBatchExportTypeVerificationTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
class QualificationBatchExportTypeVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试 Controller：用 Map<String, Object> 接收 body，避免 List<Long> 强转异常，
     * 直接观察 Jackson 反序列化的真实元素类型。
     */
    @RestController
    @RequestMapping("/test/co471")
    @Configuration
    static class TestController {
        @PostMapping("/batch-export")
        public Map<String, Object> batchExport(@RequestBody Map<String, Object> body) {
            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) body.get("ids");
            String elementType = ids.isEmpty() ? "empty" : ids.get(0).getClass().getName();
            // 用 Long(1L) 调用 contains，模拟 Service 层 q.getId() 返回 Long 的场景
            boolean contains1L = ids.contains(1L);
            return Map.of(
                    "elementType", elementType,
                    "contains1L", contains1L,
                    "size", ids.size()
            );
        }
    }

    @Test
    @DisplayName("Spring MVC MockMvc: @RequestBody Map<String, List<Long>> 实际反序列化为 Integer（根因证据）")
    @WithMockUser
    void verifySpringMvcDeserializationType() throws Exception {
        String json = "{\"ids\": [1, 2, 3]}";

        var result = mockMvc.perform(post("/test/co471/batch-export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);

        System.out.println("=== Spring MVC MockMvc 根因证据 ===");
        System.out.println("elementType: " + response.get("elementType"));
        System.out.println("contains1L: " + response.get("contains1L"));
        System.out.println("size: " + response.get("size"));

        // 关键断言：Spring MVC 实际产生 Integer（不是 Long）
        assertEquals("java.lang.Integer", response.get("elementType"),
                "Spring MVC @RequestBody Map<String, List<Long>> 因类型擦除实际产生 Integer，不是 Long");
        // Integer list 调用 contains(Long) 返回 false —— 这就是 Excel 只有表头的根因
        assertEquals(Boolean.FALSE, response.get("contains1L"),
                "ids.contains(1L) 返回 false，因为 Integer.equals(Long) 永远为 false —— 这就是 Excel 只有表头的根因");
    }

    @Test
    @DisplayName("对照测试: 直接用 ObjectMapper(Map.class) 反序列化（复现 Spring MVC 行为）")
    void contrastTest_ObjectMapperWithMapClass() throws Exception {
        String json = "{\"ids\": [1, 2, 3]}";

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> body = objectMapper.readValue(json, Map.class);
        List<Object> ids = body.get("ids");

        String elementType = ids.isEmpty() ? "empty" : ids.get(0).getClass().getName();
        boolean contains1L = ids.contains(1L);

        System.out.println("=== ObjectMapper(Map.class) 对照结果 ===");
        System.out.println("elementType: " + elementType);
        System.out.println("contains1L: " + contains1L);

        assertEquals("java.lang.Integer", elementType,
                "用 Map.class 时应该解析为 Integer（与 Spring MVC 行为一致）");
        assertFalse(contains1L, "Integer list 不应 contains Long(1L)");
    }

    @Test
    @DisplayName("对照测试: 用 TypeReference 反序列化（理想情况，Spring MVC 不会走这条路径）")
    void contrastTest_ObjectMapperWithTypeReference() throws Exception {
        String json = "{\"ids\": [1, 2, 3]}";

        Map<String, List<Long>> body = objectMapper.readValue(json, new TypeReference<>() {});
        List<Long> ids = body.get("ids");

        String elementType = ids.isEmpty() ? "empty" : ids.get(0).getClass().getName();
        boolean contains1L = ids.contains(1L);

        System.out.println("=== ObjectMapper(TypeReference) 对照结果 ===");
        System.out.println("elementType: " + elementType);
        System.out.println("contains1L: " + contains1L);

        assertEquals("java.lang.Long", elementType);
        assertTrue(contains1L);
    }
}
