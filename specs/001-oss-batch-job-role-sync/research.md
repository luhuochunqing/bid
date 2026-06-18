# Research: OSS 批量岗位/角色回查

**Feature**: OSS 批量岗位/角色回查优化
**Date**: 2026-06-18

## Decision: 新增独立批量查询方法 + 角色解析策略外迁

- 在 `OrganizationDirectoryGateway` 新增 `getUserJobAndRoleListByJobNumbers(List<String> jobNumbers)` 方法，由 `OrganizationDirectoryHttpGateway` 实现。
- 新增 `OssUserJobAndRoleDto` 承载批量接口响应，字段包括：`jobNumber`、`jobName`、`sysRoleList`、`employeeStatus`、`status`、`username`。
- 新增 `JobRoleLookupResolver` 纯函数策略类，集中处理角色解析优先级：人员 > 部门 > 岗位 > sysRoleList。
- 新增 `SystemRoleListMapper` 处理 `sysRoleList` 到内部 `role_code` 的大小写安全映射。
- `OrganizationUserSyncWriter` 在 `upsert` 流程中先按批次调用批量查询，再使用 `JobRoleLookupResolver` 解析角色。

## Rationale

- **批量优先原则**（Constitution V）：现有 `OrganizationUserSyncWriter` 对每个有 `jobId` 的用户单独调用 `/subscription/msg/job`，全量同步时产生数千次外部调用。新接口支持按工号列表批量回查，可将调用次数降低 1～2 个数量级。
- **单一职责**：`OrganizationUserSyncWriter` 当前同时负责用户写入、角色解析、岗位回查，文件行数接近上限。将角色解析外迁符合 IV. Split-First。
- **大小写安全**：历史 Bug 因 OSS 角色码大小写与内部配置不一致导致映射失败，新映射器统一使用 `Locale.ROOT` 小写比较。
- **可观测性**：批量查询在 Gateway 层统一记录请求规模、响应数、耗时，满足 Constitution V 的可观测性要求。

## Alternatives considered

| Alternative | Why Rejected |
|---|---|
| 直接复用 `/subscription/msg/user` 返回字段 | 该接口不保证返回 `jobName` 与 `sysRoleList`，且语义上是用户详情而非岗位/角色专用接口。 |
| 在 `OrganizationUserSyncWriter` 内部完成所有批量查询与解析 | 会进一步膨胀该类，违反 IV. Split-First；且无法独立单测角色优先级逻辑。 |
| 替换现有 `fetchJobByJobId` 实现为批量内部缓存 | 接口契约变更过大，且 `JOB_NOTICE` 事件仍需要单条 job 查询，保留旧接口作为兼容路径更稳妥。 |
| 使用 WebClient 替代 RestTemplate | 当前模块统一使用 RestTemplate，为保持模式一致、减少并发风险，沿用 RestTemplate 并配置合理超时。 |

## Open Questions Resolved

- **批量大小**：默认 50，与现有 `retry.batch-size` 保持一致；新增独立配置 `directory.batch-query-size` 以便单独调整。
- **超时**：连接 3s、读取 10s；批量接口返回数据量可能较大，读取超时高于单条接口的 5s。
- **降级策略**：批量查询失败时，同步任务继续写入用户基础信息，角色解析降级为"无角色"并记录错误日志；不允许批量失败导致整个同步任务失败。
