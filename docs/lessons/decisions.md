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

---

## 4. admin 专属权限过滤统一通过 RoleProfileAdminPermissionFilter

**日期**: 2026-07-08
**决策者**: claude
**相关 Issue**: spec 032 / OSS 权限扩散修复

### 背景

spec 032 修复 OSS 用户权限扩散时，发现 `all`、`system.admin`、`warehouse.manage` 等 admin 专属权限键在多处被重复过滤：

- `DataScopeConfigService` 自己维护 `ADMIN_ONLY_PERMISSION_KEYS` 集合
- `UserDetailsServiceImpl` 也调用 `RoleProfileCatalog.withoutAdminOnlyPermissions()`
- `RoleProfileCatalog` 因此类长度接近 300 行限制

### 问题

- **重复实现**：三处逻辑都过滤 admin 专属权限，但实现细节可能分叉
- **类长度超限**：`DataScopeConfigService` 308 行、`RoleProfileCatalog` 326 行，超过 300 行门禁
- **职责错位**：实体类 `RoleProfileCatalog` 不应该承担权限过滤职责

### 决策

新建纯函数类 `RoleProfileAdminPermissionFilter`，统一承担 admin 专属权限过滤和权限列表规范化：

```java
public final class RoleProfileAdminPermissionFilter {
    private static final Set<String> ADMIN_ONLY_PERMISSION_KEYS = Set.of(
        "all",
        RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION,
        RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION
    );

    public static List<String> filter(List<String> permissions) {
        return normalize(permissions).stream()
            .filter(p -> !ADMIN_ONLY_PERMISSION_KEYS.contains(p))
            .toList();
    }

    public static List<String> normalize(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
            .filter(permission -> permission != null && !permission.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
```

调用方统一替换：

- `DataScopeConfigService` → `RoleProfileAdminPermissionFilter.filter(...)` / `.normalize(...)`
- `UserDetailsServiceImpl` → `RoleProfileAdminPermissionFilter.filter(...)`
- `RoleProfileCatalog` → 删除 `withoutAdminOnlyPermissions()` 方法和 `ADMIN_ONLY_PERMISSION_KEYS` 常量

### 取舍

| 方案 | 优点 | 缺点 | 是否采纳 |
|------|------|------|---------|
| 新建 `RoleProfileAdminPermissionFilter` 纯函数类 | 单一职责；可单测；消除重复；类长度合规 | 新增一个类 | ✅ 采纳 |
| 保留 `RoleProfileCatalog.withoutAdminOnlyPermissions()` | 无新增类 | 实体类承担过滤职责；重复逻辑；行数超限 | ❌ 不符合架构规范 |
| 在 `DataScopeConfigService` 中统一过滤后传入其他服务 | 减少类 | `UserDetailsServiceImpl` 仍需过滤，无法完全集中 | ❌ 无法消除重复 |

### 权衡与约束

1. **纯函数、无状态**：`RoleProfileAdminPermissionFilter` 只包含 static 方法，不依赖 Spring 上下文，便于单测。
2. **包位置在 `com.xiyu.bid.permission`**：不放在 `entity` 包，避免 `ArchitectureTest` 失败。
3. **ADMIN_ONLY_PERMISSION_KEYS 集中管理**：新增 admin 专属权限键时只需改一处。
4. **normalize 一并迁移**：去空、trim、去重逻辑原本分散，现在与 filter 放在同一工具类。

### 验证

- `DataScopeConfigService` 从 308 行降至 294 行
- `RoleProfileCatalog` 从 326 行降至 297 行
- `ResponsibilityArchitectureTest` 全绿
- `UserDetailsServiceImplTest`、`DataScopeConfigServiceTest` 全绿

### 适用范围

本决策适用于所有需要对权限列表进行“admin 专属键过滤”或“规范化”的场景。禁止在 Service、Controller 或实体类中重复维护 admin 权限键集合。

### 相关文档

- `docs/lessons/lessons-learned.md` §47 — OSS 用户权限扩散根因
- `docs/lessons/lessons-learned.md` §48 — 止血补丁与技术债清偿分 PR
- `docs/lessons/oss-integration-lessons.md` — OSS 菜单码 1:N 映射集成经验
- `backend/src/main/java/com/xiyu/bid/permission/RoleProfileAdminPermissionFilter.java`
- `backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java`
- `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java`
- PR !1892 — 本决策落地

---

## 5. 评分项功能梳理：ScoreDraftDialog 作为评分项生命周期的唯一入口

**日期**: 2026-07-16
**决策者**: cursor
**相关 Issue**: 评分项功能闭环改造（内部梳理，无 Linear issue）
**状态**: 已采纳（设计阶段，落地待排期）

### 背景

2026-07-16 用户反馈"系统中没有放置评分项的地方"。排查发现系统存在三个与"评分"相关但职责混乱的功能：

| 功能 | 入口 | 实际行为 | 问题 |
|---|---|---|---|
| `ProjectTaskBoardCard` "AI 评分标准解析"按钮 | 任务看板卡片 | 触发 `tender-breakdown`（招标文件拆解） | 命名误导，实际不是评分项解析 |
| `TaskKanban` "AI 评分标准解析"按钮 | 任务看板顶部 | 仅作为查看器，无写入能力 | 与上面同名但行为不同 |
| `ScoreDraftDialog` 评分项解析对话框 | **无 UI 入口** | 真正的评分项解析（写入 `project_score_drafts` 表） | 真入口被隐藏 |

### 问题诊断

**问题 1: 入口错位** — 用户想解析评分项时，看到的按钮（`ProjectTaskBoardCard`）实际触发的是招标文件拆解，不是评分项解析。

**问题 2: 真入口隐藏** — 真正能解析评分项的 `ScoreDraftDialog` 没有 UI 入口，用户无法触达。

**问题 3: 命名冲突** — 两个不同行为按钮都叫"AI 评分标准解析"，用户无法区分。

**问题 4: 功能割裂** — `tender-breakdown` 解析出的"评分标准"不写入 `project_score_drafts` 表，导致评分项数据无法被任务拆解、案例推荐、案例沉淀共用。

### 核心概念：评分项是共同锚点

**评分项**（score draft）= 客户招标文件中的评分标准条目，存储于 `project_score_drafts` 表。

评分项是三个功能的共同锚点：
- **任务拆解**：根据评分项拆解投标任务
- **案例推荐**：根据评分项匹配历史案例
- **案例沉淀**：根据评分项归档中标案例

如果评分项数据缺失或不一致，三个功能都会失效。

### 决策

**闭环设计方案**：

1. **任务看板"AI 评分标准解析"按钮改为触发 `ScoreDraftDialog`**
   - 用户在任务看板点击按钮 → 打开 `ScoreDraftDialog` → 解析评分项写入 `project_score_drafts`
   - 删除原 `tender-breakdown` 触发逻辑

2. **案例推荐抽屉评分项为空时直接打开 `ScoreDraftDialog`**
   - 用户打开案例推荐抽屉 → 检查 `project_score_drafts` 是否有数据
   - 为空 → 自动打开 `ScoreDraftDialog` 引导用户先解析评分项
   - 不为空 → 正常展示案例推荐

3. **`tender-breakdown` 改名为"AI 招标文件解析"并移至项目立项阶段**
   - 改名消除"评分"命名冲突
   - 移至立项阶段：项目立项时解析招标文件，提取基本信息（项目名称、客户、预算等）
   - 不再触发任务看板按钮

4. **实现"一次上传，自动贯通"的评分项生命周期闭环**
   - 项目立项阶段上传招标文件 → 自动解析评分项 → 写入 `project_score_drafts`
   - 任务拆解、案例推荐、案例沉淀都从 `project_score_drafts` 读取
   - 评分项变更时同步通知三个功能模块

### 备选方案（及否决理由）

| 方案 | 优点 | 缺点 | 是否采纳 |
|---|---|---|---|
| `ScoreDraftDialog` 作为唯一入口 + `tender-breakdown` 移至立项 | 入口清晰；评分项数据统一；命名无冲突 | 改动较大，需要重构任务看板和案例推荐 | ✅ 采纳 |
| 保留 `tender-breakdown` 在任务看板，加"评分项同步"逻辑 | 改动小 | 命名冲突仍存在；两套解析路径维护成本高 | ❌ 治标不治本 |
| 删除 `ScoreDraftDialog`，统一用 `tender-breakdown` | 减少功能 | 丢失评分项写入能力；任务拆解/案例推荐失去锚点 | ❌ 业务降级 |
| 重命名按钮但不改逻辑 | 改动最小 | 入口仍错位；评分项数据仍割裂 | ❌ 不解决问题 |

### 权衡与约束

1. **`project_score_drafts` 是单一数据源**：所有评分项相关功能必须从该表读取，禁止从 `tender-breakdown` 临时结果获取
2. **`ScoreDraftDialog` 是唯一写入入口**：保证评分项数据的完整性和一致性
3. **`tender-breakdown` 职责收窄**：只做招标文件基本信息解析，不再承担评分项解析
4. **生命周期闭环**：评分项一次解析，三处使用（任务拆解/案例推荐/案例沉淀）

### 实施路径（三阶段）

**Phase 1: 入口对齐**（短期）
- 任务看板按钮改触发 `ScoreDraftDialog`
- 案例推荐抽屉评分项为空时打开 `ScoreDraftDialog`
- `tender-breakdown` 按钮临时改名为"AI 招标文件解析"

**Phase 2: 数据贯通**（中期）
- `tender-breakdown` 解析结果写入 `project_score_drafts`（或迁移解析逻辑到 `ScoreDraftDialog`）
- 任务拆解从 `project_score_drafts` 读取评分项
- 案例推荐匹配逻辑基于 `project_score_drafts`

**Phase 3: 立项阶段整合**（长期）
- `tender-breakdown` 完全移至项目立项阶段
- 项目立项时自动解析评分项，无需用户手动触发
- 实现"一次上传，自动贯通"

### 验证

- `ScoreDraftDialog` 有 UI 入口，用户可触达
- 任务看板"AI 评分标准解析"按钮触发 `ScoreDraftDialog`，不再触发 `tender-breakdown`
- 案例推荐抽屉评分项为空时自动打开 `ScoreDraftDialog`
- `project_score_drafts` 表数据被任务拆解、案例推荐、案例沉淀共用
- `tender-breakdown` 按钮改名为"AI 招标文件解析"

### 相关文档

- `backend/src/main/java/com/xiyu/bid/scoredraft/` — 评分项模块
- `backend/src/main/java/com/xiyu/bid/tenderbreakdown/` — 招标文件拆解模块
- `src/views/Project/components/ScoreDraftDialog.vue` — 评分项解析对话框
- `src/views/ProjectTaskBoard/components/ProjectTaskBoardCard.vue` — 任务看板卡片
- 飞书文档（内部技术版）：https://my.feishu.cn/docx/ANFTdx5MboHtHGxkGqrcBkJwnDd
- 飞书文档（客户友好版）：https://my.feishu.cn/docx/T143dQfRuoPn4RxqivPcrRI3nmh
