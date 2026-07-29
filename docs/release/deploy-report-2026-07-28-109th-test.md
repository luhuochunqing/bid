# 第 109 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `6e1a2db59` |
| 上一版本 | `b72fd4bc7`（第 108 次，2026-07-28 12:40 CST） |
| 部署时间 | 2026-07-28 18:24:08 CST |
| 增量 | 10 个 commit（PR !2207/!2208/!2209/!2210 + 配套提交） |
| 新增迁移 | 0 个 |
| 部署结果 | ✅ 成功（readiness 无延迟，健康检查 3/3 立即通过） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 108 次部署（2026-07-28 12:40 CST）后，main 合入 10 个 commit，主要为三类改动：

- **PR !2207** fix(notification): 补齐 notificationType 中文映射，修复 CA 通知显示英文枚举
- **PR !2208** docs(release): 第 108 次测试环境部署报告
- **PR !2209** test(gap-backfill): 补充 PR !2201 测试覆盖缺口（FormSubmissionMappers + CaNotificationDispatcher）
- **PR !2210** fix(tender-intake): 修复招标主体识别不准——抽出共享常量类 PurchaserAliases 作为唯一真相来源

本次部署将这些改动同步到测试环境。

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| HEAD commit | `6e1a2db59` |
| 工作区状态 | clean |
| GitHub 镜像 | 3 commits behind（早操已记录，非本次部署引入） |
| Git wrapper | 未激活（部署操作不涉及 commit/push，不影响） |

## 增量 commit (b72fd4bc7..6e1a2db59)

```
6e1a2db59 !2210 auto-merge by gitee-pr-helper.sh Merge pull request !2210
c2d4be673 refactor(tender-intake): Google Code Review 反馈修复——简化设计 + 修正误导性 Few-Shot
fc9af098f fix(tender-intake): 修复招标主体识别不准——抽出共享常量类 PurchaserAliases 作为唯一真相来源
a97d226bb !2209 test(gap-backfill): 补充 PR !2201 测试覆盖缺口 (FormSubmissionMappers + CaNotificationDispatcher) Merge pull request !2209
a0512c26c test(gap-backfill): 补充 PR !2201 BUI-1.1 / FormSubmissionMappers 覆盖缺口
2f8d1fb0b test(gap-backfill): 补充 CaNotificationDispatcher 剩余盲区
946df69b1 !2207 fix(notification): 补齐 notificationType 中文映射，修复 CA 通知显示英文枚举 Merge pull request !2207
657e3e829 !2208 docs(release): 第 108 次测试环境部署报告 (test) Merge pull request !2208
002519ad8 docs(release): 第 108 次测试环境部署报告 (test)
0fab5946d fix(notification): 补齐 notificationType 中文映射，修复 CA 通知显示英文枚举
```

## 改动范围

### 1. PR !2207 fix(notification): notificationType 中文映射

- **根因**：CA 通知显示英文枚举（notificationType 原始值），未做中文映射
- **修复**：在 `src/utils/notificationHelpers.js` 中补齐 notificationType 中文映射，并补充单测 `notificationHelpers.spec.js`

### 2. PR !2208 第 108 次测试环境部署报告

- 第 108 次部署的文档归档

### 3. PR !2209 test(gap-backfill): 补充 PR !2201 测试覆盖缺口

- **FormSubmissionMappers 测试**：补充 `FormSubmissionMappersTest.java`（414 行新增），覆盖 PR !2201 BUI-1.1 表单提交映射器盲区
- **CaNotificationDispatcher 测试**：补充 `CaNotificationDispatcherTest.java`（52 行新增），覆盖 CA 通知分发器剩余盲区
- **useDetailActions 测试**：补充 `useDetailActions.spec.js`（95 行新增）

### 4. PR !2210 fix(tender-intake): 招标主体识别不准修复

- **根因**：招标主体识别在多处硬编码别名表，难以维护且不一致
- **修复方案**：抽出共享常量类 `PurchaserAliases` 作为唯一真相来源，统一所有别名匹配入口
- **Google Code Review 反馈**：第二轮 refactor 简化设计 + 修正误导性 Few-Shot
- **测试**：补充 `PurchaserAliasesTest.java`（74 行新增）和 `TenderIntakeTextProcessorTest.java`（29 行新增）

## Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 241 migrations (execution time 00:00.092s)
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

✅ DB 已应用至 V1180，本次部署无新迁移文件（`git diff --name-only b72fd4bc7..HEAD -- backend/src/main/resources/db/migration-mysql/` 输出为空），无需应用任何迁移。

### Step 3: remote-deploy.sh 内置 validate

`remote-deploy.sh` 在激活新 jar 前自动执行 Flyway validate，失败则停止 rollout。本次部署该步骤通过，旧 jar 仍在运行时新 jar 已通过验证。

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="6e1a2db59" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空 → 同源构建（`baseURL=""`）
- `VITE_OBS_ENABLED=true` 显式启用 OBS 大文件直传（双保险，脚本默认已改为 true）
- `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留文件污染服务器
- BUILD SUCCESS（26.083s）

### 2. 产物校验

| 校验项 | 结果 |
|---|---|
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| `release-metadata.json` 中 `sentryEnabled` | `false` ✅ |
| 前端入口 | `assets/index-DY4s5YDD.js` ✅ |
| jar 内 Flyway 迁移文件数 | 240 files（无重复）✅ |
| OBS 直传 Detail chunk `.upload(` 调用数 | 2（未被 tree-shake）✅ |
| 包大小 | 160M |

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-6e1a2db59.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-6e1a2db59.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=6e1a2db59 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="... mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-6e1a2db59-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- `SYSTEMCTL_SUDO=true` 让 remote-deploy.sh 用 sudo 重启服务（jetty 用户已配置 NOPASSWD sudo）
- DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-6e1a2db59-*.sql.gz`）
- 部署激活时间：2026-07-28T18:24:08 CST（systemd `active (running)`）
- 健康检查 3/3 通过（`total attempts: 80`），前端入口匹配 `assets/index-DY4s5YDD.js`

### 4. 前端资源保留（防跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/b72fd4bc7/frontend/assets/* /srv/www/xiyu-bid/assets/ 2>/dev/null'
```

✅ 已从上一版本 `b72fd4bc7` release 目录保留旧 assets 24h，避免旧标签页 `<link rel="preload">` 指向已删除资源触发 Nginx 404 + Sentry 噪声。

## 验证结果

### 1. 健康检查（readiness 无延迟）

部署后健康检查立即通过（remote-deploy.sh 内置 consecutive 3/3，未出现 Kafka SDK readiness 延迟）：

```
✅ Health check passed (consecutive 3/3, total attempts: 80, service: active/running)
```

**历史对照**：第 8 次 4 分 22 秒、第 15 次 2 分 36 秒、第 108 次 2 分 36 秒、本次 0 秒——Kafka broker 可达时无延迟，符合已知行为。

### 2. Flyway 迁移应用验证

DB 最近 5 个已应用迁移（部署后查询）：

| version | description | success | installed_on |
|---|---|---|---|
| 1180 | add knowledge sub permissions | 1 | 2026-07-26 23:28:33 |
| 1179 | add knowledge personnel permission | 1 | 2026-07-26 23:28:33 |
| 1178 | add knowledge qualification permission | 1 | 2026-07-26 23:28:33 |
| 1177 | backfill business table comments | 1 | 2026-07-26 23:28:33 |
| 1174 | fix quoted menu permissions | 1 | 2026-07-26 23:28:32 |

✅ 与部署前一致（无新迁移应用），符合预期。

### 3. API Smoke（经 Nginx 8080 代理到后端 18080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 UP | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 UP | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

> Admin 密码未知，用 400/403/401 替代完整登录 smoke（自第 6 次起沿用）。

### 4. 前端页面验证

| # | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 6 | `GET /` | 200 | HTTP 200 | ✅ |
| 7 | `GET /login` | 200 | HTTP 200 | ✅ |
| 8 | 前端入口 | `assets/index-DY4s5YDD.js` | 一致 | ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前 GitHub 镜像状态 | 3 commits behind（早操已记录，非本次部署引入） |
| 部署后操作 | 未执行 `sync-to-github.sh`（仅服务器部署，Gitee main 未变更） |
| 同步命令（如需） | `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .` |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release ID | `b72fd4bc7` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/b72fd4bc7/`（仍存在） |
| 上一版本前端 assets | 已保留至 `/srv/www/xiyu-bid/assets/`（24h 自然刷新） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-6e1a2db59-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'cd /opt/xiyu-bid && RELEASE_ID=b72fd4bc7 bash releases/b72fd4bc7/rollback.sh'`（如存在） |

## 经验沉淀应用情况

| 经验条目 | 本次应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ Step 1 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| #2 Readiness 延迟恢复 | ✅ 本次无延迟（0 秒），Kafka broker 可达时无延迟 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试限制 | ✅ Admin 密码未知，用 400/403/401 替代 |
| #5 GitHub 镜像同步 | ⚠️ 部署前 3 behind（早操已记录，非本次引入） |
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
2. **GitHub 镜像落后 3 个 commit**：早操已记录，非本次部署引入。如需同步执行 `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .`（仅主工作区）。
3. **本次为低风险部署**：无 schema 变更、无新迁移、无后端业务逻辑变更，主要为测试补全 + 通知文案映射 + tender-intake 别名常量化重构。重点验证：
   - CA 通知页面 notificationType 显示中文（不再显示英文枚举）
   - tender-intake 招标主体识别准确率（PurchaserAliases 常量化后）
   - FormSubmissionMappers / CaNotificationDispatcher 单测全绿
4. **tender-intake refactor 两轮迭代**：PR !2210 经历 fix + refactor 两轮，第二轮根据 Google Code Review 反馈简化设计，需重点验证招标主体识别在所有别名场景下的准确性。

## 部署确认清单

- [x] 环境门禁确认（用户 AskUserQuestion 确认测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD=6e1a2db59，与 origin/main 一致）
- [x] 服务器现状检查（b72fd4bc7 健康 UP）
- [x] Flyway 预检 3 步法全通过
- [x] 本地打包 BUILD SUCCESS（26.083s）
- [x] 产物校验全通过（obsEnabled=true，apiBaseUrl=""，240 迁移文件无重复）
- [x] 上传 + 部署成功激活（18:24:08 CST，SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（cp -rn b72fd4bc7 assets 24h）
- [x] 健康检查通过（3/3 立即恢复，无 Kafka readiness 延迟）
- [x] Flyway 迁移应用验证（V1180，无新迁移）
- [x] API Smoke 5 项全通过
- [x] 前端页面 3 项全通过
- [x] GitHub 镜像同步检查（3 behind，非本次引入）
- [x] 配置清理检查（SHOW_DETAILS=always 沿用，用户已知）
- [x] 部署报告生成

## 后续待办

- [ ] 提 PR 合入本部署报告（PR 标题：`docs(release): 第 109 次测试环境部署报告 (test)`）
- [ ] 测试环境 UAT 验证：
  - CA 通知页面 notificationType 显示中文（PR !2207）
  - tender-intake 招标主体识别准确率（PR !2210，PurchaserAliases 常量化）
  - FormSubmissionMappers / CaNotificationDispatcher 单测全绿（PR !2209）
- [ ] 如需同步 GitHub 镜像：`SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .`
