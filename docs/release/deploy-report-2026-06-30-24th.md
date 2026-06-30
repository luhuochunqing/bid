# 第 24 次部署报告

> **部署日期**：2026-06-30
> **Release ID**：`2b6d7e7f3-api8080`
> **部署类型**：常规部署（无 Flyway 迁移）
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `2b6d7e7f3-api8080` |
| 激活时间 | 2026-06-30 23:17:22 CST |
| 上一次部署 | `c32d80aba-api8080`（第 23 次，2026-06-30 21:29:30 CST） |
| 增量 commit | 14 个（含 PR !1419-!1424） |
| 新增迁移 | 无 |
| 部署耗时 | 约 6 分钟（含打包 27s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/cursor`（临时 checkout 到 origin/main 构建后切回锚点） |
| 任务分支 | `agent/cursor-init`（锚点分支，部署前 rebase 到 origin/main） |
| 部署 commit | `2b6d7e7f3a45628a2d6fbfefb6fe197e9a3c12b6` |
| GitHub 镜像 | ✅ 已同步（部署前已执行 sync-to-github.sh） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080 |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1419 | fix(crm): CO-431 加诊断日志确认 CRM position 原始值格式（不改业务逻辑） |
| !1420 | docs(release): 第 23 次部署报告 |
| !1421 | fix-brand-auth-oplog-format: Automation skill-progression-map update |
| !1422 | fix(ca): CO-433 修复 CA 详情页借用按钮命名与功能不匹配 |
| !1423 | fix(CO-417): 操作日志变更摘要字段名展示为中文 |
| !1424 | fix(margin): 保证金看板补充立项表数据，解决与项目详情页数据不一致 |

## 四、改动范围

- **多个文件变更**（涉及后端、前端、文档）
- **无 Flyway 迁移**（纯业务修复+改进）
- **CO-431 CRM 诊断日志**：加诊断日志确认 CRM position 原始值格式，不改业务逻辑
- **CO-433 CA 详情页**：借用按钮命名与功能不匹配修复
- **CO-417 操作日志**：变更摘要字段名展示为中文
- **保证金看板**：补充立项表数据，解决与项目详情页数据不一致
- **CO-428 项目档案导出修复**（在 !1413，含 PR !1413 合并在第 23 次部署 commit 链中，本次完整包含）
- **部署报告**：第 23 次部署报告文档化

## 五、Flyway 预检结果

### Step 1: 服务器 validate（部署前）

```
23:13:17.882 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 187 migrations (execution time 00:00.085s)
VALIDATE OK - all checksums match
```

### Step 2: DB 已应用版本 vs 源码最新版本

| 维度 | 版本 |
|---|---|
| DB 已应用最新 | V1123（add qualification manage permission） |
| 源码最新 | V1123（无新增） |
| 待应用 | 无 |

### Step 3: remote-deploy.sh 内置 validate

```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### 迁移文件安全性

无新迁移，无需安全性评估。

## 六、部署步骤

| 步骤 | 结果 | 备注 |
|---|---|---|
| 1. 早操三连 | ✅ | dev-env.sh + sync-env.sh + check-git-wrapper.sh 全通过 |
| 2. 基线确认 | ✅ | cursor 锚点 HEAD `31327eddb`，fetch 后 origin/main `2b6d7e7f3`，14 个增量 commit |
| 3. 服务器现状 | ✅ | 上一版本 c32d80aba-api8080，health UP |
| 4. Flyway 预检 3 步 | ✅ | validate OK + DB 版本对比（无新迁移）+ remote-deploy 内置 |
| 5. GitHub 同步 | ✅ | sync-to-github.sh：Gitee → GitHub 推送 12 commit，完全一致 |
| 6. 本地打包 | ✅ | RELEASE_ID=2b6d7e7f3-api8080，VITE_API_BASE_URL= 同源构建，前端 10.57s，backend BUILD SUCCESS 27.73s |
| 7. 产物校验 | ✅ | jar 内 Flyway 迁移版本无重复，✅ jar 内 Flyway 迁移版本无重复 |
| 8. 上传 + 部署 | ✅ | scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 9. 后端重启 | ✅ | 2026-06-30 23:17:22 CST active (running) |
| 10. 健康检查 | ✅ | remote-deploy.sh 内置健康检查第 1 次通过 |
| 11. 迁移应用验证 | ✅ | 无新迁移，无需验证 |
| 12. Smoke 测试 | ✅ | health 200, readiness 200, 400/403/401 路由验证 |
| 13. 前端验证 | ✅ | / 200, /login 200, 入口 assets/index-BCiQlp1y.js 匹配 |
| 14. GitHub 同步检查 | ✅ | 0 落后 |
| 15. 锚点分支切回 | ✅ | git checkout agent/cursor-init && ff-only merge origin/main |

### 打包说明

- cursor worktree 锚点分支 HEAD `31327eddb`，origin/main `2b6d7e7f3`（领先 14 个 commit）
- 打包前临时 `git checkout origin/main` 构建，打包后切回 `agent/cursor-init` 并 ff-only merge
- 此后 cursor 锚点已与 origin/main 完全一致

## 七、验证结果

### 健康检查

```
status: UP
components:
  aiProvider: UP (doubao, deepseek-v3-2-251201, apiKeyConfigured: true)
  db: UP (MySQL, isValid())
  diskSpace: UP (free: 45423394816)
  jwt: UP (HMAC-SHA256, secretLength: 64, STRONG)
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP (6.2.19)
  sidecar: UP (http://localhost:8000, reachable)
```

### Readiness 状态

```
status: UP
readinessState: UP
```

**注意**：本次未出现 Kafka SDK readiness 延迟（第 8/9/10/13/15 次历史问题），第 1 次直接通过。

### Smoke 测试（400/403/401 替代验证）

> Admin 密码未授予，使用 HTTP 状态码替代验证接口路由（第 6 次起固化策略）。

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |
| GET / | 200 | 200 | ✅ |
| GET /login | 200 | 200 | ✅ |

## 八、GitHub 镜像同步

| 维度 | 值 |
|---|---|
| 部署前 | 落后 12 commit（sync-to-github.sh 前） |
| sync-to-github.sh | Gitee main → GitHub main 推送 12 commit |
| 部署后 | 0 落后（完全一致） |

```
╔══════════════════════════════════════════════════════════════╗
║  ✅ Gitee → GitHub 镜像同步完成                              ║
╠══════════════════════════════════════════════════════════════╣
║  Gitee main:  2b6d7e7f3a45628a2d6fbfefb6fe197e9a3c12b6 ║
║  GitHub main: 2b6d7e7f3a45628a2d6fbfefb6fe197e9a3c12b6 ║
║  状态: 完全一致                                              ║
╚══════════════════════════════════════════════════════════════╝
```

## 九、回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | `scp + remote-deploy.sh` 重新部署旧版本 |
| 旧版本 artifact | `/opt/xiyu-bid/releases/c32d80aba-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-2b6d7e7f3-20260630231551.sql.gz` |
| 回滚触发条件 | Smoke 失败（P0 接口不可用）或启动 4 分钟内 health 不 UP |
| 回滚owner | 部署执行人 |

## 十、经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 经验 1 Flyway 预检 3 步 | ✅ Step 1 服务器 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| 经验 3 同源构建 VITE_API_BASE_URL= | ✅ `RELEASE_ID=2b6d7e7f3-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh` |
| 经验 4 Smoke 400/403/401 替代 | ✅ 7/7 接口路由验证全绿 |
| 经验 5 GitHub 镜像同步 | ✅ 部署前执行 sync-to-github.sh，Gitee → GitHub 推送 12 commit |
| 经验 8 SYSTEMCTL_SUDO=true | ✅ remote-deploy.sh SYSTEMCTL_SUDO=true，服务正常重启 |
| 经验 11 服务器 /tmp/migration-mysql/ | ✅ validate 通过（不依赖 info 输出），DB 版本通过 SQL 查询确认 |
| 经验 16 Mac HTTP_PROXY 绕过 | ✅ SSH 命令不用代理，直接连接内网 IP |

## 十一、风险提示

| 风险 | 评估 | 处置 |
|---|---|---|
| Kafka SDK readiness 延迟 | 低 | 本次第 1 次即通过，无延迟 |
| Flyway checksum mismatch | 低 | 服务器 validate OK，DB 版本对齐 |
| 前端目录权限（nginx:nginx） | 低 | remote-deploy.sh 自动处理 |

## 十二、部署确认清单

- [x] 健康检查全部 UP
- [x] Smoke 测试 7/7 通过
- [x] 无 Flyway 迁移，无需验证 DB 迁移应用
- [x] 前端入口 JS 与 release 一致
- [x] GitHub 镜像完全同步
- [x] 锚点分支已切回并与 origin/main 一致
- [x] 已生成部署报告

---

**执行人**：cursor agent
**部署时间**：2026-06-30 23:15 ~ 23:20 CST
