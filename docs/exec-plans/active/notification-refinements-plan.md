# 通知模块 P1-3 + P2-6 + P2-7 优化 — 执行计划

> **任务**：技术债清理（3 个优化项）
> **分支**：`agent/zcode/notification-refinements`
> **依赖**：PR #1866 已 merged

## 目标

清理 Review 识别的 3 个 P2 优化项：
- **P1-3**：抽 `NotificationRecipientResolver` 消除接收人解析重复（A+C+D 组）
- **P2-6**：`DocumentOperationType` 枚举替代字符串字面量
- **P2-7**：`DocumentChangeTargetUrlResolver` 按 documentCategory 分流

## 进度

- [x] P2-6：DocumentOperationType 枚举 + 调用方改造
- [x] P2-7：DocumentChangeTargetUrlResolver + 分流
- [x] P1-3：NotificationRecipientResolver + 7 处迁移
- [x] 验证：144 测试全绿
- [ ] 提交

## 决策日志

- 2026-07-08：P1-3 聚焦 A+C+D 组（7 处重复），B 组（9 处跨 7 模块）作为独立后续 PR
- 2026-07-08：`getProjectLeadIds` 保留在 ProjectNotificationService（专有组合查询）
- 2026-07-08：A 组硬编码直接复用 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES`（已存在常量，无需新建）
- 2026-07-08：resolver 返回不可变列表，调用方 `addAll` 需包 `new ArrayList<>()`
