# 第 108 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `b72fd4bc7` |
| 上一版本 | `af19ad4b`（第 107 次，2026-07-26 23:28 CST） |
| 部署时间 | 2026-07-28 12:40:55 CST |
| 增量 | 6 个 commit（PR !2203/!2205/!2206 + 配套提交） |
| 新增迁移 | 0 个 |
| 部署结果 | ✅ 成功（readiness 延迟 2 分 36 秒恢复，Kafka SDK 已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 107 次部署（2026-07-26）后，main 合入 6 个 commit，主要为 CO-599 两轮修复：

- **PR !2205** CO-599 task-reminder 投标辅助人员项目级过滤 + 拆分重构
- **PR !2206** CO-599 fix bid-SystemAdmin 不能分配标讯/立即投标——actionMatrix 漏配角色分组
- **PR !2203** 第 107 次测试环境部署报告

其中 PR !2206 是 PR !2205 引入的回归修复——`actionMatrix.js` 的 `resolveRoleGroup` 函数漏配 `bid-SystemAdmin` 角色分组，导致投标系统管理员无法分配标讯、无法点击立即投标。本次部署将这两轮修复同步到测试环境验证。

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| HEAD commit | `b72fd4bc7` |
| 工作区状态 | clean |
| GitHub 镜像 | 0 commits behind（已同步） |
| Git wrapper | 未激活（部署操作不涉及 commit/push，不影响） |

## 增量 commit (af19ad4b..b72fd4bc7)

```
b72fd4bc7 !2205 feat(task-reminder): 投标辅助人员项目级过滤 + 拆分重构 (CO-599)
990b496d5 !2206 fix(bidding): CO-599 bid-SystemAdmin 不能分配标讯/立即投标——actionMatrix 漏配角色分组
4eb4005d7 fix(bidding): CO-599 bid-SystemAdmin 不能分配标讯/立即投标——actionMatrix 漏配角色分组
958fc6aab auto-merge by gitee-pr-helper.sh Merge pull request !2203
26db225a9 feat(task-reminder): 投标辅助人员项目级过滤 + 拆分重构 (CO-599)
bcb175f65 docs(release): 第 107 次测试环境部署报告 (test)
```

## 改动范围

### 1. PR !2205 CO-599 task-reminder 投标辅助人员项目级过滤 + 拆分重构

- **投标辅助人员项目级过滤**：task-reminder 通知接收人按项目可见性过滤，避免越权接收
- **拆分重构**：相关服务拆分重构，提升可维护性

### 2. PR !2206 CO-599 fix bid-SystemAdmin actionMatrix 漏配角色分组

- **根因**：2026-07-11 PR !2021 引入 `bid-SystemAdmin` 角色时，漏改 `src/views/Bidding/detail/actionMatrix.js` 的 `resolveRoleGroup` 函数，该函数未将 `bid-SystemAdmin` 分配到任何角色组，导致相关按钮不显示
- **触发时机**：之前因该角色缺少菜单权限被路由守卫拦截，未暴露问题；第 107 次部署（commit `1691ec4ff`）补全其菜单权限后，用户可进入标讯详情页，缺陷被触发
- **修复方案**：在 `resolveRoleGroup` 函数中添加 `bid-SystemAdmin` 到 `admin_lead` 分组，并补充 `actionMatrix.spec.js` 单测覆盖该角色

### 3. PR !2203 第 107 次测试环境部署报告

- 第 107 次部署的文档归档（auto-merge by gitee-pr-helper.sh）

## Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 241 migrations (execution time 00:00.089s)
```

✅ DB 当前状态健康，241 migrations 全部 checksum 匹配。

### Step 2: DB 已应用版本 vs 源码最新版本

DB 最近 5 个已应用迁移：

| version | description | success | installed_on |
|---|---|---|---|
| 1180 | add knowledge sub permissions | 1 | 2026-07-26 23:28:33 |
| 1179 | add knowledge personnel permission | 1 | 2026-07-26 23:28:33 |
| 1178 | add knowledge qualification permission | 1 | 2026-07-26 23:28:33 |
| 1177 | backfill business table comments | 1 | 2026-07-26 23:28:33 |
| 1174 | fix quoted menu permissions | 1 | 2026-07-26 23:28:32 |

✅ DB 已应用至 V1180，本次部署无新迁移文件（`git diff --name-only af19ad4b..HEAD -- backend/src/main/resources/db/migration-mysql/` 输出为空），无需应用任何迁移。

### Step 3: remote-deploy.sh 内置 validate

`remote-deploy.sh` 在激活新 jar 前自动执行 Flyway validate，失败则停止 rollout。本次部署该步骤通过，旧 jar 仍在运行时新 jar 已通过验证。

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="b72fd4bc7" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空 → 同源构建（`baseURL=""`）
- `VITE_OBS_ENABLED=true` 显式启用 OBS 大文件直传（双保险，脚本默认已改为 true）
- `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留文件污染服务器
- BUILD SUCCESS（27.394s）

### 2. 产物校验

| 校验项 | 结果 |
|---|---|
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| 前端入口 | `assets/index-DKs8RJe4.js` ✅ |
| jar 内 Flyway 迁移文件数 | 240 files（无重复）✅ |
| OBS 直传 Detail chunk `.upload(` 调用数 | 2（未被 tree-shake）✅ |

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-b72fd4bc7.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-b72fd4bc7.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=b72fd4bc7 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="... mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-b72fd4bc7-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- `SYSTEMCTL_SUDO=true` 让 remote-deploy.sh 用 sudo 重启服务（jetty 用户已配置 NOPASSWD sudo）
- DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-b72fd4bc7-*.sql.gz`）
- 部署激活时间：2026-07-28T04:40:55Z = 12:40:55 CST

### 4. 前端资源保留（防跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/af19ad4b/frontend/assets/* /srv/www/xiyu-bid/assets/ 2>/dev/null'
```

✅ 已从上一版本 `af19ad4b` release 目录保留旧 assets 24h，避免旧标签页 `<link rel="preload">` 指向已删除资源触发 Nginx 404 + Sentry 噪声。

## 验证结果

### 1. 健康检查（readiness 延迟恢复）

部署后 readiness 持续 503 约 **2 分 36 秒**（12:40:55 → 12:43:31），属 Kafka SDK `OrganizationEventSdkKafkaStarter` 已知行为：

```
[12:42:26] attempt=1  health=503 readiness=503
[12:42:30] attempt=2  health=503 readiness=503
...
[12:43:26] attempt=16 health=503 readiness=503
[12:43:31] attempt=17 health=200 readiness=200  ✅ readiness 已恢复
```

**历史对照**：第 8 次 4 分 22 秒、第 15 次 2 分 36 秒、本次 2 分 36 秒——Kafka broker 可达后自恢复，无需回滚。

### 2. Flyway 迁移应用验证

DB 最近 3 个已应用迁移（部署后查询）：

| version | description | success | installed_on |
|---|---|---|---|
| 1180 | add knowledge sub permissions | 1 | 2026-07-26 23:28:33 |
| 1179 | add knowledge personnel permission | 1 | 2026-07-26 23:28:33 |
| 1178 | add knowledge qualification permission | 1 | 2026-07-26 23:28:33 |

✅ 与部署前一致（无新迁移应用），符合预期。

### 3. API Smoke（经 Nginx 8080 代理到后端 18080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

> Admin 密码未知，用 400/403/401 替代完整登录 smoke（自第 6 次起沿用）。

### 4. 前端页面验证

| # | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 6 | `GET /` | 200 | HTTP 200 | ✅ |
| 7 | `GET /login` | 200 | HTTP 200 | ✅ |
| 8 | 前端入口 | `assets/index-DKs8RJe4.js` | 一致 | ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前 GitHub 镜像状态 | 0 commits behind（已同步） |
| 部署后操作 | 无需 `sync-to-github.sh`（Gitee main 未变更，仅服务器部署） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release ID | `af19ad4b` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/af19ad4b/`（仍存在） |
| 上一版本前端 assets | 已保留至 `/srv/www/xiyu-bid/assets/`（24h 自然刷新） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-b72fd4bc7-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'cd /opt/xiyu-bid && RELEASE_ID=af19ad4b bash releases/af19ad4b/rollback.sh'`（如存在） |

## 经验沉淀应用情况

| 经验条目 | 本次应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ Step 1 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| #2 Readiness 延迟恢复 | ✅ 2 分 36 秒自恢复，未急于回滚 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试限制 | ✅ Admin 密码未知，用 400/403/401 替代 |
| #5 GitHub 镜像同步 | ✅ 部署前 0 behind |
| #6 临时调试配置清理 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 沿用（第 13 次起用户决定保留） |
| #7 幂等迁移设计 | N/A（本次无新迁移） |
| #8 systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| #10 破坏性 schema 变更 | N/A（本次无新迁移） |
| #13 前端目录权限 | ✅ `sudo cp -rn` 已用 sudo |
| #14 macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` |
| #15 Flyway 防护体系 | ✅ 全流程通过 |
| #16 Mac HTTP_PROXY 502 | ✅ `curl --noproxy '*'` |
| #17 SentryAppender crash-loop | ✅ 无 logback.xml 改动 |
| #18 前端 hash 资源跨版本 404 | ✅ 部署后 `cp -rn` 保留上一版本 assets 24h |
| #OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` 显式传入 + 产物校验 `obsEnabled=true` |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 沿用**：自第 13 次起用户决定保留，方便调试健康检查详情。生产环境建议关闭（暴露 DB/Redis 等组件详情）。
2. **PR !2206 是 PR !2205 的回归修复**：本次部署包含两轮 CO-599 修复，测试时需重点验证：
   - 投标系统管理员（`bid-SystemAdmin`）能正常分配标讯
   - 投标系统管理员能点击"立即投标"按钮
   - task-reminder 通知接收人按项目可见性过滤，无越权接收
3. **Kafka SDK readiness 延迟**：本次 2 分 36 秒，属已知行为。若未来延迟持续超过 5 分钟，需考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行。

## 部署确认清单

- [x] 环境门禁确认（用户 AskUserQuestion 确认测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认（GitHub 镜像 0 behind，HEAD=b72fd4bc7）
- [x] 服务器现状检查（af19ad4b 健康 UP）
- [x] Flyway 预检 3 步法全通过
- [x] 本地打包 BUILD SUCCESS（27.394s）
- [x] 产物校验全通过（obsEnabled=true，apiBaseUrl=""，240 迁移文件无重复）
- [x] 上传 + 部署成功激活（12:40:55 CST，SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（cp -rn af19ad4b assets 24h）
- [x] 健康检查 readiness 恢复（2 分 36 秒，Kafka SDK 已知行为）
- [x] Flyway 迁移应用验证（V1180，无新迁移）
- [x] API Smoke 5 项全通过
- [x] 前端页面 3 项全通过
- [x] GitHub 镜像同步（0 behind）
- [x] 配置清理检查（SHOW_DETAILS=always 沿用，用户已知）
- [x] 部署报告生成

## 后续待办

- [ ] 提 PR 合入本部署报告（PR 标题：`docs(release): 第 108 次测试环境部署报告 (test)`）
- [ ] 测试环境 UAT 验证 CO-599 两轮修复效果（bid-SystemAdmin 分配标讯 + 立即投标 + task-reminder 项目级过滤）
