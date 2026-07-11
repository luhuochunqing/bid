# 仓库列表默认展示已关仓 — 实施笔记

> 分支：`agent/kimi/fix-warehouse-list-show-closed`  
> 问题：仓库列表默认不显示已关仓；单独筛选「已关仓」才显示。

## 根因

`buildWarehouseListParams` 在用户未选状态时强制传  
`statuses=IN_USE,EXPIRING,EXPIRED`，静默排除 `CLOSED`。  
筛选 UI placeholder 为「全部」，与实际请求语义不一致。

后端 `WarehouseFilterSpec` 在 `statuses` 为空时**不加**状态条件，本就会返回含 CLOSED 的全量。

## 决策

采用方案 1（与 UI「全部」对齐）：
- 未选状态 → **不传** `statuses`
- 用户显式选择 → 原样 CSV 透传

未改后端；未改 FilterBar 文案（「全部」现已与行为一致）。

## 变更

- `src/views/Knowledge/warehouseParams.js`
- `src/views/Knowledge/__tests__/warehouseParams.spec.js`

## 验证

```bash
npx vitest run src/views/Knowledge/__tests__/warehouseParams.spec.js
```
