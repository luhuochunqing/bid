# Implementation Plan: 项目档案时间戳补全

**Branch**: `cursor-sync` | **Date**: 2026-06-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-project-archive-stage-timestamps/spec.md`

## Summary

为 Project 实体新增 `initiatedAt`、`evaluatingAt`、`closedAt` 三个阶段时间戳字段，并在 ProjectStageService 的状态转换逻辑中自动填充。ProjectArchiveDetailService 改为从 Project 实体读取时间戳而非从 Tender 实体降级获取。前端 ArchiveDetailDrawer.vue 保持现有模板不变（已正确引用 DTO 字段）。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.3

**Primary Dependencies**: JPA (Hibernate), Flyway, Spring Data JPA

**Storage**: MySQL 8.0 (Project 表新增 3 列)

**Testing**: JUnit 5 + Mockito (Unit), MockMvc (Integration), Playwright (E2E)

**Target Platform**: Linux server (backend API)

**Project Type**: Web service (REST API + Vue 3 frontend)

**Performance Goals**: 无特殊性能要求，时间戳写入为同步操作

**Constraints**:
- FP-Java 约束：纯核心不修改入参，副作用在 Application Service 层
- 单文件 200 行软上限 / 300 行硬上限
- 历史数据兼容：已有 Project 记录的 timestamp 字段回填为 createdAt

**Scale/Scope**: 3 个 DTO 字段变更 + 3 个 Entity 字段新增 + 2 处服务层逻辑修改

## Constitution Check

| Gate | Status | Notes |
|------|--------|-------|
| FP-Java: Pure Core / Shell 分离 | ✅ PASS | 时间戳填充在 ProjectStageService (Shell)，无复杂业务规则进纯核心 |
| FP-Java: 不原地修改入参 | ✅ PASS | ProjectStageService.requestTransition 在决策后才修改 Project.stage |
| TDD: 测试先行 | ⚠️ 待验证 | 需在 tasks.md 中补充 Unit Test 覆盖 |
| Split-First: 无上帝类 | ✅ PASS | 改动范围小，无需拆分 |
| 迁移脚本规范 | ✅ PASS | 需创建 V1044 迁移脚本及对应 U1044 回滚 |
| 权限控制 | ✅ PASS | 仅读数据，不涉及权限变更 |

## Project Structure

### Documentation (this feature)

```text
specs/016-project-archive-stage-timestamps/
├── plan.md              # 本文件
├── research.md           # Phase 0 输出（无额外研究需求，此文件标记完成）
├── data-model.md        # Phase 1 输出（Entity + DTO 变更定义）
├── contracts/           # Phase 1 输出（API 响应结构）
│   └── archive-detail.md
└── tasks.md             # Phase 2 输出（由 /speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── entity/
│   └── Project.java                        # [MODIFY] 新增 initiatedAt/evaluatingAt/closedAt 字段
├── project/
│   ├── core/
│   │   └── ProjectStage.java              # [NO CHANGE] 纯枚举
│   └── service/
│       ├── ProjectStageService.java       # [MODIFY] 状态转换时填充时间戳
│       └── ProjectService.java            # [MODIFY] 创建项目时填充 initiatedAt
├── casework/
│   ├── application/
│   │   └── ProjectArchiveDetailService.java  # [MODIFY] 从 Project 读取时间戳
│   └── dto/
│       └── ProjectArchiveDetailResponse.java # [NO CHANGE] DTO 已有正确字段定义
backend/src/main/resources/db/
├── migration-mysql/
│   └── V1044__project_stage_timestamps.sql  # [NEW] 新增 3 列
└── rollback/migration-mysql/
    └── U1044__project_stage_timestamps.sql    # [NEW] 回滚脚本

frontend/src/views/Knowledge/views/components/
└── ArchiveDetailDrawer.vue                # [NO CHANGE] 已正确引用字段
```

**Structure Decision**: 标准 Spring Boot 分层结构 + Vue 3 前端。Entity 变更仅限 Project 表，新增 3 个 nullable LocalDateTime 列。

## Complexity Tracking

> 无 Constitution 违规，无需复杂度追踪。

## Implementation Approach

### Phase 1: 数据库迁移 (V1044)

1. 新增 `initiated_at`, `evaluating_at`, `closed_at` 三列（nullable, datetime）
2. 历史数据回填：`UPDATE projects SET initiated_at = created_at WHERE initiated_at IS NULL`
3. 对应 U1044 回滚脚本

### Phase 2: Entity 变更

- `Project.java` 新增 3 个 `LocalDateTime` 字段 + getter/setter
- 使用 Lombok `@Data` 自动生成

### Phase 3: Service 层逻辑

**ProjectStageService.requestTransition** 改造：
```
if (target == EVALUATING && project.getEvaluatingAt() == null)
    project.setEvaluatingAt(LocalDateTime.now());
if (target == CLOSED && project.getClosedAt() == null)
    project.setClosedAt(LocalDateTime.now());
```

**ProjectService.createProject** 改造：
```
if (project.getInitiatedAt() == null)
    project.setInitiatedAt(LocalDateTime.now());
```

### Phase 4: ProjectArchiveDetailService 改造

移除 Tender 降级逻辑，直接从 Project 实体读取：
```
Project p = projectOpt.get();
LocalDateTime initiatedAt = p.getInitiatedAt();           // 而非 tender.getCreatedAt()
LocalDateTime bidSubmissionAt = p.getEvaluatingAt();      // 新增
LocalDateTime closedAt = p.getClosedAt();                // 新增
```

### Phase 5: 前端验证

- ArchiveDetailDrawer.vue 已正确使用 `formatDateTime()` 渲染
- 无需修改，确认 `npm run build` 通过即可

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 状态倒退时时间戳处理 | 低 | 低 | spec 明确：时间戳保留首次进入时间，不清除 |
| 并发状态转换 | 低 | 中 | 数据库事务隔离 +乐观锁（Project.id 已设 @Id） |
| 历史数据迁移失败 | 中 | 高 | 迁移脚本先测试，U1044 回滚脚本完备 |
| 前端 formatDateTime 空值处理 | 低 | 低 | 已验证代码：null 时显示 "-" |

## Open Questions

| Question | Resolution |
|----------|------------|
| 阶段倒退时 evaluatingAt 是否清除？ | 不清除，保留首次进入 EVALUATING 的时间（spec §Edge Cases） |
| INITIATED → DRAFTING 是否需要时间戳？ | 不需要，spec §Assumptions 明确：仅 INITIATED/EVALUATING/CLOSED 三个关键节点 |
