// Input: ProjectQueryService 返回的项目列表（mock）
// Output: 窗口过滤 / 全状态参与 / 排序 / 字段映射的覆盖
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 商机预测查询服务测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectQueryService;
import com.xiyu.bid.workbench.dto.WorkbenchOpportunityDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkbenchOpportunityQueryService - 工作台商机预测")
class WorkbenchOpportunityQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Mock
    private ProjectQueryService projectQueryService;

    @InjectMocks
    private WorkbenchOpportunityQueryService service;

    private ProjectDTO.ProjectDTOBuilder baseProject() {
        return ProjectDTO.builder()
                .status(Project.Status.WON)
                .customerType("LOCAL_SOE")
                .projectType("OFFICE");
    }

    @Test
    @DisplayName("中标 + 央企截止时间规则：预测时间 = 截止前 6 个月，near=true")
    void shouldPredictCentralSoeByEndDate() {
        ProjectDTO project = baseProject()
                .id(1L).name("智慧水务一期工程")
                .customerType("CENTRAL_SOE")
                .customer("XX市水务局")
                .projectLeaderName("张明")
                .bidStatus("WON")
                .bidOpenTime(LocalDateTime.of(2021, 8, 15, 10, 0))
                .servicePeriodYears(new BigDecimal("5"))
                .servicePeriodEndDate(LocalDate.of(2027, 3, 14))
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(project));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(1, rows.size());
        WorkbenchOpportunityDTO row = rows.get(0);
        assertEquals(1L, row.projectId());
        assertEquals("智慧水务一期工程", row.projectName());
        assertEquals("WON", row.resultType());
        assertEquals("XX市水务局", row.customer());
        assertEquals("张明", row.projectLeaderName());
        assertEquals("CENTRAL_SOE", row.customerType());
        assertEquals("OFFICE", row.projectType());
        assertEquals("2021-08-15", row.bidOpenTime());
        assertEquals("2027-03-14", row.servicePeriodEndDate());
        assertEquals("2026-09-14", row.predictedBidDate()); // 截止 2027-03-14 提前 6 个月
        assertTrue(row.near()); // 距今天 27 天 ≤ 90 天
    }

    @Test
    @DisplayName("流标项目：预测时间 = 开标时间 + 2 周")
    void shouldPredictFailedProjectByPostponedBidOpenTime() {
        ProjectDTO project = baseProject()
                .id(2L).name("流标项目")
                .status(Project.Status.FAILED)
                .bidStatus("FAILED")
                .customer("XX市城管局")
                .bidOpenTime(LocalDateTime.of(2026, 8, 10, 9, 30))
                .servicePeriodEndDate(LocalDate.of(2027, 3, 14)) // 流标规则优先于截止时间
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(project));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(1, rows.size());
        assertEquals("FAILED", rows.get(0).resultType());
        assertEquals("2026-08-24", rows.get(0).predictedBidDate());
        assertTrue(rows.get(0).near());
    }

    @Test
    @DisplayName("无截止时间 + 有服务周期（年）：开标时间 + 服务周期，超 90 天不算临近")
    void shouldPredictByServicePeriodYears() {
        ProjectDTO project = baseProject()
                .id(3L).name("周期推算项目")
                .customer("XX市住建局")
                .bidStatus("WON")
                .bidOpenTime(LocalDateTime.of(2023, 12, 20, 14, 0))
                .servicePeriodYears(new BigDecimal("3"))
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(project));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(1, rows.size());
        assertEquals("2026-12-20", rows.get(0).predictedBidDate()); // 2023-12-20 + 3 年
        assertFalse(rows.get(0).near()); // 距今天 124 天 > 90 天
    }

    @Test
    @DisplayName("窗口过滤：预测时间超出未来 1 年或已过去的行不返回")
    void shouldFilterRowsOutsideOneYearWindow() {
        ProjectDTO tooLate = baseProject()
                .id(10L).name("窗口外-太远")
                .bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2028, 6, 1)) // 预测 2028-03-01 > 1 年
                .build();
        ProjectDTO alreadyPast = baseProject()
                .id(11L).name("窗口外-已过去")
                .bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 10, 1)) // 预测 2026-07-01 < 今天
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(tooLate, alreadyPast));

        assertTrue(service.getOpportunityPredictions(TODAY).isEmpty());
    }

    @Test
    @DisplayName("窗口过滤：预测时间在 6 到 12 个月内的商机应正常返回")
    void shouldIncludeRowsBetweenSixAndTwelveMonths() {
        ProjectDTO eightMonthsOut = baseProject()
                .id(12L).name("8个月后商机")
                .bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2027, 7, 1)) // 预测 2027-04-01，约 7.5 个月后（落入 1 年内）
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(eightMonthsOut));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);
        assertEquals(1, rows.size());
        assertEquals("2027-04-01", rows.get(0).predictedBidDate());
        assertFalse(rows.get(0).near());
    }

    @Test
    @DisplayName("全状态参与预测：未中标 / 投标中等非 WON/FAILED 项目同样返回")
    void shouldIncludeProjectsOfAllStatuses() {
        ProjectDTO lost = baseProject().id(20L).bidStatus("LOST")
                .servicePeriodEndDate(LocalDate.of(2026, 12, 1)).build(); // 预测 2026-09-01
        ProjectDTO bidding = baseProject().id(21L).status(Project.Status.BIDDING).bidStatus("BIDDING")
                .servicePeriodEndDate(LocalDate.of(2026, 12, 10)).build(); // 预测 2026-09-10
        ProjectDTO wonVisible = baseProject().id(23L).bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 12, 1)).build(); // 预测 2026-09-01
        ProjectDTO windowOut = baseProject().id(24L).bidStatus("LOST")
                .servicePeriodEndDate(LocalDate.of(2028, 6, 1)).build(); // 预测 2028-03-01，窗口外仍被过滤
        when(projectQueryService.getAllProjects()).thenReturn(List.of(lost, bidding, wonVisible, windowOut));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(3, rows.size());
        assertEquals(List.of("LOST", "WON", "BIDDING"),
                rows.stream().map(WorkbenchOpportunityDTO::resultType).toList()); // 按预测时间升序
        assertEquals(List.of(20L, 23L, 21L), rows.stream().map(WorkbenchOpportunityDTO::projectId).toList());
    }

    @Test
    @DisplayName("bidStatus 缺失时回退 Project.status 判断结果类型")
    void shouldFallbackToProjectStatusWhenBidStatusBlank() {
        ProjectDTO legacyWon = baseProject()
                .id(30L).name("历史中标项目")
                .status(Project.Status.WON)
                .bidStatus(null)
                .servicePeriodEndDate(LocalDate.of(2026, 12, 1))
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(legacyWon));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(1, rows.size());
        assertEquals("WON", rows.get(0).resultType());
    }

    @Test
    @DisplayName("排序与招标主体回退：按预测时间升序，customer 缺失时用业主单位")
    void shouldSortByPredictedDateAndFallbackCustomer() {
        ProjectDTO later = baseProject()
                .id(40L).name("较晚商机")
                .customer("XX市水务集团")
                .bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2027, 2, 10)) // 预测 2026-11-10
                .build();
        ProjectDTO earlier = baseProject()
                .id(41L).name("较早商机")
                .customer("   ") // 空白 → 回退 ownerUnit
                .ownerUnit("XX区教育局")
                .bidStatus("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 11, 20)) // 预测 2026-08-20
                .build();
        when(projectQueryService.getAllProjects()).thenReturn(List.of(later, earlier));

        List<WorkbenchOpportunityDTO> rows = service.getOpportunityPredictions(TODAY);

        assertEquals(List.of(41L, 40L), rows.stream().map(WorkbenchOpportunityDTO::projectId).toList());
        assertEquals("XX区教育局", rows.get(0).customer());
        assertEquals("2026-08-20", rows.get(0).predictedBidDate());
        assertEquals("2026-11-10", rows.get(1).predictedBidDate());
    }
}
