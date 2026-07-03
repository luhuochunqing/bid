# Research: 修复项目详情页 403 错误与前端权限入口校验

**Date**: 2026-07-03
**Feature**: 026-fix-project-detail-403-frontend

## Research Tasks

### R1: 前端跳转入口校验方案选择

**Question**: 跳转入口应在跳转前校验权限，还是跳转后由详情页处理 403？

**Decision**: 采用"跳转后由详情页统一处理 403"方案

**Rationale**:
- 跳转前校验需要额外调用 `GET /api/projects/{id}` 探测权限，增加一次 API 请求，影响体验
- 详情页本身必须处理 403（因为用户可能直接输入 URL），所以入口侧校验是冗余的
- 统一在详情页处理 403，入口侧只需调用 `navigateToProject(router, projectId)`，职责清晰
- 后端权限是权威，前端不应重复实现权限判断逻辑

**Alternatives considered**:
1. **入口侧预先调用权限校验接口**：需新增后端 `GET /api/projects/{id}/access-check` 接口，增加后端复杂度，且仍需详情页处理直接 URL 访问场景
2. **入口侧调用 getProjectById 探测**：复用现有接口，但增加一次请求，且权限判断仍依赖后端 403 响应，无实质收益
3. **路由守卫 beforeEnter 校验**：在路由层统一处理，但路由守卫难以展示友好 UI（只能 redirect），且无法区分 403/404

### R2: projectStore.getProjectById 错误传播策略

**Question**: getProjectById 应返回 null 还是抛出错误？

**Decision**: 改为抛出带错误类型的自定义错误

**Rationale**:
- 现有实现静默返回 null，调用方无法区分"无权限"、"不存在"、"网络错误"
- 抛出错误让调用方（boot）能设置对应的 loadError 状态，渲染差异化 UI
- 自定义错误类携带 errorType 字段，便于程序化处理

**Alternatives considered**:
1. **返回 { project, error } 对象**：改变返回类型，所有调用方都需改造，影响面大
2. **返回 null + 设置 store.errorState**：状态分散，容易遗忘读取

**自定义错误类设计**:
```javascript
class ProjectLoadError extends Error {
  constructor(errorType, message, cause) {
    super(message)
    this.name = 'ProjectLoadError'
    this.errorType = errorType // 'no-permission' | 'not-found' | 'network-error'
    this.cause = cause
  }
}
```

### R3: getProjectById 调用方影响评估

**Question**: 改变 getProjectById 行为是否影响其他调用方？

**Research**:
全局搜索 `getProjectById` 调用点：

| 调用位置 | 当前处理 | 改造后处理 |
|---|---|---|
| `useProjectDetailBoot.js:84` | 无 try-catch，依赖返回 null | 加 try-catch，设置 loadError |
| `ProjectDetailShell.vue`（通过 boot） | 通过 state.loading 和 state.project 判断 | 通过 state.loadError 判断 |

**Conclusion**: 仅 `useProjectDetailBoot.js` 一处调用，改造影响可控。

### R4: 错误状态界面 UI 设计

**Question**: 错误状态界面应如何展示？

**Decision**: 复用现有 `el-empty` 组件，差异化 description 和按钮

**Rationale**:
- ProjectDetailShell.vue 现有 `!project` 分支已使用 `el-empty`，保持一致性
- 三种错误状态共享同一布局，仅文案和按钮不同，降低复杂度

**UI 结构**:
```
loading? → el-skeleton
loadError === 'no-permission'? → el-empty("无权限访问该项目") + el-button("返回项目列表")
loadError === 'not-found'? → el-empty("项目不存在") + el-button("返回项目列表")
loadError === 'network-error'? → el-empty("加载失败，请重试") + el-button("重试") + el-button("返回项目列表")
!project（兜底）? → el-empty("未找到项目信息") + el-button("返回项目列表")
else → 正常详情页
```

### R5: 统一跳转工具函数 API 设计

**Question**: `navigateToProject` 函数签名如何设计？

**Decision**:
```javascript
navigateToProject(router, projectId, options = {})
```

**Rationale**:
- `router` 作为参数传入（而非函数内部 useRouter），便于测试和复用
- `projectId` 必填，数字或字符串
- `options` 预留扩展（如 `onError` 回调、`replace` 模式），当前不使用

**函数实现核心**:
```javascript
export function navigateToProject(router, projectId, options = {}) {
  if (!projectId) {
    ElMessage.warning('该项目信息缺失，无法跳转')
    return
  }
  router.push({ name: 'ProjectDetail', params: { id: String(projectId) } })
}
```

**为什么不在这里处理 403**:
- 跳转是同步操作，403 在跳转后详情页加载时才发生
- 详情页已有统一的 403 处理（本次改造新增）
- 入口侧无需感知 403，职责单一

### R6: E2E 测试账号确认

**Question**: E2E 测试环境是否有 bid-projectLeader 角色账号？

**Research**:
查阅 CLAUDE.md §默认登录凭据 §e2e：

| 用户名 | 密码 | RoleProfile code | 角色名称 |
|---|---|---|---|
| `xiaozhang` | `123456` | `bid-projectLeader` | 投标项目负责人 |

**Conclusion**: 有现成 E2E 账号 `xiaozhang`，可直接用于 403 场景测试。

**测试数据准备**:
- 需要一个 `xiaozhang` 无权限访问的项目 ID
- 该项目需存在于 E2E 环境，且 `xiaozhang` 不是其负责人/成员/任务执行人等
- E2E 测试中可通过 API 或数据库查询确认一个合适的项目 ID

## Summary

所有研究点已解决，无 NEEDS CLARIFICATION 残留。技术方案明确：
1. 入口侧不预处理，详情页统一处理 403
2. store 层抛出带类型的错误
3. 复用 el-empty 组件渲染错误状态
4. 统一跳转函数保持极简（仅封装 router.push + 空值检查）
