package com.xiyu.bid.template.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.entity.Template;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.TemplateDownloadRecordRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.repository.TemplateUseRecordRepository;
import com.xiyu.bid.repository.TemplateVersionRepository;
import com.xiyu.bid.support.TestPasswordEncryptionUtil;
import com.xiyu.bid.template.dto.TemplateCopyRequest;
import com.xiyu.bid.template.dto.TemplateDTO;
import com.xiyu.bid.template.dto.TemplateDownloadRecordRequest;
import com.xiyu.bid.template.dto.TemplateUseRecordRequest;
import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class TemplateAdvancedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private TemplateUseRecordRepository templateUseRecordRepository;

    @Autowired
    private TemplateDownloadRecordRepository templateDownloadRecordRepository;

    private Template template;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new TestPasswordEncryptionUtil();
        }
    }

    @BeforeEach
    void setUp() {
        templateDownloadRecordRepository.deleteAll();
        templateUseRecordRepository.deleteAll();
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();

        template = templateRepository.save(Template.builder()
                .name("智慧园区技术方案模板")
                .category(Template.Category.TECHNICAL)
                .productType(ProductType.SMART_PARK.getLabel())
                .industry(IndustryType.GOVERNMENT.getLabel())
                .documentType(DocumentType.TECHNICAL_PROPOSAL.getLabel())
                .description("园区项目模板")
                .currentVersion("1.0")
                .fileSize("2.4MB")
                .tags(List.of("园区", "技术"))
                .createdBy(1L)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void copyAndVersionHistory_ShouldPersist() throws Exception {
        TemplateCopyRequest copyRequest = new TemplateCopyRequest();
        copyRequest.setName("智慧园区技术方案模板（副本）");
        copyRequest.setCreatedBy(2L);

        mockMvc.perform(post("/api/knowledge/templates/{id}/copy", template.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(copyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("智慧园区技术方案模板（副本）"))
                .andExpect(jsonPath("$.data.currentVersion").value("1.0"))
                .andExpect(jsonPath("$.data.productType").value("智慧园区"))
                .andExpect(jsonPath("$.data.industry").value("政府"))
                .andExpect(jsonPath("$.data.documentType").value("技术方案"));

        TemplateDTO updatePayload = TemplateDTO.builder()
                .name("智慧园区技术方案模板")
                .category(Template.Category.TECHNICAL)
                .productType(ProductType.SMART_CITY)
                .industry(IndustryType.TRANSPORTATION)
                .documentType(DocumentType.INDUSTRY_SOLUTION)
                .description("更新后的模板描述")
                .fileSize("3.1MB")
                .tags(List.of("园区", "技术", "更新"))
                .createdBy(1L)
                .build();

        mockMvc.perform(put("/api/knowledge/templates/{id}", template.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value("1.1"))
                .andExpect(jsonPath("$.data.productType").value("智慧城市"))
                .andExpect(jsonPath("$.data.industry").value("交通"))
                .andExpect(jsonPath("$.data.documentType").value("行业方案"));

        mockMvc.perform(get("/api/knowledge/templates/{id}/versions", template.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value("1.1"))
                .andExpect(jsonPath("$.data[1].version").value("1.0"));

        assertThat(templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(template.getId())).hasSize(2);
        Template updatedTemplate = templateRepository.findById(template.getId()).orElseThrow();
        assertThat(updatedTemplate.getCurrentVersion()).isEqualTo("1.1");
        assertThat(updatedTemplate.getProductType()).isEqualTo(ProductType.SMART_CITY.getLabel());
        assertThat(updatedTemplate.getIndustry()).isEqualTo(IndustryType.TRANSPORTATION.getLabel());
        assertThat(updatedTemplate.getDocumentType()).isEqualTo(DocumentType.INDUSTRY_SOLUTION.getLabel());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void useAndDownload_ShouldRecordAndUpdateCounters() throws Exception {
        TemplateUseRecordRequest useRequest = new TemplateUseRecordRequest();
        useRequest.setDocumentName("IOC项目技术方案");
        useRequest.setDocType("tech");
        useRequest.setProjectId(11L);
        useRequest.setApplyOptions(List.of("content", "styles"));
        useRequest.setUsedBy(3L);

        mockMvc.perform(post("/api/knowledge/templates/{id}/use-records", template.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(useRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentName").value("IOC项目技术方案"))
                .andExpect(jsonPath("$.data.applyOptions", hasSize(2)));

        TemplateDownloadRecordRequest downloadRequest = new TemplateDownloadRecordRequest();
        downloadRequest.setDownloadedBy(9L);

        mockMvc.perform(post("/api/knowledge/templates/{id}/downloads", template.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(downloadRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloads").value(1))
                .andExpect(jsonPath("$.data.useCount").value(1));

        mockMvc.perform(get("/api/knowledge/templates/{id}", template.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloads").value(1))
                .andExpect(jsonPath("$.data.useCount").value(1));

        assertThat(templateUseRecordRepository.countByTemplateId(template.getId())).isEqualTo(1);
        assertThat(templateDownloadRecordRepository.countByTemplateId(template.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createAndFilter_ShouldSupportThreeDimensionalClassification() throws Exception {
        TemplateDTO createPayload = TemplateDTO.builder()
                .name("政府智慧园区技术方案模板")
                .category(Template.Category.TECHNICAL)
                .productType(ProductType.SMART_PARK)
                .industry(IndustryType.GOVERNMENT)
                .documentType(DocumentType.TECHNICAL_PROPOSAL)
                .description("新建模板")
                .fileUrl("https://example.com/templates/smart-park.docx")
                .fileSize("4.2MB")
                .tags(List.of("园区", "政府"))
                .createdBy(8L)
                .build();

        mockMvc.perform(post("/api/knowledge/templates")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("政府智慧园区技术方案模板"))
                .andExpect(jsonPath("$.data.productType").value("智慧园区"))
                .andExpect(jsonPath("$.data.industry").value("政府"))
                .andExpect(jsonPath("$.data.documentType").value("技术方案"));

        mockMvc.perform(get("/api/knowledge/templates")
                        .param("productType", "智慧园区")
                        .param("industry", "政府")
                        .param("documentType", "技术方案")
                        .param("name", "园区"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].productType").value("智慧园区"))
                .andExpect(jsonPath("$.data[0].industry").value("政府"))
                .andExpect(jsonPath("$.data[0].documentType").value("技术方案"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createWithoutThreeDimensionalClassification_ShouldFailValidation() throws Exception {
        TemplateDTO createPayload = TemplateDTO.builder()
                .name("缺少分类模板")
                .category(Template.Category.TECHNICAL)
                .description("维度缺失")
                .createdBy(8L)
                .build();

        mockMvc.perform(post("/api/knowledge/templates")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("产品类型、行业、文档类型不能为空"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createWithUnknownProductType_ShouldRejectExplicitly() throws Exception {
        String payload = """
                {
                  "name": "未知产品类型模板",
                  "category": "TECHNICAL",
                  "productType": "火星产品",
                  "industry": "政府",
                  "documentType": "技术方案",
                  "createdBy": 8
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/knowledge/templates")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("不支持的产品类型: 火星产品");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createWithUnknownIndustry_ShouldRejectExplicitly() throws Exception {
        String payload = """
                {
                  "name": "未知行业模板",
                  "category": "TECHNICAL",
                  "productType": "智慧园区",
                  "industry": "火星行业",
                  "documentType": "技术方案",
                  "createdBy": 8
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/knowledge/templates")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("不支持的行业: 火星行业");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createWithUnknownDocumentType_ShouldRejectExplicitly() throws Exception {
        String payload = """
                {
                  "name": "未知文档类型模板",
                  "category": "TECHNICAL",
                  "productType": "智慧园区",
                  "industry": "政府",
                  "documentType": "火星文档",
                  "createdBy": 8
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/knowledge/templates")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("不支持的文档类型: 火星文档");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getTemplateById_OnHistoricalTemplate_ShouldNotInitializeVersionsOnRead() throws Exception {
        Template historicalTemplate = templateRepository.save(Template.builder()
                .name("历史模板详情")
                .category(Template.Category.COMMERCIAL)
                .description("历史模板详情读取")
                .currentVersion("0.9")
                .fileSize("1.2MB")
                .createdBy(5L)
                .build());

        mockMvc.perform(get("/api/knowledge/templates/{id}", historicalTemplate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("历史模板详情"))
                .andExpect(jsonPath("$.data.currentVersion").value("0.9"));

        assertThat(templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(historicalTemplate.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getTemplateVersions_OnHistoricalTemplate_ShouldReturnFallbackVersionWithoutPersisting() throws Exception {
        Template historicalTemplate = templateRepository.save(Template.builder()
                .name("历史版本兼容模板")
                .category(Template.Category.COMMERCIAL)
                .description("没有版本行的历史模板")
                .currentVersion("0.9")
                .fileSize("1.2MB")
                .createdBy(5L)
                .build());

        mockMvc.perform(get("/api/knowledge/templates/{id}/versions", historicalTemplate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].version").value("0.9"));

        assertThat(templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(historicalTemplate.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void listWithNonMatchingFilters_ShouldReturnEmptyCollection() throws Exception {
        mockMvc.perform(get("/api/knowledge/templates")
                        .param("productType", "MES")
                        .param("industry", "教育")
                        .param("documentType", "资格文件")
                        .param("name", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void listWithoutFilters_ShouldKeepHistoricalTemplatesReadable() throws Exception {
        Template historicalTemplate = templateRepository.save(Template.builder()
                .name("历史商务模板")
                .category(Template.Category.COMMERCIAL)
                .description("没有补齐三维字段的历史模板")
                .currentVersion("0.9")
                .fileSize("1.2MB")
                .tags(List.of("历史"))
                .createdBy(5L)
                .build());

        MvcResult result = mockMvc.perform(get("/api/knowledge/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data).hasSize(2);

        mockMvc.perform(get("/api/knowledge/templates/{id}", historicalTemplate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("历史商务模板"))
                .andExpect(jsonPath("$.data.productType").doesNotExist())
                .andExpect(jsonPath("$.data.industry").doesNotExist())
                .andExpect(jsonPath("$.data.documentType").doesNotExist());
    }
}
