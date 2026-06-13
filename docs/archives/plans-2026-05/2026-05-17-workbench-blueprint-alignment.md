# 工作台产品蓝图对齐 - 实施计划

> **For Agent:** 使用 superpowers:subagent-driven-development 按任务并行实施。每个 Task 完成后立即 code-review。

**Goal:** 将工作台6个要素对齐飞书产品蓝图 V1.1 的要求，核心改动是指标卡片从通用统计改为 deadline-based 分时段节点统计，并增强快捷入口、日程、待办、项目列表、AI预测入口。

**Architecture:** 后端遵循 FP-Java Profile + Split-First Rule（纯核心→应用服务→Controller），前端遵循 pure-core→composable→component 分层。新建分支 `codex/workbench-blueprint-alignment`。

**Tech Stack:** Java 21 / Spring Boot 3.3 / JPA / Vue 3 / Element Plus

---

## Task 0: 环境准备（同步 + 建分支）

**Files:**
- 无新建文件

**Step 1: 同步远端代码**
```bash
cd /Users/user/xiyu/worktrees/codex && git fetch origin
```

**Step 2: 确保工作区干净**
```bash
cd /Users/user/xiyu/worktrees/codex && git status
```
Expected: clean working tree (or only unrelated changes)

**Step 3: 从 main 创建新分支**
```bash
cd /Users/user/xiyu/worktrees/codex && git switch -c codex/workbench-blueprint-alignment
```

---

## Task 1: 后端 - 新建纯核心 DeadlinePolicy（分钟级）

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/workbench/domain/WorkbenchDeadlinePolicy.java`
- Create: `backend/src/test/java/com/xiyu/bid/workbench/domain/WorkbenchDeadlinePolicyTest.java`
- Create: `backend/src/main/java/com/xiyu/bid/workbench/domain/package-info.java`

**职责**: 纯核心，计算 deadline 时间窗口（本日/本周/本月）边界，聚合 deadline 统计结果。不依赖任何框架。

### Step 1: 写测试 RED

`WorkbenchDeadlinePolicyTest.java`:
```java
package com.xiyu.bid.workbench.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchDeadlinePolicyTest {

    @Test
    void shouldReturnTodayWeekMonthBounds() {
        var now = LocalDate.of(2026, 5, 17); // Sunday
        var bounds = WorkbenchDeadlinePolicy.computeTimeWindows(now);
        assertThat(bounds.todayStart()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(bounds.todayEnd()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(bounds.weekStart()).isEqualTo(LocalDate.of(2026, 5, 11)); // Monday
        assertThat(bounds.weekEnd()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(bounds.monthStart()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(bounds.monthEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void shouldCountByWindow() {
        var registrationDeadlines = List.of(
            LocalDateTime.of(2026, 5, 17, 10, 0), // today
            LocalDateTime.of(2026, 5, 16, 10, 0), // this week
            LocalDateTime.of(2026, 5, 3, 10, 0)   // this month
        );
        var bounds = WorkbenchDeadlinePolicy.computeTimeWindows(LocalDate.of(2026, 5, 17));
        var result = WorkbenchDeadlinePolicy.countByTimeWindow(registrationDeadlines, bounds);
        assertThat(result.todayCount()).isEqualTo(1);
        assertThat(result.weekCount()).isEqualTo(2);
        assertThat(result.monthCount()).isEqualTo(3);
    }

    @Test
    void shouldHandleEmptyDeadlines() {
        var bounds = WorkbenchDeadlinePolicy.computeTimeWindows(LocalDate.of(2026, 5, 17));
        var result = WorkbenchDeadlinePolicy.countByTimeWindow(List.of(), bounds);
        assertThat(result.todayCount()).isZero();
        assertThat(result.weekCount()).isZero();
        assertThat(result.monthCount()).isZero();
    }

    @Test
    void shouldHandleNullDeadlines() {
        var bounds = WorkbenchDeadlinePolicy.computeTimeWindows(LocalDate.of(2026, 5, 17));
        var deadlines = List.of(
            LocalDateTime.of(2026, 5, 17, 10, 0),
            null,
            LocalDateTime.of(2026, 5, 1, 0, 0)
        );
        var result = WorkbenchDeadlinePolicy.countByTimeWindow(deadlines, bounds);
        assertThat(result.todayCount()).isEqualTo(1);
        assertThat(result.monthCount()).isEqualTo(2);
    }

    @Test
    void shouldBuildDeadlineStats() {
        var today = LocalDate.of(2026, 5, 17);
        var regDeadlines = List.of(LocalDateTime.of(2026, 5, 17, 10, 0));
        var openingDeadlines = List.of(LocalDateTime.of(2026, 5, 16, 10, 0));
        var depositDeadlines = List.of(LocalDateTime.of(2026, 5, 3, 10, 0));

        var stats = WorkbenchDeadlinePolicy.buildDeadlineStats(
            today, regDeadlines, openingDeadlines, depositDeadlines
        );

        assertThat(stats.registrationDeadline().todayCount()).isEqualTo(1);
        assertThat(stats.bidOpening().weekCount()).isEqualTo(1);
        assertThat(stats.depositDeadline().monthCount()).isEqualTo(1);
    }
}
```

Run: `cd /Users/user/xiyu/worktrees/codex/backend && mvn test -pl . -Dtest=WorkbenchDeadlinePolicyTest -DfailIfNoTests=false`
Expected: compilation FAIL (class not found)

### Step 2: 最小实现 GREEN

`WorkbenchDeadlinePolicy.java` (record-based, pure, no framework):
```java
package com.xiyu.bid.workbench.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Objects;

public final class WorkbenchDeadlinePolicy {

    private WorkbenchDeadlinePolicy() {}

    public record TimeWindowBounds(
        LocalDate todayStart, LocalDate todayEnd,
        LocalDate weekStart, LocalDate weekEnd,
        LocalDate monthStart, LocalDate monthEnd
    ) {}

    public record WindowCounts(long todayCount, long weekCount, long monthCount) {
        public static final WindowCounts ZERO = new WindowCounts(0, 0, 0);
    }

    public record DeadlineTypeStats(
        WindowCounts registrationDeadline,
        WindowCounts bidOpening,
        WindowCounts depositDeadline
    ) {}

    public record WorkbenchDeadlineStats(
        DeadlineTypeStats registrationDeadline,
        DeadlineTypeStats bidOpening,
        DeadlineTypeStats depositDeadline
    ) {}

    public static TimeWindowBounds computeTimeWindows(LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new TimeWindowBounds(today, today, weekStart, today, monthStart, monthEnd);
    }

    public static WindowCounts countByTimeWindow(Collection<LocalDateTime> deadlines, TimeWindowBounds bounds) {
        if (deadlines == null || deadlines.isEmpty()) return WindowCounts.ZERO;
        long today = 0, week = 0, month = 0;
        for (LocalDateTime dt : deadlines) {
            if (dt == null) continue;
            LocalDate date = dt.toLocalDate();
            if (!date.isBefore(bounds.todayStart) && !date.isAfter(bounds.todayEnd)) today++;
            if (!date.isBefore(bounds.weekStart) && !date.isAfter(bounds.weekEnd)) week++;
            if (!date.isBefore(bounds.monthStart) && !date.isAfter(bounds.monthEnd)) month++;
        }
        return new WindowCounts(today, week, month);
    }

    public static WorkbenchDeadlineStats buildDeadlineStats(
        LocalDate today,
        Collection<LocalDateTime> registrationDeadlines,
        Collection<LocalDateTime> bidOpeningDeadlines,
        Collection<LocalDateTime> depositDeadlines
    ) {
        var bounds = computeTimeWindows(today);
        return new WorkbenchDeadlineStats(
            new DeadlineTypeStats(countByTimeWindow(registrationDeadlines, bounds)),
            new DeadlineTypeStats(countByTimeWindow(bidOpeningDeadlines, bounds)),
            new DeadlineTypeStats(countByTimeWindow(depositDeadlines, bounds))
        );
    }
}
```

Run: `mvn test -pl . -Dtest=WorkbenchDeadlinePolicyTest`
Expected: all 5 tests PASS

### Step 3: Commit
```bash
git add backend/src/main/java/com/xiyu/bid/workbench/domain/
git add backend/src/test/java/com/xiyu/bid/workbench/domain/
git commit -m "feat(workbench): add WorkbenchDeadlinePolicy pure core"
```

---

## Task 2（修订）: 后端 - 查询服务 + DTO（含权限过滤）

**关键**: 蓝图要求「分权限展示」，必须复用 `ProjectAccessScopeService` 做项目级数据过滤。

**数据权限链路**: `SecurityContext → ProjectAccessScopeService.getAllowedProjectIdsForCurrentUser() → ProjectRepository.findTenderIdsByProjectIds() → TenderRepository/FeeRepository 按范围查询 → WorkbenchDeadlinePolicy 聚合`

| 角色 | 可见范围 |
|------|---------|
| Admin | getAllowedProjectIds 返回空 = 全量，不过滤 |
| Manager/Staff | 按用户实际权限聚合（负责项目+成员项目+项目组+CRM客户+部门） |

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/workbench/dto/WorkbenchDeadlineStatsDTO.java`
- Create: `backend/src/main/java/com/xiyu/bid/workbench/dto/package-info.java`
- Modify: `backend/src/main/java/com/xiyu/bid/repository/TenderRepository.java` (加 deadline 按 tenderId 过滤查询 + 全量 fallback)
- Modify: `backend/src/main/java/com/xiyu/bid/repository/ProjectRepository.java` (加 findTenderIdsByProjectIds)
- Modify: `backend/src/main/java/com/xiyu/bid/fees/repository/FeeRepository.java` (加 deposit deadline 按 projectId 过滤)
- Create: `backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchDeadlineQueryService.java`
- Create: `backend/src/test/java/com/xiyu/bid/workbench/service/WorkbenchDeadlineQueryServiceTest.java`

### Step 1: 创建 DTO（同原计划）

`WorkbenchDeadlineStatsDTO.java`:
```java
package com.xiyu.bid.workbench.dto;

public record WorkbenchDeadlineStatsDTO(
    DeadlinePeriodStatsDTO registrationDeadline,
    DeadlinePeriodStatsDTO bidOpening,
    DeadlinePeriodStatsDTO depositDeadline
) {
    public record DeadlinePeriodStatsDTO(long todayCount, long weekCount, long monthCount) {}
}
```

### Step 2: TenderRepository — 保留全量查询 + 新增按 tenderId 过滤

```java
// 全量（Admin 用）
@Query("SELECT t.registrationDeadline FROM Tender t WHERE t.registrationDeadline BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
List<LocalDateTime> findRegistrationDeadlinesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Query("SELECT t.bidOpeningTime FROM Tender t WHERE t.bidOpeningTime BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
List<LocalDateTime> findBidOpeningTimesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

// 按项目范围过滤（非 Admin 用）
@Query("SELECT t.registrationDeadline FROM Tender t WHERE t.id IN :tenderIds AND t.registrationDeadline BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
List<LocalDateTime> findRegistrationDeadlinesByTenderIds(@Param("tenderIds") Collection<Long> tenderIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Query("SELECT t.bidOpeningTime FROM Tender t WHERE t.id IN :tenderIds AND t.bidOpeningTime BETWEEN :start AND :end AND t.status NOT IN ('WON', 'LOST', 'ABANDONED')")
List<LocalDateTime> findBidOpeningTimesByTenderIds(@Param("tenderIds") Collection<Long> tenderIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
```

### Step 3: ProjectRepository — 新增获取项目关联标讯ID

```java
@Query("SELECT DISTINCT p.tenderId FROM Project p WHERE p.id IN :projectIds")
List<Long> findTenderIdsByProjectIds(@Param("projectIds") Collection<Long> projectIds);
```

### Step 4: FeeRepository — 新增按项目范围过滤

```java
// 全量（Admin 用）
@Query("SELECT f.feeDate FROM Fee f WHERE f.feeType = 'BID_BOND' AND f.status = 'PENDING' AND f.feeDate BETWEEN :start AND :end")
List<LocalDateTime> findDepositDeadlinesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

// 按项目范围过滤（非 Admin 用）
@Query("SELECT f.feeDate FROM Fee f WHERE f.feeType = 'BID_BOND' AND f.status = 'PENDING' AND f.projectId IN :projectIds AND f.feeDate BETWEEN :start AND :end")
List<LocalDateTime> findDepositDeadlinesByProjectIds(@Param("projectIds") Collection<Long> projectIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
```

### Step 5: 创建 Application Service（注入 ProjectAccessScopeService）

`WorkbenchDeadlineQueryService.java`:
```java
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workbench.domain.WorkbenchDeadlinePolicy;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkbenchDeadlineQueryService {

    private final TenderRepository tenderRepository;
    private final FeeRepository feeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional(readOnly = true)
    public WorkbenchDeadlineStatsDTO getDeadlineStats(LocalDate today) {
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        var monthStart = today.withDayOfMonth(1).atStartOfDay();
        var monthEnd = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);

        List<LocalDateTime> regDeadlines;
        List<LocalDateTime> openingTimes;
        List<LocalDateTime> depositDeadlines;

        if (allowedProjectIds.isEmpty()) {
            // Admin: 全量
            regDeadlines = tenderRepository.findRegistrationDeadlinesBetween(monthStart, monthEnd);
            openingTimes = tenderRepository.findBidOpeningTimesBetween(monthStart, monthEnd);
            depositDeadlines = feeRepository.findDepositDeadlinesBetween(monthStart, monthEnd);
        } else {
            // 非 Admin: 按项目范围过滤
            List<Long> allowedTenderIds = projectRepository.findTenderIdsByProjectIds(allowedProjectIds);
            regDeadlines = allowedTenderIds.isEmpty() ? List.of()
                : tenderRepository.findRegistrationDeadlinesByTenderIds(allowedTenderIds, monthStart, monthEnd);
            openingTimes = allowedTenderIds.isEmpty() ? List.of()
                : tenderRepository.findBidOpeningTimesByTenderIds(allowedTenderIds, monthStart, monthEnd);
            depositDeadlines = feeRepository.findDepositDeadlinesByProjectIds(allowedProjectIds, monthStart, monthEnd);
        }

        var stats = WorkbenchDeadlinePolicy.buildDeadlineStats(today, regDeadlines, openingTimes, depositDeadlines);
        return new WorkbenchDeadlineStatsDTO(
            new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                stats.registrationDeadline().todayCount(),
                stats.registrationDeadline().weekCount(),
                stats.registrationDeadline().monthCount()
            ),
            new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                stats.bidOpening().todayCount(),
                stats.bidOpening().weekCount(),
                stats.bidOpening().monthCount()
            ),
            new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(
                stats.depositDeadline().todayCount(),
                stats.depositDeadline().weekCount(),
                stats.depositDeadline().monthCount()
            )
        );
    }
}
```

### Step 6: 测试覆盖权限场景

`WorkbenchDeadlineQueryServiceTest`:
```java
@ExtendWith(MockitoExtension.class)
class WorkbenchDeadlineQueryServiceTest {

    @Mock TenderRepository tenderRepository;
    @Mock FeeRepository feeRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @InjectMocks WorkbenchDeadlineQueryService service;

    @Test
    void adminShouldSeeAllDeadlines() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any()))
            .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        var result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
    }

    @Test
    void managerShouldSeeOnlyOwnProjects() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L, 2L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L, 2L))).thenReturn(List.of(10L));
        when(tenderRepository.findRegistrationDeadlinesByTenderIds(eq(List.of(10L)), any(), any()))
            .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesByTenderIds(eq(List.of(10L)), any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L, 2L)), any(), any())).thenReturn(List.of());

        var result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
    }

    @Test
    void staffWithNoAccessShouldGetZeroCounts() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(99L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(99L))).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(99L)), any(), any())).thenReturn(List.of());

        var result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isZero();
        assertThat(result.bidOpening().todayCount()).isZero();
        assertThat(result.depositDeadline().todayCount()).isZero();
    }
}
```

Run: `mvn test -pl . -Dtest=WorkbenchDeadlineQueryServiceTest`
Expected: 3 tests PASS

### Step 7: Commit
```bash
git add backend/src/main/java/com/xiyu/bid/workbench/dto/
git add backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchDeadlineQueryService.java
git add backend/src/main/java/com/xiyu/bid/repository/TenderRepository.java
git add backend/src/main/java/com/xiyu/bid/repository/ProjectRepository.java
git add backend/src/main/java/com/xiyu/bid/fees/repository/FeeRepository.java
git add backend/src/test/java/com/xiyu/bid/workbench/service/
git commit -m "feat(workbench): add permission-scoped WorkbenchDeadlineQueryService"
```

---

## Task 3: 后端 - 新增 Controller 端点 + 清理 Demo 引用

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/workbench/controller/WorkbenchDeadlineController.java`
- Modify: `backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchScheduleQueryService.java` (remove Demo deps)
- Create: `backend/src/test/java/com/xiyu/bid/workbench/controller/WorkbenchDeadlineControllerTest.java`

### Step 1: 创建 Controller

`WorkbenchDeadlineController.java`:
```java
package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import com.xiyu.bid.workbench.service.WorkbenchDeadlineQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@Slf4j
public class WorkbenchDeadlineController {

    private final WorkbenchDeadlineQueryService deadlineQueryService;

    @GetMapping("/deadline-stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<WorkbenchDeadlineStatsDTO>> getDeadlineStats() {
        log.info("GET /api/workbench/deadline-stats");
        return ResponseEntity.ok(ApiResponse.success(
            deadlineQueryService.getDeadlineStats(LocalDate.now())
        ));
    }
}
```

### Step 2: 清理 WorkbenchScheduleQueryService 中的 Demo 引用

Remove `DemoModeService`, `DemoDataProvider`, `DemoFusionService` dependencies. Simplify to:
```java
@Service
@RequiredArgsConstructor
public class WorkbenchScheduleQueryService {

    private final CalendarService calendarService;

    public ScheduleOverviewDTO getScheduleOverview(LocalDate start, LocalDate end, Long assigneeId) {
        List<CalendarEventDTO> events = calendarService.getEventsByDateRange(start, end);
        events = events.stream()
                .sorted(Comparator.comparing(CalendarEventDTO::getEventDate))
                .toList();
        return ScheduleOverviewDTO.builder()
                .start(start).end(end).assigneeId(assigneeId)
                .total(events.size())
                .urgent(events.stream().filter(item -> Boolean.TRUE.equals(item.getIsUrgent())).count())
                .events(events)
                .build();
    }
}
```

### Step 3: 写 Controller 集成测试

`WorkbenchDeadlineControllerTest.java` (use `@WebMvcTest`):
```java
@WebMvcTest(WorkbenchDeadlineController.class)
class WorkbenchDeadlineControllerTest {
    @MockBean WorkbenchDeadlineQueryService service;
    @Autowired MockMvc mvc;

    @Test
    void shouldReturnDeadlineStats() throws Exception {
        when(service.getDeadlineStats(any()))
            .thenReturn(new WorkbenchDeadlineStatsDTO(/* ... */));
        mvc.perform(get("/api/workbench/deadline-stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registrationDeadline.todayCount").isNumber());
    }
}
```

Run: `mvn test -pl . -Dtest=WorkbenchDeadlineControllerTest`
Expected: PASS

### Step 4: Commit
```bash
git add backend/src/main/java/com/xiyu/bid/workbench/controller/WorkbenchDeadlineController.java
git add backend/src/main/java/com/xiyu/bid/workbench/service/WorkbenchScheduleQueryService.java
git add backend/src/test/java/com/xiyu/bid/workbench/controller/
git commit -m "feat(workbench): add deadline-stats endpoint, clean demo refs from schedule service"
```

---

## Task 4: 前端 - 新纯核心 deadline 计算 + API 模块

**Files:**
- Create: `src/views/Dashboard/workbench-deadline-core.js`
- Create: `src/views/Dashboard/workbench-deadline-core.spec.js`
- Modify: `src/api/modules/workbench.js` (add getDeadlineStats)
- Create: `src/api/modules/workbench-deadline.spec.js`

### Step 1: 写 deadline 纯核心

`workbench-deadline-core.js`:
```js
// Input: WorkbenchDeadlineStatsDTO from API
// Output: pure deadline metric transforms per role
// Pos: src/views/Dashboard/ - Dashboard pure core helpers

export function normalizeDeadlineStats(raw = {}) {
  const reg = raw.registrationDeadline || {}
  const opening = raw.bidOpening || {}
  const deposit = raw.depositDeadline || {}
  return {
    registrationDeadline: {
      todayCount: Number(reg.todayCount) || 0,
      weekCount: Number(reg.weekCount) || 0,
      monthCount: Number(reg.monthCount) || 0,
    },
    bidOpening: {
      todayCount: Number(opening.todayCount) || 0,
      weekCount: Number(opening.weekCount) || 0,
      monthCount: Number(opening.monthCount) || 0,
    },
    depositDeadline: {
      todayCount: Number(deposit.todayCount) || 0,
      weekCount: Number(deposit.weekCount) || 0,
      monthCount: Number(deposit.monthCount) || 0,
    },
  }
}

const DEADLINE_METRIC_DEFS = {
  admin: [
    { key: 'reg_today', label: '今日报名截止', deadlineType: 'registrationDeadline', period: 'todayCount', icon: 'Document', variant: 'red' },
    { key: 'opening_week', label: '本周开标', deadlineType: 'bidOpening', period: 'weekCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_month', label: '本月保证金截止', deadlineType: 'depositDeadline', period: 'monthCount', icon: 'TrendCharts', variant: 'blue' },
    { key: 'reg_month', label: '本月报名截止', deadlineType: 'registrationDeadline', period: 'monthCount', icon: 'Briefcase', variant: 'green' },
  ],
  manager: [
    { key: 'reg_week', label: '本周报名截止', deadlineType: 'registrationDeadline', period: 'weekCount', icon: 'Document', variant: 'red' },
    { key: 'opening_today', label: '今日开标', deadlineType: 'bidOpening', period: 'todayCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_week', label: '本周保证金截止', deadlineType: 'depositDeadline', period: 'weekCount', icon: 'TrendCharts', variant: 'blue' },
  ],
  staff: [
    { key: 'reg_today', label: '今日报名截止', deadlineType: 'registrationDeadline', period: 'todayCount', icon: 'Document', variant: 'red' },
    { key: 'opening_week', label: '本周开标', deadlineType: 'bidOpening', period: 'weekCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_month', label: '本月保证金截止', deadlineType: 'depositDeadline', period: 'monthCount', icon: 'TrendCharts', variant: 'blue' },
  ],
}

/**
 * Select deadline metrics based on user's menuPermissions.
 * - analytics → admin-level (4 cards, full overview)
 * - project → department-level (3 cards, team focus)
 * - default → staff-level (3 cards, personal focus)
 * 
 * @param {string[]} menuPermissions - user's menuPermissions from store
 * @param {object} deadlineStats - normalized deadline stats
 * @returns {Array} metric cards for display
 */
export function selectDeadlineMetrics(menuPermissions, deadlineStats) {
  if (hasAnyAnalyticsAccess(menuPermissions)) {
    return buildMetrics(DEADLINE_METRIC_DEFS.admin, deadlineStats)
  }
  if (hasAnyProjectAccess(menuPermissions)) {
    return buildMetrics(DEADLINE_METRIC_DEFS.manager, deadlineStats)
  }
  return buildMetrics(DEADLINE_METRIC_DEFS.staff, deadlineStats)
}

function hasAnyAnalyticsAccess(perms) {
  return hasAnyPermission(perms, ['analytics'])
}

function hasAnyProjectAccess(perms) {
  return hasAnyPermission(perms, ['project'])
}

function buildMetrics(defs, deadlineStats) {
  return defs.map((def) => {
    const typeStats = deadlineStats[def.deadlineType] || {}
    return {
      key: def.key,
      label: def.label,
      value: String(typeStats[def.period] || 0),
      icon: def.icon,
      variant: def.variant,
      change: '--',
      changeClass: 'neutral',
      deadlineType: def.deadlineType,
      period: def.period,
    }
  })
}
  return defs.map((def) => {
    const typeStats = deadlineStats[def.deadlineType] || {}
    return {
      key: def.key,
      label: def.label,
      value: String(typeStats[def.period] || 0),
      icon: def.icon,
      variant: def.variant,
      change: '--',
      changeClass: 'neutral',
      deadlineType: def.deadlineType,
      period: def.period,
    }
  })
}
```

### Step 2: API 模块添加方法

Add to `src/api/modules/workbench.js`:
```js
  async getDeadlineStats() {
    const response = await httpClient.get('/api/workbench/deadline-stats')
    return {
      success: response?.success === true,
      data: normalizeDeadlineStats(response?.data || {}),
    }
  },
```

### Step 3: Commit
```bash
git add src/views/Dashboard/workbench-deadline-core.js src/views/Dashboard/workbench-deadline-core.spec.js
git add src/api/modules/workbench.js src/api/modules/workbench-deadline.spec.js
git commit -m "feat(workbench): add deadline core + API module for frontend"
```

---

## Task 5: 前端 - 新建 DeadlineMetricCards 组件 + 更新 useWorkbenchMetrics

**Files:**
- Create: `src/views/Dashboard/components/DeadlineMetricCards.vue`
- Create: `src/views/Dashboard/components/__tests__/DeadlineMetricCards.spec.js`
- Modify: `src/views/Dashboard/useWorkbenchMetrics.js` (add deadline metrics loading)
- Modify: `src/views/Dashboard/Workbench.vue` (wire DeadlineMetricCards)

### Step 1: 写 DeadlineMetricCards 组件

`DeadlineMetricCards.vue`:
```vue
<template>
  <div class="deadline-metrics-grid" v-loading="loading">
    <EmptyState v-if="error" state="error" icon="!" title="指标加载失败"
      :description="error" action-label="重试" @action="emit('retry')" />
    <template v-else>
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card deadline-metric"
        :class="`metric-${metric.variant}`"
        role="button" tabindex="0"
        @click="selectMetric(metric)"
        @keydown.enter.prevent="selectMetric(metric)"
        @keydown.space.prevent="selectMetric(metric)"
      >
        <div class="metric-header">
          <span class="metric-label">{{ metric.label }}</span>
          <div class="metric-icon" :style="{ background: metric.iconBg || '#FEE2E2', color: metric.iconColor || '#DC2626' }">
            <el-icon :size="20"><component :is="metric.icon" /></el-icon>
          </div>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-footer">
          <span class="metric-period">个</span>
        </div>
      </div>
    </template>
  </div>
</template>
```

### Step 2: 更新 useWorkbenchMetrics.js

Add deadline metrics loading logic (fetch from new API, integrate selectDeadlineMetrics).

### Step 3: 更新 Workbench.vue

Add `<DeadlineMetricCards>` after `<MetricCards>`, pass deadline metrics props.

### Step 4: Commit
```bash
git add src/views/Dashboard/components/DeadlineMetricCards.vue
git add src/views/Dashboard/components/__tests__/DeadlineMetricCards.spec.js
git add src/views/Dashboard/useWorkbenchMetrics.js
git add src/views/Dashboard/Workbench.vue
git commit -m "feat(workbench): add DeadlineMetricCards component with deadline stats"
```

---

## Task 6: 前端 - 新建 QuickEntrySection 快捷入口组件

**Files:**
- Create: `src/views/Dashboard/components/QuickEntrySection.vue`
- Create: `src/views/Dashboard/components/__tests__/QuickEntrySection.spec.js`
- Modify: `src/views/Dashboard/Workbench.vue` (add QuickEntrySection after WelcomeBanner)

### Step 1: 写 QuickEntrySection 组件

三按钮布局：「发起项目」「查看标讯」「处理待办」，每个带图标和简短描述。

### Step 2: Commit
```bash
git add src/views/Dashboard/components/QuickEntrySection.vue
git add src/views/Dashboard/components/__tests__/QuickEntrySection.spec.js
git add src/views/Dashboard/Workbench.vue
git commit -m "feat(workbench): add QuickEntrySection shortcut component"
```

---

## Task 7: 前端 - 增强日程日历（项目里程碑）+ AI 预测占位

**Files:**
- Modify: `src/views/Dashboard/components/WorkCalendar.vue` (or inner child - add milestone badges)
- Modify: `src/views/Dashboard/Workbench.vue` (add AI prediction placeholder section)
- Create: `src/views/Dashboard/components/AiPredictionPlaceholder.vue`

### Step 1: 增强日历里程碑

在 WorkCalendar 事件渲染中，为项目里程碑类型事件增加特殊标记（里程碑图标 + 标签）。

### Step 2: AI 商机预测占位

`AiPredictionPlaceholder.vue` - 显示"规划中"状态卡片，标题"AI 商机预测"，描述"基于存量客户历史投标数据分析招标窗口期，自动生成跟进提醒"。

### Step 3: Commit
```bash
git add src/views/Dashboard/components/WorkCalendar.vue
git add src/views/Dashboard/components/AiPredictionPlaceholder.vue
git add src/views/Dashboard/Workbench.vue
git commit -m "feat(workbench): enhance calendar milestones + AI prediction placeholder"
```

---

## Task 8: 加固 todo/approval/process 待办展示

**Files:**
- Modify: `src/views/Dashboard/useWorkbenchTodos.js` (ensure bid-review items are highlighted)
- Modify: `src/views/Dashboard/useWorkbenchApprovals.js` (add bid-review approval type)
- Modify: `src/views/Dashboard/components/WorkbenchStaticLayout.vue` (ensure proper section rendering)

### Step 1: 确保待办涵盖蓝图要求的4类

任务待办 (已有)、任务审核 (已有)、标书评审 (需添加)、流程审批 (已有)。标书评审部分接入 `useBidReviewStatus` composable。

### Step 2: Commit
```bash
git add src/views/Dashboard/useWorkbenchTodos.js
git add src/views/Dashboard/useWorkbenchApprovals.js
git add src/views/Dashboard/components/WorkbenchStaticLayout.vue
git commit -m "feat(workbench): integrate bid review status into workbench todos"
```

---

## Task 9: 全面验证 + 架构门禁

**Files:**
- No new files (run tests only)

### Step 1: 后端单元测试全跑
```bash
cd backend && mvn test -pl . -Dtest="com.xiyu.bid.workbench.**"
```
Expected: all workbench tests PASS

### Step 2: 后端架构门禁
```bash
cd backend && mvn test -pl . -Dtest="FPJavaArchitectureTest,MaintainabilityArchitectureTest"
```
Expected: PASS (no violations)

### Step 3: 前端单元测试
```bash
npm run test:unit -- src/views/Dashboard/
```
Expected: all Dashboard tests PASS

### Step 4: 前端 lint
```bash
npm run lint -- src/views/Dashboard/ src/api/modules/workbench.js
```
Expected: no errors

### Step 5: 构建验证
```bash
npm run build 2>&1 | tail -5
```
Expected: build succeeds

---

## 并行执行策略

```
Task 0 (环境准备) → 顺序执行，阻塞后续

Task 1 (纯核心) ─┬→ Task 2 (查询服务+DTO) ─┬→ Task 3 (Controller+清理) ─┬→ Task 9 (验证)
                 │                          │                             │
                 └→ Task 4 (前端纯核心+API) ─┴→ Task 5 (组件) ─┬→ Task 6 ─┤
                                                             │→ Task 7 ─┤
                                                             └→ Task 8 ─┘
```

- Task 1 和 Task 4 可并行派发（后端纯核心 vs 前端纯核心，无冲突）
- Task 2 依赖 Task 1（DTO 引用纯核心）
- Task 3 依赖 Task 2（Controller 引用 QueryService）
- Task 5-8 依赖 Task 4（前端组件引用纯核心+API）
- Task 5-8 之间无冲突，可完全并行
- Task 9 依赖全部完成

---

## 假设与决策

1. **权限范围（后端）**：复用 `ProjectAccessScopeService` 做项目级过滤。Admin 看全量，非 Admin 按负责项目+成员项目+项目组+CRM客户+部门范围过滤
2. **权限驱动（前端）**：指标卡片配置用 `hasAnyPermission(menuPermissions, [...])` 驱动，不硬编码角色名。analytics 权限 → admin 级 4 卡，project 权限 → manager 级 3 卡，默认 → staff 级 3 卡
2. **Deposit deadline**：使用 Fee 表 `feeType='BID_BOND'` 且 `status='PENDING'` 的 `feeDate` 作为保证金缴纳截止
3. **API 端点路径**：新增 `/api/workbench/deadline-stats`，不修改现有 analytics 端点以避免破坏兼容性
4. **快捷入口**：新增独立组件 `QuickEntrySection`，不删除现有 banner actions（保留兼容）
5. **AI 预测**：仅占位展示，不实现后端逻辑
6. **Demo 引用清理**：仅清理 `WorkbenchScheduleQueryService` 中的 Demo 依赖，不影响其他模块
