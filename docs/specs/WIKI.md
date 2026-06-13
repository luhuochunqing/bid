# WIKI.md — 双栈知识底座治理规范

> 本文件定义 Wiki 在“研发 + 实施”阶段的统一治理协议。  
> LLM 对 Wiki 的所有更新必须遵循此文件。

## 1. 架构目标

- Wiki 同时服务两类核心场景：
  - `Engineering Space`：研发设计、模块边界、技术决策、实现追溯
  - `Implementation Space`：实施交付、里程碑推进、风险控制、验收闭环
- 采用混合摄入模式：优先接收原始 Office/Markdown 文件，由 LLM 自动抽取并合成知识页面。

## 2. 分层模型

```text
Layer 1  Raw Sources
  .wiki/sources/
  - 原始证据层（不可改写）

Layer 1.5 Extracts
  .wiki/extracts/
  - 自动抽取中间层（可重跑、可审计）

Layer 2  Wiki Pages
  .wiki/pages/
  - 合成知识页面（可维护、可互链）

Layer 2.5 Outputs
  .wiki/outputs/
  - 问答产物回流层（答复稿、Marp、图表等）

Layer 3  Schema
  WIKI.md
  - 治理协议（人工确认后变更）
```

## 3. 目录与索引

```text
.wiki/
├── INDEX.md                  # Source Catalog（源文档编目）
├── PAGE_INDEX.md             # Page Catalog（页面索引）
├── log.md                    # 操作日志（可追加）
├── catalog/
│   ├── source-catalog.json   # Source Catalog 机器可读版本
│   ├── page-catalog.json     # Page Catalog 机器可读版本
│   └── pending-build.json    # 增量构建输入清单
├── sources/                  # 原始资料（不可改写）
├── extracts/                 # 自动抽取中间层
├── pages/                    # 合成知识层
└── outputs/                  # 输出回流层
```

## 4. 页面 Frontmatter 协议

每个页面必须包含：

```yaml
---
title: 页面标题
space: engineering | implementation
category: architecture | business | module | guide | reference | decision
tags: [标签1, 标签2]
sources:
  - docs/xxx.md
backlinks:
  - other-page-slug
created: 2026-04-22
updated: 2026-04-22
health_checked: 2026-04-22
---
```

## 5. 混合摄入模式（默认）

- 用户优先提供原文件：`docx/xlsx/pdf/图片/md/txt`
- LLM 执行 `npm run wiki:ingest`：
  - 扫描 `.wiki/sources/`
  - 抽取到 `.wiki/extracts/`
  - 更新 Source Catalog
- 对复杂版式或低置信度抽取，标记为 `manual_review`，允许人工补充 Markdown。

## 6. 命令与触发策略

- `npm run wiki:ingest`：摄入与抽取
- `npm run wiki:build`：页面规范化、backlinks、Page Catalog 编译
- `npm run wiki:check`：健康检查门禁

触发策略：
- 手动运行 ingest/build
- 提交前运行 `wiki:check`（pre-commit 阻断）

大小写说明：
- 当前环境文件系统大小写不敏感，不能同时稳定维护 `INDEX.md` 与 `index.md`。
- 因此 Page Catalog 使用 `PAGE_INDEX.md` 命名避免冲突。

## 7. 质量门禁

- 页面 frontmatter 字段完整
- `[[wiki-link]]` 目标页面存在
- `sources` 路径必须可追溯
- Source Catalog 与 Page Catalog 必须可读且非空
- `updated` 不应超过 30 天未刷新
- `health_checked` 不应超过 7 天

## 8. 双空间治理约束

- Engineering 页面必须可追溯到代码/技术文档事实源
- Implementation 页面必须可追溯到交付文档、计划、验收和风险台账
- 两空间关键节点要双向链接，形成“研发事实 -> 实施动作 -> 验收证据”闭环

## 9. 数据契约

### Source Catalog

- `id/path/type/topic/hash/status/extract_path/confidence/ingested_at`

### Page Catalog

- `slug/title/space/category/sources/backlinks/updated/health_checked/path`

## 10. 变更原则

- Layer 1 原始文件不可改写，仅允许新增版本
- Layer 1.5 可重跑覆盖（由 ingest 负责）
- Layer 2 页面由 build 统一规范化
- WIKI.md 作为治理协议，重大变更需人工确认

## 11. docs/ 与 .wiki/ 分层协议

### 11.1 定位

```text
docs/         ← 原始文档库 / 事实源仓库
  - 不可改写的源 Office/PDF 文件及其中文副本
  - 临时工作产物（开发计划、调研笔记、测试用例）
  - 交付物原始素材（讲标文件、演示脚本、汇报稿）
  - 是"写入"的地方

.wiki/pages/  ← 合成知识库 / 最终视图
  - 由 docs 内容提炼、结构化、归约后的双空间知识页面
  - 支持 frontmatter/backlinks/自动索引
  - 是"查阅"的地方
```

### 11.2 单向收敛原则

- `docs/` 是事实源，`.wiki/pages/` 的 `sources` 应优先指向 `docs/` 下的文件。
- 同一份知识**禁止双写**：只在一个位置维护。如果 `.wiki/pages/` 有合成页面，`docs/` 下不应有功能重复的独立文件。
- `docs/` 下的"最终态"文档（如架构、数据模型）应通过 `npm run wiki:ingest` + `wiki:build` 合成为知识页面，原始文件保留在 `docs/` 供追溯。
- 清理规则：
  - `docs/plans/`：已完成的历史计划移入 `docs/archives/`
  - `docs/artifacts/` vs `docs/specs/`：讲标/演示类最终产物统一归 `docs/artifacts/`，`docs/specs/` 中不保留副本
  - `docs/` 下的文件若在 `.wiki/pages/` 有对应合成页面，且不再作为独立参考物，应删除或归入 `archives/`

### 11.3 用户导航指引

| 场景 | 去哪里 |
|------|--------|
| 查阅业务知识、架构、实施状态 | `.wiki/pages/_index.md` |
| 查阅模块需求、API 接口 | `.wiki/pages/` 对应模块页面 |
| 投放原始 Office/PDF 文件 | `docs/` 对应子目录 |
| 查阅开发计划 | `docs/plans/`（最新） / `docs/archives/`（历史） |
| 查阅发布清单、验收文档 | `docs/release/` |
| 查阅讲标、演示素材 | `docs/artifacts/` |
| 查阅治理规范 | `docs/specs/WIKI.md` |
| 添加新的原始素材 | 放入 `docs/` 对应子目录 → 运行 `npm run wiki:ingest` |
