# 弹层二级操作列单行 — 库房附件 / 项目文档

> 分支：`agent/kimi/fix-ops-col-drawer-docs`

## 范围（用户确认的两处）

1. 库房详情抽屉 → 附件 Tab：`WarehouseDrawer.vue`
2. 项目详情 → 文档卡片：`ProjectDetailDocumentsCard.vue`

## 改动

| 文件 | 列宽 | 布局 |
|---|---|---|
| `WarehouseDrawer.vue` | 100 → 140 | `.ops-actions` flex nowrap |
| `ProjectDetailDocumentsCard.vue` | 120 → 140 | 同上 |

按钮文案/权限/事件逻辑不变。

## 未改

- 标书流程弹层评审人表、质量建议表等（用户未点名）
- 一级列表页操作列
