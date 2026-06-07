一旦我所属的文件夹有所变化，请更新我。

# Pinia 状态管理

**位置**: `src/stores/`

**功能**: 集中管理前端应用状态，包括用户、项目、标讯、BAR 资产等数据。

## 文件清单

| 文件 | 类型 | 功能 |
|------|------|------|
| `user.js` | Pinia Store | 用户状态管理（登录、角色切换、会话恢复） |
| `project.js` | Pinia Store | 投标项目状态管理（列表、详情、CRUD、任务状态）|
| `bidding.js` | Pinia Store | 标讯状态管理（标讯列表、待办、日历） |
| `bar.js` | Pinia Store | BAR 投标资产台账状态管理（站点、账号、UK） |
| `qualification.js` | Pinia Store | 资质列表、借阅记录和未接入态管理 |

## 更新记录

- 2026-03-11: `project.js` 添加 API 失败时 mock 数据回退逻辑
- 2026-03-18: `user.js` 开始保存 `allowedProjectIds`，用于认证恢复后的项目级权限快照
- 2026-03-19: `user.js` 会话快照新增 `allowedDepts` 校验，避免恢复旧快照时遗漏部门级权限范围
- 2026-04-19: 新增 `qualification.js`，把资质页真实列表与借阅记录状态从页面内拆出，移除本地借阅 mock 数据
- 2026-04-21: `bidding.js` 保持筛选参数透传给标讯后端检索接口
