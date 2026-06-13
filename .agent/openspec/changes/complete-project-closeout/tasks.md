# Tasks: complete-project-closeout

## Prerequisites

- [x] 后端 Closure 基础设施已存在：`ProjectClosureGatePolicy`、`ProjectClosureService`、`ProjectClosureController`、`ProjectClosure` entity、`ProjectClosureRepository`
- [x] 后端测试已存在：`ProjectClosureGatePolicyTest`、`ProjectClosureServiceTest`、`ProjectClosureControllerTest`
- [x] 前端 ClosureStage.vue 已存在（524 行，包含完整模板 + 脚本 + 样式）
- [x] 前端 ClosureStage.spec.js 已存在（8 个测试用例）
- [x] API 模块 `projectLifecycle.js` 已包含 closure 相关端点

## Implementation Tasks

### 1. OpenSpec 规格补充

- [ ] 确认 proposal.md 完成（当前文件）
- [ ] 补充 `openspec/specs/tender-lifecycle/spec.md` — 添加 REQ-TL-003 结项审核流程场景
- [ ] 补充 `openspec/specs/tender-project/spec.md` — 添加 REQ-TP-005 项目结项页面字段展示

### 2. 规格验证

- [ ] 使用 `openspec validate` 检查规格一致性（如工具可用）
- [ ] 确认规格表述与已有实现一致

### 3. 前端 ClosureStage 确认

- [ ] 确认模板完整性：保证金管理、项目总结、操作按钮区、驳回对话框
- [ ] 确认 computed 属性覆盖：canFillDeposit、canSubmitClosure、canApprove、canSubmit
- [ ] 确认提交/审核/驳回流程完整性
- [ ] 确认 AI 案例沉淀按钮和触发逻辑

### 4. 后端实现确认

- [ ] 确认 ProjectClosureGatePolicy 所有分支正确
- [ ] 确认 ProjectClosureService.submitClosure/approveClosure/rejectClosure 流程
- [ ] 确认 Stage 跳转（CLOSED）已集成到 approveClosure

### 5. 验证执行

- [ ] 确认前端测试通过：ClosureStage.spec.js 8 个用例
- [ ] 确认后端测试通过：GatePolicyTest / ServiceTest / ControllerTest
- [ ] 确认集成测试通过：BidResultClosureIntegrationTest
