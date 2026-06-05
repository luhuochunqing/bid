package com.xiyu.bid.formengine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FormDefinition REST API 集成测试。
 *
 * 使用 @Sql 加载 V140 种子数据，测试所有运行时端点和管理端点。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig.class)
@DisplayName("FormDefinitionIntegrationTest")
class FormDefinitionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleProfileRepository roleProfileRepository;

    @Autowired
    private FormDefinitionRegistryRepository formDefinitionRepository;

    private User adminUser;

    @BeforeEach
    void setUp() {
        roleProfileRepository.deleteAll();
        userRepository.deleteAll();
        formDefinitionRepository.deleteAll();

        com.xiyu.bid.entity.RoleProfile adminProfile = roleProfileRepository.save(
                com.xiyu.bid.entity.RoleProfile.builder()
                        .code("admin-profile")
                        .name("管理员")
                        .dataScope("all")
                        .build()
        );

        adminUser = userRepository.save(User.builder()
                .username("form-admin")
                .password("dummy")  // NoOp encoder ignores this
                .fullName("表单管理员")
                .email("form-admin@example.com")
                .role(User.Role.ADMIN)
                .roleProfile(adminProfile)
                .departmentName("IT")
                .build()
        );

        // Insert form definition seed data (mirrors V140 seed INSERT IGNORE)
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        FormDefinitionRegistryEntity tenderEntry = new FormDefinitionRegistryEntity();
        tenderEntry.setScope("tender.entry");
        tenderEntry.setScopeLabel("标讯手工录入");
        tenderEntry.setVersion(1);
        tenderEntry.setSchemaJson("{\"fields\":[{\"key\":\"title\",\"label\":\"标讯标题\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"source\",\"label\":\"信息来源\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"招标公告\",\"value\":\"bidding\"},{\"label\":\"比选公告\",\"value\":\"selection\"},{\"label\":\"竞争性谈判\",\"value\":\"negotiation\"},{\"label\":\"单一来源\",\"value\":\"single_source\"},{\"label\":\"其他\",\"value\":\"other\"}]},{\"key\":\"budget\",\"label\":\"预算金额\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"region\",\"label\":\"项目地区\",\"type\":\"ADDRESS\",\"required\":false},{\"key\":\"publishDate\",\"label\":\"发布日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"deadline\",\"label\":\"截止日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"contactName\",\"label\":\"联系人\",\"type\":\"TEXT\",\"required\":false},{\"key\":\"contactPhone\",\"label\":\"联系电话\",\"type\":\"PHONE\",\"required\":false},{\"key\":\"description\",\"label\":\"标讯描述\",\"type\":\"TEXTAREA\",\"required\":false}]}");
        tenderEntry.setEnabled(true);
        tenderEntry.setOrgId(null);
        tenderEntry.setCreatedBy("system");
        tenderEntry.setCreatedAt(now);
        tenderEntry.setUpdatedAt(now);
        formDefinitionRepository.save(tenderEntry);

        FormDefinitionRegistryEntity projectBasic = new FormDefinitionRegistryEntity();
        projectBasic.setScope("project.basic");
        projectBasic.setScopeLabel("项目基本信息");
        projectBasic.setVersion(1);
        projectBasic.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"项目名称\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"managerId\",\"label\":\"项目经理\",\"type\":\"PERSON\",\"required\":true},{\"key\":\"teamMembers\",\"label\":\"团队成员\",\"type\":\"PERSON\",\"required\":false},{\"key\":\"startDate\",\"label\":\"开始日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"endDate\",\"label\":\"结束日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"budget\",\"label\":\"项目预算\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"industry\",\"label\":\"所属行业\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"政府\",\"value\":\"government\"},{\"label\":\"央企\",\"value\":\"soe\"},{\"label\":\"民营\",\"value\":\"private\"}]},{\"key\":\"description\",\"label\":\"项目描述\",\"type\":\"TEXTAREA\",\"required\":false}]}");
        projectBasic.setEnabled(true);
        projectBasic.setOrgId(null);
        projectBasic.setCreatedBy("system");
        projectBasic.setCreatedAt(now);
        projectBasic.setUpdatedAt(now);
        formDefinitionRepository.save(projectBasic);

        FormDefinitionRegistryEntity resourceExpense = new FormDefinitionRegistryEntity();
        resourceExpense.setScope("resource.expense");
        resourceExpense.setScopeLabel("费用申请");
        resourceExpense.setVersion(1);
        resourceExpense.setSchemaJson("{\"fields\":[{\"key\":\"projectId\",\"label\":\"关联项目\",\"type\":\"PROJECT\",\"required\":true},{\"key\":\"category\",\"label\":\"费用类别\",\"type\":\"SELECT\",\"required\":true,\"options\":[{\"label\":\"差旅费\",\"value\":\"travel\"},{\"label\":\"办公费\",\"value\":\"office\"},{\"label\":\"咨询费\",\"value\":\"consulting\"},{\"label\":\"其他\",\"value\":\"other\"}]},{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"CURRENCY\",\"required\":true},{\"key\":\"expenseDate\",\"label\":\"费用日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"description\",\"label\":\"费用说明\",\"type\":\"TEXTAREA\",\"required\":false}]}");
        resourceExpense.setEnabled(true);
        resourceExpense.setOrgId(null);
        resourceExpense.setCreatedBy("system");
        resourceExpense.setCreatedAt(now);
        resourceExpense.setUpdatedAt(now);
        formDefinitionRepository.save(resourceExpense);

        FormDefinitionRegistryEntity knowledgeCase = new FormDefinitionRegistryEntity();
        knowledgeCase.setScope("knowledge.case");
        knowledgeCase.setScopeLabel("案例建档");
        knowledgeCase.setVersion(1);
        knowledgeCase.setSchemaJson("{\"fields\":[{\"key\":\"title\",\"label\":\"案例标题\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"industry\",\"label\":\"所属行业\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"政府\",\"value\":\"government\"},{\"label\":\"央企\",\"value\":\"soe\"},{\"label\":\"民营\",\"value\":\"private\"}]},{\"key\":\"amount\",\"label\":\"合同金额\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"projectDate\",\"label\":\"完成日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"description\",\"label\":\"案例描述\",\"type\":\"TEXTAREA\",\"required\":false},{\"key\":\"tags\",\"label\":\"标签\",\"type\":\"TEXT\",\"required\":false}]}");
        knowledgeCase.setEnabled(true);
        knowledgeCase.setOrgId(null);
        knowledgeCase.setCreatedBy("system");
        knowledgeCase.setCreatedAt(now);
        knowledgeCase.setUpdatedAt(now);
        formDefinitionRepository.save(knowledgeCase);
    }

    // ==================== Runtime API Tests ====================

    @Nested
    @DisplayName("运行时 API (/api/form-definitions)")
    class RuntimeApi {

        @Test
        @DisplayName("GET /api/form-definitions/{scope}/active → 200 返回 schema")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void getActiveForm_returnsSchema() throws Exception {
            mockMvc.perform(get("/api/form-definitions/tender.entry/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.scope").value("tender.entry"))
                    .andExpect(jsonPath("$.data.scopeLabel").value("标讯手工录入"))
                    .andExpect(jsonPath("$.data.fields").isArray())
                    .andExpect(jsonPath("$.data.fields.length()", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data.version").value(1));
        }

        @Test
        @DisplayName("GET /api/form-definitions/project.basic/active → 200")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void getActiveForm_projectBasic() throws Exception {
            mockMvc.perform(get("/api/form-definitions/project.basic/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.scope").value("project.basic"))
                    .andExpect(jsonPath("$.data.scopeLabel").value("项目基本信息"));
        }

        @Test
        @DisplayName("GET /api/form-definitions/nonexistent/active → 404")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void getActiveForm_notFound() throws Exception {
            mockMvc.perform(get("/api/form-definitions/nonexistent.scope/active"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("POST /api/form-definitions/{scope}/validate - valid data → 200 valid=true")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void validateForm_validData() throws Exception {
            Map<String, Object> formData = Map.of(
                    "title", "测试标讯",
                    "deadline", "2026-12-31"
            );

            mockMvc.perform(post("/api/form-definitions/tender.entry/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(formData)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.valid").value(true))
                    .andExpect(jsonPath("$.data.errors").isEmpty());
        }

        @Test
        @DisplayName("POST /api/form-definitions/{scope}/validate - missing required → 200 valid=false")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void validateForm_missingRequired() throws Exception {
            Map<String, Object> formData = Map.of(
                    "source", "bidding"  // title and deadline missing (both required=true)
            );

            mockMvc.perform(post("/api/form-definitions/tender.entry/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(formData)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.errors", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.data.errors[0]", containsString("title")));
        }

        @Test
        @DisplayName("POST /api/form-definitions/{scope}/submit → 200")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void submitForm_success() throws Exception {
            Map<String, Object> formData = Map.of(
                    "title", "测试标讯",
                    "deadline", "2026-12-31"
            );

            mockMvc.perform(post("/api/form-definitions/tender.entry/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(formData)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("提交成功"));
        }

        @Test
        @DisplayName("POST /api/form-definitions/{scope}/submit - validation failure → 400")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void submitForm_validationFailure() throws Exception {
            Map<String, Object> formData = Map.of(
                    "source", "bidding"  // title missing (required=true), deadline also required
            );

            mockMvc.perform(post("/api/form-definitions/tender.entry/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(formData)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Unauthenticated → 403 (AuthorizationFilter denies unauthenticated requests)")
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/form-definitions/tender.entry/active"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Admin API Tests ====================

    @Nested
    @DisplayName("管理端 API (/api/admin/form-definitions)")
    class AdminApi {

        @Test
        @DisplayName("GET /api/admin/form-definitions → 200 paginated list")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void listDefinitions_paginated() throws Exception {
            mockMvc.perform(get("/api/admin/form-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()", greaterThanOrEqualTo(4)))  // 4 scopes seeded
                    .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(4)));
        }

        @Test
        @DisplayName("POST /api/admin/form-definitions → 201 creates definition")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void createDefinition_201() throws Exception {
            String body = """
                {
                  "scope": "test.custom",
                  "scopeLabel": "测试自定义表单",
                  "schemaJson": "{\\"fields\\":[{\\"key\\":\\"name\\",\\"label\\":\\"名称\\",\\"type\\":\\"TEXT\\",\\"required\\":true}]}",
                  "enabled": true
                }
                """;

            mockMvc.perform(post("/api/admin/form-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.scope").value("test.custom"))
                    .andExpect(jsonPath("$.data.scopeLabel").value("测试自定义表单"))
                    .andExpect(jsonPath("$.data.version").value(1));
        }

        @Test
        @DisplayName("POST /api/admin/form-definitions → 400 duplicate scope")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void createDefinition_duplicateScope() throws Exception {
            String body = """
                {
                  "scope": "tender.entry",
                  "scopeLabel": "重复的标讯",
                  "schemaJson": "{\\"fields\\":[]}",
                  "enabled": true
                }
                """;

            mockMvc.perform(post("/api/admin/form-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("POST /api/admin/form-definitions/{id}/publish → 200, version incremented")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void publishDefinition_incrementsVersion() throws Exception {
            // Create first
            MvcResult createResult = mockMvc.perform(post("/api/admin/form-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "scope": "test.publish",
                                  "scopeLabel": "发布测试",
                                  "schemaJson": "{\\"fields\\":[]}",
                                  "enabled": true
                                }
                                """))
                    .andExpect(status().isCreated())
                    .andReturn();

            Long id = objectMapper.readTree(
                    createResult.getResponse().getContentAsString()
            ).at("/data/id").asLong();

            // Publish
            mockMvc.perform(post("/api/admin/form-definitions/{id}/publish", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.version").value(2));
        }

        @Test
        @DisplayName("DELETE /api/admin/form-definitions/{id} → 200")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void deleteDefinition_200() throws Exception {
            // Create first
            MvcResult createResult = mockMvc.perform(post("/api/admin/form-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "scope": "test.delete",
                                  "scopeLabel": "删除测试",
                                  "schemaJson": "{\\"fields\\":[]}",
                                  "enabled": true
                                }
                                """))
                    .andExpect(status().isCreated())
                    .andReturn();

            Long id = objectMapper.readTree(
                    createResult.getResponse().getContentAsString()
            ).at("/data/id").asLong();

            // Delete
            mockMvc.perform(delete("/api/admin/form-definitions/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Non-admin accessing admin endpoint → 403")
        @WithMockUser(username = "regular-user", roles = {"STAFF"})
        void nonAdmin_forbidden() throws Exception {
            mockMvc.perform(get("/api/admin/form-definitions"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Scope Coverage ====================

    @Nested
    @DisplayName("种子数据验证")
    class SeedDataVerification {

        @Test
        @DisplayName("tender.entry 表单包含 title, source, budget 等字段")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void tenderEntry_hasExpectedFields() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/form-definitions/tender.entry/active"))
                    .andExpect(status().isOk())
                    .andReturn();

            String content = result.getResponse().getContentAsString();
            assertThat(content).contains("\"key\":\"title\"");
            assertThat(content).contains("\"type\":\"TEXT\"");
        }

        @Test
        @DisplayName("4 个 scope 均可用")
        @WithMockUser(username = "form-admin", roles = {"ADMIN"})
        void allFourScopesAvailable() throws Exception {
            String[] scopes = {"tender.entry", "project.basic", "resource.expense", "knowledge.case"};
            for (String scope : scopes) {
                mockMvc.perform(get("/api/form-definitions/{scope}/active", scope))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.scope").value(scope));
            }
        }
    }
}
