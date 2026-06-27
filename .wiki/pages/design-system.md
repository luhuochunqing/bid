---
title: 设计系统基线
space: engineering
category: decision
tags: [design-system, ui, token, frontend]
sources:
  - docs/design-system/MASTER.md
  - CLAUDE.md
  - src/styles/variables.css
  - src/main.js
  - scripts/check-token-coverage.mjs
  - scripts/migrate-colors.mjs
backlinks:
  - _index
  - overview
created: 2026-04-22
updated: 2026-06-19
health_checked: 2026-06-27
---
# 设计系统基线

## 决策摘要

2026-04-22 起，仓库以根目录 `docs/design-system/MASTER.md` 作为 UI/视觉设计单一事实源（Single Source of Truth），用于约束前端字体、色彩、间距、布局、动效与增量治理策略。

同时在 `CLAUDE.md` 明确执行约束：任何视觉/UI 决策需先读取 `docs/design-system/MASTER.md`，未经明确批准不得偏离。

## 背景与问题

历史代码中已存在全局样式与 token 基础，但页面层局部样式较多，导致视觉规则在模块间漂移。该问题的风险不在“某个页面不好看”，而在于：

- 新页面持续扩散硬编码视觉值；
- 同类信息在不同页面呈现不一致，影响操作效率；
- 后续联调和验收阶段难以形成稳定视觉标准。

## 设计方向（基线）

- Aesthetic: Industrial / Utilitarian（工业化执行中台风格）
- Color Approach: balanced（品牌主色 + 中性色阶 + 语义状态色）
- Typography: Plus Jakarta Sans（业务文本）+ JetBrains Mono（技术标识）
- Layout: grid-disciplined
- Motion: minimal-functional

完整定义以 `docs/design-system/MASTER.md` 为准，不在本页重复维护明细参数。

## 落地策略

采用”冻结标准 + 增量收敛”，不做一次性大改：

1. 新需求从即日起必须遵循 token 与视觉规则；
2. 首批治理布局骨架：`MainLayout` / `Header` / `Sidebar`；
3. 次批治理高频业务页：`Dashboard`、`Bidding`、`Resource`；
4. 低频页面按需求触发式修整。

## Design Token 治理（2026-05-18）

### 本轮成果

- **Token 覆盖率**: 19.9% → 40.1%（-484 处硬编码色）
- **修改文件**: 49 个（样式系统 + Dashboard 13 文件 + Bidding 9 文件 + 组件 16 SFC）
- **构建/测试**: 全部通过（914 tests）

### 新增 CSS 变量

在 `src/styles/variables.css` 中扩展了 17 个变量：

| 类别 | 新增变量 |
|------|---------|
| 灰度扩展 | `--gray-150`, `--gray-250`, `--gray-350`, `--gray-550`, `--gray-650`, `--gray-750`, `--gray-950` |
| 语义文本色 | `--text-primary-ui`, `--text-secondary-ui`, `--text-muted`, `--text-slate` |
| 背景系统 | `--bg-page`, `--bg-card`, `--bg-subtle` |
| 强调色 | `--accent-blue`, `--accent-blue-light` |
| 侧边栏 | `--sidebar-text-secondary` |

### 治理工具

- `scripts/check-token-coverage.mjs` — 覆盖率检测脚本
- `scripts/migrate-colors.mjs` — 自动颜色替换脚本（支持 `--dry-run`）

### 文档同步

- `docs/design-system/MASTER.md` 色板已对齐实际 variables.css 值

## 与实施空间的关系

该基线是“研发事实 -> 实施动作 -> 验收证据”链路中的研发事实节点。实施阶段若出现“页面风格跑偏”问题，应以 `docs/design-system/MASTER.md` 为判定依据并回链本页。

## 相关页面

- [[overview]]
- [[modules]]
- [[dashboard-gap-analysis]]
- [[implementation/acceptance-and-closure]]
