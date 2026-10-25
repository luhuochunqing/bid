---
title: 重复实现门禁（防造轮子）
space: engineering
category: guide
tags: [门禁, PR, 重复实现, 复用, 治理]
created: 2026-08-21
updated: 2026-08-21
health_checked: 2026-10-25
sources:
  - scripts/check-duplicate-implementations.mjs
  - RELIABILITY.md
  - docs/implementation-notes/duplicate-implementation-gate.md
backlinks:
  - _index
---
# 重复实现门禁（防造轮子）

提交 PR 时检查**本次新增**的 Component / Hook / Service / API / Function / Utils，是否与仓库已有实现重复。

## 怎么跑

```bash
npm run check:duplicate-implementations
node scripts/check-duplicate-implementations.mjs --base origin/main --head HEAD
```

只走本地，不依赖 Gitee CI（未付费）。提交 PR 时自动执行：`git push`（agent 分支会自动创建 PR）、`pr-create.sh`、`npm run gitee:pr-create`、`npm run ci:pre-pr`。

## 判定

- **PASS**：没有发现重复实现（没有合理复用对象）
- **FAIL**：发现重复实现，报告给出应复用的现有路径和符号。名称不同但 token + 函数体足够像，也算重复。

只拦新增符号，不扫存量，也不拦「改已有函数」。

## 豁免

```js
// REUSE-OK: src/utils/formatDateTime.js — 业务日历与自然日不同
export function formatDate(input) { ... }
```

门禁级逃生阀：`DUPLICATE_IMPL_SKIP=1`（PR 里写明理由）。

## 刻意不拦的

- 页面 Vue（`src/views/**` 且不在 `components/`）
- Java Controller 对 Service 的同名委托
- Entity / DTO / Request / Response
- 两个 Java Service 同名但函数体明显不同（如两边的 `findVisible`）
