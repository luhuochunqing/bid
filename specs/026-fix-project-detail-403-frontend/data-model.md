# Data Model: 修复项目详情页 403 错误与前端权限入口校验

**Date**: 2026-07-03
**Feature**: 026-fix-project-detail-403-frontend

## 前端状态模型（无后端数据模型变更）

本次修复纯前端，不涉及后端数据库或 Entity 变更。仅扩展前端状态模型。

### 1. useProjectDetailState 状态扩展

**新增状态**:

| 状态字段 | 类型 | 取值 | 说明 |
|---|---|---|---|
| `loadError` | `Ref<string \| null>` | `null` \| `'no-permission'` \| `'not-found'` \| `'network-error'` | 项目详情加载错误类型，null 表示无错误 |

**现有状态（不变）**:
- `loading: Ref<boolean>` — 加载中状态
- `project: ComputedRef<Project \| null>` — 当前项目（来自 `projectStore.currentProject`）

**状态转换图**:
```
init: loading=true, loadError=null, project=null
  ↓
getProjectById 成功:
  loading=false, loadError=null, project=<Project>
  ↓
  [正常详情页]

getProjectById 403:
  loading=false, loadError='no-permission', project=null
  ↓
  [无权限错误状态]

getProjectById 404:
  loading=false, loadError='not-found', project=null
  ↓
  [项目不存在错误状态]

getProjectById 其他错误:
  loading=false, loadError='network-error', project=null
  ↓
  [加载失败错误状态]
```

### 2. projectStore.getProjectById 返回值变更

**变更前**:
```javascript
async getProjectById(id) → Project | null
  // 403/404 时静默返回 null
```

**变更后**:
```javascript
async getProjectById(id) → Project  // 成功返回 Project，失败抛出 ProjectLoadError
  // 403 → throw ProjectLoadError('no-permission', '无权限访问该项目', cause)
  // 404 → throw ProjectLoadError('not-found', '项目不存在', cause)
  // 其他 → throw ProjectLoadError('network-error', '加载失败', cause)
```

### 3. 自定义错误类 ProjectLoadError

**文件位置**: `src/stores/project.js`（或独立 `src/utils/projectErrors.js`）

```javascript
export class ProjectLoadError extends Error {
  constructor(errorType, message, cause) {
    super(message)
    this.name = 'ProjectLoadError'
    this.errorType = errorType
    this.cause = cause
  }
}
```

### 4. 统一跳转工具函数

**文件位置**: `src/utils/projectNavigation.js`

**导出 API**:
| 函数/常量 | 签名 | 说明 |
|---|---|---|
| `navigateToProject` | `(router: Router, projectId: string\|number, options?: object) => void` | 跳转到项目详情页，projectId 为空时提示 |
| `navigateToProjectList` | `(router: Router) => void` | 跳转到项目列表页 |
| `PROJECT_NOT_LINKED_MESSAGE` | `string` | "该标讯未关联项目" 提示文案 |

### 5. ProjectDetailShell.vue 渲染状态机

| 条件 | 渲染 |
|---|---|
| `loading` | `el-skeleton`（加载骨架屏） |
| `loadError === 'no-permission'` | `el-empty("无权限访问该项目")` + `el-button("返回项目列表")` |
| `loadError === 'not-found'` | `el-empty("项目不存在")` + `el-button("返回项目列表")` |
| `loadError === 'network-error'` | `el-empty("加载失败，请重试")` + `el-button("重试")` + `el-button("返回项目列表")` |
| `!project`（兜底） | `el-empty("未找到项目信息")` + `el-button("返回项目列表")`（保留现有） |
| else | 正常详情页内容 |

## 无后端数据模型变更

本次修复不涉及：
- 数据库 schema 变更
- JPA Entity 变更
- Flyway 迁移脚本
- 后端 API 契约变更（`GET /api/projects/{id}` 的 403/404 响应契约不变）
