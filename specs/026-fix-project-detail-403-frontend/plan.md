# Implementation Plan: 修复项目详情页 403 错误与前端权限入口校验

**Branch**: `agent/claude/fix-project-detail-403-frontend` | **Date**: 2026-07-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/026-fix-project-detail-403-frontend/spec.md`

## Summary

修复生产 Bug：`bid-projectLeader` 角色用户从外部入口（标讯详情页、工作台、分析看板等）跳转到无权限项目详情页时，后端返回 403，前端静默置 `currentProject = null` 导致后续代码崩溃（TypeError）。

技术方案（纯前端修复，不涉及后端）：
1. **入口治理**：创建统一跳转工具 `src/utils/projectNavigation.js`，所有跳转到 ProjectDetail 的入口 MUST 调用 `navigateToProject(router, projectId)`，该函数封装跳转 + 403 友好处理逻辑
2. **详情页状态机扩展**：`useProjectDetailState.js` 新增 `loadError` 状态（null | 'no-permission' | 'not-found' | 'network-error'），`ProjectDetailShell.vue` 根据 loadError 渲染差异化错误状态界面
3. **store 层错误传播**：`projectStore.getProjectById` 在 403/404 时抛出带错误类型的错误，而非静默返回 null
4. **boot 层防御**：`useProjectDetailBoot.js` 在 `currentProject` 为 null 时提前 return，不触发后续依赖

## Technical Context

**Language/Version**: JavaScript (ES2020+) / Vue 3.4 / Vite 5

**Primary Dependencies**: Vue 3 (Composition API)、Vue Router 4、Pinia、Element Plus、Axios

**Storage**: 无新增存储；复用现有 Pinia store（`useProjectStore`）

**Testing**: Vitest（单元测试）+ Playwright（E2E，覆盖 4 角色权限验证）

**Target Platform**: 现代浏览器（Chrome 100+/Edge 100+/Safari 15+）

**Project Type**: Web application（前端 SPA，纯前端修复）

**Performance Goals**: 跳转响应 < 200ms（与修复前无显著差异）；不增加额外 API 请求

**Constraints**: 不修改后端权限模型；不新增后端接口；保持现有有权限用户的跳转体验

**Scale/Scope**: 7 个跳转入口点 + 1 个详情页 Shell + 1 个 store + 1 个 boot composable + 1 个 state composable

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | Compliance | Notes |
|---|---|---|
| I. FP-Java Architecture | N/A | 纯前端修复，不涉及后端 FP-Java 分层 |
| II. Real-API Only | ✅ Pass | 复用现有真实 API `GET /api/projects/{id}`，不引入 Mock |
| III. TDD | ✅ Pass | 将先写 Vitest 单元测试（store 错误处理、boot null 防御）+ Playwright E2E（403 场景） |
| IV. Split-First & Simplicity | ✅ Pass | 新增 `projectNavigation.js` 工具函数（< 50 行）；修改的文件均不超 300 行 |
| V. OSS Integration | N/A | 不涉及 OSS 集成 |
| VI. Authorization Unification | N/A | 纯前端，不涉及后端 `@PreAuthorize` |
| VII. Boring Proven Patterns | ✅ Pass | 使用 Vue Composition API 标准模式（ref/computed），不引入新魔法 |

**Security & Access Control**: 不修改后端权限模型；前端 403 处理是防御性 UI，不替代后端权限兜底（后端 `ProjectAccessScopeService.assertCurrentUserCanAccessProject` 仍是权限权威）。

**Development Workflow**: 已执行早操（sync-env.sh）、已开任务分支、已通过 who-touches 检查（无其他 agent 在动这些文件）。

## Project Structure

### Documentation (this feature)

```text
specs/026-fix-project-detail-403-frontend/
├── plan.md              # This file
├── research.md          # Phase 0 output - 技术决策研究
├── data-model.md        # Phase 1 output - 前端状态模型
├── quickstart.md        # Phase 1 output - 验证步骤
├── checklists/
│   └── requirements.md  # spec 质量检查清单
└── tasks.md             # Phase 2 output (待 /speckit-tasks 生成)
```

### Source Code (repository root)

```text
src/
├── utils/
│   └── projectNavigation.js          # 新增：统一项目跳转工具函数
├── stores/
│   └── project.js                    # 修改：getProjectById 错误传播
├── composables/projectDetail/
│   ├── useProjectDetailState.js      # 修改：新增 loadError 状态
│   └── useProjectDetailBoot.js       # 修改：null 防御 + 错误状态设置
├── components/project/detail/
│   └── ProjectDetailShell.vue        # 修改：错误状态界面渲染
└── views/
    ├── Bidding/detail/
    │   └── DetailPage.vue            # 修改：跳转入口迁移到统一工具
    ├── Dashboard/
    │   ├── Workbench.vue             # 修改：跳转入口迁移
    │   └── useWorkbenchSchedule.js   # 修改：跳转入口迁移
    ├── Analytics/
    │   ├── Dashboard.vue             # 修改：跳转入口迁移
    │   └── dashboard/composables/
    │       └── useMetricDrillDown.js # 修改：跳转入口迁移
    ├── Resource/
    │   └── MarginManagement.vue      # 修改：跳转入口迁移
    └── Project/stages/
        └── ClosureStage.vue          # 修改：跳转入口迁移（二次招标）

tests/ (vitest)
├── unit/
│   ├── projectNavigation.spec.js     # 新增：工具函数测试
│   ├── projectStore.spec.js          # 新增/扩展：getProjectById 错误处理测试
│   └── useProjectDetailBoot.spec.js  # 新增/扩展：null 防御测试
└── e2e/ (playwright)
    └── project-detail-403.spec.js    # 新增：403 场景 E2E
```

**Structure Decision**: 纯前端修复，遵循现有 `src/` 目录结构。新增 `utils/projectNavigation.js` 作为统一跳转入口，避免在每个 view/composable 中重复实现跳转逻辑。

## Complexity Tracking

> 无 Constitution Check 违规，无需填表。

## Implementation Phases

### Phase 1: 核心修复（P1 - User Story 1 & 2）

**1.1 创建统一跳转工具 `src/utils/projectNavigation.js`**
- 导出 `navigateToProject(router, projectId, options)` 函数
- 跳转到 `/project/:id`，由详情页统一处理 403（不在入口侧预处理，避免额外请求）
- 提供 `navigateToProjectList(router)` 辅助函数
- 导出 `TENDER_NOT_LINKED_PROJECT` 常量提示文案

**1.2 改造 `projectStore.getProjectById` 错误传播**
- 403 时抛出 `ProjectAccessDeniedError`（自定义错误类，携带 'no-permission' 类型）
- 404 时抛出 `ProjectNotFoundError`（携带 'not-found' 类型）
- 其他错误抛出 `ProjectLoadError`（携带 'network-error' 类型）
- 不再静默返回 null

**1.3 扩展 `useProjectDetailState.js` 状态**
- 新增 `loadError` ref（null | 'no-permission' | 'not-found' | 'network-error'）
- 导出 `loadError` 供 Shell 使用

**1.4 改造 `useProjectDetailBoot.js` null 防御**
- `onMounted` 中 `getProjectById` 调用包裹 try-catch
- 捕获到错误时设置 `state.loadError.value`，提前 return，不执行后续 `initializeProjectActivities` 等依赖
- `initializeProjectActivities` 内部 `ensureProjectCollections` 返回 null 时提前 return

**1.5 改造 `ProjectDetailShell.vue` 错误状态界面**
- 在 `loading` 和 `!project` 之间插入 `loadError` 分支
- `loadError === 'no-permission'`：显示"无权限访问该项目" + "返回项目列表"按钮
- `loadError === 'not-found'`：显示"项目不存在" + "返回项目列表"按钮
- `loadError === 'network-error'`：显示"加载失败，请重试" + "重试" + "返回项目列表"按钮
- 保留现有 `!project` 兜底空状态（理论上不会走到，但作为防御）

### Phase 2: 入口迁移（P2 - User Story 3）

**2.1 标讯详情页 `DetailPage.vue`**
- "查看投标项目"按钮：调用 `navigateToProject(router, tender.value.projectId)`
- `tender.projectId` 为空时：ElMessage.warning('该标讯未关联项目')，不跳转

**2.2 工作台 `Workbench.vue` + `useWorkbenchSchedule.js`**
- `handleProjectClick`：调用 `navigateToProject`
- `handleCalendarAction`：调用 `navigateToProject`

**2.3 分析看板 `Dashboard.vue` + `useMetricDrillDown.js`**
- `goToProject`：调用 `navigateToProject`
- `handleRowAction`：调用 `navigateToProject`

**2.4 保证金管理 `MarginManagement.vue`**
- `goToProject`：调用 `navigateToProject`

**2.5 二次招标 `ClosureStage.vue`**
- 新项目跳转：调用 `navigateToProject`（新项目当前用户必然有权限，但仍走统一入口保证一致性）

### Phase 3: 测试与验证

**3.1 Vitest 单元测试**
- `projectNavigation.spec.js`：验证跳转函数调用 router.push 正确参数
- `projectStore.spec.js`：验证 403/404/500 抛出对应错误类型
- `useProjectDetailBoot.spec.js`：验证 null 防御和 loadError 设置

**3.2 Playwright E2E**
- `project-detail-403.spec.js`：
  - bid-projectLeader 账号从标讯详情页跳转无权限项目 → 看到友好提示
  - bid-projectLeader 账号直接访问无权限项目 URL → 看到错误状态界面
  - 有权限账号正常跳转 → 无影响

**3.3 门禁验证**
- `npm run check:front-data-boundaries`
- `npm run check:doc-governance`
- `npm run check:line-budgets`
- `npm run build`
- `npm run test:unit`
- `npm run test:e2e`

## Phase 0 Research Output

详见 [research.md](./research.md)

## Phase 1 Design Output

详见 [data-model.md](./data-model.md) 和 [quickstart.md](./quickstart.md)

## Re-evaluation: Constitution Check (Post-Design)

设计完成后重新评估：
- **III. TDD**：测试计划已明确（3 个单元测试 + 1 个 E2E），符合 TDD 流程
- **IV. Split-First**：新增文件 `projectNavigation.js` 预计 < 50 行；修改的文件均不超 300 行
- **VII. Boring Proven Patterns**：使用标准 Vue Composition API，无新魔法
- 无违规，无需 Complexity Tracking 记录

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| 7 个入口点迁移遗漏 | 部分入口仍报 403 崩溃 | 用 grep 全量扫描 `router.push.*ProjectDetail` 确认无遗漏；E2E 覆盖主要入口 |
| `getProjectById` 抛错改变现有调用方行为 | 其他调用方未捕获错误导致新崩溃 | 全局搜索 `getProjectById` 调用点，确认仅详情页 boot 调用；boot 已加 try-catch |
| 错误状态界面与现有 UI 风格不一致 | 体验割裂 | 复用 Element Plus `el-empty` 和 `el-button`，保持与现有 `!project` 空状态一致风格 |
| E2E 测试环境无 bid-projectLeader 测试账号 | E2E 无法验证 | 复用 E2E 已有测试账号（`xiaozhang` / `bid-projectLeader`），参见 CLAUDE.md |
