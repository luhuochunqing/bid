package com.xiyu.bid.contractborrow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.contractborrow.application.service.ContractBorrowCommandAppService;
import com.xiyu.bid.contractborrow.application.service.ContractBorrowQueryAppService;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowEventView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowOverviewView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowPageView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowView;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowEventType;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractBorrowControllerTest {

    private final ContractBorrowQueryAppService queryService = mock(ContractBorrowQueryAppService.class);
    private final ContractBorrowCommandAppService commandService = mock(ContractBorrowCommandAppService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractBorrowController(queryService, commandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOverview_ShouldExposeContractBorrowCounts() throws Exception {
        when(queryService.overview()).thenReturn(new ContractBorrowOverviewView(4, 1, 1, 0, 1, 1, 0, 1));

        mockMvc.perform(get("/api/contract-borrows/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.overdue").value(1));
    }

    @Test
    void list_ShouldExposePagedContractBorrowRows() throws Exception {
        when(queryService.page(any(), any())).thenReturn(new ContractBorrowPageView(
                List.of(sampleView(ContractBorrowStatus.APPROVED, "OVERDUE")),
                21,
                2,
                10,
                3
        ));

        mockMvc.perform(get("/api/contract-borrows")
                        .param("keyword", "智算")
                        .param("status", "OVERDUE")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void create_ShouldDelegateToCommandService() throws Exception {
        when(commandService.create(any())).thenReturn(sampleView(ContractBorrowStatus.PENDING_APPROVAL, "PENDING_APPROVAL"));

        mockMvc.perform(post("/api/contract-borrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContractBorrowRequest(
                                "HT-2026-0421",
                                "西域智算中心年度框架合同",
                                "法务归档室",
                                "小王",
                                "销售一部",
                                "西域智算中心",
                                "投标文件复核",
                                "原件借阅",
                                LocalDate.now().plusDays(7)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        verify(commandService).create(any());
    }

    @Test
    void create_BlankRequiredFields_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/contract-borrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContractBorrowRequest(
                                "",
                                "",
                                "法务归档室",
                                "",
                                "销售一部",
                                "西域智算中心",
                                "",
                                "原件借阅",
                                LocalDate.now().plusDays(7)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("合同编号不能为空")));
    }

    @Test
    void approveRejectReturnCancel_ShouldUseDedicatedLifecycleEndpoints() throws Exception {
        when(commandService.approve(eq(9L), any())).thenReturn(sampleView(ContractBorrowStatus.APPROVED, "APPROVED"));
        when(commandService.reject(eq(9L), any())).thenReturn(sampleView(ContractBorrowStatus.REJECTED, "REJECTED"));
        when(commandService.returnBack(eq(9L), any())).thenReturn(sampleView(ContractBorrowStatus.RETURNED, "RETURNED"));
        when(commandService.cancel(eq(9L), any())).thenReturn(sampleView(ContractBorrowStatus.CANCELLED, "CANCELLED"));

        mockMvc.perform(post("/api/contract-borrows/{id}/approve", 9L)
                        .principal(new TestingAuthenticationToken("审计用户", "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorName\":\"张经理\",\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(post("/api/contract-borrows/{id}/reject", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorName\":\"张经理\",\"reason\":\"信息不完整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(post("/api/contract-borrows/{id}/return", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorName\":\"小王\",\"comment\":\"已归还\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));
        mockMvc.perform(post("/api/contract-borrows/{id}/cancel", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorName\":\"小王\",\"reason\":\"不再需要\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(commandService).approve(eq(9L), argThat(command -> "审计用户".equals(command.actorName())));
    }

    @Test
    void events_ShouldReturnApplicationEventHistory() throws Exception {
        when(queryService.events(9L)).thenReturn(List.of(new ContractBorrowEventView(
                3L,
                9L,
                ContractBorrowEventType.APPROVED,
                ContractBorrowStatus.APPROVED,
                "张经理",
                "同意",
                LocalDateTime.of(2026, 4, 21, 11, 0)
        )));

        mockMvc.perform(get("/api/contract-borrows/{id}/events", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("APPROVED"))
                .andExpect(jsonPath("$.data[0].statusAfter").value("APPROVED"));
    }

    private ContractBorrowView sampleView(ContractBorrowStatus status, String displayStatus) {
        return new ContractBorrowView(
                9L,
                "HT-2026-0421",
                "西域智算中心年度框架合同",
                "法务归档室",
                "小王",
                "销售一部",
                "西域智算中心",
                "投标文件复核",
                "原件借阅",
                LocalDate.of(2026, 4, 30),
                LocalDateTime.of(2026, 4, 21, 10, 30),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                displayStatus,
                false
        );
    }
}
