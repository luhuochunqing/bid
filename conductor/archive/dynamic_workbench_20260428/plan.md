# Implementation Plan: 动态工作台配置体系与数据域权限

## 阶段一：前端功能权限改造 [checkpoint: 8e5a6a5]

- [x] **Task 1.1**: 与后端对齐组件权限标识（如 `dashboard:view_project_list`, `dashboard:view_tender_list` 等）。 [checkpoint: a1b2c3d]
- [x] **Task 1.2**: 修改 `src/stores/user.js` 或权限工具类，暴露便捷的权限检查方法。 [checkpoint: f4e5d6c]
- [x] **Task 1.3**: 重构 `Workbench.vue`，将基于角色 (`currentUserRole === 'manager'`) 的判断，替换为精确的权限点检查。 [checkpoint: 6113275]

## 阶段二：后端数据权限拦截引入 [checkpoint: 6c70112]

- [x] **Task 2.1**: 在后端设计 `DataScope` 注解和相关枚举规则（SELF, DEPT, ALL 等）。 [checkpoint: 6af88ce]
- [x] **Task 2.2**: 开发基于切面（AOP）或 MyBatis Plus 的 SQL 拦截/重写机制。 [checkpoint: 4118d43]
- [x] **Task 2.3**: 为 `ProjectController` 和 `TenderController` 等提供列表数据的接口接入数据权限过滤逻辑。 [checkpoint: a9f5ea4]

## 阶段三：后端动态布局 API [checkpoint: 974873f]

- [x] **Task 3.1**: 设计 `sys_dashboard_widget` 和 `sys_dashboard_layout` 数据库表结构。 [checkpoint: 0491be6]
- [x] **Task 3.2**: 开发工作台布局方案的创建、配置、查询接口。 [checkpoint: 7b11e42]
- [x] **Task 3.3**: 前端重构 `Workbench.vue`，实现基于 JSON 树结构的组件动态注册与动态渲染。 [checkpoint: f1f26ee]

## Phase: Review Fixes
- [x] Task: Apply review suggestions f6f2ec8
