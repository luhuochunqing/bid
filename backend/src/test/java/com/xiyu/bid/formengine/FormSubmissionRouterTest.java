package com.xiyu.bid.formengine;

import com.xiyu.bid.contractborrow.application.service.ContractBorrowCommandAppService;
import com.xiyu.bid.formengine.application.FormSubmissionRouter;
import com.xiyu.bid.formengine.domain.SubmitResult;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.qualification.service.QualificationService;
import com.xiyu.bid.resources.service.BarCertificateService;
import com.xiyu.bid.resources.service.ExpenseService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FormSubmissionRouter 单元测试。
 * 测试 dispatch() 对 tender.entry 和 project.basic 的路由分发，
 * 以及字段映射的正确性。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FormSubmissionRouter")
class FormSubmissionRouterTest {

    @Mock
    private TenderCommandService tenderCommandService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private BarCertificateService barCertificateService;
    @Mock
    private QualificationService qualificationService;
    @Mock
    private ContractBorrowCommandAppService contractBorrowCommandAppService;
    @Mock
    private AuthService authService;

    private FormSubmissionRouter router;

    @BeforeEach
    void setUp() {
        router = new FormSubmissionRouter(tenderCommandService, projectService, expenseService, barCertificateService, qualificationService, contractBorrowCommandAppService, authService);
        lenient().when(authService.resolveUserIdByUsername(any())).thenReturn(1L);
    }

    // ==================== tender.entry ====================

    @Nested
    @DisplayName("tender.entry")
    class TenderEntry {

        @Test
        @DisplayName("完整字段映射到 TenderDTO")
        void handleTender_allFields_mappedCorrectly() {
            Map<String, Object> formData = Map.of(
                    "title", "测试标讯",
                    "source", "bidding",
                    "budget", 500000,
                    "region", "北京",
                    "deadline", "2026-12-31T23:59:59",
                    "publishDate", "2026-01-01",
                    "contactName", "张三",
                    "contactPhone", "13800138000",
                    "description", "这是一条测试标讯",
                    "tags", List.of("公开招标", "智慧城市")
            );

            TenderDTO savedDto = TenderDTO.builder().id(99L).title("测试标讯").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
            assertThat(result.data()).isNotNull();

            ArgumentCaptor<TenderDTO> captor = ArgumentCaptor.forClass(TenderDTO.class);
            verify(tenderCommandService).createTender(captor.capture(), any());
            TenderDTO captured = captor.getValue();

            assertThat(captured.getTitle()).isEqualTo("测试标讯");
            assertThat(captured.getSource()).isEqualTo("bidding");
            assertThat(captured.getBudget()).isEqualByComparingTo(BigDecimal.valueOf(500000));
            assertThat(captured.getRegion()).isEqualTo("北京");
            assertThat(captured.getDeadline().getYear()).isEqualTo(2026);
            assertThat(captured.getDeadline().getMonthValue()).isEqualTo(12);
            assertThat(captured.getDeadline().getDayOfMonth()).isEqualTo(31);
            assertThat(captured.getPublishDate().getYear()).isEqualTo(2026);
            assertThat(captured.getContactName()).isEqualTo("张三");
            assertThat(captured.getContactPhone()).isEqualTo("13800138000");
            assertThat(captured.getDescription()).isEqualTo("这是一条测试标讯");
            assertThat(captured.getTags()).containsExactly("公开招标", "智慧城市");
        }

        @Test
        @DisplayName("仅必填字段 title + deadline")
        void handleTender_onlyRequiredFields() {
            Map<String, Object> formData = Map.of(
                    "title", "最小标讯",
                    "deadline", "2027-06-30T12:00:00"
            );

            TenderDTO savedDto = TenderDTO.builder().id(1L).title("最小标讯").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
            verify(tenderCommandService).createTender(any(TenderDTO.class), any());
        }

        @Test
        @DisplayName("空字段被忽略，不抛异常")
        void handleTender_emptyFields_ignored() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("title", "有空字段的标讯");
            formData.put("deadline", "2026-12-31T23:59:59");
            formData.put("budget", "");
            formData.put("region", "");
            formData.put("description", null);

            TenderDTO savedDto = TenderDTO.builder().id(2L).title("有空字段的标讯").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("tags 为逗号分隔字符串")
        void handleTender_tagsAsCommaString() {
            Map<String, Object> formData = Map.of(
                    "title", "标签测试",
                    "deadline", "2026-12-31T23:59:59",
                    "tags", "政府,央企,智慧城市"
            );

            TenderDTO savedDto = TenderDTO.builder().id(3L).title("标签测试").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
            ArgumentCaptor<TenderDTO> captor = ArgumentCaptor.forClass(TenderDTO.class);
            verify(tenderCommandService).createTender(captor.capture(), any());
            assertThat(captor.getValue().getTags()).containsExactly("政府", "央企", "智慧城市");
        }

        @Test
        @DisplayName("Service 抛异常时返回 failure")
        void handleTender_serviceThrows_returnsFailure() {
            Map<String, Object> formData = Map.of(
                    "title", "异常测试",
                    "deadline", "2026-12-31T23:59:59"
            );
            when(tenderCommandService.createTender(any(TenderDTO.class), any()))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("创建标讯失败");
        }
    }

    // ==================== project.basic ====================

    @Nested
    @DisplayName("project.basic")
    class ProjectBasic {

        @Test
        @DisplayName("完整字段映射到 ProjectDTO")
        void handleProject_allFields_mappedCorrectly() {
            Map<String, Object> formData = Map.of(
                    "name", "测试项目",
                    "managerId", 10L,
                    "tenderId", 5L,
                    "budget", 1000000,
                    "industry", "government",
                    "startDate", "2026-01-01",
                    "endDate", "2026-12-31",
                    "description", "项目描述",
                    "tags", List.of("智慧城市", "政府")
            );

            ProjectDTO savedDto = ProjectDTO.builder().id(77L).name("测试项目").build();
            when(projectService.createProject(any(ProjectDTO.class))).thenReturn(savedDto);

            SubmitResult result = router.dispatch("project.basic", formData, "admin");

            assertThat(result.success()).isTrue();
            assertThat(result.data()).isNotNull();

            ArgumentCaptor<ProjectDTO> captor = ArgumentCaptor.forClass(ProjectDTO.class);
            verify(projectService).createProject(captor.capture());
            ProjectDTO captured = captor.getValue();

            assertThat(captured.getName()).isEqualTo("测试项目");
            assertThat(captured.getManagerId()).isEqualTo(10L);
            assertThat(captured.getTenderId()).isEqualTo(5L);
            assertThat(captured.getBudget()).isEqualByComparingTo(BigDecimal.valueOf(1000000));
            assertThat(captured.getIndustry()).isEqualTo("government");
            assertThat(captured.getStartDate().getYear()).isEqualTo(2026);
            assertThat(captured.getEndDate().getYear()).isEqualTo(2026);
            assertThat(captured.getDescription()).isEqualTo("项目描述");
            assertThat(captured.getTagsJson()).contains("智慧城市", "政府");
        }

        @Test
        @DisplayName("仅必填字段 name + managerId")
        void handleProject_onlyRequiredFields() {
            Map<String, Object> formData = Map.of(
                    "name", "最小项目",
                    "managerId", 1L
            );

            ProjectDTO savedDto = ProjectDTO.builder().id(1L).name("最小项目").build();
            when(projectService.createProject(any(ProjectDTO.class))).thenReturn(savedDto);

            SubmitResult result = router.dispatch("project.basic", formData, "admin");

            assertThat(result.success()).isTrue();
            verify(projectService).createProject(any(ProjectDTO.class));
        }

        @Test
        @DisplayName("teamMembers 列表字段映射")
        void handleProject_teamMembers_listMapped() {
            Map<String, Object> formData = Map.of(
                    "name", "团队项目",
                    "managerId", 5L,
                    "teamMembers", List.of(1L, 2L, 3L)
            );

            ProjectDTO savedDto = ProjectDTO.builder().id(2L).name("团队项目").build();
            when(projectService.createProject(any(ProjectDTO.class))).thenReturn(savedDto);

            SubmitResult result = router.dispatch("project.basic", formData, "admin");

            assertThat(result.success()).isTrue();
            ArgumentCaptor<ProjectDTO> captor = ArgumentCaptor.forClass(ProjectDTO.class);
            verify(projectService).createProject(captor.capture());
            assertThat(captor.getValue().getTeamMembers()).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("Service 抛异常时返回 failure")
        void handleProject_serviceThrows_returnsFailure() {
            Map<String, Object> formData = Map.of(
                    "name", "异常项目",
                    "managerId", 1L
            );
            when(projectService.createProject(any(ProjectDTO.class)))
                    .thenThrow(new RuntimeException("项目名重复"));

            SubmitResult result = router.dispatch("project.basic", formData, "admin");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("创建项目失败");
        }
    }

    // ==================== 未知 scope ====================

    @Nested
    @DisplayName("未知 scope")
    class UnknownScope {

        @Test
        @DisplayName("未知 scope 返回 failure")
        void unknownScope_returnsFailure() {
            SubmitResult result = router.dispatch("unknown.scope", Map.of(), "admin");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("不支持");
        }

        @Test
        @DisplayName("空 scope 返回 failure")
        void emptyScope_returnsFailure() {
            SubmitResult result = router.dispatch("", Map.of(), "admin");

            assertThat(result.success()).isFalse();
        }
    }

    // ==================== 类型转换边界 ====================

    @Nested
    @DisplayName("类型转换边界")
    class TypeConversion {

        @Test
        @DisplayName("budget 为字符串时正常解析")
        void budget_asString_parsedCorrectly() {
            Map<String, Object> formData = Map.of(
                    "title", "预算字符串测试",
                    "deadline", "2026-12-31T23:59:59",
                    "budget", "999999.99"
            );

            TenderDTO savedDto = TenderDTO.builder().id(1L).title("预算字符串测试").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
            ArgumentCaptor<TenderDTO> captor = ArgumentCaptor.forClass(TenderDTO.class);
            verify(tenderCommandService).createTender(captor.capture(), any());
            assertThat(captor.getValue().getBudget()).isEqualByComparingTo("999999.99");
        }

        @Test
        @DisplayName("deadline 为仅日期格式时兼容处理（转为当天 00:00:00）")
        void deadline_dateOnly_compatible() {
            Map<String, Object> formData = Map.of(
                    "title", "日期格式测试",
                    "deadline", "2026-06-15"
            );

            TenderDTO savedDto = TenderDTO.builder().id(1L).title("日期格式测试").build();
            when(tenderCommandService.createTender(any(TenderDTO.class), any())).thenReturn(savedDto);

            SubmitResult result = router.dispatch("tender.entry", formData, "admin");

            assertThat(result.success()).isTrue();
            ArgumentCaptor<TenderDTO> captor = ArgumentCaptor.forClass(TenderDTO.class);
            verify(tenderCommandService).createTender(captor.capture(), any());
            assertThat(captor.getValue().getDeadline().getYear()).isEqualTo(2026);
            assertThat(captor.getValue().getDeadline().getMonthValue()).isEqualTo(6);
            assertThat(captor.getValue().getDeadline().getDayOfMonth()).isEqualTo(15);
        }
    }
}
