package com.xiyu.bid.tender.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO.GapFileRef;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.TenderCrmLinkRequest;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import org.springframework.test.jdbc.JdbcTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiyu.bid.service.AuthService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")

public class TenderCrmLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private TenderEvaluationRepository evaluationRepository;

    @Autowired
    private TenderEvaluationCustomerInfoRepository customerInfoRepository;

    @Autowired
    private ProjectDocumentRepository documentRepository;

    @Autowired
    private com.xiyu.bid.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate,
                "tender_evaluation_customer_info",
                "tender_evaluation_basics",
                "project_documents",
                "tender_evaluations",
                "tenders",
                "users"
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("CO-329: 关联 CRM 商机时，必须成功持久化客户信息矩阵和 GAP 附件")
    void testLinkCrmOpportunity_persistsCustomerInfoAndGapFiles() throws Exception {
        com.xiyu.bid.entity.User adminUser = com.xiyu.bid.entity.User.builder()
                .username("admin")
                .fullName("Admin User")
                .email("admin@test.com")
                .password("admin123")
                .role(com.xiyu.bid.entity.User.Role.ADMIN)
                .build();
        userRepository.saveAndFlush(adminUser);

        // 1. 准备测试标讯
        Tender tender = Tender.builder()
                .title("CO-329 测试标讯")
                .status(Tender.Status.TRACKING)
                .creatorId(adminUser.getId())
                .build();
        tenderRepository.saveAndFlush(tender);

        // 初始化评估表(通常在标讯创建或关联时需要先有记录或级联创建)
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(tender.getId())
                .build();
        evaluationRepository.saveAndFlush(evaluation);

        // 2. 构造请求 Payload (带有客户信息和 GAP 附件)
        EvaluationBasicDTO basicDTO = new EvaluationBasicDTO(
                null, null, null, "Testing Gap Risk", null, null, null, null, null,
                List.of(new GapFileRef("GAP1.pdf", "https://example.com/gap1.pdf"))
        );
        List<EvaluationCustomerInfoDTO> customerInfosDto = List.of(
                new EvaluationCustomerInfoDTO("KEY_DECISION_MAKER", "NAME", "张三", null)
        );
        TenderEvaluationSubmitRequest evaluationPayload = new TenderEvaluationSubmitRequest(
                null, basicDTO, customerInfosDto, null
        );
        TenderCrmLinkRequest requestObj = TenderCrmLinkRequest.builder()
                .crmOpportunityId("CC12345")
                .crmOpportunityName("测试CRM商机")
                .evaluationPayload(evaluationPayload)
                .build();
        String requestJson = objectMapper.writeValueAsString(requestObj);

        // 3. 执行 PATCH 请求
        mockMvc.perform(patch("/api/tenders/{id}/crm-opportunity", tender.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. 断言持久化结果
        Tender updatedTender = tenderRepository.findById(tender.getId()).orElseThrow();
        assertThat(updatedTender.getCrmOpportunityId()).isEqualTo("CC12345");
        assertThat(updatedTender.getCrmOpportunityName()).isEqualTo("测试CRM商机");

        // 断言: 客户信息已持久化
        List<TenderEvaluationCustomerInfo> customerInfos = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(customerInfos).hasSize(1);
        assertThat(customerInfos.get(0).getRoleKey()).isEqualTo("KEY_DECISION_MAKER");
        assertThat(customerInfos.get(0).getInfoKey()).isEqualTo("NAME");
        assertThat(customerInfos.get(0).getCellValue()).isEqualTo("张三");

        // 断言: GAP附件已持久化
        List<ProjectDocument> documents = documentRepository.findByProjectIdOrderByCreatedAtDesc(tender.getId());
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getName()).isEqualTo("GAP1.pdf");
        assertThat(documents.get(0).getFileUrl()).isEqualTo("https://example.com/gap1.pdf");
        
        // 断言: 评估表状态应该为 DRAFT，因为数据不完整
        TenderEvaluation updatedEval = evaluationRepository.findById(evaluation.getId()).orElseThrow();
        assertThat(updatedEval.getEvaluationStatus()).isEqualTo(TenderEvaluation.EvaluationStatus.DRAFT);
    }
}
