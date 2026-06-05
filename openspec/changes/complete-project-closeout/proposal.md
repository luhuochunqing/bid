# Change: complete-project-closeout

## Why

项目结项（蓝图 §3.3.1.6）是项目全生命周期的最终闸门，负责保证金闭环管理、项目总结生成、资料归档与审核流程。前端 ClosureStage.vue 和大部分后端基础设施已存在，但存在以下关键缺口：

1. **缺乏 OpenSpec 规格覆盖** — tender-lifecycle 和 tender-project 两个 spec 均未记录结项阶段的完整需求
2. **未覆盖场景**：
   - 项目结页页面功能已实现，但需要补充 OpenSpec 规格文档
   - 需要补充结项场景保证覆盖完整度
3. **二次招标入口** — 结项后应支持"二次招标"创建新项目（当前未实现）

本 change 旨在补齐 OpenSpec 规格、完善已有实现、打通从"提交结项"到"正式结项 + 二次招标"的完整链路。

## What Changes

### A. OpenSpec 规格补充

- **`tender-lifecycle/spec.md`** — 补充 REQ-TL-003（结项审核流程场景）
- **`tender-project/spec.md`** — 补充 REQ-TP-005（项目结项字段与展示规则）

### B. 后端补充（完全基于现有模型，不新增表/字段）

- 无新增表或 Flyway 迁移 — `project_closure` 表、`ProjectClosureService`、`ProjectClosureGatePolicy` 均已存在
- 无需新增 DTO — `ClosureDTO`、`ClosurePreviewDTO`、`ClosureSubmitRequest`、`ClosureReviewRequest` 均已存在
- 测试覆盖已有：`ProjectClosureGatePolicyTest`、`ProjectClosureServiceTest`、`ProjectClosureControllerTest`、集成测试

### C. 前端补充（ClosureStage.vue 已有 + 完善）

- ClosureStage.vue 已实现：保证金管理、项目总结、提交结项、审核通过/驳回、AI 案例沉淀
- 当前模板和脚本已完整（524 行）—— 确认完整性后无需额外修改

### D. 测试覆盖

- 前端 `ClosureStage.spec.js` 已有 8 个测试用例覆盖
- 后端已有单元测试 + 集成测试覆盖
- 补充 OpenSpec 规格文档以提升可追溯性

## Not In Scope

- 二次招标创建新项目的完整流程（仅标记入口位置，不做实现）
- 文档导出功能（另案处理，当前 ClosureStage 不包含文档导出按钮）

## Impact

- **新增规格文档**：`openspec/changes/complete-project-closeout/` 下的 proposal/tasks
- **修改规格**：`openspec/specs/tender-lifecycle/spec.md`（补充 REQ-TL-003）
- **修改规格**：`openspec/specs/tender-project/spec.md`（补充 REQ-TP-005）
- **无代码线变更**：前端 ClosureStage.vue 和所有后端实现已完成

## Related

- 蓝图 §3.3.1.6 项目结项
- PRD §3.6 保证金管理
- 既有文件：
  - `src/views/Project/stages/ClosureStage.vue`（524 行，完整实现）
  - `src/api/modules/projectLifecycle.js`（getClosurePreview/submitClosure/approveClosure/rejectClosure）
  - `backend/.../project/core/ProjectClosureGatePolicy.java`（纯核心闸门）
  - `backend/.../project/service/ProjectClosureService.java`（编排服务）
  - `backend/.../project/controller/ProjectClosureController.java`（REST 端点）
  - `backend/.../project/entity/ProjectClosure.java`（JPA 实体）
  - `backend/src/test/.../ProjectClosureGatePolicyTest.java`
  - `backend/src/test/.../ProjectClosureServiceTest.java`
  - `backend/src/test/.../ProjectClosureControllerTest.java`
  - `src/views/Project/stages/ClosureStage.spec.js`（8 个测试用例）
