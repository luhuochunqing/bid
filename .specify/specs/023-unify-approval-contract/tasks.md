# Tasks: 统一审批接口契约

**Branch**: `agent/qoder/unify-approval-contract` | **Date**: 2026-07-02 | **Plan**: [plan.md](./plan.md)

## Phase 1: 规范文档 + CA 借用补测试（低风险）

### Task 1.1: 写审批接口契约规范文档
- [ ] 1.1.1 创建 `docs/architecture/approval-contract.md`
- [ ] 1.1.2 覆盖内容：DTO 命名规则（`Xxx{Approval|Rejection}Request`）、字段命名规则（`comment`）、Controller 签名风格（`@Valid @RequestBody`）、前端调用方式（显式传对象）
- [ ] 1.1.3 包含正反示例
- [ ] 1.1.4 提交：`docs(approval): 制定审批接口契约规范`

### Task 1.2: 补 CaCertificateController WebMvcTest
- [ ] 1.2.1 创建 `backend/src/test/java/com/xiyu/bid/resources/controller/CaCertificateControllerWebMvcTest.java`
- [ ] 1.2.2 测试场景：空 body → 400、空字符串 `''` → 400、`{"comment":""}` → 400、`{"comment":"同意"}` → 200
- [ ] 1.2.3 跑 `mvn test -Dtest=CaCertificateControllerWebMvcTest` 验证通过
- [ ] 1.2.4 提交：`test(ca): 补 CaCertificateController WebMvcTest 覆盖反序列化场景`

### Task 1.3: 移除 ca.js 的 data = {} 默认参数
- [ ] 1.3.1 修改 `src/api/modules/ca.js` 的 `approve`/`reject`/`borrow`/`returnCa` 方法，移除 `data = {}` 默认参数
- [ ] 1.3.2 确认所有调用方都显式传对象（`CAManagement.vue` 已修复）
- [ ] 1.3.3 跑前端单测验证
- [ ] 1.3.4 提交：`refactor(ca): 移除 API 模块默认参数假防御，强制显式传对象`

## Phase 2: DraftingController 迁移（中等风险）

### Task 2.1: 新增 DraftingApprovalRequest / DraftingRejectionRequest DTO
- [ ] 2.1.1 创建 `DraftingApprovalRequest.java`（字段 `comment`，可空）
- [ ] 2.1.2 创建 `DraftingRejectionRequest.java`（字段 `comment`，`@NotBlank`）
- [ ] 2.1.3 提交：`feat(drafting): 新增标书审核 DTO`

### Task 2.2: 修改 ProjectDraftingController 从 Map 改为 DTO
- [ ] 2.2.1 修改 `approve` 方法签名：`Map<String,String>` → `@Valid @RequestBody DraftingApprovalRequest`
- [ ] 2.2.2 修改 `reject` 方法签名：`Map<String,String>` → `@Valid @RequestBody DraftingRejectionRequest`
- [ ] 2.2.3 跑后端测试验证编译通过
- [ ] 2.2.4 提交：`refactor(drafting): Controller 从 Map 迁移到 DTO`

### Task 2.3: 更新前端调用点
- [ ] 2.3.1 `DraftingStage.vue:315` `approveBid(id, { comment: '' })` → 确认契约一致
- [ ] 2.3.2 `DraftingStage.vue:319` `rejectBid(id, { reason: ... })` → `rejectBid(id, { comment: ... })`
- [ ] 2.3.3 `TaskBoardBidReviewActions.vue:68` `approveBid(id, { comment: '' })` → 确认
- [ ] 2.3.4 `TaskBoardBidReviewActions.vue:92` `rejectBid(id, { reason: ... })` → `rejectBid(id, { comment: ... })`
- [ ] 2.3.5 跑前端单测验证
- [ ] 2.3.6 提交：`refactor(drafting): 前端调用点统一使用 comment 字段`

### Task 2.4: 补 ProjectDraftingControllerWebMvcTest
- [ ] 2.4.1 创建测试类，覆盖 4 种场景
- [ ] 2.4.2 跑测试验证
- [ ] 2.4.3 提交：`test(drafting): 补 WebMvcTest 覆盖反序列化场景`

## Phase 3: ClosureController 迁移（中等风险）

### Task 3.1: 新增 ClosureApprovalRequest DTO
- [ ] 3.1.1 创建 `ClosureApprovalRequest.java`（字段 `comment`，可空——通过操作不需要意见）
- [ ] 3.1.2 提交：`feat(closure): 新增结项审批 DTO`

### Task 3.2: 修改 ProjectClosureController.approve 加 body
- [ ] 3.2.1 修改 `approve` 方法签名：无 body → `@Valid @RequestBody ClosureApprovalRequest`
- [ ] 3.2.2 跑后端测试验证
- [ ] 3.2.3 提交：`refactor(closure): approve 接口加 body，保持契约统一`

### Task 3.3: 更新前端调用点
- [ ] 3.3.1 `ClosureStage.vue:472` `approveClosure(id)` → `approveClosure(id, { comment: '' })`
- [ ] 3.3.2 `projectLifecycle.js:114` `approveClosure(id)` → `approveClosure(id, data)`
- [ ] 3.3.3 跑前端单测验证
- [ ] 3.3.4 提交：`refactor(closure): 前端调用点适配新契约`

### Task 3.4: 补 ProjectClosureControllerWebMvcTest
- [ ] 3.4.1 创建测试类，覆盖 4 种场景
- [ ] 3.4.2 跑测试验证
- [ ] 3.4.3 提交：`test(closure): 补 WebMvcTest`

## Phase 4: InitiationController 字段名统一（低风险）

### Task 4.1: 检查并统一 InitiationApprovalRequest / InitiationRejectionRequest 字段名
- [ ] 4.1.1 读取两个 DTO，确认字段名
- [ ] 4.1.2 如字段名不是 `comment`，统一改为 `comment`
- [ ] 4.1.3 跑后端测试验证
- [ ] 4.1.4 提交：`refactor(initiation): 统一 DTO 字段名为 comment`

### Task 4.2: 更新前端调用点
- [ ] 4.2.1 `useInitiationStageActions.js:344` 确认/更新字段名
- [ ] 4.2.2 `useInitiationStageActions.js:371` 确认/更新字段名
- [ ] 4.2.3 跑前端单测验证
- [ ] 4.2.4 提交：`refactor(initiation): 前端调用点统一使用 comment 字段`

### Task 4.3: 补 ProjectInitiationControllerWebMvcTest（如不存在）
- [ ] 4.3.1 检查是否已有测试
- [ ] 4.3.2 如无，创建测试类覆盖 4 种场景
- [ ] 4.3.3 跑测试验证
- [ ] 4.3.4 提交：`test(initiation): 补 WebMvcTest`

## Phase 5: 前端 API 模块统一移除默认参数

### Task 5.1: 扫描并移除所有 approve/reject 的 data = {} 默认参数
- [ ] 5.1.1 扫描 `src/api/modules/*.js` 所有 `approve`/`reject` 方法
- [ ] 5.1.2 移除 `data = {}` 默认参数
- [ ] 5.1.3 确认 `projectLifecycle.js`、`expense.js`（如有）都已处理
- [ ] 5.1.4 跑前端单测 + build 验证
- [ ] 5.1.5 提交：`refactor(api): 统一移除审批类 API 的默认参数假防御`

## Phase 6: 端到端验证

### Task 6.1: 后端全量测试
- [ ] 6.1.1 `mvn test` 全量通过
- [ ] 6.1.2 `mvn test -Dtest=ArchitectureTest` 架构测试通过

### Task 6.2: 前端验证
- [ ] 6.2.1 `npm run test:unit` 单测通过
- [ ] 6.2.2 `npm run build` 构建通过（在主工作区 trae 跑）

### Task 6.3: 静态扫描验证契约统一
- [ ] 6.3.1 grep `@RequestBody.*Map<String` 在审批类 Controller 应返回空
- [ ] 6.3.2 grep `approve.*data = {}\|reject.*data = {}` 在前端 API 应返回空

### Task 6.4: 提 PR
- [ ] 6.4.1 推送分支
- [ ] 6.4.2 用 Gitee API 创建 PR
- [ ] 6.4.3 PR 描述包含 spec + plan + tasks 链接
