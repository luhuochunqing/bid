---

description: "Task list for archive-oss-direct-upload-sync feature"
---

# Tasks: OBS 直传项目文档同步归档到项目档案

**Input**: Design documents from `/specs/039-archive-oss-direct-upload-sync/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: 本次改造遵循 Constitution III (TDD)，测试先行。

**Organization**: US1（OBS 直传标书归档）和 US2（其他阶段归档）共享同一代码路径（`ProjectDocumentWorkflowService.createProjectDocument` 末尾调用 `attachFileToArchive`），合并到 Phase 3 一起实现。US3（历史数据回填）独立在 Phase 4。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **后端**: `backend/src/main/java/com/xiyu/bid/projectworkflow/service/`
- **数据库迁移**: `backend/src/main/resources/db/migration-mysql/`、`backend/src/main/resources/db/rollback/migration-mysql/`
- **测试**: `backend/src/test/java/com/xiyu/bid/projectworkflow/service/`

---

## Phase 1: Setup (无新建基础设施)

**Purpose**: 本次改造无需 Setup，跳过。归档逻辑复用现有 `ProjectArchiveWorkflowService.attachFileToArchive` 方法，无新建基础设施。

---

## Phase 2: Foundational (TDD 核心归档逻辑上提)

**Purpose**: 将 `attachFileToArchive` 调用从 `ProjectDocumentUploadWorkflowService` 上提到 `ProjectDocumentWorkflowService.createProjectDocument`，让 JSON 和 multipart 两条路径统一归档

**⚠️ CRITICAL**: US1/US2 的所有验收场景都依赖此 Phase 完成

### Tests (Red - 先写测试，确保 FAIL)

- [ ] T001 [Red] 修改 `backend/src/test/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowServiceTest.java`：
  - 新增用例 `createProjectDocument_ShouldAttachFileToArchive`：调用 createProjectDocument 后 verify `projectArchiveWorkflowService.attachFileToArchive` 被调用一次，参数包含 projectId、fileName、documentCategory（归一化后）、fileUrl（作为 physicalPath）、fileSize、uploaderId、uploaderName
  - 新增用例 `createProjectDocument_ShouldNormalizeCategoryBeforeArchiving`：传入 documentCategory='BID_DOCUMENT'（历史别名），verify `attachFileToArchive` 收到 'BID'
  - 新增用例 `createProjectDocument_ShouldFallbackToOtherWhenCategoryNull`：传入 documentCategory=null，verify `attachFileToArchive` 收到 'OTHER'
  - 新增用例 `createProjectDocument_ShouldUseFileUrlAsPhysicalPath`：传入 fileUrl='obs-direct:abc123'，verify `attachFileToArchive` 的 physicalPath 参数 = 'obs-direct:abc123'
  - 新增用例 `createProjectDocument_ShouldNotFailWhenArchiveThrows`：mock `attachFileToArchive` 抛 RuntimeException，verify `createProjectDocument` 仍成功返回 savedDocument（归档失败不影响主流程）
- [ ] T002 [Red] 修改 `backend/src/test/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentUploadWorkflowServiceTest.java`：
  - 修改现有用例，verify `uploadWorkflowService` 不再直接调用 `projectArchiveWorkflowService.attachFileToArchive`（归档由下游 `createProjectDocument` 统一处理）
  - 验证 multipart 上传后 `archive_file` 仍会有记录（通过 verify `projectDocumentWorkflowService.createProjectDocument` 被调用，归档在其内部完成）
  - 移除测试类中 `projectArchiveWorkflowService` 字段（若已无其他用例引用）并相应调整 `@Mock` 和 `@InjectMocks`

### Implementation (Green - 实现使测试通过)

- [ ] T003 [Green] 修改 `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowService.java`：
  - 在构造器注入 `ProjectArchiveWorkflowService` 依赖（与 `ProjectDocumentBindingGateway` 并列）
  - 在 `createProjectDocument` 方法末尾（`documentChangeNotificationService.notifyDocumentChanged` 之后、return 之前）增加归档触发：
    ```java
    try {
        projectArchiveWorkflowService.attachFileToArchive(
            savedDocument.getProjectId(),
            savedDocument.getName(),
            savedDocument.getDocumentCategory(),
            savedDocument.getFileUrl(),  // OBS 直传为 obs-direct:{uploadId}，multipart 为 fileUrl
            parseFileSize(savedDocument.getSize()),
            savedDocument.getUploaderId(),
            savedDocument.getUploaderName()
        );
    } catch (Exception e) {
        log.warn("归档失败但不影响文档上传：projectId={}, documentId={}, error={}",
            savedDocument.getProjectId(), savedDocument.getId(), e.getMessage());
    }
    ```
  - 新增 private 方法 `parseFileSize(String size)`：解析 project_documents.size 字符串（如 "1.5MB"）为 Long 字节数；解析失败返回 0L（避免归档失败）
  - 添加 `private static final Logger log = LoggerFactory.getLogger(ProjectDocumentWorkflowService.class);` 字段
- [ ] T004 [Green] 修改 `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentUploadWorkflowService.java`：
  - 删除 `createUploadedProjectDocument` 方法末尾的 `if (projectArchiveWorkflowService != null && storedFile.physicalPath() != null) { projectArchiveWorkflowService.attachFileToArchive(...) }` 整段（约 11 行）
  - 删除 `private final ProjectArchiveWorkflowService projectArchiveWorkflowService;` 字段
  - 删除构造器中对应的 `this.projectArchiveWorkflowService = projectArchiveWorkflowService;` 赋值
  - 删除对应 import `com.xiyu.bid.casework.application.ProjectArchiveWorkflowService`

**Checkpoint**: 运行 `mvn test -Dtest=ProjectDocumentWorkflowServiceTest,ProjectDocumentUploadWorkflowServiceTest` 应全绿（Red → Green 完成）

---

## Phase 3: User Story 1 + 2 - OBS 直传文档归档（所有阶段）(Priority: P1) 🎯 MVP

**Goal**: OBS 直传和 multipart 两条路径在所有阶段（立项/标书制作/评标/结果确认/复盘/结项）都能正确归档

**Independent Test**: OBS 启用环境下，在标书制作阶段上传 BID 文件，验证 archive_file 表新增对应记录。同理验证 TENDER/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT 分类。

**说明**: US1 和 US2 共享 `createProjectDocument` 代码路径，Phase 2 的核心改造已覆盖两者。本 Phase 仅需补充分类场景的测试用例确保各分类归一化正确。

### Implementation for User Story 1 + 2

- [ ] T005 [P] [US1+US2] 在 `ProjectDocumentWorkflowServiceTest.java` 新增用例 `createProjectDocument_ShouldArchiveByCategory_BID`：传入 documentCategory='BID'，verify `attachFileToArchive` 收到 'BID'
- [ ] T006 [P] [US1+US2] 在 `ProjectDocumentWorkflowServiceTest.java` 新增用例 `createProjectDocument_ShouldArchiveByCategory_TENDER`：传入 documentCategory='TENDER'，verify `attachFileToArchive` 收到 'TENDER'
- [ ] T007 [P] [US1+US2] 在 `ProjectDocumentWorkflowServiceTest.java` 新增用例 `createProjectDocument_ShouldArchiveByCategory_OPEN_LIST`：传入 documentCategory='OPEN_LIST'，verify `attachFileToArchive` 收到 'OPEN_LIST'
- [ ] T008 [P] [US1+US2] 在 `ProjectDocumentWorkflowServiceTest.java` 新增用例 `createProjectDocument_ShouldArchiveByCategory_WIN_NOTICE`：传入 documentCategory='WIN_NOTICE'，verify `attachFileToArchive` 收到 'WIN_NOTICE'
- [ ] T009 [P] [US1+US2] 在 `ProjectDocumentWorkflowServiceTest.java` 新增用例 `createProjectDocument_ShouldArchiveByCategory_DEPOSIT_RECEIPT`：传入 documentCategory='DEPOSIT_RECEIPT'，verify `attachFileToArchive` 收到 'DEPOSIT_RECEIPT'

**Checkpoint**: US1 + US2 实现完成。运行 `mvn test -Dtest=ProjectDocumentWorkflowServiceTest` 确认全绿。

---

## Phase 4: User Story 3 - 历史已上传的 OBS 直传文档回填归档 (Priority: P2)

**Goal**: 部署 V1168 迁移后，扫描并回填历史已通过 OBS 直传上传但未归档的 project_documents 记录

**Independent Test**: 在测试库插入若干条 fileUrl 以 `obs-direct:` 开头且无对应 archive_file 的 project_documents 记录，运行 V1168 迁移，验证 archive_file 新增对应数量记录。

### Implementation for User Story 3

- [ ] T010 [US3] 创建迁移脚本 `backend/src/main/resources/db/migration-mysql/V1168__backfill_archive_files_for_obs_direct_uploads.sql`：
  - 头部注释包含 `-- Input: 迁移脚本、blueprint 4.1.1.1`、`-- Output: 回填 OBS 直传历史文档到 archive_file`、`-- Pos:`、`-- 维护声明: 维护者按项目SOP；与 U1168 配对，含 header`
  - SQL 逻辑（参见 data-model.md）：INSERT INTO archive_file SELECT FROM project_documents pd INNER JOIN project_archive pa LEFT JOIN archive_file af，WHERE pd.file_url LIKE 'obs-direct:%' AND af.id IS NULL
  - document_category 用 CASE WHEN 归一化（BID_DOCUMENT→BID 等，详见 data-model.md）
  - file_size 取 0，upload_user_id/uploader_name 用 COALESCE 兜底
- [ ] T011 [P] [US3] 创建回滚脚本 `backend/src/main/resources/db/rollback/migration-mysql/U1168__backfill_archive_files_for_obs_direct_uploads.sql`：
  - 头部注释同 T010 规范（与 V1168 配对）
  - SQL 逻辑：`DELETE FROM archive_file WHERE file_path LIKE 'obs-direct:%' AND created_at >= NOW() - INTERVAL 1 HOUR`
  - 注释说明 created_at 时间范围是启发式，生产回滚需手动调整 INTERVAL

**Checkpoint**: US3 实现完成。本地测试库执行 V1168 + U1168 验证幂等和回滚。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 验证无回归，准备提交 PR

- [ ] T012 运行后端单元测试：`cd backend && mvn test -Dtest=ProjectDocumentWorkflowServiceTest,ProjectDocumentUploadWorkflowServiceTest`（确认全绿）
- [ ] T013 [P] 运行后端架构测试：`cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`（确认无新增违规）
- [ ] T014 [P] 运行后端集成测试（若涉及）：`cd backend && mvn test -Dtest=ProjectWorkflowIntegrationTest`（确认无回归）
- [ ] T015 [P] 验证 Flyway 迁移：本地执行 `mvn spring-boot:run` 启动后端，确认 V1168 迁移成功执行，archive_file 表数据按预期回填
- [ ] T016 Git 状态确认：`git status` 检查只修改了授权文件（见下方预期文件列表）
- [ ] T017 提交 PR：分支 `agent/trae2/archive-oss-direct-upload-sync` → `main`，PR 描述包含 spec/plan/research 引用、根因分析、修复方案、测试证据

**预期修改文件清单**：
- `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowService.java`（修改）
- `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentUploadWorkflowService.java`（修改）
- `backend/src/main/resources/db/migration-mysql/V1168__backfill_archive_files_for_obs_direct_uploads.sql`（新增）
- `backend/src/main/resources/db/rollback/migration-mysql/U1168__backfill_archive_files_for_obs_direct_uploads.sql`（新增）
- `backend/src/test/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowServiceTest.java`（修改）
- `backend/src/test/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentUploadWorkflowServiceTest.java`（修改）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: 无依赖，可立即开始。T001 → T003、T002 → T004（TDD 顺序：Red → Green）
- **User Story 1+2 (Phase 3)**: 依赖 Phase 2 完成。T005-T009 可并行（同一文件不同方法，建议串行编辑）
- **User Story 3 (Phase 4)**: 无依赖（与 Phase 2/3 可并行，不同文件）。T010 → T011（T011 依赖 T010 的版本号和命名）
- **Polish (Phase 5)**: 依赖 Phase 2-4 全部完成。T012 → T013/T014/T015（可并行）→ T016 → T017

### Within Each Phase

- Phase 2 严格遵循 TDD：T001/T002（Red）→ T003/T004（Green）
- Phase 3 测试用例可串行编写（同一文件，建议一次会话完成）
- Phase 4 迁移脚本与回滚脚本可并行起草，但回滚脚本依赖迁移脚本的版本号

### Parallel Opportunities

- T005-T009（US1+US2 分类测试用例）虽标 [P] 但属同一文件，建议串行编辑避免冲突
- T013/T014/T015（架构/集成/Flyway 验证）可并行
- Phase 4（迁移脚本）与 Phase 2/3 可并行（不同文件，不同技术栈）

---

## Implementation Strategy

### MVP First (Phase 2-3)

1. 完成 Phase 2: TDD 上提归档逻辑（Red → Green）
2. 完成 Phase 3: 补充分类场景测试
3. **STOP and VALIDATE**: 运行 `mvn test -Dtest=ProjectDocumentWorkflowServiceTest,ProjectDocumentUploadWorkflowServiceTest` 确认全绿
4. 此时 US1 + US2 已可交付（OBS 直传和 multipart 都正确归档）

### Incremental Delivery

1. Phase 2-3 → 后端核心功能就绪（US1 + US2）
2. Phase 4 → 数据修复迁移就绪（US3）
3. Phase 5 → 全量验证 + PR 提交

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US1 和 US2 共享代码路径，合并在 Phase 3 实现
- 严格遵循 TDD：Phase 2 的 T001/T002（Red）必须在 T003/T004（Green）之前
- 每个任务完成后提交 atomic commit（遵循 RELIABILITY.md 原子提交 + 测试证据）
- 数据库迁移版本号 V1168 由人工指定（migration-mysql 当前最大 V1167，下一个 V1168），无需运行 `new-migration.sh`
- 归档副作用失败不阻断主流程（FR-010），try-catch 包裹是关键实现细节
- 跨模块依赖 `projectworkflow → casework` 已存在（`ProjectDocumentUploadWorkflowService` 已注入），本次仅转移依赖到 `ProjectDocumentWorkflowService`，无新增循环
