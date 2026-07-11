# CA 列表操作列单行 — 实施笔记

> 分支：`agent/kimi/ca-ops-column-nowrap`  
> 页面：`/resource/ca-management`

## 需求

操作列「编辑 / 申请使用 / 下架」换行显示，希望同一行。

## 根因

- 操作列写死 `width="200"`，单元格左右 padding 后可用宽度约 176px
- Element Plus `link` 按钮默认相邻 `margin-left: 12px`，三按钮总宽略超列宽
- `td > .cell` 允许 `white-space: normal`，于是按钮被挤到第二行

## 决策

1. **列宽 200 → 260**：覆盖「编辑 + 申请使用/登记归还 + 下架」三按钮场景（申请使用与登记归还互斥）
2. **包一层 `.ca-ops-actions` + `inline-flex` + `nowrap`**：即使边界情况下列被压缩，也优先横向排布
3. **相邻按钮间距 12px → 4px**：略收紧，避免虚胖占宽
4. **不动按钮文案/权限逻辑**：纯布局修复，不改变 CO-489 / CO-409 行为

## 未改

- 项目负责人简化视图操作列（`width="160"`）
- 「我的申请 / 我的审批」Tab 操作列
- 后端与 API

## 验证

- `CAManagement.spec.js`：32/32 通过
