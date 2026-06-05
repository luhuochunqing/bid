package com.xiyu.bid.demo.service;

import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.resources.dto.BarAssetResponseDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.project.dto.ProjectDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * In-memory demo dataset provider for e2e(H2) profile.
 *
 * <p>Maintenance contract (v1):
 * <ul>
 *   <li>Demo records must use negative IDs to avoid conflicts with DB identities.</li>
 *   <li>This provider is read-only. Do not add write/persistence behavior here.</li>
 *   <li>DTO field shape must stay aligned with production API contracts.</li>
 *   <li>When adding fields, update tests in DemoDataProviderTest and fusion tests together.</li>
 * </ul>
 */
@Component
public class DemoDataProvider {

    public List<ProjectDTO> getDemoProjects() {
        return List.of(
                demoProject(-101L, "某央企智慧办公平台", Project.Status.BIDDING, 1001L, "某央企集团", "北京", new BigDecimal("5000000"), 5),
                demoProject(-102L, "华南电力集团集采项目", Project.Status.EVALUATING, 1002L, "华南电力集团", "广州", new BigDecimal("12000000"), 11),
                demoProject(-103L, "西部云数据中心建设", Project.Status.BIDDING, 1003L, "西部云科技", "西安", new BigDecimal("18000000"), 2),
                demoProject(-104L, "制造执行系统(MES)", Project.Status.BIDDING, 1004L, "某制造企业", "苏州", new BigDecimal("8600000"), 16)
        );
    }

    public Optional<ProjectDTO> findDemoProjectById(Long id) {
        return getDemoProjects().stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public List<TenderDTO> getDemoTenders() {
        return List.of(
                TenderDTO.builder()
                        .id(-201L)
                        .title("某央企智慧办公平台采购项目")
                        .source("DEMO")
                        .budget(new BigDecimal("500"))
                        .region("北京")
                        .status(com.xiyu.bid.entity.Tender.Status.TRACKING)
                        .aiScore(92)
                        .riskLevel(com.xiyu.bid.entity.Tender.RiskLevel.LOW)
                        .deadline(LocalDateTime.now().plusDays(12))
                        .createdAt(LocalDateTime.now().minusDays(3))
                        .build(),
                TenderDTO.builder()
                        .id(-202L)
                        .title("XX市大数据中心建设项目")
                        .source("DEMO")
                        .budget(new BigDecimal("800"))
                        .region("深圳")
                        .status(com.xiyu.bid.entity.Tender.Status.TRACKING)
                        .aiScore(85)
                        .riskLevel(com.xiyu.bid.entity.Tender.RiskLevel.MEDIUM)
                        .deadline(LocalDateTime.now().plusDays(9))
                        .createdAt(LocalDateTime.now().minusDays(5))
                        .build(),
                TenderDTO.builder()
                        .id(-203L)
                        .title("某省政务云平台采购")
                        .source("DEMO")
                        .budget(new BigDecimal("1200"))
                        .region("杭州")
                        .status(com.xiyu.bid.entity.Tender.Status.TRACKING)
                        .aiScore(78)
                        .riskLevel(com.xiyu.bid.entity.Tender.RiskLevel.MEDIUM)
                        .deadline(LocalDateTime.now().plusDays(16))
                        .createdAt(LocalDateTime.now().minusDays(2))
                        .build()
        );
    }

    public Optional<TenderDTO> findDemoTenderById(Long id) {
        return getDemoTenders().stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public List<BarAssetResponseDTO> getDemoBarAssets() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                BarAssetResponseDTO.builder()
                        .id(-301L)
                        .name("华北站点 BAR 演示资产")
                        .type(BarAssetResponseDTO.AssetType.FACILITY)
                        .value(new BigDecimal("68000"))
                        .status(BarAssetResponseDTO.AssetStatus.AVAILABLE)
                        .acquireDate(LocalDate.now().minusMonths(8))
                        .remark("H2 演示资产")
                        .createdAt(now.minusDays(12))
                        .updatedAt(now.minusDays(1))
                        .build(),
                BarAssetResponseDTO.builder()
                        .id(-302L)
                        .name("华南站点 BAR 演示资产")
                        .type(BarAssetResponseDTO.AssetType.EQUIPMENT)
                        .value(new BigDecimal("42000"))
                        .status(BarAssetResponseDTO.AssetStatus.IN_USE)
                        .acquireDate(LocalDate.now().minusMonths(5))
                        .remark("H2 演示资产")
                        .createdAt(now.minusDays(10))
                        .updatedAt(now.minusDays(2))
                        .build()
        );
    }

    public Optional<BarAssetResponseDTO> findDemoBarAssetById(Long id) {
        return getDemoBarAssets().stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public List<CalendarEventDTO> getDemoScheduleEvents(LocalDate start, LocalDate end) {
        List<CalendarEventDTO> candidates = List.of(
                calendarEvent(-401L, "央企项目截标", EventType.DEADLINE, LocalDate.now().plusDays(2), -101L, true),
                calendarEvent(-402L, "电力项目评审会", EventType.REVIEW, LocalDate.now().plusDays(5), -102L, false),
                calendarEvent(-403L, "数据中心开标", EventType.SUBMISSION, LocalDate.now().plusDays(9), -103L, true),
                calendarEvent(-404L, "MES 里程碑检查", EventType.MILESTONE, LocalDate.now().plusDays(14), -104L, false)
        );
        return candidates.stream()
                .filter(event -> !event.getEventDate().isBefore(start) && !event.getEventDate().isAfter(end))
                .toList();
    }

    public SummaryStats getDemoSummaryStats() {
        return SummaryStats.builder()
                .totalTenders(12L)
                .activeProjects(8L)
                .pendingTasks(23L)
                .totalBudget(new BigDecimal("32800000"))
                .successRate(41.7)
                .build();
    }

    public List<TrendData> getDemoTenderTrends() {
        return List.of(
                trend("2026-01", 5L, 8600000, 8.3),
                trend("2026-02", 7L, 11200000, 16.0),
                trend("2026-03", 9L, 13800000, 28.6),
                trend("2026-04", 6L, 9200000, -12.5)
        );
    }

    public List<TrendData> getDemoProjectTrends() {
        return List.of(
                trend("2026-01", 4L, 7000000, 5.0),
                trend("2026-02", 6L, 9300000, 14.0),
                trend("2026-03", 8L, 11800000, 21.0),
                trend("2026-04", 5L, 8600000, -10.0)
        );
    }

    public List<CompetitorData> getDemoCompetitors() {
        return List.of(
                CompetitorData.builder().name("A公司").bidCount(11L).winCount(4L).winRate(36.4).totalBidAmount(new BigDecimal("9600000")).build(),
                CompetitorData.builder().name("B公司").bidCount(9L).winCount(3L).winRate(33.3).totalBidAmount(new BigDecimal("8400000")).build(),
                CompetitorData.builder().name("C公司").bidCount(7L).winCount(2L).winRate(28.6).totalBidAmount(new BigDecimal("6700000")).build()
        );
    }

    public List<RegionalData> getDemoRegions() {
        return List.of(
                RegionalData.builder().region("华北").tenderCount(8L).totalBudget(new BigDecimal("12600000")).percentage(34.0).build(),
                RegionalData.builder().region("华东").tenderCount(7L).totalBudget(new BigDecimal("10800000")).percentage(30.0).build(),
                RegionalData.builder().region("华南").tenderCount(5L).totalBudget(new BigDecimal("7600000")).percentage(21.0).build(),
                RegionalData.builder().region("西部").tenderCount(4L).totalBudget(new BigDecimal("5800000")).percentage(15.0).build()
        );
    }

    public List<ProductLineData> getDemoProductLines() {
        return List.of(
                ProductLineData.builder().name("数字化平台").revenue(new BigDecimal("14600000")).cost(new BigDecimal("8600000")).bids(9L).rate(44.0).build(),
                ProductLineData.builder().name("基础设施").revenue(new BigDecimal("9800000")).cost(new BigDecimal("6700000")).bids(7L).rate(36.0).build(),
                ProductLineData.builder().name("行业应用").revenue(new BigDecimal("8200000")).cost(new BigDecimal("5900000")).bids(6L).rate(31.0).build()
        );
    }

    private ProjectDTO demoProject(
            Long id,
            String name,
            Project.Status status,
            Long managerId,
            String customer,
            String region,
            BigDecimal budget,
            int deadlineDays
    ) {
        LocalDateTime now = LocalDateTime.now();
        return ProjectDTO.builder()
                .id(id)
                .name(name)
                .tenderId(Math.abs(id) + 5000)
                .status(status)
                .managerId(managerId)
                .teamMembers(List.of(managerId))
                .customer(customer)
                .region(region)
                .budget(budget)
                .endDate(now.plusDays(deadlineDays))
                .deadline(LocalDate.now().plusDays(deadlineDays))
                .customerManager("演示负责人")
                .createdAt(now.minusDays(5))
                .updatedAt(now.minusDays(1))
                .build();
    }

    private CalendarEventDTO calendarEvent(
            Long id,
            String title,
            EventType eventType,
            LocalDate eventDate,
            Long projectId,
            boolean urgent
    ) {
        LocalDateTime now = LocalDateTime.now();
        return CalendarEventDTO.builder()
                .id(id)
                .title(title)
                .eventType(eventType)
                .eventDate(eventDate)
                .projectId(projectId)
                .isUrgent(urgent)
                .description("H2 Demo 事件")
                .createdAt(now.minusDays(1))
                .updatedAt(now)
                .build();
    }

    private TrendData trend(String period, long count, double value, double change) {
        return TrendData.builder()
                .period(period)
                .count(count)
                .value(new BigDecimal(String.valueOf(value)))
                .changePercentage(change)
                .build();
    }
}
