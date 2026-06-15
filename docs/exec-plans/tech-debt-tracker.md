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

### 待登记

> 后续发现的技术债请追加到对应分类下，不要新建文件。
