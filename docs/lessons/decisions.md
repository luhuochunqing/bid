# 架构决策记录

> 记录项目中重要的架构/设计决策，包括选型、取舍和拍板的方案。按 session 追加条目。

---

## 1. GAP 附件加载统一通过 DocumentService.getDocuments() 入口

**日期**: 2026-06-20
**决策者**: trae
**相关 Issue**: CO-262

### 背景

CO-262 修复 CRM 商机关联回填的 GAP 附件未持久化问题时，最初存在两套 GAP 附件加载代码路径：

1. `TenderEvaluationService.toDTO()` 和 `TenderEvaluationReviewService.toDTO()` 调用 `documentService.getDocuments(tenderId)`
2. `TenderEvaluationSubmissionService.loadOrInitDraft()` 调用 `gapFilesSync.loadGapFiles(tenderId)`

两者内部都是调用同一个 `projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(ENTITY_TYPE_EVALUATION_GAP, tenderId)`，完全相同。

### 问题

- **重复代码**：两套路径做完全相同的事
- **维护风险**：未来查询逻辑变更（如加缓存、改排序）容易漏改其中一个
- **职责不清**：`GapFilesSync` 既负责"写"（applyGapFiles）又负责"读"（loadGapFiles），但"读"已经有 `DocumentService.getDocuments()` 负责

### 决策

统一用 `TenderEvaluationDocumentService.getDocuments()` 作为 GAP 附件加载的唯一入口：

- `TenderEvaluationService` / `TenderEvaluationReviewService` / `TenderEvaluationSubmissionService` 三个 Service 都注入 `TenderEvaluationDocumentService`，调用 `getDocuments(tenderId)`
- 删除 `TenderEvaluationGapFilesSync.loadGapFiles()` 方法
- `TenderEvaluationGapFilesSync` 只保留"写"职责（`applyGapFiles`）

### 取舍

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| 统一用 `DocumentService.getDocuments()` | 单一入口，职责清晰 | `GapFilesSync` 丧失"读"能力 | ✅ 采纳 |
| 统一用 `GapFilesSync.loadGapFiles()` | 读写都在一个类 | 需要将 `GapFilesSync` 改为 Spring Bean，调整可见性 | ❌ 改动更大 |
| 保持两套路径 | 无需改动 | 重复代码，维护风险 | ❌ 不解决技术债 |

### 验证

- 三个 Service 的 `toDTO()` / `loadOrInitDraft()` 都调用 `documentService.getDocuments()`
- `TenderEvaluationGapFilesSync` 只剩 `applyGapFiles` 一个 public 方法
- 80 个后端测试全绿，33 个架构测试全绿

### 相关文档

- `docs/lessons/root-cause-analysis-co262-crm-eval-gap-files.md` — 完整根因分析
- `docs/lessons/crm-integration-lessons.md` §9 — CRM 集成经验

---

## 2. 阶段变更通知必须携带明确 actor，旧签名使用系统 actor 兜底

> 决策日期：2026-06-21
> 决策者：zcode
> 状态：已采纳

### 背景

`POST /api/projects/{id}/drafting/submit-bid` 在阶段成功切到 `EVALUATING` 后，发送阶段变更通知时触发数据库错误：`Column 'created_by' cannot be null`。根因是 `ProjectNotificationService.notifyStageTransition(projectId, fromStage, toStage)` 的旧三参签名没有 actor 参数，通知创建最终把 null 写入 `notification.created_by`。

### 决策

新增 actor-aware 的四参 `notifyStageTransition(projectId, fromStage, toStage, userId)`；`submitBid` 调用四参方法并传入 `currentUserId`。保留旧三参方法以兼容既有调用，但旧签名统一委托到 `SYSTEM_USER_ID = 0L`，禁止再向通知创建链路传 null actor。

### 备选方案（及否决理由）

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| 四参方法传真实 actor，旧三参用系统 actor 兜底 | 最小改动；保留兼容；submitBid 审计主体准确 | `0L` 仍是约定值，不一定有真实用户记录 | ✅ |
| 全量修改所有调用方，删除三参签名 | 语义最清晰，编译期强制 actor | 改动范围大，超出本次 500 修复范围 | ❌ 本次只做直接相关最小修复 |
| 放宽 `notification.created_by` 数据库约束 | 可避免 null 插入失败 | 破坏审计完整性，掩盖调用方问题 | ❌ 不符合审计字段非空语义 |
| 在 `sendNotification` catch 后吞掉异常 | 表面避免接口 500 | JPA/事务可能已被污染，且 null createdBy 仍未解决 | ❌ 治标不治本 |

### 权衡与约束

- `submitBid` 这类用户触发动作必须传真实 `currentUserId`，保证通知审计可追溯。
- 旧三参方法只作为兼容入口；新代码应优先使用四参签名。
- `SYSTEM_USER_ID = 0L` 是最小兼容方案。如果未来外键或审计要求 `created_by` 必须对应真实用户，应引入正式系统用户账号或调整通知创建人模型。

### 影响范围

- `backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java`
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java`
- `backend/src/test/java/com/xiyu/bid/project/notification/ProjectNotificationServiceTest.java`
- `backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java`

### 相关文档

- `docs/lessons/root-cause-analysis-stage-notification-created-by.md` — 完整根因分析
- `docs/lessons/lessons-learned.md` §10 — 同一接口错误形态变化时的日志排查教训

---

## 3. CRM 商机负责人优先于本地采购人映射，自动分配不得覆盖

> 决策日期：2026-06-26
> 决策者：mimo
> 状态：已采纳

### 背景

CRM 推送标讯 581 后，王凯毅（工号 08687，User.id 5052）作为 CRM 商机负责人本应担任项目负责人，但实际落库 `project_manager_id=2556`（郑蓉蓉）。根因是 `createNewTender` 调用链中，`CrmTenderLinkService.linkIfPresent` 已通过 CRM 商机接口设置了正确的负责人，但随后 `TenderIntegrationCommandSupport.tryAutoAssign` 又按 `purchaserName` 匹配本地 `CrmProjectMapping` 映射表（海德鲁铝型材 → 郑蓉蓉），无条件覆盖了 CRM 商机负责人。

### 决策

在 `tryAutoAssign` 入口加 guard clause，标讯已有 `projectManagerId` 或 `projectManagerName`（由 CRM 商机负责人设置）时，跳过自动分配：

```java
void tryAutoAssign(Tender tender) {
    if (tender.getProjectManagerId() != null || hasText(tender.getProjectManagerName())) {
        log.info("Tender {} already has project manager (id={}, name={}), skip auto-assignment", ...);
        return;
    }
    // ... 原有自动分配逻辑
}
```

### 备选方案（及否决理由）

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| tryAutoAssign 入口 guard clause | 影响面最小，保留自动分配兜底能力 | guard clause 散落在调用方 | ✅ |
| 修改 applyAssignmentResult 仅在原值为空时才设 | 覆盖所有调用方 | 改动核心逻辑，影响其他调用路径 | ❌ 影响面大 |
| 删除 tryAutoAssign，全部由 CRM 商机接口决定 | 逻辑最清晰 | 失去未关联商机标讯的兜底分配能力 | ❌ 业务降级 |
| 让本地映射表优先于 CRM 商机接口 | 本地配置可控 | 业务上 CRM 商机负责人是 source of truth，本地映射只是兜底 | ❌ 业务语义错误 |

### 权衡与约束

- **业务优先级**：CRM 商机负责人是 source of truth，本地 `CrmProjectMapping` 映射表只是兜底（针对未关联商机的标讯）
- **guard clause 仅检查 `projectManagerId` 不够**：CRM 商机接口返回的工号未匹配本地用户时，只会设 `projectManagerName`（无 id），因此必须同时检查 name 字段
- **自动分配逻辑保留**：未关联商机的标讯仍走自动分配，guard clause 不影响兜底能力

### 影响范围

- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandSupport.java`
- `backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationCommandSupportTest.java`

### 存量数据

PR #1173 部署后到本 PR 部署前创建的标讯（如 581，郑蓉蓉被错误分配），需在服务器上跑数据修复脚本把 `project_manager_id` 改回王凯毅（5052）。这部分不在本 PR 范围内，部署后单独处理。

### 相关文档

- `docs/lessons/root-cause-analysis-crm-leader-priority.md` — 完整根因分析
- `docs/lessons/crm-integration-lessons.md` §11 — projectManagerId 存储与调用链覆盖经验

---

## 3. Controller @PreAuthorize 放宽为 isAuthenticated()，真权限交给 Service 层 Policy

**日期**: 2026-06-29
**决策者**: cursor
**相关 Issue**: CO-375（Linear）/ 内部任务编号 CO-383
**状态**: 已采纳

### 背景

`ProjectDocumentController.deleteProjectDocument` 的 `@PreAuthorize` 在 CO-382 修复时收紧为 `hasAnyRole("ADMIN","BIDADMIN","BID_TEAMLEADER")`，意图做"早过滤"挡住非管理员。但实际业务规则中，**上传者本人在未提交前也应能删除自己上传的文件**（可能传错需要重传）。

Controller 层 `hasAnyRole` 早过滤直接挡住了 bid-projectLeader 用户 08687，导致他无法删除自己上传的文件，根本到不了 Service 层 Policy。

### 问题

- **Controller 早过滤过度收紧**：`hasAnyRole` 是基于角色的过滤，无法表达"上传者本人"这种基于身份的授权规则
- **隐藏 Policy 问题**：Controller 直接 403 挡住，Policy 内部的 `canDelete` 即使想放行上传者本人也接收不到请求
- **测试盲区**：测试环境主要用 admin 账号测试，Controller 早过滤在 admin 路径下不暴露问题
- **业务规则错配**：业务需要"上传者本人可删除自己未提交的文件"，但 Controller 角色过滤无法表达这个规则

### 决策

Controller 层 `@PreAuthorize` 放宽为 `isAuthenticated()`，真权限交给 Service 层 `ProjectDocumentWorkflowPolicy.canDeleteProjectDocument`：

```java
@DeleteMapping("/{documentId}")
@PreAuthorize("isAuthenticated()")  // 只做"是否登录"级别的过滤
public ResponseEntity<ApiResponse<Void>> deleteProjectDocument(
        @PathVariable Long projectId,
        @PathVariable Long documentId
) {
    projectWorkflowService.deleteProjectDocument(projectId, documentId);
    return ResponseEntity.ok(ApiResponse.success("Project document deleted successfully", null));
}
```

Service 层 Policy 承担真权限闸门：

```java
public static AuthorizationDecision canDeleteProjectDocument(
        String roleCode, Long currentUserId, Long uploaderId) {
    // 管理员组：admin/bidAdmin/bid-TeamLeader → permit
    // 上传者本人：currentUserId.equals(uploaderId) → permit
    // 其他：deny
}
```

### 取舍

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| Controller `isAuthenticated()` + Service Policy 真权限 | 可表达身份维度授权（上传者本人）；权限集中管理 | Controller 层不再做角色过滤，依赖 Service 层正确性 | ✅ 采纳 |
| Controller `hasAnyRole` + Service Policy 双层过滤 | 双层防御 | 无法表达身份维度授权；上传者本人永远被 Controller 挡住 | ❌ 业务规则无法实现 |
| Controller SpEL 表达式 `@PreAuthorize("@documentAuth.canDelete(authentication, #documentId)") | 单层过滤 | SpEL 表达式复杂；权限规则分散在多个 Bean 中；测试困难 | ❌ 维护成本高 |
| 全部放 Controller 层（在 Controller 内手写 if 判断） | 直观 | Controller 承担业务逻辑，违反分层；无法单测 | ❌ 违反架构边界 |

### 权衡与约束

1. **Controller 只做"是否登录"过滤**：`isAuthenticated()` 是最低级别的过滤，确保用户已登录。任何基于角色或身份的授权规则都交给 Service 层 Policy。
2. **Service 层 Policy 是真权限闸门**：所有权限决策集中在 Policy 类中，便于单测和维护。
3. **Policy 必须包含所有决策维度**：方法签名必须显式传入 `roleCode`、`currentUserId`、`uploaderId` 等所有决策维度，不能依赖隐式上下文。
4. **风险：Controller 层不再做角色过滤**：如果 Service 层 Policy 有 bug，Controller 层无法兜底。通过严格的单测覆盖（PolicyTest 46 个测试）来降低风险。

### 验证

- Controller `@PreAuthorize` 改为 `isAuthenticated()`
- Service 层 Policy 承担真权限闸门
- `ProjectDocumentWorkflowPolicyTest`：46 个测试全 Green（覆盖管理员组、上传者本人、非上传者、null 维度等场景）
- `ProjectDocumentWorkflowServiceTest`：18 个测试全 Green
- `ArchitectureTest`：26 条规则全 Green

### 适用范围

本决策适用于所有需要"身份维度授权"的接口（如：上传者本人可删除自己上传的文件、任务 assignee 可修改自己任务、审核人可查看自己审核的文档等）。

对于纯角色维度的接口（如：只有管理员能查看系统日志），仍可使用 `hasAnyRole`。但建议统一用 `isAuthenticated()` + Service Policy，保持架构一致性。

### 相关文档

- `docs/lessons/root-cause-analysis-co-375-uploader-delete-permission.md` — 完整根因分析
- `docs/lessons/lessons-learned.md` §24 — Policy canUpload/canDelete 权限矩阵必须对称设计
- `backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectDocumentController.java` — Controller 实现
- `backend/src/main/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicy.java` — Policy 实现
