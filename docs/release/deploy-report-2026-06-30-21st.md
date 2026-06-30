# 第 21 次部署报告

> **部署日期**：2026-06-30  
> **Release ID**：`48c429c58-api8080`  
> **部署类型**：常规部署（含 Flyway 迁移）  
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `48c429c58-api8080` |
| 激活时间 | 2026-06-30 13:08:36 CST |
| 上一次部署 | `3280389b6-api8080`（第 20 次，2026-06-30 11:03:12 CST） |
| 增量 commit | 16 个（含 PR !1385-!1392） |
| 新增迁移 | V1118（1 个，有 rollback U1118） |
| 部署耗时 | 约 5 分钟（含打包 41s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 任务分支 | `agent/trae/deploy-21st` |
| 部署 commit | `48c429c58cd566c934561d62ce19a33157f5319b` |
| GitHub 镜像 | 已同步（从落后 17 commit 到完全一致） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代 18080） |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1385 | refactor(permission): 统一 bid-projectLeader 授权模式并下沉 owner 检查到 Policy 层 |
| !1386 | docs(release): 第 20 次部署报告 |
| !1387 | docs(lessons): 新增 §28 权限 Bug 多 UI 入口对称审查（CO-400 五轮 + CO-415 归纳） |
| !1388 | CO-414 CRM feedback add abandonmentReason field |
| !1389 | fix(platform-borrow): 登记归还入口收敛到我的审批Tab + 申请人列显示姓名工号 |
| !1390 | fix(deposit-task): 保证金待办执行人改为项目负责人，详细描述加入金额和缴纳方式 |
| !1391 | fix(permission): CO-416 下沉账户编辑权限到 Policy 层，放行投标专员绑定联系人 |
| !1392 | fix(flyway): V1118 表名 role_profile 改为 roles + 修复 hook 误判（**部署前 hotfix**） |

## 四、改动范围

- **35 个文件变更**，+1091/-114 行
  - 后端 24 文件、前端 5 文件（3 views + 2 components）、文档/脚本 6 文件
- **1 个新 Flyway 迁移**（V1118）
- **权限治理**：
  - 统一 bid-projectLeader 授权模式，下沉 owner 检查到 Policy 层（!1385）
  - CO-416 账户编辑权限下沉到 Policy，放行投标专员绑定联系人（!1391）
  - V1118 新增 `tender.view` / `personnel.view` 权限点，替代硬编码 roleCode 白名单
- **业务修复**：
  - CO-414 CRM feedback 增加 abandonmentReason 独立字段（!1388）
  - 平台借用登记归还入口收敛到"我的审批"Tab + 申请人列显示姓名工号（!1389）
  - 保证金待办执行人改为项目负责人，详细描述加入金额和缴纳方式（!1390）
- **文档沉淀**：lessons §28 权限 Bug 多 UI 入口对称审查方法论

## 五、Flyway 预检结果

### Step 1: 服务器 validate
```
VALIDATE OK - all checksums match（181 migrations）
```

### Step 2: DB 版本对比
- 部署前 DB 最新版本：V1117（2026-06-30 11:04:49）
- 源码最新版本：V1118
- V1118 待应用（预期）

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match（部署前再次验证）
```

### V1118 迁移评估

| 项 | 评估 |
|---|---|
| 迁移内容 | CO-403: 为 bid-projectLeader/bid-TeamLeader//bidAdmin/bid-Team 追加 `tender.view` 和 `personnel.view` 权限点 |
| 幂等性 | ✅ 使用 `LIKE '%tender.view%'` 检查 + CASE WHEN，重复执行安全 |
| 安全性 | ✅ 纯 UPDATE roles.menu_permissions，无 DDL 变更 |
| 回滚脚本 | ✅ U1118 存在，使用 REPLACE 移除权限点 |
| 风险等级 | 低（权限点追加，不影响现有数据） |

### ⚠️ V1118 表名修复（!1392 hotfix）

**发现**：V1118 原始版本误用表名 `role_profile`（历史遗留），实际生产表名为 `roles`。pre-commit hook 误判未拦截。

**修复**：commit `2848446dd` 将表名改为 `roles`，并修复 hook 误判逻辑。PR !1392 合入 main 后才打包部署。

## 六、部署步骤

1. **早操三连**（锚点分支 `agent/trae-init`）
   - `source scripts/dev-env.sh` → 环境检测通过（trae 主工作区）
   - `bash scripts/sync-env.sh .` → init 分支 ff-only 同步，已是最新
   - `bash scripts/check-git-wrapper.sh` → 7 项门禁全部通过
2. **创建任务分支**：`agent-start-task.sh trae deploy-21st origin/main --in-place`
3. **服务器现状检查**：deployed-release.json（3280389b6）+ health（200 UP）+ 增量 commit（16 个）+ 迁移文件变更（V1118+U1118）
4. **Flyway 预检 3 步法**：validate 通过 + DB 版本对比（V1117→V1118）+ remote-deploy 内置
5. **本地打包**：
   ```bash
   RELEASE_ID="48c429c58-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
   ```
   - 前端：7.65s 构建完成，同源 baseURL（`VITE_API_BASE_URL=` 显式设空）
   - 后端：33.356s BUILD SUCCESS，2570 源文件编译
   - jar 内 Flyway 迁移版本无重复（181 files）
6. **产物校验**：
   - jar 内 V1118 存在（2007 bytes）✅
   - 前端入口 `assets/index-DoHPT0Tx.js` ✅
7. **上传 + 部署**：
   ```bash
   scp .release/xiyu-bid-release-48c429c58-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/
   ```
   - DB 备份完成 ✅
   - Flyway validate 预检通过 ✅
   - 激活新 jar + sudo systemctl restart ✅（`SYSTEMCTL_SUDO=true`）

## 七、验证结果

### 健康检查
| 检查项 | 结果 |
|---|---|
| health | HTTP 200 UP（9 个组件全部 UP：aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar） |
| readiness | HTTP 200 UP ✅（**本次无 Kafka 延迟**） |
| 后端服务 | active ✅ |

### 迁移应用验证
```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN ('1118','1117') ORDER BY version;
```
| version | description | success | installed_on |
|---|---|---|---|
| 1117 | fix tenders evaluation source to enum | 1 | 2026-06-30 11:04:49 |
| 1118 | add tender view personnel view permissions | 1 | 2026-06-30 13:10:13 |

V1118 已成功应用 ✅

### Smoke 测试（Admin 密码未知，用 400/403/401 替代验证）
| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /`（前端） | 200 | 200 | ✅ |
| `GET /login`（前端） | 200 | 200 | ✅ |
| 前端 asset | index-DoHPT0Tx.js | index-DoHPT0Tx.js | ✅ 与 release 一致 |

> 登录 smoke 因 admin 密码未授予而跳过，使用 400/403/401 替代验证策略（第 6 次起固化）。

## 八、GitHub 镜像同步

| 项 | 值 |
|---|---|
| 部署前 | GitHub main 落后 Gitee main 17 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 部署后 | Gitee main = GitHub main = `48c429c58cd566c934561d62ce19a33157f5319b` ✅ 完全一致 |
| 门禁 | sync-to-github.sh 内置 10 道门禁全部通过（eslint + Agent 任务分支集中度等） |

## 九、回滚信息

| 项 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 回滚 posture | Ready |
| 前一版本 artifact | `/opt/xiyu-bid/releases/3280389b6-api8080/`（第 20 次部署） |
| 前一版本 jar | `bid-poc-1.0.3.jar`（commit 3280389b6） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-48c429c58-api8080-<timestamp>.sql.gz` |
| V1118 回滚脚本 | `U1118__add_tender_view_personnel_view_permissions.sql`（REPLACE 移除权限点） |
| 回滚步骤 | 1. 激活旧 jar + 重启；2. 执行 U1118 回滚 SQL（移除 tender.view/personnel.view 权限点） |

## 十、经验沉淀应用情况

本次部署应用了以下 15 条经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ Step 1 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| 2 | Readiness Kafka SDK 延迟 | ✅ 容忍策略就绪（本次未出现延迟） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，触发 baseURL="" |
| 4 | Smoke 测试 400/403/401 | ✅ 替代验证策略执行 |
| 5 | GitHub 镜像同步 | ✅ 部署后执行 sync-to-github.sh |
| 6 | 临时调试配置清理 | ✅ SHOW_DETAILS=always 保留（第 13/14/15 次用户决定保留） |
| 7 | 幂等迁移设计 | ✅ V1118 使用 LIKE 检查 + CASE WHEN，幂等安全 |
| 8 | systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true 显式传参 |
| 12 | rollback 脚本命名规范 | ✅ U1118（U 前缀，非 RV） |
| 15 | Flyway 防护体系 | ✅ 15 项防护全部生效（pre-commit + pre-push + package-release + remote-deploy） |

### V1118 表名修复（!1392 hotfix）的经验

**发现**：V1118 原始版本误用 `role_profile` 表名（历史遗留），pre-commit hook 的表名守卫未拦截。

**修复**：commit `2848446dd` 修正为 `roles` + 修复 hook 误判逻辑。

**教训**：新增迁移的 SQL 应直接查询 `SHOW TABLES` 确认表名，不依赖记忆。pre-commit hook 的表名白名单需要与 `db-schema.md` 保持同步。

## 十一、风险提示

1. **V1118 权限点生效依赖前端配合**：`tender.view` / `personnel.view` 权限点需要在 `@PreAuthorize(hasAuthority)` 注解中使用才生效。本次只追加了 DB 权限点，前端/后端代码是否已切换到新权限点需业务验证。
2. **SHOW_DETAILS=always 保留**：生产环境 health 端点暴露详情（第 13/14/15 次用户决定保留，运维监控需要）。如后续需收紧安全，改为 `never` 并重启后端。
3. **GitHub 仓库迁移提示**：sync-to-github.sh 输出 `This repository moved. Please use the new location: git@github.com:luhuochunqing/bid.git`。当前 SSH config 仍用 `github-bid:yzcynk5vtp-ship-it/bid.git`，同步正常但建议后续确认 GitHub 仓库归属。

## 十二、部署确认清单

- [x] 早操三连完成（dev-env + sync-env + check-git-wrapper）
- [x] 任务分支创建（agent/trae/deploy-21st）
- [x] 基线确认（工作区干净，HEAD=48c429c58）
- [x] 服务器现状检查（deployed-release.json + health）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] V1118 迁移评估（幂等 + 安全 + 回滚脚本）
- [x] 本地打包（前端同源构建 + 后端 BUILD SUCCESS）
- [x] 产物校验（jar 内 V1118 + 前端入口一致）
- [x] 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true）
- [x] 健康检查（health UP + readiness UP，无 Kafka 延迟）
- [x] 迁移应用验证（V1118 success=1）
- [x] Smoke 测试（400/403/401 + 前端 200 + asset 一致）
- [x] GitHub 镜像同步（两边 main 完全一致）
- [x] 配置清理检查（SHOW_DETAILS=always 保留，已说明理由）
- [x] 部署报告生成

---

**部署完成时间**：2026-06-30 13:15 CST  
**部署执行者**：Trae Agent  
**回滚 readiness**：Ready（未需要）
