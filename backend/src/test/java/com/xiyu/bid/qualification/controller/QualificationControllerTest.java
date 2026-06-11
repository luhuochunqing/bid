package com.xiyu.bid.qualification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.qualification.dto.QualificationBorrowRecordDTO;
import com.xiyu.bid.qualification.dto.QualificationBorrowRequest;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.service.QualificationService;
import com.xiyu.bid.qualification.service.QualificationAiParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QualificationControllerTest {

    @Mock
    private QualificationService qualificationService;

    @Mock
    private QualificationAiParserService qualificationAiParserService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QualificationController(qualificationService, qualificationAiParserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getQualificationById_ShouldExposeDerivedFieldsOnLegacyRoute() throws Exception {
        when(qualificationService.getQualificationById(1L)).thenReturn(QualificationDTO.builder()
                .id(1L)
                .name("高新技术企业证书")
                .remainingDays(15)
                .alertLevel("warning")
                .status("expiring")
                .build());

        mockMvc.perform(get("/api/knowledge/qualifications/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.remainingDays").value(15))
                .andExpect(jsonPath("$.data.alertLevel").value("warning"))
                .andExpect(jsonPath("$.data.status").value("expiring"));
    }

    @Test
    void borrowQualification_ShouldStayOnLegacyRoute() throws Exception {
        when(qualificationService.borrowQualification(eq(1L), any(QualificationBorrowRequest.class)))
                .thenReturn(QualificationBorrowRecordDTO.builder()
                        .id(8L)
                        .qualificationId(1L)
                        .qualificationName("高新技术企业证书")
                        .borrower("小王")
                        .expectedReturnDate(LocalDate.now().plusDays(7).toString())
                        .status("borrowed")
                        .build());

        QualificationBorrowRequest request = QualificationBorrowRequest.builder()
                .borrower("小王")
                .department("销售部")
                .purpose("投标使用")
                .expectedReturnDate(LocalDate.now().plusDays(7))
                .build();

        mockMvc.perform(post("/api/knowledge/qualifications/{id}/borrow", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.borrower").value("小王"));
    }

    @Test
    void getBorrowRecords_ShouldReturnCompatibilityPayload() throws Exception {
        when(qualificationService.getBorrowRecords(1L)).thenReturn(List.of(QualificationBorrowRecordDTO.builder()
                .id(9L)
                .qualificationId(1L)
                .qualificationName("高新技术企业证书")
                .status("OVERDUE")
                .build()));

        mockMvc.perform(get("/api/knowledge/qualifications/{id}/borrow-records", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].qualificationName").value("高新技术企业证书"))
                .andExpect(jsonPath("$.data[0].status").value("OVERDUE"));
    }

    @Test
    void getBorrowRecordsByQuery_WithoutQualificationId_ShouldReturnAllRecords() throws Exception {
        when(qualificationService.getBorrowRecords(null)).thenReturn(List.of(QualificationBorrowRecordDTO.builder()
                .id(12L)
                .qualificationId(2L)
                .qualificationName("总包资质")
                .status("borrowed")
                .build()));

        mockMvc.perform(get("/api/knowledge/qualifications/borrow-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].qualificationName").value("总包资质"));
    }

    @Test
    void retireQualification_ShouldRejectReasonShorterThanFourChars() throws Exception {
        mockMvc.perform(post("/api/knowledge/qualifications/{id}/retire", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"短\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("下架原因不少于4个字"));

        verify(qualificationService, never()).retireQualification(eq(1L), any());
    }

    @Test
    void retireQualification_ShouldRejectReasonLongerThanTwoHundredChars() throws Exception {
        String longReason = "a".repeat(201);
        mockMvc.perform(post("/api/knowledge/qualifications/{id}/retire", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + longReason + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("下架原因不超过200字"));

        verify(qualificationService, never()).retireQualification(eq(1L), any());
    }

    @Test
    @DisplayName("列表分页 - 返回 Page<DTO> 含 content/totalElements，category/status 透传")
    void getAllQualifications_ShouldReturnPaginatedResponseWithFilters() throws Exception {
        QualificationDTO dto = QualificationDTO.builder()
                .id(1L)
                .name("测试证书")
                .status("in_stock")
                .build();
        Page<QualificationDTO> mockPage = new PageImpl<>(List.of(dto), PageRequest.of(0, 15), 1);
        when(qualificationService.getAllQualifications(
                isNull(), isNull(), eq("LICENSE"), anyList(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                anyInt(), anyInt()
        )).thenReturn(mockPage);

        mockMvc.perform(get("/api/knowledge/qualifications")
                        .param("category", "LICENSE")
                        .param("status", "IN_STOCK")
                        .param("status", "EXPIRING")
                        .param("page", "0")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("测试证书"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.number").value(0));

        verify(qualificationService).getAllQualifications(
                isNull(), isNull(), eq("LICENSE"), anyList(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                anyInt(), anyInt()
        );
    }

    @Test
    @DisplayName("列表分页 - size 超过 200 自动截断到 200")
    void getAllQualifications_ShouldClampSizeAt200() throws Exception {
        Page<QualificationDTO> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 200), 0);
        when(qualificationService.getAllQualifications(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(200)
        )).thenReturn(mockPage);

        mockMvc.perform(get("/api/knowledge/qualifications")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk());

        verify(qualificationService).getAllQualifications(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(200)
        );
    }

    @Test
    @DisplayName("列表分页 - 负数 page 兜底 0，size=0 兜底 15")
    void getAllQualifications_ShouldClampInvalidPaginationToDefaults() throws Exception {
        Page<QualificationDTO> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 15), 0);
        when(qualificationService.getAllQualifications(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(15)
        )).thenReturn(mockPage);

        mockMvc.perform(get("/api/knowledge/qualifications")
                        .param("page", "-5")
                        .param("size", "0"))
                .andExpect(status().isOk());

        verify(qualificationService).getAllQualifications(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(15)
        );
    }

    @Test
    void retireQualification_ShouldAcceptValidReason() throws Exception {
        when(qualificationService.retireQualification(1L, "证书已过期且不再使用"))
                .thenReturn(QualificationDTO.builder().id(1L).status("retired").retireReason("证书已过期且不再使用").build());

        mockMvc.perform(post("/api/knowledge/qualifications/{id}/retire", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"证书已过期且不再使用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("retired"))
                .andExpect(jsonPath("$.data.retireReason").value("证书已过期且不再使用"));
    }
}
