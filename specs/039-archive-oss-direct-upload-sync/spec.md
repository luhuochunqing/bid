# Feature Specification: OBS 直传项目文档同步归档到项目档案

**Feature Branch**: `039-archive-oss-direct-upload-sync`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "项目档案里面沉淀的文档不全，和项目详情-标书制作阶段中的项目文档不一致"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - OBS 直传的标书文件同步归档到项目档案 (Priority: P1)

作为投标负责人，在标书制作阶段通过 OBS 直传上传投标文件（documentCategory=BID）后，我希望该文件自动归档到对应项目的档案中（archive_file 表新增一条 BID 分类记录），这样项目档案页面与项目详情页文档列表保持一致，结项后案例沉淀的前置条件检查也能正确识别到标书文件。

**Why this priority**: OBS 直传是默认上传路径，标书文件是案例沉淀的硬前置条件。当前所有 OBS 直传的标书文件都缺失归档记录，直接影响案例沉淀误判和档案完整性，是 P1。

**Independent Test**: 在 OBS 启用环境下，进入任意项目标书制作阶段，上传一个 BID 分类文件，验证 `archive_file` 表中新增一条记录（archive_id 对应项目档案，document_category='BID'），且项目档案详情抽屉的"文件清单"区块出现该文件。

**Acceptance Scenarios**:

1. **Given** 项目 P1 已立项（project_archive 已建），OBS 直传已启用，**When** 用户在标书制作阶段通过 OBS 直传上传文件 "投标文件v1.docx"（documentCategory=BID，fileUrl="obs-direct:xxx"），**Then** archive_file 表新增一条记录：document_category='BID'、file_name='投标文件v1.docx'、file_path='obs-direct:xxx'、file_size=实际大小、upload_user_name=当前用户姓名
2. **Given** 用户在标书制作阶段通过 multipart 上传投标文件，**When** 上传成功，**Then** archive_file 表仍新增一条记录（保留原有 multipart 归档逻辑，不重复不丢失）
3. **Given** 项目 P1 还未建档（project_archive 不存在），**When** 用户通过 OBS 直传上传文件，**Then** 系统先自动建档（archive_status='ACTIVE'），再 attach 文件到档案

---

### User Story 2 - OBS 直传在其他阶段同样归档 (Priority: P1)

作为投标管理员/投标负责人，在立项、评标、结果确认、复盘、结项等阶段通过 OBS 直传上传对应分类的文档后，我希望该文件自动归档到项目档案（按阶段对应分类 TENDER/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER），这样所有阶段的档案沉淀行为一致，项目档案台账的"归档文件数"统计准确。

**Why this priority**: 不只标书阶段，所有用 useObsProjectDocumentUpload composable 的阶段都受影响（InitiationStage/EvaluationEvidenceUpload/ResultConfirmStage/RetrospectiveStage/ClosureStage），一致性要求 P1。

**Independent Test**: 在 OBS 启用环境下，分别在立项阶段上传 TENDER 分类文件、在评标阶段上传 OPEN_LIST 分类文件，验证 archive_file 表新增对应分类记录。

**Acceptance Scenarios**:

1. **Given** 用户在立项阶段上传招标文件（documentCategory=TENDER，OBS 直传），**When** 上传成功，**Then** archive_file 表新增 document_category='TENDER' 记录
2. **Given** 用户在评标阶段上传开标一览表（documentCategory=OPEN_LIST，OBS 直传），**When** 上传成功，**Then** archive_file 表新增 document_category='OPEN_LIST' 记录
3. **Given** 用户在结果确认阶段上传中标通知书（documentCategory=WIN_NOTICE，OBS 直传），**When** 上传成功，**Then** archive_file 表新增 document_category='WIN_NOTICE' 记录
4. **Given** 用户在结项阶段上传保证金银行回单（documentCategory=DEPOSIT_RECEIPT，OBS 直传），**When** 上传成功，**Then** archive_file 表新增 document_category='DEPOSIT_RECEIPT' 记录

---

### User Story 3 - 历史已上传的 OBS 直传文档回填归档 (Priority: P2)

作为系统管理员，我希望本次修复部署后自动扫描并回填历史已通过 OBS 直传上传但未归档的 project_documents 记录到 archive_file 表，这样历史数据与新增数据一致，档案台账数据完整。

**Why this priority**: 数据修复类任务，影响存量数据完整性但不影响新数据流转。优先级低于新数据归档（P1），但部署后必须执行一次。P2。

**Independent Test**: 在测试库插入若干条 fileUrl 以 `obs-direct:` 开头且无对应 archive_file 的 project_documents 记录，运行 V1168 迁移，验证 archive_file 新增对应数量记录。

**Acceptance Scenarios**:

1. **Given** 数据库中存在 N 条 project_documents 记录（fileUrl 以 `obs-direct:` 开头），且这些记录无对应 archive_file，**When** Flyway 迁移 V1168 执行，**Then** 对应 archive_file 表新增 N 条记录（按 document_category 归一化到标准枚举）
2. **Given** 历史文档对应的 project_archive 不存在（极少见，理论上 ProjectImportService 已建档），**When** 迁移执行，**Then** 跳过该条记录并在日志中记录（避免 archive_id 为 NULL 违反外键约束）
3. **Given** 同一 project_documents 记录已有对应 archive_file（已归档过，通过 file_path 匹配判定），**When** 迁移重复执行，**Then** 不重复插入（迁移幂等）

---

### Edge Cases

- **OBS 直传失败回退 multipart**：前端 `useObsProjectDocumentUpload` 已处理，回退走 multipart 路径，归档不受影响（已有逻辑）
- **fileUrl 为空字符串**：JSON 路径的 `createProjectDocument` 如果 fileUrl 为空，归档时 `file_path` 字段传空字符串（NOT NULL 约束允许空字符串）。不影响归档记录创建，但档案下载时该文件无法定位
- **documentCategory 为空**：`DocumentCategoryNormalizer.normalize(null)` 返回 null，`attachFileToArchive` 内部 fallback 到 'OTHER'
- **documentCategory 是历史别名（如 TENDER_DOCUMENT）**：`DocumentCategoryNormalizer` 已处理，归一化到 TENDER
- **归档失败**：`attachFileToArchive` 抛异常时，主流程（project_document 创建）不应失败。通过 try-catch 包裹，log.warn 记录但不抛出。档案缺失比文档上传失败更可接受
- **删除项目文档时档案记录的同步**：当前 `deleteProjectDocument` 只走 `BindingGateway.onDocumentDeleted`，`BidResultProjectDocumentBindingGateway` 只处理 BID_RESULT 类型。普通文档删除后 archive_file 不会同步删除。本次修复不涉及删除同步（保持现状），列入后续技术债
- **并发上传同一文件名**：archive_file 表无 document_id 关联字段，若两次上传同名文件，archive_file 会有两条记录（file_name 相同，file_path 不同）。本次修复不引入 document_id 字段（避免 schema 扩大）

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在通过 JSON API 创建 project_document（即 OBS 直传 / 已有 fileUrl 的非 multipart 上传）后，自动调用 `attachFileToArchive` 将该文件归档到对应 project_archive
- **FR-002**: 系统 MUST 在 multipart 上传路径保留原有归档行为不变（archive_file 仍有一条记录），不重复归档、不丢失归档
- **FR-003**: 系统 MUST 在通过 JSON 路径创建 project_document 时，将 documentCategory 归一化后写入 archive_file.document_category（与 multipart 路径行为一致）
- **FR-004**: 系统 MUST 在通过 JSON 路径创建 project_document 时，将 fileUrl 写入 archive_file.file_path（NOT NULL 约束）
- **FR-005**: 系统 MUST 在 project_archive 不存在时先自动建档（archive_status='ACTIVE'），再 attach 文件
- **FR-006**: 系统 MUST 提供数据修复迁移脚本 V1168，扫描所有 project_documents 中 fileUrl 以 `obs-direct:` 开头且无对应 archive_file 的记录，回填 archive_file
- **FR-007**: 数据修复迁移 MUST 幂等：重复执行不产生重复 archive_file 记录
- **FR-008**: 系统 MUST 在 `ProjectDocumentWorkflowService.createProjectDocument` 末尾统一调用 `attachFileToArchive`，删除 `ProjectDocumentUploadWorkflowService` 中重复的 `attachFileToArchive` 调用，避免双写
- **FR-009**: 系统 MUST 保留 `ProjectDocumentBindingGateway` 扩展点不变（`BidResultProjectDocumentBindingGateway` 仍只处理 BID_RESULT 类型，不被归档逻辑影响）
- **FR-010**: 系统 MUST 在 `attachFileToArchive` 失败时不影响 project_document 主流程（try-catch 包裹，log.warn 不抛出）
- **FR-011**: 单元测试 MUST 覆盖：JSON 路径创建文档后 archive_file 有对应记录、multipart 路径不重复归档、documentCategory 归一化在两条路径行为一致

### Key Entities *(include if feature involves data)*

- **ProjectDocument（项目文档）**: project_documents 表，本次修复不修改其 schema。新增归档触发点
- **ProjectArchive（项目档案）**: project_archive 表，本次修复不修改 schema。自动建档逻辑保留
- **ArchiveFile（归档文件）**: archive_file 表，本次修复不修改 schema。新增数据来源：JSON 路径创建的 project_document
- **DocumentCategoryNormalizer（文档分类归一化器）**: 纯核心组件，复用现有。本次修复不修改

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: OBS 启用环境下，任意阶段通过 OBS 直传上传文件后，archive_file 表新增一条对应分类记录，100% 覆盖
- **SC-002**: OBS 未启用环境下，multipart 上传文件后，archive_file 表新增记录数与修复前一致（无回归）
- **SC-003**: 部署 V1168 迁移后，所有 fileUrl 以 `obs-direct:` 开头且无对应 archive_file 的 project_documents 记录 100% 被回填
- **SC-004**: 案例沉淀前置条件检查（`CasePrecipitationAppService.checkReadiness`）在 OBS 直传标书文件后正确识别 hasBidFile=true
- **SC-005**: 单元测试覆盖率：JSON 路径归档新分支 100% 覆盖（创建后 archive_file 有记录、归一化分类正确、file_path 取 fileUrl）
- **SC-006**: 现有 multipart 归档行为 0 回归（`ProjectDocumentUploadWorkflowServiceTest` 全绿）

## Assumptions

- 服务器部署 OBS 直传能力（`VITE_OBS_ENABLED=true`）是默认配置，修复主要针对 OBS 启用环境
- OBS 直传的 fileUrl 形如 `obs-direct:{uploadId}`，下载链路由 `ProjectDocumentDownloadService` 处理（不在本次修复范围）
- 档案台账下载 archive_file 的链路（`/api/archive/files/{fileId}/download`）能处理 `obs-direct:` 前缀的 file_path；若不能，需在实现阶段补处理（待 research.md 确认，列为已知风险）
- 数据修复迁移的"无对应 archive_file"判定通过 `archive_file.file_path = project_documents.file_url` 匹配（因为当前 archive_file 表无 document_id 字段）
- 本次修复不引入 archive_file 表的 document_id 关联字段（避免 schema 变更扩大），幂等通过 `file_path` 匹配判定
- 删除 project_document 时不联动删除 archive_file（保持现状，本次修复不涉及删除同步）
- file_size 在 archive_file 中取 0L（project_documents.size 是 VARCHAR 字符串如 "1MB"，SQL 层无法可靠解析为字节；接受字段值不精确，下载/统计不依赖此字段）

## Open Questions

- **OQ-001**：档案台账下载 `/api/archive/files/{fileId}/download` 是否已支持 `obs-direct:` 前缀的 file_path？若不支持，需要在本 spec 范围内补处理还是单独拆 spec？（research.md 调查后决定）
- **OQ-002**：数据修复迁移 V1168 是否需要同时回填 multipart 历史路径上传但未归档的文档？（理论上是 0 条，因为 multipart 路径一直有归档；但若有边缘 case 漏归档，是否一并回填？倾向不回填，仅处理 OBS 直传场景）
