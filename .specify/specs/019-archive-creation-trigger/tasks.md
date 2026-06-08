---
description: "任务清单：档案创建时机修复 — 立项通过时创建档案"
---

# Tasks: 档案创建时机修复 — 立项通过时创建档案

**Input**: Design documents from `/specs/019-archive-creation-trigger/`
- `spec.md`（需求与成功标准）
- `plan.md`（实现计划与文件改动清单）

**Prerequisites**: plan.md ✅ required, spec.md ✅ required

**Tests**: 本任务包含 3 个测试文件改动（2 个 NEW + 1 个 MODIFY），测试任务已编排到对应阶段。

**Organization**: 任务按"分析与准备 → 核心改动 → 测试补充 → 验证与提交"四阶段组织；每个任务可独立 commit 与 push。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- 描述中包含精确文件路径

## Path Conventions

- **后端 main 代码**：`backend/src/main/java/com/xiyu/bid/`
- **后端测试代码**：`backend/src/test/java/com/xiyu/bid/`
- **任务分支名**：`019-archive-creation-trigger` 或 `agent/<name>/019-archive-creation-trigger`

---

## Phase 1: 分析与准备

**Purpose**: 任务启动前的事实核查与协调（避免与 main 漂移、避免文件锁撞车）

- [ ] T001 跑早操 SOP（`./scripts/sync-env.sh .`），确保当前任务分支与 `origin/main` 一致
- [ ] T002 跑 `who-touches.sh project/service/ProjectInitiationApprovalService.java project/service/ProjectService.java` 确认无其他 agent 在动这两个文件；如有撞车，按 §5.4 协调或换任务
- [ ] T003 阅读 `ProjectArchiveWorkflowService.createArchive()` 实现，确认幂等逻辑（`archiveRepository.findByProjectId(projectId).orElseGet(...)`）；本次仅迁移调用方，不修改该方法

**Checkpoint**: 任务分支干净、无 lock 冲突、幂等实现已理解

---

## Phase 2: 核心改动

**Purpose**: 把档案创建调用从 `ProjectService.createProject()` 迁到 `ProjectInitiationApprovalService.approve()`，并清理 PR #315 重复类

- [ ] T004 [US1] 在 `backend/src/main/java/com/xiyu/bid/project/service/ProjectInitiationApprovalService.java` 中新增 2 个字段注入：`ProjectRepository projectRepository` + `ProjectArchiveWorkflowService projectArchiveWorkflowService`（更新构造函数/Lombok 参数列表）
- [ ] T005 [US1] 在 `ProjectInitiationApprovalService.approve()` 方法末尾追加 4 行代码：调用 `projectRepository.findById(projectId)` 取 Project、调用 `projectArchiveWorkflowService.createArchive(project.getId(), project.getName(), "ACTIVE")`
- [ ] T006 [US1] 在 `backend/src/main/java/com/xiyu/bid/project/service/ProjectService.java` 中删除 `archiveWorkflowService` 字段注入 + 构造函数对应参数
- [ ] T007 [US1] 在 `ProjectService.createProject()` 方法体内删除 `archiveWorkflowService.createArchive(...)` 调用行
- [ ] T008 [US1] 删除 `backend/src/main/java/com/xiyu/bid/qualification/application/QualificationExpiryScanTask.java`（PR #315 引入的重复类；删除前先 grep 确认无其他引用：`grep -r "qualification.application.QualificationExpiryScanTask" backend/src/`）

**Checkpoint**: 2 个 main 文件改造完成、1 个重复类已删除；本地 `mvn compile` 应通过

---

## Phase 3: 测试补充

**Purpose**: 为本次改动添加回归保护，确保：(a) 审批通过创建档案、(b) createProject 不再依赖档案服务、(c) 历史导入仍创建档案

- [ ] T009 [P] [US1] 新建 `backend/src/test/java/com/xiyu/bid/project/service/ProjectInitiationApprovalServiceTest.java`，编写 `approve_shouldCreateArchive_whenPendingInitiationPasses`（Mockito 模拟 ProjectRepository / ProjectArchiveWorkflowService / ProjectInitiationRepository，断言 approve() 调用 createArchive(projectId, name, "ACTIVE")）
- [ ] T010 [P] [US1] 在 `ProjectInitiationApprovalServiceTest.java` 中追加 `approve_shouldBeIdempotent_whenArchiveAlreadyExists`（验证幂等保护：第二次审批通过不抛异常、不重复创建）
- [ ] T011 [P] [US2] 新建 `backend/src/test/java/com/xiyu/bid/project/service/ProjectImportServiceTest.java`，编写 `archiveIfEnabled_shouldCreateArchive_whenStageIsInitiated`（验证历史导入回归：stage=INITIATED 仍创建档案）
- [ ] T012 [P] [US1] 修改 `backend/src/test/java/com/xiyu/bid/project/service/ProjectServiceDemoModeTest.java`，追加 `createProject_shouldSucceedWithoutArchiveService`（验证 createProject 不再创建档案、不再依赖 archiveWorkflowService）

**Checkpoint**: 3 个测试文件改动完成（2 个 NEW + 1 个 MODIFY）

---

## Phase 4: 验证与提交

**Purpose**: 跑全部门禁、生成 PR 描述、提交推送

- [ ] T013 在 `backend/` 目录跑受影响测试：`mvn -Dtest=ProjectInitiationApprovalServiceTest,ProjectServiceDemoModeTest,ProjectImportServiceTest test`，预期 8/8 全绿
- [ ] T014 跑架构门禁回归：`mvn -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest test`，预期全绿
- [ ] T015 跑全量后端测试：`mvn test`，确认无意外回归（关注 alerts/service 调度相关测试，确认 QualificationExpiryScanTask 删除后无 break）
- [ ] T016 跑 `bash scripts/ci-pre-pr.sh`（13 项门禁），预期 12/13 通过；唯一失败必须是 `test:agent-start-task-contract`（pre-existing 失败，与本改动无关）
- [ ] T017 编写 PR 描述（引用 spec.md / plan.md / tasks.md + 验证证据），通过 `scripts/pr-create.sh` 创建 PR；任务分支推送前再跑一次 `npm run agent:lock-check:changed` 确认无锁冲突

**Checkpoint**: 全部门禁通过、PR 创建、等待 review

---

## 依赖与执行顺序

### Phase 依赖

- **Phase 1 (分析与准备)**: 无依赖，开新任务即可跑
- **Phase 2 (核心改动)**: 依赖 Phase 1 完成（确认无 lock 冲突 + 幂等逻辑已理解）
- **Phase 3 (测试补充)**: 依赖 Phase 2 完成（测试需要 main 代码已就位）
- **Phase 4 (验证与提交)**: 依赖 Phase 2 + Phase 3 完成

### 任务内依赖

- T004 → T005（T004 先注入字段，T005 才能在 approve() 末尾调用）
- T006 → T007（T006 先删字段，T007 才能删调用行；二者也可合并为单次编辑）
- T008 与 T004-T007 互相独立（不同文件），可并行
- T009-T012 互相独立（不同文件），可并行；但都依赖 T004-T008 完成
- T013 依赖 T009-T012 完成（测试文件必须就位）
- T014-T015 依赖 T013 通过
- T016 依赖 T015 通过
- T017 依赖 T016 通过

### 并行机会

- T004-T005（同一文件、紧邻位置）建议**串行**
- T006-T007（同文件）建议**串行**
- T008 vs T004-T007：**可并行**（不同包、不同文件）
- T009-T012（4 个测试方法，分布在 3 个文件）：**可并行**
- T014-T015 可与 T013 串行（同一 mvn 进程，避免资源争用）

---

## Implementation Strategy

### MVP 优先（Phase 1+2 → 最小可验证）

1. 完成 Phase 1：分析与准备（确认无冲突）
2. 完成 Phase 2：核心改动（2 main + 1 delete）
3. 临时 `mvn compile` 验证 main 代码可编译
4. **STOP and VALIDATE**：手动 grep 确认档案创建调用点已正确迁移

### 增量交付（加入 Phase 3 测试）

5. 完成 Phase 3：3 个测试文件
6. **STOP and VALIDATE**：跑受影响测试，8/8 全绿

### 完整交付（加入 Phase 4 验证与提交）

7. 完成 Phase 4：跑全部门禁 + 写 PR
8. 等待 review → auto-merge → 任务结束

---

## 关键变更点速查

### `ProjectInitiationApprovalService.java` 末尾新增（约 +6 行）

```java
// 字段新增
private final ProjectRepository projectRepository;
private final ProjectArchiveWorkflowService projectArchiveWorkflowService;

// approve() 末尾追加
Project project = projectRepository.findById(projectId)
    .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
projectArchiveWorkflowService.createArchive(project.getId(), project.getName(), "ACTIVE");
```

### `ProjectService.java` 删除（约 -3 行）

```java
// 删除字段
- private final ArchiveWorkflowService archiveWorkflowService;

// 删除 createProject() 内的调用行
- archiveWorkflowService.createArchive(savedProject.getId(), savedProject.getName(), "ACTIVE");
```

### `QualificationExpiryScanTask.java` 整文件删除

```bash
rm backend/src/main/java/com/xiyu/bid/qualification/application/QualificationExpiryScanTask.java
```

---

## 验证证据收集

任务完成时，需在 PR 描述中附上以下证据：

1. **mvn 受影响测试输出**（截取 `Tests run: 8, Failures: 0, Errors: 0` 行）
2. **mvn 架构门禁输出**（截取 `BUILD SUCCESS` 行）
3. **`bash scripts/ci-pre-pr.sh` 输出**（截取 12/13 通过 + 1 项 pre-existing 失败说明）
4. **重复类删除安全检查**（`grep -r "qualification.application.QualificationExpiryScanTask" backend/src/` 无输出）

---

## Notes

- 所有任务完成后必须满足"完成前必须说明：纯核心在哪里，副作用在哪里，跑了哪些验证"
  - **纯核心**：`ProjectArchiveWorkflowService.createArchive()` 不变（幂等查询 + 创建），本次零修改
  - **副作用**：`ProjectInitiationApprovalService.approve()` 末尾追加的 4 行（DB 写入 project_archive 表）；跟随原 `@Transactional` 一起提交/回滚
  - **验证**：`mvn -Dtest=...` 8/8 + `mvn test` 全绿 + `bash scripts/ci-pre-pr.sh` 12/13 + grep 安全检查
- 任务分支命名遵循 `.wiki/pages/branch-naming.md`：建议 `019-archive-creation-trigger` 或 `agent/<your-name>/019-archive-creation-trigger`
- 删除 `QualificationExpiryScanTask` 前**必须**先 grep 确认无引用，否则可能引起 NPE 或调度失败
- PR 走 `scripts/pr-create.sh`，CI 门禁通过 + 1 个 required review 批准后由 `.github/workflows/auto-enable-merge-on-approved.yml` 自动 enable auto-merge
- **不要**使用 `git push --no-verify` / `git commit --no-verify`（系统级 git 包装器会拦截，见 CLAUDE.md §5）
- 推前必跑 `npm run agent:lock-check:changed`，确认无文件锁冲突
