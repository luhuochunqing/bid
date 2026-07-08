# 实现笔记 — 通知模块 P1-3 + P2-6 + P2-7 优化

> 分支：`agent/zcode/notification-refinements`

## 决策记录

### 1. P1-3 范围界定：聚焦 A+C+D 组

调研发现接收人解析重复有 4 组：
- A 组：admin 三连码（3 处完全复制粘贴）
- B 组：按角色码解析（9 处跨 7 模块）
- C 组：项目成员解析（2 处）
- D 组：filterRecipientsSafe（2 处完全相同）

**决策**：只做 A+C+D（7 处，跨 project 模块），B 组作为独立后续 PR。
**理由**：B 组跨 7 个模块（tender/alerts/resources/personnel/platform/task），工作量和回归风险都大，独立 PR 更安全。

### 2. 复用已有常量 GLOBAL_ACCESS_ROLES

原计划新建 `PROJECT_ADMIN_REVIEWER_ROLES` 常量，但调研发现 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES`（`Set.of("admin", "/bidAdmin", "bid-TeamLeader")`）与 A 组硬编码完全一致——直接复用，无需新建常量。

### 3. getProjectLeadIds 保留不迁移

它联合 `ProjectMemberRepository` + `ProjectLeadAssignmentRepository`，是 `notifyInitiationApproved` 专有组合查询。放入通用解析器会让所有调用方被迫注入不需要的 `ProjectLeadAssignmentRepository` 依赖。

### 4. resolver 返回不可变列表的陷阱

`resolver.getProjectMemberUserIds` 返回 `.toList()`（Java 16+，不可变），而 `ProjectNotificationService.notifyAbandonBid` 调 `recipientIds.addAll(...)` 试图修改它 → `UnsupportedOperationException`。

**修复**：调用方包 `new ArrayList<>(resolver.getProjectMemberUserIds(...))`。
**教训**：解析器返回值不应被调用方修改——返回不可变列表是正确做法，调用方需要修改时应自己包装。

### 5. P2-7 映射表设计

按文档生命周期阶段分流到项目详情子页面：
- TENDER → initiation（招标文件属立项阶段，**不是** tender——前端无 /tender 路由）
- BID → drafting
- OPEN_LIST → evaluation
- WIN_NOTICE / BID_RESULT_* → result
- DEPOSIT_RECEIPT → closure
- 其他/null → drafting（兜底）

**陷阱**：RETROSPECTIVE_REPORT 会被 DocumentCategoryNormalizer 归一化为 OTHER → 兜底 drafting。如果产品坚持复盘报告要跳 /retrospective，需在归一化前捕获原始值——但这与 CO-420 归一化策略相悖，不建议为单分类破例。

## 改动清单

### 新建文件（4 个）
1. `DocumentOperationType.java` — 枚举（UPLOAD/MODIFY/DELETE + 中文 label）
2. `DocumentChangeTargetUrlResolver.java` — 纯核心 targetUrl 分流策略
3. `NotificationRecipientResolver.java` — @Component 接收人解析器（4 方法）
4. 3 个对应测试文件

### 修改文件（7 个）
1. `DocumentChangeNotificationService.java` — 签名变更（operationType 枚举 + documentCategory 参数）+ 用 resolver
2. `ProjectNotificationService.java` — 用 resolver 替代 private 方法
3. `TaskReviewNotificationService.java` — 用 resolver.filterByProjectAccess
4. `ProjectClosureService.java` — 用 resolver.getAdminUserIds
5. `ProjectRetrospectiveService.java` — 同上
6. `ProjectDocumentWorkflowService.java` — 调用方传 documentCategory + 枚举
7. 5 个对应测试文件 + baseline
