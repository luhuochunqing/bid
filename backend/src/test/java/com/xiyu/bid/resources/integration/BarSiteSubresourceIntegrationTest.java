package com.xiyu.bid.resources.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.resources.dto.*;
import com.xiyu.bid.resources.entity.BarAsset;
import com.xiyu.bid.resources.repository.*;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BarSiteSubresourceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BarAssetRepository barAssetRepository;

    @Autowired
    private BarSiteAccountRepository barSiteAccountRepository;

    @Autowired
    private BarSiteAttachmentRepository barSiteAttachmentRepository;

    @Autowired
    private BarSiteSopRepository barSiteSopRepository;

    @Autowired
    private BarSiteVerificationRepository barSiteVerificationRepository;

    private BarAsset asset;

    @BeforeEach
    void setUp() {
        barSiteVerificationRepository.deleteAll();
        barSiteAttachmentRepository.deleteAll();
        barSiteSopRepository.deleteAll();
        barSiteAccountRepository.deleteAll();
        barAssetRepository.deleteAll();

        asset = barAssetRepository.save(BarAsset.builder()
                .name("上海公共资源交易中心")
                .type(BarAsset.AssetType.LICENSE)
                .value(new BigDecimal("1.00"))
                .status(BarAsset.AssetStatus.AVAILABLE)
                .acquireDate(LocalDate.now().minusMonths(3))
                .remark("测试站点")
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void accountLifecycleAndStatusToggle_ShouldPersist() throws Exception {
        BarSiteAccountRequest createRequest = new BarSiteAccountRequest();
        createRequest.setUsername("bar-admin");
        createRequest.setRole("admin");
        createRequest.setOwner("李总");
        createRequest.setPhone("13800000000");
        createRequest.setEmail("lizong@example.com");
        createRequest.setStatus("active");

        String response = mockMvc.perform(post("/api/resources/bar-assets/{assetId}/accounts", asset.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("bar-admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long accountId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/accounts", asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].owner").value("李总"));

        BarSiteStatusUpdateRequest statusRequest = new BarSiteStatusUpdateRequest();
        statusRequest.setStatus("inactive");
        mockMvc.perform(patch("/api/resources/bar-assets/{assetId}/status", asset.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));

        BarSiteAccountRequest updateRequest = new BarSiteAccountRequest();
        updateRequest.setUsername("bar-operator");
        updateRequest.setRole("operator");
        updateRequest.setOwner("王经理");
        updateRequest.setPhone("13900000000");
        updateRequest.setEmail("wang@example.com");
        updateRequest.setStatus("active");
        mockMvc.perform(put("/api/resources/bar-assets/{assetId}/accounts/{accountId}", asset.getId(), accountId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bar-operator"));

        mockMvc.perform(delete("/api/resources/bar-assets/{assetId}/accounts/{accountId}", asset.getId(), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(barSiteAccountRepository.findByBarAssetIdOrderByCreatedAtAsc(asset.getId())).isEmpty();
        assertThat(barAssetRepository.findById(asset.getId()).orElseThrow().getStatus()).isEqualTo(BarAsset.AssetStatus.MAINTENANCE);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void verifySopAndAttachments_ShouldPersistAndReturnStructuredPayload() throws Exception {
        BarSiteVerificationRequest verificationRequest = new BarSiteVerificationRequest();
        verificationRequest.setVerifiedBy("李总");
        verificationRequest.setStatus("SUCCESS");
        verificationRequest.setMessage("登录页检查通过");

        mockMvc.perform(post("/api/resources/bar-assets/{assetId}/verify", asset.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verificationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verifiedBy").value("李总"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        BarSiteSopRequest sopRequest = new BarSiteSopRequest();
        sopRequest.setResetUrl("https://bar.example.com/reset");
        sopRequest.setUnlockUrl("https://bar.example.com/unlock");
        sopRequest.setContacts(List.of("400-800-1234", "service@example.com"));
        sopRequest.setEstimatedTime("2小时");
        BarSiteSopRequest.RequiredDocItem doc = new BarSiteSopRequest.RequiredDocItem();
        doc.setName("营业执照");
        doc.setRequired(true);
        sopRequest.setRequiredDocs(List.of(doc));
        BarSiteSopRequest.FaqItem faq = new BarSiteSopRequest.FaqItem();
        faq.setQ("UK 锁死怎么办");
        faq.setA("走人工解锁");
        sopRequest.setFaqs(List.of(faq));

        mockMvc.perform(put("/api/resources/bar-assets/{assetId}/sop", asset.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sopRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetUrl").value("https://bar.example.com/reset"))
                .andExpect(jsonPath("$.data.contacts", hasSize(2)))
                .andExpect(jsonPath("$.data.requiredDocs", hasSize(1)));

        BarSiteAttachmentCreateRequest attachmentRequest = new BarSiteAttachmentCreateRequest();
        attachmentRequest.setName("授权书.pdf");
        attachmentRequest.setSize("256KB");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setUploadedBy("李总");
        attachmentRequest.setUrl("https://files.example.com/auth.pdf");

        String attachmentResponse = mockMvc.perform(post("/api/resources/bar-assets/{assetId}/attachments", asset.getId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachmentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("授权书.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long attachmentId = objectMapper.readTree(attachmentResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/verification-records", asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].message").value("登录页检查通过"));

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/sop", asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlockUrl").value("https://bar.example.com/unlock"))
                .andExpect(jsonPath("$.data.faqs", hasSize(1)));

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/attachments", asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].uploadedBy").value("李总"));

        mockMvc.perform(delete("/api/resources/bar-assets/{assetId}/attachments/{attachmentId}", asset.getId(), attachmentId))
                .andExpect(status().isOk());

        assertThat(barSiteVerificationRepository.findByBarAssetIdOrderByVerifiedAtDesc(asset.getId())).hasSize(1);
        assertThat(barSiteSopRepository.findByBarAssetId(asset.getId())).isPresent();
        assertThat(barSiteAttachmentRepository.findByBarAssetIdOrderByUploadedAtDesc(asset.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void missingAsset_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/accounts", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }
}
