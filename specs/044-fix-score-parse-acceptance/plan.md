# Implementation Plan: PR !2292 验收缺陷修复

**Branch**: `agent/zcode/fix-score-parse-acceptance`（基线 `origin/043-harden-score-parse-intake` = PR !2298） | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/044-fix-score-parse-acceptance/spec.md`

## Summary

修复 PR !2292 双重验收（PRD × 原型 × 代码）判定的剩余缺陷：阶段 1 客观项"待确认"被误显示为红色 0 分（P1）、详情弹窗超高（P2）、待确认状态视觉与 50MB 文案不符 PRD（P2）、specs/042 与验收交接文档声明失实（P2）。P0（后端编译失败）已由基线 PR !2298 修复，本计划仅含验证。

## Technical Context

**Language/Version**: 前端 Vue 3 + Element Plus（Vite 5）；后端 Java 21 + Spring Boot 3.2

**Primary Dependencies**: Element Plus el-dialog/el-drawer；Vitest + @vue/test-utils；JUnit 5

**Storage**: 不涉及（无数据模型变更）

**Testing**: Vitest（前端单测 + QA spec）；`mvn test -Dtest='com.xiyu.bid.scoreparse.**'`（后端）；`mvn compile`（编译门禁）

**Target Platform**: Web（项目详情页抽屉）/ Spring Boot 服务

**Project Type**: web-application（既有功能缺陷修复）

**Constraints**: 改动限于展示层纯函数、样式与文案；不引入新组件、不改接口契约、不改数据库

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I. FP-Java 分层 | 改动位于展示归一化纯函数（scoreParseTask.js）与样式/文案，不触碰后端域核心 | ✅ |
| II. Real-API Only | 无 mock 引入；测试沿用真接口 DTO 形状 fixture | ✅ |
| III. 原子提交+测试证据 | 每个 FR 独立提交并附测试结果（见 tasks.md 验证列） | ✅ |
| VII. 防御性集合 | 无集合收集改动 | ✅ |
| 行数预算 | 仅微调既有文件，无超限风险 | ✅ |

## 改动方案（按 FR 映射）

### FR-001/002/003 — 待确认空值展示（P1）

根因定位：`src/composables/projectDetail/scoreParseTask.js` `normalizeScoreItem()` 将客观项空 `estScore` 硬转为数字 `0`，下游 `ScoreParseTable.vue` 的 `getScoreClass()` 对数字 0 渲染零分红样式。

- `scoreParseTask.js`：客观项空值得分保留 `null`（不再转 0）；主观项仍为 `'待确认'`
- `ScoreParseTable.vue` / `ScoreItemDetailModal.vue`：现有空值分支（`item.estScore || '待确认'` / 非 number → subjective 样式）已天然兼容 `null`，无需改动渲染逻辑——修复点收敛为归一化一处
- 合计口径：`useScoreParseDrawer.js` 的 `estTotalScore` 仅对 `typeof number` 求和，`null` 不计入（符合 PRD 2.1 空数据处理），零改动
- 测试：`scoreParseTask.spec.js` 增补"客观项 null 保留 null"断言；`ScoreParseV3.qa.spec.js` 增补"待确认客观项显示待确认而非 0"场景

### FR-004 — 详情弹窗 max-height 70vh

`ScoreItemDetailModal.vue` 已 `:append-to-body="true"`（scoped 样式不可达），新增非 scoped 样式块：`.score-item-detail-dialog` 限高 70vh + flex 列布局 + `.el-dialog__body` 内部滚动。

### FR-005 — 待确认灰字蓝点

`ScoreParseTable.vue`：`.status-cell.neutral` 文字改 `var(--text-muted)`（灰），`.dot` 前缀单独 `var(--brand-primary)`（蓝），对齐原型 `.status-cell.neutral::before` 行为。

### FR-006 — 50MB 文案对齐 PRD 5.3

`backend/.../BidDocumentUploadService.validateFile()`：超限消息改为 PRD 原文"文件大小超过限制（50MB），请压缩后重新上传"；同步检查既有测试断言。

### FR-007 — 验收声明更正

`specs/042-score-parse-v3-acceptance/tasks.md` T03 与 `spec.md` US1 场景 3：更正为"用户点击「AI 实际打分」触发（PRD 1.1），打开抽屉仅展示已有结果"；`docs/implementation-notes/prd-acceptance-handoff-2292-2293.md` 同步修正自动打分相关表述。

### FR-008 — 工程纪律沉淀

`docs/references/engineering-discipline.md` 追加：PR 合并前置 CI 必须绿（含后端 `mvn compile`）；自验收"测试通过"结论必须附可复现命令与输出。

## Project Structure

### Documentation (this feature)

```text
specs/044-fix-score-parse-acceptance/
├── spec.md            # 需求规格（已完成）
├── checklists/requirements.md
├── plan.md            # 本文件
├── research.md        # 根因与方案取舍记录
├── quickstart.md      # 验证指南
└── tasks.md           # 任务清单（speckit-tasks 产出）
```

### Source Code (repository root)

```text
src/
├── composables/projectDetail/scoreParseTask.js          # 归一化修复（P1）
├── composables/projectDetail/scoreParseTask.spec.js     # 增补断言
├── views/Project/stages/components/
│   ├── ScoreParseTable.vue                              # 状态视觉（P2）
│   ├── ScoreItemDetailModal.vue                         # 70vh（P2）
│   └── ScoreParseV3.qa.spec.js                          # QA 场景增补
backend/src/main/java/com/xiyu/bid/scoreparse/application/
│   └── BidDocumentUploadService.java                    # 50MB 文案（P2）
specs/042-score-parse-v3-acceptance/{tasks.md,spec.md}   # 声明更正
docs/implementation-notes/prd-acceptance-handoff-2292-2293.md
docs/references/engineering-discipline.md                # 纪律沉淀
```

## Verification

1. `npx vitest run src/composables/projectDetail/scoreParseTask.spec.js src/views/Project/stages/components/`（前端全绿，含既有 041/043 用例零回归）
2. `cd backend && mvn compile`（基线含 !2298 import 修复，必须通过）
3. `mvn test -Dtest='com.xiyu.bid.scoreparse.**.*Test'`（文案改动后仍全绿）
4. 手工走查：待确认行灰字蓝点、详情弹窗长内容滚动（quickstart.md）
