package com.xiyu.bid.resources.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.dto.ExpenseReturnActionRequest;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.entity.ExpenseApprovalRecord;
import com.xiyu.bid.resources.repository.ExpenseApprovalRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
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
class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseApprovalRecordRepository approvalRecordRepository;

    @Autowired
    private BidResultFetchResultRepository bidResultFetchResultRepository;

    private Expense guaranteeExpense;
    private Expense normalExpense;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "auditLogExecutor")
        TaskExecutor auditLogExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.initialize();
            return executor;
        }

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
    void setUp() {
        approvalRecordRepository.deleteAll();
        bidResultFetchResultRepository.deleteAll();
        expenseRepository.deleteAll();

        guaranteeExpense = expenseRepository.save(Expense.builder()
                .projectId(1001L)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("保证金")
                .amount(new BigDecimal("120000.00"))
                .date(LocalDate.now().minusDays(2))
                .expectedReturnDate(LocalDate.now().plusDays(5))
                .description("投标保证金")
                .createdBy("creator")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build());

        bidResultFetchResultRepository.save(BidResultFetchResult.builder()
                .source("公开信息同步")
                .projectId(guaranteeExpense.getProjectId())
                .projectName("测试项目")
                .result(BidResultFetchResult.Result.LOST)
                .fetchTime(java.time.LocalDateTime.now().minusDays(1))
                .status(BidResultFetchResult.Status.CONFIRMED)
                .confirmedAt(java.time.LocalDateTime.now().minusDays(1))
                .confirmedBy(1L)
                .build());

        normalExpense = expenseRepository.save(Expense.builder()
                .projectId(1001L)
                .category(Expense.ExpenseCategory.MATERIAL)
                .expenseType("材料费")
                .amount(new BigDecimal("3000.00"))
                .date(LocalDate.now().minusDays(1))
                .description("材料采购")
                .createdBy("creator")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void approveExpense_ShouldPersistApprovedStateAndHistory() throws Exception {
        ExpenseApproveRequest request = new ExpenseApproveRequest();
        request.setResult(ExpenseApproveRequest.ApprovalResult.APPROVED);
        request.setApprover("manager");
        request.setComment("审批通过");

        mockMvc.perform(post("/api/resources/expenses/{id}/approve", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("manager"))
                .andExpect(jsonPath("$.data.approvalComment").value("审批通过"));

        Expense refreshed = expenseRepository.findById(guaranteeExpense.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(Expense.ExpenseStatus.APPROVED);
        assertThat(refreshed.getApprovedBy()).isEqualTo("manager");
        assertThat(refreshed.getApprovedAt()).isNotNull();

        List<ExpenseApprovalRecord> records =
                approvalRecordRepository.findByExpenseIdOrderByActedAtDesc(guaranteeExpense.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getResult()).isEqualTo(ExpenseApprovalRecord.ApprovalResult.APPROVED);
        assertThat(records.get(0).getApprover()).isEqualTo("manager");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void guaranteeExpenseReturnFlow_ShouldTransitionToReturned() throws Exception {
        approveGuaranteeExpense();

        ExpenseReturnActionRequest requestReturn = new ExpenseReturnActionRequest();
        requestReturn.setActor("cashier");
        requestReturn.setComment("申请退还保证金");

        mockMvc.perform(post("/api/resources/expenses/{id}/return-request", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestReturn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"))
                .andExpect(jsonPath("$.data.returnComment").value("申请退还保证金"));

        Expense afterRequest = expenseRepository.findById(guaranteeExpense.getId()).orElseThrow();
        assertThat(afterRequest.getStatus()).isEqualTo(Expense.ExpenseStatus.RETURN_REQUESTED);
        assertThat(afterRequest.getReturnRequestedAt()).isNotNull();

        ExpenseReturnActionRequest confirmReturn = new ExpenseReturnActionRequest();
        confirmReturn.setActor("manager");
        confirmReturn.setComment("确认已到账");

        mockMvc.perform(post("/api/resources/expenses/{id}/confirm-return", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReturn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.returnComment").value("确认已到账"));

        Expense afterConfirm = expenseRepository.findById(guaranteeExpense.getId()).orElseThrow();
        assertThat(afterConfirm.getStatus()).isEqualTo(Expense.ExpenseStatus.RETURNED);
        assertThat(afterConfirm.getReturnConfirmedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void nonGuaranteeExpenseReturnRequest_ShouldFail() throws Exception {
        ExpenseReturnActionRequest request = new ExpenseReturnActionRequest();
        request.setActor("cashier");
        request.setComment("尝试退还普通费用");

        mockMvc.perform(post("/api/resources/expenses/{id}/return-request", normalExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("Only deposit-like expenses can enter return flow"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void confirmReturnWithoutRequestState_ShouldFailWithConflict() throws Exception {
        ExpenseReturnActionRequest request = new ExpenseReturnActionRequest();
        request.setActor("manager");
        request.setComment("待审批状态直接确认退还");

        mockMvc.perform(post("/api/resources/expenses/{id}/confirm-return", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("Expense is not awaiting return confirmation"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void missingExpense_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/resources/expenses/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getApprovalRecords_ShouldReturnProjectHistory() throws Exception {
        approveGuaranteeExpense();

        mockMvc.perform(get("/api/resources/expenses/approval-records")
                        .param("projectId", guaranteeExpense.getProjectId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].expenseId").value(guaranteeExpense.getId().intValue()))
                .andExpect(jsonPath("$.data[0].approver").value("manager"))
                .andExpect(jsonPath("$.data[0].result").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void manualReturnReminder_ShouldPersistLastReminderTime() throws Exception {
        approveGuaranteeExpense();

        ExpenseReturnActionRequest request = new ExpenseReturnActionRequest();
        request.setActor("finance-user");
        request.setComment("财务手工提醒");

        mockMvc.perform(post("/api/resources/expenses/{id}/return-reminder", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(guaranteeExpense.getId()))
                .andExpect(jsonPath("$.data.expectedReturnDate").value(guaranteeExpense.getExpectedReturnDate().toString()))
                .andExpect(jsonPath("$.data.lastReturnReminderAt").isNotEmpty());

        Expense refreshed = expenseRepository.findById(guaranteeExpense.getId()).orElseThrow();
        assertThat(refreshed.getLastReturnReminderAt()).isNotNull();
    }

    private void approveGuaranteeExpense() throws Exception {
        ExpenseApproveRequest request = new ExpenseApproveRequest();
        request.setResult(ExpenseApproveRequest.ApprovalResult.APPROVED);
        request.setApprover("manager");
        request.setComment("进入退还流程前先审批");

        mockMvc.perform(post("/api/resources/expenses/{id}/approve", guaranteeExpense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
