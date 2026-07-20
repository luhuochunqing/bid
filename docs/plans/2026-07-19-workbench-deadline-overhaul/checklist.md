# 工作台截止时间模块改造 - 验收清单

> 关联 spec：./spec.md
> 创建日期：2026-07-19
> 关联 Linear：CO-593

## 模块 A：报名截止时间

- [ ] 显示标讯名称（`Tender.title`）
- [ ] 显示日期（`registrationDeadline`，YYYY-MM-DD 格式）
- [ ] 点击名称跳转 `/bidding/:tenderId`（标讯详情）
- [ ] 按日期升序排列
- [ ] 数据按 period（today/week/month）筛选

## 模块 B：开标时间

- [ ] 显示项目名称（通过 `Tender.projectId` 关联 `Project.name`）
- [ ] 显示日期（`bidOpeningTime`，YYYY-MM-DD 格式）
- [ ] 点击名称跳转 `/project/:projectId`（项目详情）
- [ ] 按日期升序排列
- [ ] `Tender.projectId` 为 null 的条目不展示
- [ ] 数据按 period 筛选

## 模块 C：保证金截止时间

- [ ] 显示项目名称（通过 `Fee.projectId` 关联 `Project.name`）
- [ ] 显示日期（`Fee.feeDate`，YYYY-MM-DD 格式）
- [ ] 点击名称跳转 `/project/:projectId`（项目详情）
- [ ] 按日期升序排列
- [ ] 数据按 period 筛选
- [ ] 仅查询 `feeType=BID_BOND` 且 `status=PENDING` 的 Fee

## UI 显示

- [ ] 固定列宽，名称过长显示省略号（3 个点）
- [ ] 鼠标悬停名称显示全称（title 属性）
- [ ] 名称左侧，日期右侧
- [ ] 日期格式统一 YYYY-MM-DD
- [ ] 每模块容器高度容纳 4 条
- [ ] 超出 4 条出现纵向滚动条
- [ ] 移除原 countdown 列
- [ ] 列表行布局与待办卡片（第一部分）一致

## 时间筛选

- [ ] 今天 Tab：只展示今日数据
- [ ] 本周 Tab：展示本周数据（周一~周日）
- [ ] 本月 Tab：展示本月数据（月初~月末）
- [ ] 切换 Tab 重新拉取数据
- [ ] 默认选中本周

## 数据权限

- [ ] 投标管理员（/bidAdmin）登录，看到所有数据
- [ ] 投标系统管理员（bid-SystemAdmin）登录，看到所有数据
- [ ] 投标组长（bid-TeamLeader）登录，看到所有数据
- [ ] 本地 admin 登录，看到所有数据
- [ ] 投标专员（bid-Team）登录，只看到自己参与项目的数据
- [ ] 项目负责人（bid-projectLeader）登录，只看到自己参与项目的数据
- [ ] 跨部门协同人员（bid-otherDept）登录，只看到自己参与项目的数据
- [ ] 无项目权限的非管理角色 → 空列表

## 通用

- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过
- [ ] `npm run build` 通过
- [ ] `npm run test` 通过
- [ ] 无新增 Mock 数据
- [ ] 无新增硬编码颜色（CSS 全用变量）
- [ ] line-budget 门禁通过
- [ ] 后端新接口有 `@PreAuthorize("isAuthenticated()")` 注解
- [ ] 保留现有 `/api/workbench/deadline-stats`（向后兼容）
- [ ] 组件开头注释已更新
