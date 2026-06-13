# Change: add-knowledge-base-impl

## Why
实现知识库前端路由配置和项目档案台账视图，以提供项目档案、文件分类展示、详情展示和审计日志浏览功能，满足用户对于投标项目归档档案的检索与审计需求。

## What Changes
- 在前端路由中配置 `/knowledge` 及其子路由（档案台账、案例库、资质列表、保证金看板）。
- 创建 `FileCategoryPopover` 组件以气泡卡片形式展示文档分类下的文件总数（含悬停 0.5 秒延迟显示）。
- 编写 `ProjectArchive.vue` 档案台账视图，实现项目列表展示、双轴日期选择（上传、结项区间）与文档分类筛选。
- 开发详情抽屉，支持右滑展示 60% 宽度项目只读基础信息、倒序文件列表以及文件预览/下载审计日志时间轴。
- 基于 Axios 直接请求真实后端 API，界面为 Premium 风格。

## Impact
- 影响路由模块 `src/router/index.js`
- 影响视图模块 `src/views/Knowledge`
- 新增 `src/views/Knowledge/components/FileCategoryPopover.vue` 和 `src/views/Knowledge/views/ProjectArchive.vue`
