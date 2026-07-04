# Knowledge 模块样式统一实施计划

## 背景

Knowledge 模块 9 个页面各自在 scoped style 中重复定义了 `.page-header`、`.filter-card`、`.table-card`，数值和写法各不相同，导致视觉不一致。

## 目标

1. 扩展 `_knowledge-utils.scss` 添加 `.kb-page-header` / `.kb-filter-card` / `.kb-table-card` 公共样式
2. 各页面删除重复的骨架样式，引用公共 class
3. 统一视觉效果，后续维护只改一处

## 统一数值

| 属性 | 统一值 | 说明 |
|---|---|---|
| h2 font-weight | `600` | 多数页面使用 |
| h2 color | `var(--text-primary)` | 设计系统变量 |
| header margin-bottom | `var(--space-lg)` (24px) | 使用设计 token |
| card border-radius | `var(--radius-md)` (8px) | 使用设计 token |
| card margin-bottom | `var(--space-md)` (16px) | 折中值 |
| card box-shadow | `var(--shadow-sm)` | 使用设计 token |
| card border | `1px solid var(--el-border-color-lighter)` | Element Plus 变量 |

## 修改文件清单（按顺序）

1. `_knowledge-utils.scss` - 添加公共样式
2. `Qualification.vue` - 标准三段式
3. `Personnel.vue` - 标准三段式
4. `Performance.vue` + `Performance.scss` - 保留 h2 font-weight:700 特有
5. `BrandAuth.vue` - 注意 tab-toolbar 不受影响
6. `Template.vue` - 只统一 page-header
7. `Warehouse.vue` - 无 page-header，用独立 FilterBar
8. `views/ProjectArchive.vue` - 子页面
9. `views/DepositBoard.vue` - 子页面
10. `views/CaseGrid.vue` - 子页面

## 特殊处理

- Performance.vue 的 h2 font-weight:700 需在 scoped 中保留覆盖
- Template.vue 的 page-header 使用 align-items:flex-start（特有）
- BrandAuth.vue 的 tab-toolbar 保持不变
- Warehouse.vue 删除未使用的 .page-header 残留样式

## 验证

1. `vite build` 无 SCSS 编译错误
2. 逐页视觉对比，确认无回归
3. 筛选/表格/分页功能不受影响
