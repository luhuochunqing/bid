# Implementation Notes — PR !2020 后续修复（bid-SystemAdmin 前端/测试闭环）

**分支**：`agent/kimi/fix-pr2020-systemadmin-frontend`  
**基线**：`origin/agent/codex/remove-person-whitelist-and-add-bid-systemadmin`（PR !2020）  
**日期**：2026-07-11

## 背景

PR !2020 删除 `person-to-role-mappings`，并将 `bid-SystemAdmin` 注册为独立第 8 角色（不再映射为 `admin`）。
评审发现：**后端已拆角色，前端仍按「SystemAdmin=admin」写死**，会制造新的前后端分裂。

## 本次决策（规格未写明处）

1. **前端 `isBidAdmin` 含 `bid-SystemAdmin`**  
   与产品原则一致：SystemAdmin 是 OSS 管理级角色，UI 管理操作（如项目转移）应可见。  
   本地 `isAdminRole()` 仍仅 `admin`，不把 SystemAdmin 当成本地超级管理员。

2. **新增 `BID_ADMIN_LEVEL_ROLES` / `isBidAdminLevelRole` 作为前端单一集合**  
   避免 `user.js` / `permission.js` / drafting 各自 hardcode 列表再次漂移。

3. **`legacyRoleForCode(bid-SystemAdmin) → ADMIN` 保持不动**  
   这是 !2020 的过渡兼容（大量 `hasAnyRole('ADMIN')`）。本次只补测试锁定该行为 + 注释说明是过渡，不在本 PR 拆掉。

4. **不修改组织同步优先级（部门 > 岗位 > sysRoleList）**  
   仅修正 application.yml 注释，避免宣称「完全由 sysRoleList 决定」。登录 vs 同步双路径收敛留给后续课题。

5. **部署 runbook 重写 §6.2 检查 2**  
   person 映射对比改为「应删除死配置」+ 原白名单 14 人 OSS 审计表。

6. **Claude.md 角色表 7→8**  
   与 catalog 对齐。

## 未做（有意）

- 多角色 sysRoleList 优先级 pure core（需产品定序，另开任务）
- CompositeUserDetailsService 路径分离（spec 033）
- 删除 `PersonToRoleMapping` Java 类型（兼容外部 yml 反序列化，防启动炸）
- 直接改 Gitee PR !2020 描述（可在 follow-up PR 描述中引用）

## 验证证据

- 前端 unit：4 files / 169 tests passed  
  (`user.spec.js`, `permission.test.js`, transfer/drafting specs)
- 后端：`UserDetailsServiceImplTest` + `RoleProfileCatalogTest` + 相关 resolver 测试 exit 0  
  （含 SystemAdmin authorities 契约 + 哨兵参数化含 `bid-SystemAdmin`）
