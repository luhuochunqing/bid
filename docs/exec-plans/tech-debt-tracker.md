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
  status: open
  source: CO-274 复盘（PR #842）
  note: `TenderSubmissionService.proceedToBid()` 没有任何 Controller 调用，疑似 V118/V119 快速投标遗留方法；建议确认后删除，或由 TenderEvaluationController 统一调用。

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

### 待登记

> 后续发现的技术债请追加到对应分类下，不要新建文件。
