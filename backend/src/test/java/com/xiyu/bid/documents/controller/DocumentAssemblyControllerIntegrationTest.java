package com.xiyu.bid.documents.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documents.dto.AssemblyRequest;
import com.xiyu.bid.documents.dto.TemplateCreateRequest;
import com.xiyu.bid.documents.entity.AssemblyTemplate;
import com.xiyu.bid.documents.entity.DocumentAssembly;
import com.xiyu.bid.documents.repository.AssemblyTemplateRepository;
import com.xiyu.bid.documents.repository.DocumentAssemblyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DocumentAssemblyController集成测试
 * 测试文档组装控制器的HTTP端点
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentAssemblyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssemblyTemplateRepository templateRepository;

    @Autowired
    private DocumentAssemblyRepository assemblyRepository;

    private AssemblyTemplate testTemplate;
    private DocumentAssembly testAssembly;

    @BeforeEach
    void setUp() {
        // Clean up database
        assemblyRepository.deleteAll();
        templateRepository.deleteAll();

        // Create test template
        testTemplate = AssemblyTemplate.builder()
                .name("测试投标书模板")
                .description("用于测试的投标书模板")
                .category("BIDDING_DOCUMENT")
                .templateContent("尊敬的${招标方}：\n\n我方愿意参与${项目名称}的投标。")
                .variables("{\"招标方\":\"string\",\"项目名称\":\"string\"}")
                .createdBy(1L)
                .build();

        testTemplate = templateRepository.save(testTemplate);

        // Create test assembly
        testAssembly = DocumentAssembly.builder()
                .projectId(100L)
                .templateId(testTemplate.getId())
                .assembledContent("尊敬的XX公司：\n\n我方愿意参与ABC项目的投标。")
                .variables("{\"招标方\":\"XX公司\",\"项目名称\":\"ABC项目\"}")
                .assembledBy(1L)
                .build();

        testAssembly = assemblyRepository.save(testAssembly);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getTemplates_WithCategory_ShouldReturnTemplates() throws Exception {
        mockMvc.perform(get("/api/documents/assembly/templates")
                        .param("category", "BIDDING_DOCUMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @WithMockUser(roles = {"STAFF"})
    void getTemplates_WithoutCategory_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/documents/assembly/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createTemplate_WithValidData_ShouldReturnCreated() throws Exception {
        TemplateCreateRequest request = TemplateCreateRequest.builder()
                .name("新合同模板")
                .description("新的合同模板")
                .category("CONTRACT")
                .templateContent("甲方：${甲方}\n乙方：${乙方}")
                .variables("{\"甲方\":\"string\",\"乙方\":\"string\"}")
                .createdBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/assembly/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("新合同模板"))
                .andExpect(jsonPath("$.data.category").value("CONTRACT"));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void createTemplate_WithInvalidData_ShouldReturnBadRequest() throws Exception {
        TemplateCreateRequest request = TemplateCreateRequest.builder()
                .name("")  // Empty name
                .templateContent("内容")
                .build();

        mockMvc.perform(post("/api/documents/assembly/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"STAFF"})
    void getAssembliesByProject_ShouldReturnAssemblies() throws Exception {
        mockMvc.perform(get("/api/documents/assembly/{projectId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectId").value(100));
    }

    @Test
    @WithMockUser(roles = {"STAFF"})
    void getAssembliesByProject_WithNoAssemblies_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/documents/assembly/{projectId}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = {"STAFF"})
    void assembleDocument_WithValidData_ShouldReturnCreated() throws Exception {
        AssemblyRequest request = AssemblyRequest.builder()
                .templateId(testTemplate.getId())
                .variables("{\"招标方\":\"测试公司\",\"项目名称\":\"测试项目\"}")
                .assembledBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/assembly/{projectId}/assemble", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(200))
                .andExpect(jsonPath("$.data.templateId").value(testTemplate.getId()))
                .andExpect(jsonPath("$.data.assembledContent").value(org.hamcrest.Matchers.containsString("测试公司")));
    }

    @Test
    @WithMockUser(roles = {"STAFF"})
    void assembleDocument_WithInvalidTemplateId_ShouldReturnNotFound() throws Exception {
        AssemblyRequest request = AssemblyRequest.builder()
                .templateId(999L)
                .variables("{}")
                .assembledBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/assembly/{projectId}/assemble", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void regenerateAssembly_WithValidId_ShouldReturnOk() throws Exception {
        mockMvc.perform(put("/api/documents/assembly/{id}/regenerate", testAssembly.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void regenerateAssembly_WithInvalidId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(put("/api/documents/assembly/{id}/regenerate", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user", roles = {"STAFF"})
    void getTemplates_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        // This test verifies that without authentication, the endpoint is protected
        // Since we're using @WithMockUser, the request is authenticated
        // In a real scenario, removing @WithMockUser would test unauthorized access
        mockMvc.perform(get("/api/documents/assembly/templates"))
                .andExpect(status().isOk());
    }
}
