---
title: 评分抽屉打分中不再锁死页面
space: engineering
category: pitfalls
tags: [scoreparse, drawer, overlay]
sources:
  - src/views/Project/stages/components/ScoreParseDrawer.vue
  - src/views/Project/stages/components/scoreParseDrawer.css
  - src/composables/projectDetail/useScoreParseDrawer.js
  - docs/lessons/lessons-learned.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-12
updated: 2026-11-12
health_checked: 2026-11-12
archive: false
---

# 评分抽屉打分中不再锁死页面

## 结论

- 「AI 实际打分」仍走 `useScoreParseDrawer.runScoring`：POST `/api/projects/{id}/score-parse/scoring`，每 2 秒轮询 `/scoring/status`，最多 900 次。
- 打分或解析进行中只显示吸顶提示。内容可滚动，抽屉蒙层可点穿，可关抽屉。后台任务继续。
- 进行中仍禁用再次解析、再次打分、刷新预判和生效，避免并发任务。

## 根因

- `scoringOverlayVisible` 曾给 `.drawer-content-wrapper` 加 `scroll-locked`，并用 `position: absolute; inset: 0` 的白遮罩盖住抽屉。
- `el-drawer` 默认模态，且 `close-on-click-modal` 为 false。遮罩会一直留到任务结束或 30 分钟。
- Element Plus 的 body 滚动锁在 setup 时读一次 `lockScroll`。事后把该 prop 改成 false，解不开已经装上的锁。`modal` 和 `modalPenetrable` 是响应式的。

## 做法

- 静态 `:lock-scroll="false"`。
- 进行中 `:modal="false"` 且 `:modal-penetrable="true"`。穿透条件是 `modalPenetrable && !modal`。
- 提示条改为 `position: sticky`。关闭按钮只把抽屉 `visible` 设为 false。

## 副作用

- 关抽屉不取消服务端打分。Redis 进度若停在 PROCESSING，轮询仍会等到终态或超时，但不再挡住页面。
- 重新解析和实际打分共用同一条「实际打分」提示文案。
- 打开抽屉、尚未打分时，页面在蒙层下仍可滚动，因为滚动锁始终关闭。蒙层本身仍挡住点击。

## 验证

- `npx vitest run src/views/Project/stages/components/ScoreParseDrawer.spec.js src/composables/projectDetail/useScoreParseDrawer.spec.js src/views/Project/stages/components/ScoreParseV3.qa.spec.js`：68 项通过。
- 布局 E2E `e2e/score-parse-drawer-layout.spec.js` 已改成断言不锁滚动。本 worktree 未启动开发服务，没有实机跑 Playwright。
