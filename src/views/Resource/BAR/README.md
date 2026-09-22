# BAR 子模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
BAR 子模块负责站点、SOP 和借还相关页面。
该目录承接 BAR 资源的列表、详情、检查和辅助操作入口。
组件子目录用于承载站点页内的局部交互部件。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `SiteList.vue` | View | BAR 站点列表页 |
| `SiteDetail.vue` | View | BAR 站点详情页 |
| `SOPDetail.vue` | View | SOP 详情页 |
| `CheckPanel.vue` | View | 检查面板页 |
| `components/` | 目录 | BAR 页面局部组件边界 |
| `components/AssetCard.vue` | Component | 资产卡片 |
| `components/BorrowDialog.vue` | Component | 借用对话框 |
