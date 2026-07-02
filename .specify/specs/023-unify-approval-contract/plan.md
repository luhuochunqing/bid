# Implementation Plan: 统一审批接口契约

**Branch**: `agent/qoder/unify-approval-contract` | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-unify-approval-contract/spec.md`

## Summary

CO-459 审批 bug 的元根因是项目内审批接口存在四种契约风格、无统一规范。本计划将所有审批类 Controller 迁移到统一契约（`@Valid @RequestBody XxxApprovalRequest` DTO + `comment` 字段），移除前端 API 模块的 `data = {}` 默认参数假防御，补充 `@WebMvcTest` 反序列化测试覆盖，并制定审批接口契约规范文档。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.2) + JavaScript (Vue 3 + Vite 5)
**Primary Dependencies**: Spring Web MVC (`@RequestBody`, `@Valid`), Jakarta Validation (`@NotBlank`), Pinia, axios
**Storage**: MySQL 8.0 (DTO 字段与 DB 字段两层映射，不影响)
**Testing**: JUnit 5 + MockMvc (`@WebMvcTest`), Vitest (前端单测), Playwright (E2E)
**Target Platform**: Linux server (生产) + macOS (开发)
**Project Type**: Web service (前后端分离)
**Performance Goals**: 无特殊性能要求（契约统一不涉及运行时性能）
**Constraints**: 不能破坏现有前端调用方的兼容性
**Scale/Scope**: 4 个 Controller + 5 个前端 API 方法 + 1 个规范文档 + 多个测试

## Constitution Check

- ✅ FP-Java：纯核心可单测——DTO 是纯 POJO，符合 FP-Java
- ✅ 真实 API 唯一源：不涉及 Mock
- ✅ 复杂任务走 Spec Kit：当前就在 Spec Kit 流程中
- ✅ 原子提交 + 测试证据：每步迁移后跑测试
- ✅ 不删多 Agent 持久 Worktree：仅在 qoder worktree 内操作

## Project Structure

### Documentation (this feature)

```text
specs/023-unify-approval-contract/
├── plan.md              # 本文件
├── spec.md              # 需求规格
├── checklists/
│   └── requirements.md  # 质量检查清单
└── tasks.md             # 待 /speckit-tasks 生成
```

### Source Code (受影响文件)

```text
# 后端
backend/src/main/java/com/xiyu/bid/
├── project/controller/
│   ├── ProjectInitiationController.java   # 立项审批（已用 DTO，需统一字段名）
│   ├── ProjectDraftingController.java     # 标书审核（需从 Map 迁移到 DTO）
│   └── ProjectClosureController.java      # 结项审核（approve 需加 body）
├── project/dto/
│   ├── InitiationApprovalRequest.java     # 立项审批 DTO（已存在）
│   ├── InitiationRejectionRequest.java    # 立项驳回 DTO（已存在）
│   ├── DraftingApprovalRequest.java       # 标书审批 DTO（新增）
│   ├── DraftingRejectionRequest.java      # 标书驳回 DTO（新增）
│   ├── ClosureApprovalRequest.java        # 结项审批 DTO（新增，comment 可选）
│   └── ClosureReviewRequest.java          # 结项驳回 DTO（已存在）
├── resources/controller/
│   └── CaCertificateController.java       # CA 借用审批（已用 DTO，无需改）
└── resources/dto/
    └── CaApprovalRequest.java             # CA 审批 DTO（已存在，无需改）

# 前端
src/
├── api/modules/
│   ├── ca.js               # CA API（移除 data = {} 默认参数）
│   ├── projectLifecycle.js # 项目生命周期 API（移除默认参数，确认调用契约）
│   └── expense.js          # 费用 API（如有 approve/reject 也要改）
├── stores/
│   └── ca.js               # CA Store（确认显式传对象）
└── views/
    ├── Resource/CAManagement.vue       # CA 审批入口（已修复）
    ├── Project/stages/DraftingStage.vue # 标书审核入口（更新调用）
    ├── Project/stages/ClosureStage.vue  # 结项审核入口（更新调用）
    └── TaskBoard/components/TaskBoardBidReviewActions.vue # 任务看板审核（更新调用）

# 规范文档
docs/architecture/
└── approval-contract.md   # 审批接口契约规范（新增）

# 后端测试
backend/src/test/java/com/xiyu/bid/
├── project/controller/
│   ├── ProjectInitiationControllerWebMvcTest.java  # 立项审批 WebMvcTest
│   ├── ProjectDraftingControllerWebMvcTest.java     # 标书审核 WebMvcTest（新增）
│   └── ProjectClosureControllerWebMvcTest.java     # 结项审核 WebMvcTest（新增）
└── resources/controller/
    └── CaCertificateControllerWebMvcTest.java      # CA 审批 WebMvcTest（新增）
```

**Structure Decision**: 4 个审批 Controller 全部迁移到 DTO 风格，新增 3 个 DTO（Drafting×2 + Closure×1），1 个规范文档，4 个 WebMvcTest。

## Implementation Phases

### Phase 1: 规范文档 + CA 借用补测试（低风险，先做）

1. 写 `docs/architecture/approval-contract.md` 规范文档
2. 补 `CaCertificateControllerWebMvcTest`（覆盖空 body / 空字符串 / 缺字段 / 正常 4 种场景）
3. 移除 `src/api/modules/ca.js` 的 `data = {}` 默认参数

### Phase 2: DraftingController 迁移（中等风险）

1. 新增 `DraftingApprovalRequest` / `DraftingRejectionRequest` DTO
2. 修改 `ProjectDraftingController` 从 `Map<String,String>` 改为 DTO
3. 更新前端调用点：`DraftingStage.vue`, `TaskBoardBidReviewActions.vue`
4. 补 `ProjectDraftingControllerWebMvcTest`

### Phase 3: ClosureController 迁移（中等风险）

1. 新增 `ClosureApprovalRequest` DTO（comment 可选）
2. 修改 `ProjectClosureController.approve` 加 `@RequestBody`
3. 更新前端调用点：`ClosureStage.vue`, `TaskBoardBidReviewActions.vue`（如有）
4. 补 `ProjectClosureControllerWebMvcTest`

### Phase 4: InitiationController 字段名统一（低风险）

1. 检查 `InitiationApprovalRequest` / `InitiationRejectionRequest` 的字段名
2. 如字段名不是 `comment`（可能是 `reason` 等），统一改为 `comment`
3. 更新前端调用点：`useInitiationStageActions.js`
4. 补 `ProjectInitiationControllerWebMvcTest`（如不存在）

### Phase 5: 前端 API 模块统一移除默认参数

1. 扫描所有 `src/api/modules/*.js` 的 `approve`/`reject` 方法
2. 移除所有 `data = {}` 默认参数
3. 更新所有调用方显式传对象
4. 跑前端单测 + build 验证

### Phase 6: 端到端验证

1. 跑后端全量测试：`mvn test`
2. 跑前端单测：`npm run test:unit`
3. 跑架构测试：`mvn test -Dtest=ArchitectureTest`
4. 跑 E2E 烟雾测试（如 CA 审批有 spec）
5. 静态扫描验证契约统一：grep `Map<String` / `data = {}` 应返回空

## Complexity Tracking

无 Constitution Check 违规，无需填写。
