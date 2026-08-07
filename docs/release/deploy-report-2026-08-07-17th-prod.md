# 第 17 次生产环境部署报告 — 2026-08-07

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 17 次（生产） |
| 部署时间 | 2026-08-07 19:38 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `8f568d5cc` |
| 上一版本 Release | `a3ecaa47b-prod`（2026-08-04 23:21:17 CST，第 16 次生产部署） |
| 基线 commit | `8f568d5cc6d6268157a3150d67a15bd46e4cb80c`（origin/main） |
| 激活时间 | 2026-08-07T19:38 CST |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 17 个（3 天跨度） |
| 新增 Flyway 迁移 | 0 个（无 DB 结构变更） |
| Smoke 测试 | 7 项全部通过 |
| GitHub 镜像 | ✅ 已同步（两边 main 完全一致） |
| 前端资源保留 | ✅ 已保留上一版本 `a3ecaa47b-prod` 旧 assets（脚本自动提取失败，手动补齐） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`
- 工作区状态：干净，HEAD = origin/main = `8f568d5cc6d6268157a3150d67a15bd46e4cb80c`
- 早操三连：sync-env.sh 在锚点分支拦截（ff-only 同步，非开发行为），已手动确认 HEAD = origin/main、工作区干净
- 部署前 GitHub 镜像落后 Gitee 2 个 commit（部署后已同步）

## 增量改动（a3ecaa47b → 8f568d5cc，17 个 commit）

### 关键 commit / PR 列表

| Commit | 描述 | 类型 |
|---|---|---|
| !2274 | 日历和截止时间模块重复项目显示问题（workbench 去重） | fix |
| 874651476 | 思维链 H1/H2/H3 收口 — stats 接受差异、前端收敛薄防御层 | refactor |
| 6f0cc1266 | 收窄去重范围到 Tender 派生类型 + stats 去重对齐 + 补前端单测 | fix |
| 9fffffdc8 | 修复保证金列表前后端去重不对称 + 固化业务键取舍注释 | fix |
| 0097ad1d7 | 补去重纯函数单测 + 登记 follow-up 任务 | test |
| 3eee7a282 | 日历和截止时间模块重复项目显示问题 | fix |
| !2275 | 补齐 doc-governance 脚本 header 契约缺失 | fix |
| #2269 | CO-605 设置页加载优化 — data-scope 去冗余 + endpoints ETag 缓存 + 通知轮询去重 | perf |
| #2272 | 新增 CA 证书对外查询接口 + Postman 测试集合 | feat |
| !2267 | 通知接收人解析排除 admin 超级管理员，消除 Sentry XIYU-F 告警 | fix |
| 373cb3625 | prune stale expired locks | chore |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/workbench | 日历/截止时间去重（WorkbenchScheduleQueryService / WorkbenchDeadlineQueryService / WorkbenchDeadlinePolicy） |
| backend/integration | CA 证书对外查询接口（CaIntegrationController） |
| backend/notification | 通知接收人排除 admin（NotificationRecipientResolver / CaNotificationDispatcher / TenderNotification services） |
| backend/admin | 权限端点目录 + DataScopeConfig 优化（CO-605） |
| backend/warehouse | 仓库过期扫描任务 |
| src/composables | useNotifications 通知轮询去重（CO-605） |
| src/views/Dashboard | 日历/截止去重前端收敛（useWorkbenchSchedule / workbench-deadline-core） |
| e2e | notification-abandon-excludes-admin 测试 |
| scripts | 通知接收人验证脚本 + 死代码检查脚本 |

### 新增 Flyway 迁移（0 个）

- **无新增迁移**，本次为纯代码 + 前端变更，无 DB 结构变更
- 无破坏性 DROP TABLE / DROP COLUMN

## Flyway 预检 3 步法

### Step 1: Flyway validate（部署前）

```
VALIDATE OK - all checksums match
Successfully validated 245 migrations (execution time 00:00.095s)
```

### Step 2: DB 已应用版本对比

| 项 | 值 |
|---|---|
| 部署前 DB 最新版本 | 1184（create performance export task, 2026-08-04 23:21:24） |
| 源码最新版本 | 1184 |
| 待应用 | 无（本次无新增迁移） |

### Step 3: remote-deploy 内置 validate（部署中）

```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="8f568d5cc" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

- 打包时间：约 45 秒（mvn clean -DskipTests package）
- 产物大小：154M
- 同源构建：`apiBaseUrl=""`（生产前端+后端同入口经 Nginx 反代）
- OBS 直传：`obsEnabled=true` ✅（Detail chunk .upload( 调用数=2）

### 2. 产物校验

| 校验项 | 结果 |
|---|---|
| release-metadata.json obsEnabled | `true` ✅ |
| jar 内 V*.sql 无重复版本 | ✅ |
| 前端 index.html 入口 | `assets/index-DmTMHDGK.js` ✅ |
| 前端无 dev API 地址 | ✅（baseURL 同源） |

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-8f568d5cc.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.10.149:/opt/xiyu-bid/incoming/

ssh jetty@172.16.10.149 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=... RELEASE_ID=8f568d5cc \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="..." \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-8f568d5cc-<timestamp>.sql.gz` ✅
- Flyway validate：✅ 通过
- 后端服务重启：`active (running) since Fri 2026-08-07 19:38:21 CST`
- 健康检查：✅ 14 次尝试，3 次连续成功
- 前端一致性：✅ `assets/index-DmTMHDGK.js` 与 release 一致

### 4. 前端资源保留（防跨版本 404）

```bash
sudo cp -rn $PREV/frontend/assets/* /srv/www/xiyu-bid/assets/ 2>/dev/null
```

- ⚠️ 脚本内自动提取 releaseDir 失败（正则未匹配，`PREV=` 为空），随后**手动补齐**：`sudo cp -rn /opt/xiyu-bid/releases/a3ecaa47b-prod/frontend/assets/* /srv/www/xiyu-bid/assets/`
- ✅ 已保留上一版本 `a3ecaa47b-prod` 的 assets（当前 assets 目录 258 个文件），旧标签页跨版本 404 风险消除

## 验证结果

### 健康检查（部署后）

| 项 | 结果 |
|---|---|
| health（本地 18080） | HTTP 200 UP ✅ |
| readiness | HTTP 200 UP ✅ |
| systemd 服务 | active (running) |

### Smoke 测试（服务器本地经 Nginx 8080 代理）

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 | ✅ |
| `/api/auth/login` POST 空 body | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| `/` 前端首页 | 200 | 200 | ✅ |
| `/login` 前端登录页 | 200 | 200 | ✅ |

- 前端入口：`assets/index-DmTMHDGK.js`（与 release 一致）✅
- 注：从 Mac 访问返回 HTTP 000（本地网络/防火墙），服务器内部 curl 全部通过，非服务故障

### 迁移应用验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| 1184 | create performance export task | 1 | 2026-08-04 23:21:24 |

本次无新增迁移，DB 版本保持 V1184 不变 ✅

## GitHub 镜像同步

| 项 | 值 |
|---|---|
| 部署前落后 | 2 commits |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（HEAD: 8f568d5cc） |
| 门禁 | 12 通过 / 0 失败 / 20 跳过（frontend lint 275 warnings 无 errors） |

## 回滚信息

| 项 | 值 |
|---|---|
| 回滚状态 | ✅ Ready（未需要） |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/a3ecaa47b-prod/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/a3ecaa47b-prod/backend/app.jar` |
| 上一版本前端 | `/opt/xiyu-bid/releases/a3ecaa47b-prod/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-8f568d5cc-<timestamp>.sql.gz` |
| 回滚脚本 | 无新增迁移，无需 U 脚本 |

### 回滚步骤（如需）

1. 停止后端服务：`sudo systemctl stop xiyu-bid-backend`
2. 恢复上一版本 jar：`cp /opt/xiyu-bid/releases/a3ecaa47b-prod/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`
3. 恢复上一版本前端：`sudo cp -r /opt/xiyu-bid/releases/a3ecaa47b-prod/frontend/* /srv/www/xiyu-bid/`
4. 启动后端：`sudo systemctl start xiyu-bid-backend`
5. 健康检查：`curl http://127.0.0.1:18080/actuator/health`

## 经验沉淀应用情况

| 经验条目 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行（validate + DB 版本对比 + remote-deploy 内置） |
| OBS 直传双保险 | ✅ 打包显式 `VITE_OBS_ENABLED=true` + 产物校验 `obsEnabled=true` |
| 同源构建 baseURL="" | ✅ 显式 `VITE_API_BASE_URL=` |
| COPYFILE_DISABLE=1 | ✅ 防 macOS `._*` 残留 |
| SYSTEMCTL_SUDO=true | ✅ 防 `Interactive authentication required` |
| 前端资源保留防 404 | ⚠️ 脚本自动提取 releaseDir 失败，已手动 `cp -rn` 补齐旧 assets |
| Smoke 测试 400/403/401 | ✅ admin 密码未知用路由验证替代 |
| Mac HTTP_PROXY 502 | ✅ 服务器内部 curl 绕过 |
| 临时配置清理检查 | ⚠️ 发现 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留，第 13/14/15/16 次用户决定） |

## 风险提示

| 风险 | 等级 | 说明 |
|---|---|---|
| 前端资源保留未执行 | 🟡 低 | 脚本自动提取 releaseDir 失败，已手动 `cp -rn` 补齐旧 assets（当前 assets 258 个文件），风险已消除 |
| 日历/截止去重逻辑 | 🟡 中 | 业务键不含 id（同标题同日期不同标讯会误并），属刻意取舍，根治需数据清理 + 策略加固（已登记 lessons-learned §109） |
| CO-605 设置页优化 | 🟢 低 | 新增 ETag 缓存 + 轮询去重，需验证设置页功能正常 |
| 通知接收人排除 admin | 🟡 中 | 需验证各角色通知接收是否正常（不再广播给 admin） |
| CA 证书对外查询接口 | 🟢 低 | 新增对外接口，首用待验证 |

## 部署确认清单

- [x] 环境门禁通过（用户确认部署到生产 172.16.10.149）
- [x] 早操三连完成（sync-env 锚点拦截 + 手动确认基线干净）
- [x] 基线确认（HEAD = origin/main = 8f568d5cc）
- [x] 服务器现状检查（健康 UP，systemd 运行）
- [x] Flyway 预检 3 步通过（无新增迁移）
- [x] 本地打包成功（154M，obsEnabled=true）
- [x] 产物校验通过（jar 无重复 V*.sql，前端入口一致）
- [x] 上传 + 部署成功（健康检查 14 次/3 次连续通过）
- [x] 前端资源保留（脚本自动提取失败，已手动 `cp -rn` 补齐旧 assets）
- [x] 迁移应用验证（DB 保持 V1184，无新迁移）
- [x] Smoke 测试 7 项全部通过
- [x] GitHub 镜像同步（两边 main 完全一致）
- [x] 配置清理检查（SHOW_DETAILS=always 历史保留）
- [x] 回滚就绪（旧 release + DB 备份，无 U 脚本）

## 待用户验证事项

> 部署已完成，以下事项建议用户在生产环境实际验证：

1. **日历/截止时间去重**（PR !2274）
   - 访问仪表盘日历，确认不再显示重复项目
   - 注意：同标题同日期不同标讯会被合并（已知取舍），如有误并需报给数据清理任务
2. **通知接收人排除 admin**（PR !2267）
   - 验证高等管理员不再收到广播通知，其他角色通知正常
3. **CO-605 设置页优化**
   - 验证设置页加载速度提升 + 通知轮询去重无异常
4. **CA 证书对外查询接口**（PR #2272）
   - 验证新接口对外可用