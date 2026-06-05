package com.xiyu.bid.resources.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.resources.dto.BarCertificateBorrowRequest;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.BarCertificateReturnRequest;
import com.xiyu.bid.resources.entity.BarAsset;
import com.xiyu.bid.resources.entity.BarCertificate;
import com.xiyu.bid.resources.entity.BarCertificateBorrowRecord;
import com.xiyu.bid.resources.repository.BarAssetRepository;
import com.xiyu.bid.resources.repository.BarCertificateBorrowRecordRepository;
import com.xiyu.bid.resources.repository.BarCertificateRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BarCertificateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BarAssetRepository barAssetRepository;

    @Autowired
    private BarCertificateRepository barCertificateRepository;

    @Autowired
    private BarCertificateBorrowRecordRepository borrowRecordRepository;

    private BarAsset asset;

    @BeforeEach
    void setUp() {
        borrowRecordRepository.deleteAll();
        barCertificateRepository.deleteAll();
        barAssetRepository.deleteAll();

        asset = barAssetRepository.save(BarAsset.builder()
                .name("杭州 BAR 站点")
                .type(BarAsset.AssetType.LICENSE)
                .value(new BigDecimal("1.00"))
                .status(BarAsset.AssetStatus.AVAILABLE)
                .acquireDate(LocalDate.now().minusMonths(2))
                .remark("测试站点")
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createAndListCertificates_ShouldReturnCreatedCertificate() throws Exception {
        Long certificateId = createCertificate();

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/certificates", asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(certificateId.intValue()))
                .andExpect(jsonPath("$.data[0].serialNo").value("CERT-001"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void borrowAndReturnCertificate_ShouldUpdateStatusAndRecords() throws Exception {
        Long certificateId = createCertificate();

        BarCertificateBorrowRequest borrowRequest = new BarCertificateBorrowRequest();
        borrowRequest.setBorrower("小王");
        borrowRequest.setProjectId(3001L);
        borrowRequest.setPurpose("投标电子签章");
        borrowRequest.setRemark("外借测试");
        borrowRequest.setExpectedReturnDate(LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow",
                                asset.getId(), certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(borrowRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("BORROWED"))
                .andExpect(jsonPath("$.data.currentBorrower").value("小王"));

        BarCertificate borrowed = barCertificateRepository.findById(certificateId).orElseThrow();
        assertThat(borrowed.getStatus()).isEqualTo(BarCertificate.CertificateStatus.BORROWED);
        assertThat(borrowed.getCurrentBorrower()).isEqualTo("小王");

        BarCertificateReturnRequest returnRequest = new BarCertificateReturnRequest();
        returnRequest.setRemark("已归还入柜");

        mockMvc.perform(post("/api/resources/bar-assets/{assetId}/certificates/{certificateId}/return",
                                asset.getId(), certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.currentBorrower").doesNotExist());

        BarCertificate returned = barCertificateRepository.findById(certificateId).orElseThrow();
        assertThat(returned.getStatus()).isEqualTo(BarCertificate.CertificateStatus.AVAILABLE);
        assertThat(returned.getCurrentBorrower()).isNull();

        List<BarCertificateBorrowRecord> records =
                borrowRecordRepository.findByCertificateIdOrderByBorrowedAtDesc(certificateId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(BarCertificateBorrowRecord.BorrowStatus.RETURNED);
        assertThat(records.get(0).getReturnedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getBorrowRecords_ShouldReturnCertificateHistory() throws Exception {
        Long certificateId = createCertificate();

        BarCertificateBorrowRequest borrowRequest = new BarCertificateBorrowRequest();
        borrowRequest.setBorrower("小李");
        borrowRequest.setProjectId(3002L);
        borrowRequest.setPurpose("项目投标");
        borrowRequest.setExpectedReturnDate(LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow",
                                asset.getId(), certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(borrowRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow-records",
                                asset.getId(), certificateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].borrower").value("小李"))
                .andExpect(jsonPath("$.data[0].status").value("BORROWED"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void returnCertificateWithoutBorrow_ShouldFailWithConflict() throws Exception {
        Long certificateId = createCertificate();

        BarCertificateReturnRequest request = new BarCertificateReturnRequest();
        request.setRemark("尚未借出");

        mockMvc.perform(post("/api/resources/bar-assets/{assetId}/certificates/{certificateId}/return",
                                asset.getId(), certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("Only borrowed certificates can be returned"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void missingAsset_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/resources/bar-assets/{assetId}/certificates", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private Long createCertificate() throws Exception {
        BarCertificateCreateRequest request = new BarCertificateCreateRequest();
        request.setType("UK");
        request.setProvider("CFCA");
        request.setSerialNo("CERT-001");
        request.setHolder("杭州公司");
        request.setLocation("主柜");
        request.setExpiryDate(LocalDate.now().plusYears(1));
        request.setRemark("测试证书");

        String response = mockMvc.perform(post("/api/resources/bar-assets/{assetId}/certificates", asset.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serialNo").value("CERT-001"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        BarCertificate created = objectMapper.readTree(response).path("data").traverse(objectMapper)
                .readValueAs(BarCertificate.class);
        return created.getId();
    }
}
