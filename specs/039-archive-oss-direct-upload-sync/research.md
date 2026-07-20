# Research: OBS 直传项目文档同步归档到项目档案

**Date**: 2026-07-17

**Spec**: [spec.md](./spec.md)

## 背景与根因

### 现象

用户反馈：项目档案台账里沉淀的文档不全，与项目详情-标书制作阶段中看到的项目文档列表不一致。

### 调查链路

项目文档上传有两条后端路径，归档逻辑只挂在其中一条上：

**路径 A：multipart 上传（OBS 未启用 / 直传失败回退）→ 会归档 ✓**

```
前端 useObsProjectDocumentUpload.customUpload
  ↓ formData.set('file', file)  // 有 file key
  ↓ httpClient.post multipart
后端 ProjectDocumentController.uploadProjectDocument  (consumes=MULTIPART_FORM_DATA)
  → ProjectDocumentUploadWorkflowService.createUploadedProjectDocument
    → fileStorage.store(...)  // 写本地存储
    → projectDocumentWorkflowService.createProjectDocument(...)
    → projectArchiveWorkflowService.attachFileToArchive(...)  // ★ 归档触发点
```

**路径 B：OBS 直传（OBS 启用且直传成功）→ 不归档 ✗**

```
前端 useObsProjectDocumentUpload.customUpload
  ↓ tryObsDirectUpload(obsUpload, file) → obs-direct:{uploadId}
  ↓ formData.set('fileUrl', obsFileUrl)  // 无 file key，有 fileUrl
  ↓ httpClient.post JSON
后端 ProjectDocumentController.createProjectDocument  (consumes=APPLICATION_JSON)
  → ProjectDocumentFacade.createProjectDocument
    → ProjectDocumentWorkflowService.createProjectDocument
      → projectDocumentRepository.save(document)
      → projectDocumentBindingGateway.onDocumentCreated(savedDocument)
        → BidResultProjectDocumentBindingGateway.onDocumentCreated
          → if (!isBidResultAttachment) return;  // ★ 标书文件（BID）直接 return，不归档
      → documentChangeNotificationService.notifyDocumentChanged(...)
      // ❌ 没有 attachFileToArchive 调用
```

### 根因

1. **归档逻辑挂错位置**：`attachFileToArchive` 只在 `ProjectDocumentUploadWorkflowService`（multipart 专用）里调用，JSON 路径的 `ProjectDocumentWorkflowService.createProjectDocument` 没有任何归档触发。两条上传路径归档行为不对称。

2. **`ProjectDocumentBindingGateway` 扩展点未补完**：原设计意图是"任何文档创建都经 gateway 编排副作用"，但归档副作用从未通过 gateway 实现，而是硬编码在 upload service 里。`NoOpProjectDocumentBindingGateway` 的存在说明这个扩展点被"占位"了但没补完；`BidResultProjectDocumentBindingGateway` 是 `@Primary` 实现但只处理 BID_RESULT 类型，不处理归档。

### 影响范围

- **不只标书制作阶段**：所有用 `useObsProjectDocumentUpload` composable 的阶段都受影响：
  - `InitiationStage.vue`（立项阶段，TENDER）
  - `DraftingStage.vue`（标书制作阶段，BID）
  - `EvaluationEvidenceUpload.vue`（评标阶段，OPEN_LIST）
  - `ResultConfirmStage.vue`（结果确认阶段，WIN_NOTICE）
  - `RetrospectiveStage.vue`（复盘阶段，OTHER）
  - `ClosureStage.vue`（结项阶段，DEPOSIT_RECEIPT）

- **案例沉淀误判**：`CasePrecipitationAppService.checkReadiness` 检查 `archive_files` 表里是否有 BID 分类文件作为前置条件，OBS 直传的标书文件不在档案表里，会导致案例沉淀误报"缺少标书文件"。

### 历史背景

`backend/implementation-notes.md` 蓝图 4.1.1.1 差距表第 2 项写的是"uploadWorkflow 捕获 physical + attachFileToArchive"——当时只考虑了 multipart 场景。后来为修 APISIX 网关 413（见 `useObsProjectDocumentUpload.js` 顶部注释）加了 OBS 直传，但没补归档逻辑，属于回归型遗漏。

## 修复方案对比

### 方案 A：归档逻辑上提到 createProjectDocument（推荐）

将 `attachFileToArchive` 调用从 `ProjectDocumentUploadWorkflowService` 上提到 `ProjectDocumentWorkflowService.createProjectDocument` 末尾，让 JSON 和 multipart 两条路径统一归档。同时删除 `ProjectDocumentUploadWorkflowService` 里那段重复调用避免双写。

**优点**：
- 改动最小（2 个 Java 文件，约 30 行）
- 不引入新 Bean / 新抽象
- 复用现有 `attachFileToArchive` 方法，归一化、建档逻辑保留
- 与现有 `ProjectDocumentBindingGateway` 扩展点无冲突

**缺点**：
- `ProjectDocumentWorkflowService` 新增对 `ProjectArchiveWorkflowService` 的依赖（跨模块：projectworkflow → casework）。需检查是否引入循环依赖（已确认 casework 不依赖 projectworkflow 的 createProjectDocument，无循环）

**physicalPath 取值**：
- multipart 路径：`storedFile.physicalPath()`（本地存储路径）
- JSON 路径：`savedDocument.getFileUrl()`（OBS 直传场景为 `obs-direct:{uploadId}`）
- 在 `createProjectDocument` 内统一取 `savedDocument.getFileUrl()`，因为 multipart 路径在 `ProjectDocumentUploadWorkflowService.createUploadedProjectDocument` 里把 `storedFile.fileUrl()`（非 physicalPath）作为 fileUrl 传给了 `createProjectDocument`。这样两条路径在 `createProjectDocument` 内看到的都是 fileUrl，统一取 fileUrl 即可

### 方案 B：新建 ArchiveProjectDocumentBindingGateway 实现 gateway

新建 `ArchiveProjectDocumentBindingGateway` 实现 `ProjectDocumentBindingGateway` 接口，在 `onDocumentCreated` 里归档。

**优点**：
- 符合原设计意图（gateway 编排副作用）
- 归档副作用与主流程解耦更彻底

**缺点**：
- gateway 当前是单 Bean 注入（`@Primary` + NoOp），需改成 List 注入或 Composite 模式才能与 BidResult gateway 共存，改动较大
- 跨模块依赖同样存在（gateway 实现需要注入 ProjectArchiveWorkflowService）
- 改动文件数多（新增 gateway 实现 + 修改注入方式 + 修改 BidResult gateway），不符合"最小改动"原则

### 决策

**采用方案 A**。理由：
1. 改动最小、风险最低
2. 不引入新抽象，符合 Constitution VIII（Boring Proven Patterns）
3. gateway 扩展点保留，未来若有更多副作用需求再演进到 Composite 模式
4. 跨模块依赖通过 Spring 的 `@RequiredArgsConstructor` 注入即可，无循环依赖风险

## 数据修复迁移策略

### 范围

扫描 `project_documents` 表中 `file_url LIKE 'obs-direct:%'` 且无对应 `archive_file` 记录的文档，回填 `archive_file`。

### 幂等判定

由于 `archive_file` 表无 `document_id` 关联字段，通过 `file_path = file_url` 匹配判定是否已归档：
- LEFT JOIN archive_file af ON af.archive_id = pa.id AND af.file_path = pd.file_url
- WHERE af.id IS NULL（即未归档）

### 字段映射

| archive_file 字段 | 取值 | 说明 |
|---|---|---|
| archive_id | project_archive.id | 通过 project_id 关联，若 project_archive 不存在则跳过 |
| file_name | project_documents.name | 直接取 |
| document_category | CASE WHEN 归一化 | SQL 层用 CASE WHEN 处理历史别名（BID_DOCUMENT→BID 等） |
| file_path | project_documents.file_url | OBS 直传场景为 `obs-direct:{uploadId}` |
| file_size | 0 | project_documents.size 是 VARCHAR（如 "1MB"），SQL 层无法可靠解析为字节，统一取 0 |
| upload_user_id | COALESCE(project_documents.uploader_id, 0) | 兜底 0 |
| upload_user_name | COALESCE(project_documents.uploader_name, '系统') | 兜底"系统" |
| created_at | NOW() | 迁移执行时间 |

### SQL 层分类归一化

```sql
CASE pd.document_category
    WHEN 'TENDER_DOCUMENT' THEN 'TENDER'
    WHEN 'BID_DOCUMENT' THEN 'BID'
    WHEN 'EVALUATION_EVIDENCE' THEN 'OPEN_LIST'
    WHEN 'RESULT_EVIDENCE' THEN 'WIN_NOTICE'
    WHEN 'CLOSURE_EVIDENCE' THEN 'DEPOSIT_RECEIPT'
    WHEN 'RETROSPECTIVE_REPORT' THEN 'OTHER'
    ELSE COALESCE(pd.document_category, 'OTHER')
END
```

与 `DocumentCategoryNormalizer.normalize()` 的 ALIAS 映射保持一致。

## OQ-001 调查：档案下载链路是否支持 obs-direct: 前缀

### 调查

`ProjectArchiveController` 提供 `/api/archive/files/{fileId}/download` 接口。该接口从 `archive_file.file_path` 取值后决定如何下载文件。

由于本次修复仅在 archive_file.file_path 写入 `obs-direct:{uploadId}` 字符串，下载时若直接把 file_path 当本地路径读文件会失败。

### 决策

**本 spec 不处理档案下载链路对 `obs-direct:` 的支持**。理由：
1. 当前 archive_file 已有 `obs-direct:` 前缀记录的可能性极低（修复前 OBS 直传根本不归档，archive_file 里都是 multipart 的本地路径）
2. 档案下载是次要场景（用户主要在项目详情页下载文档，那里走 project_documents 路径，已支持 obs-direct:）
3. 拆分 spec 保持单一职责，避免改动扩大

**列为已知风险**：V1168 迁移后，档案台账点击下载 OBS 直传归档的文件可能失败。后续单独拆 spec 处理（CO-XXX 待登记）。

**临时缓解**：若用户反馈档案下载失败，可在 `ProjectArchiveController` 下载接口里识别 `obs-direct:` 前缀后重定向到 `/api/projects/{projectId}/documents/{documentId}/download`（需要先反查 project_document）。但需要 archive_file 增加 document_id 字段才能反查，又回到 schema 变更问题。所以短期建议忽略此风险。

## OQ-002 调查：是否回填 multipart 历史漏归档

### 调查

multipart 路径自 V1043（2026-06-04）起一直有 `attachFileToArchive` 调用，理论上无漏归档。但 V1043 之前上传的文档（2026-06-04 之前）确实可能未归档。

### 决策

**不回填 multipart 历史漏归档**。理由：
1. V1043 之前的文档量很少（项目早期），且那时项目档案功能本身也未上线
2. multipart 历史漏归档的判定不能通过 `file_url LIKE 'obs-direct:%'` 过滤，需要扫描所有 project_documents，迁移代价大
3. 若用户反馈个别项目档案不全，可手动通过管理后台重新上传或单独处理

## 跨模块依赖检查

### ProjectDocumentWorkflowService → ProjectArchiveWorkflowService

- `ProjectDocumentWorkflowService` 在 `projectworkflow.service` 包
- `ProjectArchiveWorkflowService` 在 `casework.application` 包
- 现状：`ProjectDocumentUploadWorkflowService` 已注入 `ProjectArchiveWorkflowService`，跨模块依赖已存在
- 修改后：依赖关系从 `UploadWorkflowService → ArchiveWorkflowService` 改为 `WorkflowService → ArchiveWorkflowService`，依赖方向不变，无新增循环

### Spring Bean 加载顺序

- `ProjectArchiveWorkflowService` 是普通 `@Service`，无 `@DependsOn` 约束
- `ProjectDocumentWorkflowService` 注入它无顺序问题

## 测试策略

### 单元测试（ProjectDocumentWorkflowServiceTest）

新增以下用例：
1. `createProjectDocument_ShouldAttachFileToArchive`：调用后 verify `attachFileToArchive` 被调用一次
2. `createProjectDocument_ShouldNormalizeCategoryBeforeArchiving`：传 'BID_DOCUMENT'，verify 收到 'BID'
3. `createProjectDocument_ShouldFallbackToOtherWhenCategoryNull`：传 null，verify 收到 'OTHER'
4. `createProjectDocument_ShouldUseFileUrlAsPhysicalPath`：传 fileUrl='obs-direct:xxx'，verify attachFileToArchive 的 physicalPath 参数 = 'obs-direct:xxx'
5. `createProjectDocument_ShouldNotFailWhenArchiveThrows`：mock attachFileToArchive 抛异常，verify createProjectDocument 仍成功返回

### 单元测试（ProjectDocumentUploadWorkflowServiceTest）

修改现有用例：
1. 移除对 `projectArchiveWorkflowService.attachFileToArchive` 的 verify（因为已上提到 createProjectDocument）
2. 验证 `projectDocumentWorkflowService.createProjectDocument` 仍被调用（归档在其内部完成）

### 集成测试

可选：若单元测试覆盖足够，可不补集成测试。`ProjectWorkflowIntegrationTest` 现有用例应保持全绿。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 归档失败导致文档上传失败 | try-catch 包裹 attachFileToArchive，log.warn 不抛出 |
| 跨模块依赖引入循环 | 已确认 casework 不依赖 projectworkflow.createProjectDocument，无循环 |
| V1168 迁移性能（大表 JOIN） | archive_file 表数据量小（项目数 × 文档数），project_documents 同理，无性能风险 |
| 档案下载 obs-direct: 失败 | 列为已知风险，后续 spec 处理（见 OQ-001） |
| 双写（若忘记删 UploadWorkflowService 的调用） | T004 明确删除，测试用例 verify 不再被 uploadWorkflowService 直接调用 |
