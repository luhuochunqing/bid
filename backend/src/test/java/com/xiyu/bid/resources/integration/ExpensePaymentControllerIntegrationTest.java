package com.xiyu.bid.resources.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseApprovalRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class ExpensePaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseApprovalRecordRepository approvalRecordRepository;

    private Expense approvedExpense;
    private Expense pendingExpense;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new PasswordEncryptionUtil() {
                @Override
                public void initialize() {
                }

                @Override
                public String encrypt(String plainPassword) {
                    return plainPassword;
                }

                @Override
                public String decrypt(String encryptedPassword) {
                    return encryptedPassword;
                }

                @Override
                public boolean isKeyValid() {
                    return true;
                }
            };
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        approvalRecordRepository.deleteAll();
        expenseRepository.deleteAll();

        approvedExpense = expenseRepository.save(Expense.builder()
                .projectId(2001L)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("标书购买费")
                .amount(new BigDecimal("800.00"))
                .date(LocalDate.now().minusDays(3))
                .description("线上购买标书")
                .createdBy("creator")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build());

        pendingExpense = expenseRepository.save(Expense.builder()
                .projectId(2001L)
                .category(Expense.ExpenseCategory.TRANSPORTATION)
                .expenseType("差旅费")
                .amount(new BigDecimal("1200.00"))
                .date(LocalDate.now().minusDays(2))
                .description("现场踏勘交通")
                .createdBy("creator")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build());

        approveExpense(approvedExpense.getId(), "manager");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void registerPayment_ShouldMarkExpensePaidAndReturnPaymentRecord() throws Exception {
        mockMvc.perform(post("/api/resources/expenses/{id}/payments", approvedExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("800.00"),
                                "paidAt", LocalDateTime.now().minusHours(2),
                                "paidBy", "cashier",
                                "paymentReference", "PAY-20260419-001",
                                "paymentMethod", "BANK_TRANSFER",
                                "remark", "已完成付款"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(get("/api/resources/expenses/{id}/payments", approvedExpense.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].expenseId").value(approvedExpense.getId().intValue()))
                .andExpect(jsonPath("$.data[0].amount").value(800.00))
                .andExpect(jsonPath("$.data[0].paidBy").value("cashier"))
                .andExpect(jsonPath("$.data[0].paymentReference").value("PAY-20260419-001"))
                .andExpect(jsonPath("$.data[0].paymentMethod").value("BANK_TRANSFER"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void registerPayment_ShouldAllowAdditionalPaymentRecordsForPaidExpense() throws Exception {
        mockMvc.perform(post("/api/resources/expenses/{id}/payments", approvedExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("300.00"),
                                "paidAt", LocalDateTime.now().minusHours(3),
                                "paidBy", "cashier-a",
                                "paymentReference", "PAY-20260419-010",
                                "paymentMethod", "BANK_TRANSFER",
                                "remark", "首笔支付"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/resources/expenses/{id}/payments", approvedExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("500.00"),
                                "paidAt", LocalDateTime.now().minusHours(1),
                                "paidBy", "cashier-b",
                                "paymentReference", "PAY-20260419-011",
                                "paymentMethod", "CASH",
                                "remark", "尾款支付"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidBy").value("cashier-b"))
                .andExpect(jsonPath("$.data.paymentReference").value("PAY-20260419-011"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"));

        mockMvc.perform(get("/api/resources/expenses/{id}/payments", approvedExpense.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].paymentReference").value("PAY-20260419-011"))
                .andExpect(jsonPath("$.data[1].paymentReference").value("PAY-20260419-010"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void registerPayment_ForPendingExpense_ShouldFailWithConflict() throws Exception {
        mockMvc.perform(post("/api/resources/expenses/{id}/payments", pendingExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("1200.00"),
                                "paidAt", LocalDateTime.now().minusHours(1),
                                "paidBy", "cashier",
                                "paymentReference", "PAY-20260419-002",
                                "paymentMethod", "CASH",
                                "remark", "未审批先支付"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("Only approved or already-paid expenses can register payment records"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getPayments_WithoutRecords_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/resources/expenses/{id}/payments", pendingExpense.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getExpensesByProject_ShouldExposeLatestPaymentFields() throws Exception {
        mockMvc.perform(post("/api/resources/expenses/{id}/payments", approvedExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("800.00"),
                                "paidAt", LocalDateTime.now().minusHours(2),
                                "paidBy", "cashier-a",
                                "paymentReference", "PAY-20260419-003",
                                "paymentMethod", "BANK_TRANSFER",
                                "remark", "项目费用支付"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resources/expenses/project/{projectId}", approvedExpense.getProjectId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.id==" + approvedExpense.getId() + ")].paidBy", hasItem("cashier-a")))
                .andExpect(jsonPath("$.data.content[?(@.id==" + approvedExpense.getId() + ")].paymentReference", hasItem("PAY-20260419-003")))
                .andExpect(jsonPath("$.data.content[?(@.id==" + approvedExpense.getId() + ")].paymentMethod", hasItem("BANK_TRANSFER")));
    }

    private void approveExpense(Long expenseId, String approver) throws Exception {
        ExpenseApproveRequest request = new ExpenseApproveRequest();
        request.setResult(ExpenseApproveRequest.ApprovalResult.APPROVED);
        request.setApprover(approver);
        request.setComment("进入支付登记前先审批");

        mockMvc.perform(post("/api/resources/expenses/{id}/approve", expenseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
