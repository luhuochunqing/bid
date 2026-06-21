# 技术债追踪器 (tech-debt-tracker)

> 供后台"文档园丁" / 重构 agent 定期扫描的结构化技术债清单。新增技术债时追加到对应分类下，处理后标记 `status: resolved` 并保留记录。

## 命名约定

每条记录建议字段：
```
- area: <模块/包/文件>
  type: <god-class | dead-code | mock-leak | out-of-sync-doc | dependency-debt | test-gap>
  severity: <high | medium | low>
  status: <open | in-progress | resolved>
  source: <发现来源，如 implementation-notes 某行 / 某次 review>
  note: <一句话说明与建议处理方式>
```

## 当前已知技术债

来源：`SECURITY.md §Mock 政策` 与 `docs/reports/`、`backend/implementation-notes.md`。

### 安全权限类

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderTransferController.java`
  type: security
  severity: high
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.3 第1项
  note: 转派接口 Controller 层仅 `isAuthenticated()`，任何登录用户可调用。已修复：方法级注解 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')")` 限制为投标管理员/组长。

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
  type: security
  severity: high
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.3 第2项
  note: 编辑/删除接口白名单含 `ROLE_STAFF`，bid_specialist 可越权操作。已修复：`PUT/DELETE /api/tenders/{id}` 收窄为 `hasAnyRole('ADMIN', 'MANAGER')`。

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
  type: security
  severity: medium
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.2 第4项 / §6 P1
  note: `participateBid`（投标决策）和 `abandonBid`（弃标决策）接口缺少 `@PreAuthorize`，仅依赖 Service 层校验，防护层过薄。已修复：添加 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')")` 限制为投标管理员/组长。

### 遗留清理类

- area: `frontendDemo` 适配层 / `demoPersistence`
  type: mock-leak
  severity: medium
  status: open
  source: SECURITY.md §Mock 政策（遗留代码现状）
  note: 历史遗留适配层，仅作清理对象，不允许新增、不允许扩散。

- area: `src/mock` / `src/api/mock-adapters/` / `.env.mock`
  type: mock-leak
  severity: low
  status: resolved
  source: SECURITY.md §Mock 政策（前端 Mock）
  note: 已清空，`src/api/config.js` 硬编码 `mode: 'api'`。

### 文档同步类

- area: `docs/generated/db-schema.md`
  type: out-of-sync-doc
  severity: low
  status: resolved
  source: docs/generated/README.md
  note: 由 `scripts/generate-db-schema.mjs` 自动生成，跟随 Flyway 迁移刷新。

- area: `docs/lessons/README.md`
  type: out-of-sync-doc
  severity: medium
  status: resolved
  source: knowledge-capture (CO-279 / CO-281 session)
  note: 文件第 15 行残留未解决的 git 冲突标记 `<<<<<<< HEAD`，已在本 session 中修复，并补充 CO-279、spring-boot-actuator-gotchas 索引条目。建议后续提交前检查文档文件是否含冲突标记。

### 字段名双轨制

- area: `backend/src/main/java/com/xiyu/bid/tender/core/TenderEvaluationCustomerInfoPolicy.java`
  type: out-of-sync-doc
  severity: medium
  status: open
  source: docs/lessons/root-cause-analysis-co-266-co-267.md
  note: 客户信息 infoKey 存在双轨命名：EVALUATION_BASIS / INFO_TENDENCY_BASIS、CONTACT（旧 CRM 字段）/ CONTACT_INFO（新标准）。当前通过 TenderIntegrationService 兼容映射缓解，建议未来统一收敛为一套标准 key，并移除兼容代码。

### 流程不一致与死代码类

- area: `backend/src/main/java/com/xiyu/bid/tender/service/TenderSubmissionService.java`
  type: dead-code
  severity: low
  status: resolved
  source: CO-274 复盘（PR #842）
  note: `TenderSubmissionService.proceedToBid()` 没有任何 Controller 调用，疑似 V118/V119 快速投标遗留方法。已删除该方法及关联的 `copyEvaluationToProject` 方法，清理无用 import 和字段。

- area: `backend/src/main/java/com/xiyu/bid/tender/core/AssignmentPermissionRules.java`
  type: dead-code
  severity: low
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.5 第1项
  note: 审计报告误判为死代码。实际被 `TenderAssignmentPermissions.java` 第68行和第79行调用，用于 `canFill` 和 `canDecide` 权限判定。非死代码，无需清理。

- area: `src/views/Bidding/detail/useTenderActions.js` / `src/views/Bidding/list/useTenderListPage.js`
  type: out-of-sync-doc
  severity: medium
  status: open
  source: CO-274 复盘（PR #842）
  note: 标讯「投标」存在两个行为不一致的入口：详情页自动创建项目，列表页跳转 `/project/create` 手工创建。建议产品侧统一交互，或至少在两处入口补充一致的测试覆盖。

- area: `src/views/Bidding/detail/useTenderActions.js`
  type: test-gap
  severity: medium
  status: open
  source: CO-274 复盘（PR #842）
  note: `proceedToBid` 失败被空 catch 吞掉，导致后端 404 对用户不可见。需补充错误反馈与 E2E 回归测试。

### 接口规范设计缺陷类

- area: 接口规范/CRM 对接（`docs/integration/integration-tender-api-v3.1.md` §3.2 推标讯接口 / `WebhookEventListener` / `TenderPushRequest`）
  type: out-of-sync-doc
  severity: medium
  status: open
  source: CO-277 深挖（CRM 实推商机主键 id，非编号 code）
  note: 推标讯接口仅定义 `crmOpportunityId` 字段，CRM 据此推送**主键 id**（如 20916）；但 `bidInfoSync` 回传契约要求商机**编号 code**（CC... 格式）。CO-277 的"识别纯数字 id → 反查 code"本质是补偿这个设计缺陷，而非弯路。演进路径：接口规范新增 `crmOpportunityCode` 字段让 CRM 显式推 code，代码优先用 code（`firstNonBlank(crmOpportunityCode, crmOpportunityId)`），保留 id 反查作为兜底；需 CRM 团队配合改推送代码。当前不改动——CO-277 已生效，改接口需外部协调，且向后兼容仍需保留 id 反查。

### 待登记

> 后续发现的技术债请追加到对应分类下，不要新建文件。
