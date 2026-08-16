# Tasks: PR !2292 验收缺陷修复

## Phase 1: P1 核心展示缺陷

- [ ] T01: `scoreParseTask.js` `normalizeScoreItem` 客观项空 `estScore` 保留 `null`（FR-001/003）
- [ ] T02: `scoreParseTask.spec.js` 增补断言：客观项 null 保留 null / 主观项仍"待确认" / 数字得分不受影响（FR-003）
- [ ] T03: `ScoreParseV3.qa.spec.js` 增补场景：待确认客观项表格与详情显示"待确认"而非 0 分；真实零分仍红色 0（FR-001/002）

## Phase 2: P2 视觉与文案

- [ ] T04: `ScoreItemDetailModal.vue` 弹窗 max-height 70vh + body 内部滚动（FR-004）
- [ ] T05: `ScoreParseTable.vue` 待确认状态灰字 + 蓝点（FR-005）
- [ ] T06: `BidDocumentUploadService` 50MB 超限文案对齐 PRD 5.3（FR-006）

## Phase 3: 声明与纪律

- [ ] T07: `specs/042` tasks.md T03 + spec.md US1 场景 3 声明更正为手动触发（FR-007）
- [ ] T08: `prd-acceptance-handoff-2292-2293.md` 自动打分相关表述更正（FR-007）
- [ ] T09: `docs/references/engineering-discipline.md` 沉淀合并前置 CI 规则（FR-008）

## Phase 4: 验证与收尾

- [ ] T10: 前端 vitest（composables + components 全量）通过
- [ ] T11: 后端 `mvn compile` 通过（P0 门禁复验）+ scoreparse 测试通过
- [ ] T12: 原子提交 + push + PR（PR 描述注明基于 !2298，等其合并后 rebase main）
