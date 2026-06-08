# Implementation Plan: 档案创建时机修复 — 立项通过时创建档案

**Branch**: `019-archive-creation-trigger` | **Date**: 2026-06-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-archive-creation-trigger/spec.md`

## Summary

修复产品蓝图 §4.1.1.1.1 的合规偏差：项目档案必须在**立项审批通过**时创建，而不是在项目实体创建时创建。

当前实现错误地把档案创建调用放在 `ProjectService.createProject()`，导致项目处于「草稿（DRAFT）」或「待立项（PENDING_INITIATION）」状态时也会立即创建档案，污染档案库、违反"档案是已批准项目的事实容器"这一业务规则。

**修复方案**：把 `archiveWorkflowService.createArchive(...)` 调用从 `ProjectService.createProject()` 迁移到 `ProjectInitiationApprovalService.approve()`，并新增字段注入 `projectRepository` + `projectArchiveWorkflowService`；同时清理 PR #315 引入的重复扫描任务类。

**幂等性保证**：`ProjectArchiveWorkflowService.createArchive()` 内部已通过 `archiveRepository.findByProjectId(projectId).orElseGet(...)` + DB `UNIQUE(project_id)` 实现幂等，本次仅迁移调用方，不修改该方法的纯核心实现。

**回归保护**：`ProjectImportService.archiveIfEnabled()` 路径保持不变（历史项目 stage=INITIATED 的批量导入仍创建档案），并通过 `ProjectImportServiceTest` 锁死该行为。

## Technical Context

- **Language/Version**: Java 21（项目统一技术栈）
- **Primary Dependencies**: Spring Boot 3.3 + JPA (Hibernate 6) + MySQL 8.0 + Flyway + JUnit 5 + Mockito + AssertJ
- **Storage**: MySQL 8.0（`project` 表与 `project_archive` 表已有 `UNIQUE(project_id)` 约束，幂等性由 DB 层兜底）
- **Testing**: JUnit 5 + Mockito + Spring Boot Test；本任务不涉及前端 E2E
- **Target Platform**: Linux server（后端 Java 应用）
- **Project Type**: Web service（后端 Spring Boot 单体，FP-Java Profile）
- **Performance Goals**: 无新增性能压力（仅调用点迁移，业务量不变）
- **Constraints**:
  - **FP-Java Profile 遵守**：纯核心不变（`ProjectArchiveWorkflowService.createArchive()` 是已有纯核心逻辑，本次不修改），Application Service 只做编排（`ProjectInitiationApprovalService` 仅追加 4 行编排代码）。
  - **DB 迁移**：本次不涉及 schema 变更（`UNIQUE(project_id)` 约束在 V__add_project_archive_unique 时已添加）；不需要新增 Flyway 脚本。
  - **事务边界**：`ProjectInitiationApprovalService.approve()` 已是 `@Transactional` 方法，新增的档案创建调用会跟随该事务一起提交/回滚，天然避免"项目状态未变更但档案已创建"的脏数据。
  - **架构门禁**：`FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`、`ProjectAccessGuardCoverageTest` 全部必须继续绿。
- **Scale/Scope**:
  - 改动 2 个 main 文件 + 删除 1 个重复类 + 新建/修改 3 个测试文件
  - 涉及包：`com.xiyu.bid.project.service`、`com.xiyu.bid.qualification.application`
  - 估算代码量：main 净增 ~6 行，main 净删 ~3 行，测试净增 ~150 行

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 门禁项 | 状态 | 说明 |
|---|---|---|
| FP-Java Profile 遵守 | ✅ Pass | 本次仅迁移调用方；`ProjectArchiveWorkflowService.createArchive()` 纯核心不变；`ProjectInitiationApprovalService` 仍是 Application Service 编排层（接收请求、调用 Repository + Workflow Service、返回结果） |
| 纯核心不变 | ✅ Pass | `domain/projectarchive` 内的纯函数（如有）本次零修改 |
| Application Service 只做编排 | ✅ Pass | `ProjectInitiationApprovalService.approve()` 末尾新增 4 行：注入 `projectRepository.findById`、构造参数、调用 `projectArchiveWorkflowService.createArchive(...)`；不引入新业务规则 |
| 业务失败用 Result/Optional 返回 | ✅ Pass | 档案创建幂等是 `createArchive()` 内部行为，对外仍返回 archive 实体；不引入新异常路径 |
| 不可变值对象优先 | ✅ Pass | 新增代码仅传递 `projectId (Long)`、`name (String)`、`status (String)` 三个基本参数，不引入新 DTO |
| 业务方法必须返回值 | ✅ Pass | `approve()` 原方法签名不变（继续返回立项结果/Project） |
| Split-First Rule | ✅ Pass | 改动前先确认职责边界：`createProject`（项目落库）↔ `approve`（审批 + 档案创建），分别属于 ProjectService 与 ProjectInitiationApprovalService 各自的清晰职责 |
| 单文件行数 | ✅ Pass | 涉及文件均 < 200 行；增量后仍 < 200 行 |
| Flyway 迁移规范 | ✅ N/A | 本次无 schema 变更 |
| 回滚脚本 | ✅ N/A | 本次无 Flyway 脚本，无须回滚 |
| 架构门禁（ArchUnit） | ✅ Must Pass | `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest` 必须继续绿；本次仅做调用方迁移，预期不破 |
| Mock 政策 | ✅ Pass | 本次为后端业务逻辑调整，E2E/演示走真实 API；不引入 mock-adapters、src/mock 路径代码 |

**Constitution Check 结论**：全部通过。**不触发** §Complexity Tracking 任何条目；本次不属于"必须记录违规"的场景。

## Project Structure

### Documentation (this feature)

```text
specs/019-archive-creation-trigger/
├── spec.md              # 需求规格（User Stories + FR + 成功标准）
├── plan.md              # 本文件（实现计划）
└── tasks.md             # 任务清单（按 Phase 划分）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── project/service/
│   │   ├── ProjectInitiationApprovalService.java     # MODIFY: 新增 2 个字段注入 + approve() 末尾 +4 行
│   │   └── ProjectService.java                       # MODIFY: 删除 archiveWorkflowService 字段 + createProject 内档案创建调用
│   └── qualification/application/
│       └── QualificationExpiryScanTask.java          # DELETE: 与 alerts/service/ 重复（PR #315 引入）
│
└── src/test/java/com/xiyu/bid/project/service/
    ├── ProjectInitiationApprovalServiceTest.java     # NEW: 2 个测试（审批通过创建档案 + 幂等不重复）
    ├── ProjectImportServiceTest.java                 # NEW: 1 个测试（历史导入仍创建档案）
    └── ProjectServiceDemoModeTest.java               # MODIFY: 新增 createProject_shouldSucceedWithoutArchiveService
```

**Structure Decision**: 本次为后端 Java 项目，无前端/移动端/CLI 变更。仓库结构沿用 `backend/src/main/java/...` + `backend/src/test/java/...` 既有布局（与 plan-template Option 2 Web application 的 backend 子树一致）。

## 改动文件清单

### 主代码（2 个 MODIFY + 1 个 DELETE）

#### 1. `backend/src/main/java/com/xiyu/bid/project/service/ProjectInitiationApprovalService.java`

- **新增字段注入**（2 个）：
  - `private final ProjectRepository projectRepository;`（用于审批通过后回查 Project 实体以取 `name`）
  - `private final ProjectArchiveWorkflowService projectArchiveWorkflowService;`
- **构造函数改造**：在现有 `@RequiredArgsConstructor` 或显式构造函数中追加上述 2 个参数（Lombok 自动按需更新）
- **`approve()` 方法末尾追加 4 行**（事务提交前的最后一组调用）：
  ```java
  Project project = projectRepository.findById(projectId)
      .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
  projectArchiveWorkflowService.createArchive(project.getId(), project.getName(), "ACTIVE");
  ```
- **事务边界**：上述代码在原 `@Transactional` 方法体内执行，**不**新增 `@Transactional` 注解
- **行数变化**：净增 ~6 行

#### 2. `backend/src/main/java/com/xiyu/bid/project/service/ProjectService.java`

- **删除字段**：`private final ArchiveWorkflowService archiveWorkflowService;`（如存在）
- **删除构造函数对应参数**：同步删除
- **删除 `createProject()` 内的 `archiveWorkflowService.createArchive(...)` 调用行**
- **行数变化**：净减 ~3 行

#### 3. `backend/src/main/java/com/xiyu/bid/qualification/application/QualificationExpiryScanTask.java` — **DELETE**

- 与 `alerts/service/QualificationExpiryScanTask` 重复（PR #315 引入）
- 删除前 grep 确认无其他引用（`grep -r "qualification.application.QualificationExpiryScanTask" backend/src/`）
- 删除后通过 `mvn test` 验证 `alerts/service/QualificationExpiryScanTask` 仍正常调度

### 测试代码（2 个 NEW + 1 个 MODIFY）

#### 4. `backend/src/test/java/com/xiyu/bid/project/service/ProjectInitiationApprovalServiceTest.java` — **NEW**

- 2 个测试方法：
  - `approve_shouldCreateArchive_whenPendingInitiationPasses`（核心场景，验证 FR-001）
  - `approve_shouldBeIdempotent_whenArchiveAlreadyExists`（验证 FR-003）
- 使用 Mockito 模拟 `ProjectRepository`、`ProjectArchiveWorkflowService`、`ProjectInitiationRepository`
- 行数估算：~80 行

#### 5. `backend/src/test/java/com/xiyu/bid/project/service/ProjectImportServiceTest.java` — **NEW**

- 1 个测试方法：
  - `archiveIfEnabled_shouldCreateArchive_whenStageIsInitiated`（验证 FR-004 历史导入回归）
- 行数估算：~50 行

#### 6. `backend/src/test/java/com/xiyu/bid/project/service/ProjectServiceDemoModeTest.java` — **MODIFY**

- 新增 1 个测试方法：
  - `createProject_shouldSucceedWithoutArchiveService`（验证 FR-002 createProject 不再依赖档案服务、不创建档案）
- 行数估算：~40 行

## 复杂度跟踪

> **本次无 Constitution 违规，无需 Complexity Tracking 记录**

| 违规 | 必要性 | 被拒绝的简化方案 |
|---|---|---|
| — | — | — |

## 验证计划

### 本地验证（任务完成门禁）

```bash
# 1. 受影响测试（3 个测试类、4 个测试方法、8 个断言）
cd /Users/user/xiyu/worktrees/trae/backend
mvn -Dtest=ProjectInitiationApprovalServiceTest,ProjectServiceDemoModeTest,ProjectImportServiceTest test
# 预期：8/8 全绿

# 2. 架构门禁（回归保护，确认未引入新违规）
mvn -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest test
# 预期：全绿

# 3. 重复类删除安全检查
grep -r "qualification.application.QualificationExpiryScanTask" backend/src/
# 预期：无输出（确认无残留引用）

# 4. CI pre-PR 脚本（13 项门禁）
cd /Users/user/xiyu/worktrees/trae
bash scripts/ci-pre-pr.sh
# 预期：12/13 通过；唯一失败必须是 test:agent-start-task-contract（pre-existing，与本改动无关）
```

### E2E 验证

- 本次**不涉及**前端 UI 变更，**不**新增 Playwright 脚本
- 既有 `e2e/project-archive-flow.spec.js`、`e2e/project-initiation-flow.spec.js` 仍应继续绿（CI 行为回归）
- 如有疑虑，可手工跑：`npm run test:e2e -- --grep "project-archive|project-initiation"`

### PR 提交

- 分支：`019-archive-creation-trigger` 或 `agent/<name>/019-archive-creation-trigger`
- 标题：`fix: 在立项审批通过时创建项目档案（产品蓝图 §4.1.1.1.1 合规修复）`
- Body：引用本 spec.md + plan.md + tasks.md
- 验证证据：本地 `mvn test` 输出 + `bash scripts/ci-pre-pr.sh` 输出（12/13 通过说明）

## 风险与回滚

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 事务回滚路径档案未回滚 | 极低 | 中 | `createArchive` 在 `@Transactional` 方法体内调用，天然跟随事务；已用 SC-001 单测覆盖 |
| 幂等性被破坏（重复档案） | 极低 | 高 | 复用既有 `createArchive()` 幂等实现 + DB UNIQUE 约束双保险；新增 `approve_shouldBeIdempotent` 单测 |
| 历史导入回归（重复触发） | 极低 | 高 | 保留 `ProjectImportService.archiveIfEnabled()` 原行为不变；新增 `archiveIfEnabled_shouldCreateArchive_whenStageIsInitiated` 单测锁死 |
| 删除 `QualificationExpiryScanTask` 后调度失败 | 极低 | 中 | 删除前 grep 确认无引用；保留 `alerts/service/QualificationExpiryScanTask` 为唯一注册点；既有扫描调度测试应继续绿 |
| ArchUnit 门禁失败 | 低 | 中 | 仅做调用方迁移，不动包结构、不动依赖方向；如有违规按真实违规修复 |
| ProjectRepository 注入导致循环依赖 | 极低 | 中 | ProjectRepository 已在 project.repository 包内、被多个 service 引用，无循环风险 |

**回滚方案**：本任务无 Flyway 迁移，回滚即 `git revert` 单次提交 + 重新跑 `mvn test` 即可。

## 完成标准

- [ ] 6 个文件改动全部按本计划落地（2 main modify + 1 delete + 2 test new + 1 test modify）
- [ ] 本地 `mvn -Dtest=ProjectInitiationApprovalServiceTest,ProjectServiceDemoModeTest,ProjectImportServiceTest test` → 8/8 通过
- [ ] 架构门禁 `mvn -Dtest=ArchitectureTest` → 全绿
- [ ] `bash scripts/ci-pre-pr.sh` → 12/13 通过（1 项 pre-existing 失败已说明）
- [ ] PR 创建并通过团队 review
- [ ] 在 PR 描述中引用本 spec.md / plan.md / tasks.md
