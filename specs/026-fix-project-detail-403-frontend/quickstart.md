# Quickstart: 修复项目详情页 403 错误与前端权限入口校验

**Date**: 2026-07-03
**Feature**: 026-fix-project-detail-403-frontend

## 前置条件

- 主工作区 `/Users/user/xiyu/worktrees/trae` 已启动开发环境（前端 1323 / 后端 18089）
- 后端 `GET /api/projects/{id}` 接口正常工作，无权限返回 403，不存在返回 404

## 验证步骤

### 1. 单元测试验证

```bash
cd /Users/user/xiyu/worktrees/claude
npm run test:unit -- --grep "projectNavigation|projectStore|useProjectDetailBoot"
```

**预期**:
- `projectNavigation.spec.js`: navigateToProject 正确调用 router.push；projectId 为空时调用 ElMessage.warning
- `projectStore.spec.js`: getProjectById 在 403 时抛出 ProjectLoadError('no-permission')；404 时抛出('not-found')；500 时抛出('network-error')
- `useProjectDetailBoot.spec.js`: getProjectById 抛错时设置 state.loadError 且不执行后续 initializeProjectActivities

### 2. 构建验证

```bash
cd /Users/user/xiyu/worktrees/claude
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build
```

**预期**: 全部通过，无新增 error。

### 3. E2E 验证（需主工作区环境）

E2E 在主工作区运行（claude worktree 不启动开发环境）：

```bash
cd /Users/user/xiyu/worktrees/trae
# 确保 dev 环境已启动
npm run test:e2e -- --grep "project-detail-403"
```

**预期**:
- bid-projectLeader 账号（xiaozhang）从标讯详情页跳转无权限项目 → 看到"无权限访问该项目"提示，不进入崩溃页面
- bid-projectLeader 账号直接访问无权限项目 URL → 看到错误状态界面 + "返回项目列表"按钮
- 有权限账号正常跳转 → 无影响，正常进入详情页

### 4. 手动验证（主工作区浏览器）

1. 用 `xiaozhang` / `123456` 登录 `http://127.0.0.1:1323`
2. 找一个 xiaozhang 无权限的项目 ID（可通过管理员账号查看项目列表，选一个 xiaozhang 不参与的）
3. 在浏览器地址栏直接输入 `http://127.0.0.1:1323/project/<无权限项目ID>`
4. **预期**: 页面显示"无权限访问该项目" + "返回项目列表"按钮，控制台无 403 错误和 TypeError
5. 点击"返回项目列表" → 跳转到 `/project`，显示 xiaozhang 有权限的项目列表

### 5. 入口点迁移验证

用管理员账号（admin）登录，从以下入口点击跳转到有权限的项目：
- 标讯详情页"查看投标项目"按钮
- 工作台项目卡片
- 工作台日程事件
- 分析看板下钻
- 保证金管理跳转

**预期**: 所有入口均能正常跳转到项目详情页，无影响。

### 6. 门禁全量验证

```bash
cd /Users/user/xiyu/worktrees/claude
npm run build
npm run test:unit
# E2E 在主工作区运行
cd /Users/user/xiyu/worktrees/trae
npm run test:e2e
```

**预期**: 全部通过。
