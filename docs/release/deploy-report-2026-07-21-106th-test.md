# 第 106 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `0277ccb75` |
| 上一版本 | `ad9f2378c`（第 105 次，2026-07-20 23:15） |
| 部署时间 | 2026-07-21 22:23 CST |
| 增量 | 16 个 commit（PR !2176/!2177/!2179 + 文档） |
| 新增迁移 | 无（最新仍为 V1173） |
| 部署结果 | ✅ 成功（健康检查 79 次尝试后 3/3 通过，Kafka SDK readiness 延迟属已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 105 次部署后，main 合入多轮关键修复：PR !2176 修复 `isLocalSystemAccount` 语义放宽覆盖所有本地非 OSS 账户（解决 settings-permission-effect E2E 回归）+ 9 个 E2E spec 替换已下线的 MANAGER/STAFF 角色码；PR !2177 修复 CO-582 仓库合订本 Word 标题样式失效 + ZIP 下载返回 HTML；PR !2179 修复 §78 OSS 角色识别（覃超颖 403 案例根治）。本次部署将这些修复同步到测试环境。

## 修复内容

### 1. PR !2176 isLocalSystemAccount 语义放宽 + E2E 角色码修复

- **核心修复**：`DataScopeConfigService.isLocalSystemAccount()` 从同时检查 `!user.isOssUser()` + admin 角色码，简化为只检查 `!user.isOssUser()`。覆盖所有本地非 OSS 账户（`/bidAdmin`、`bid-TeamLeader` 等），解决 settings-permission-effect E2E 回归（本地注册用户访问 `/settings` 被 403 重定向到 `/login`）
- **E2E 角色码修复**：替换 9 个 spec 文件中已下线的 MANAGER/STAFF 角色码（`bid-document-quality-check.spec.js`、`closure-flow.spec.js`、`customer-opportunity-center.spec.js` 等）
- **closure-flow #3 修复**：改用 Fee API 触发保证金派生 + URL 直跳结项 tab
- **ProjectRequest 字段补齐**：cookie 域 + CSS selector 修复
- **BIDADMIN 大小写修复**：H13 Set-Cookie token 提取

### 2. PR !2177 CO-582 仓库合订本 Word 标题样式 + ZIP 下载 HTML 修复

- **Word 标题样式失效根治**：POI `new XWPFDocument()` 默认不生成 `styles.xml`，`p.setStyle` 找不到样式定义 → 导航窗格为空。新增 `WarehouseWordStyleRegistrar` 注册 Title/Heading1-3 四个样式（含 `qFormat` + `outlineLvl`）
- **ZIP 下载返回 HTML 修复**：`useAsyncTask.js` dev 模式下 `fetch` 未拼接 `API_BASE_URL` 导致下载到 HTML

### 3. PR !2179 §78 OSS 角色识别修复（覃超颖 403 案例根治）

- **OSS 角色识别**：区分投标系统角色与其他系统 admin
- **DataScopeConfigService 双数据源根治**：彻底修复覃超颖 403 案例
- **四条故障链修复**：补齐 §78 系列整改
- **OSS 端无属于本系统的 admin**：admin 是本地独有，修正 lessons-learned

### 4. 文档

- 第 13 次生产环境部署报告
- 第 105 次测试环境部署报告
- PR-2178 生产风险评审文档

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| Worktree | trae（主工作区） |
| 分支 | `agent/trae/fix-e2e-remaining-specs`（任务分支，HEAD = origin/main） |
| 同步后 HEAD | `0277ccb75`（同 `origin/main`） |
| GitHub 镜像同步前落后 | 14 commits |
| GitHub 镜像同步后 | ⚠️ 未同步（pre-push 门禁拦截，eslint 错误，待后续修复 lint 后同步） |
| 工作区状态 | 干净 |
| 本地门禁 | ✅ 7/7 通过（git wrapper 生效，pre-push-gate.sh 存在） |

## PR 列表（增量 16 commits 内）

| PR | 标题 | 类型 |
|---|---|---|
| !2176 | fix(auth): isLocalSystemAccount 覆盖所有本地非 OSS 账户 + E2E 角色码修复 | fix |
| !2177 | fix(warehouse): CO-582 §3.4 仓库合订本 Word 标题样式失效 + ZIP 下载返回 HTML | fix |
| !2179 | fix(role): §78 OSS 角色识别修复——区分投标系统角色与其他系统 admin | fix |
| - | docs(release): 第 13 次生产环境部署报告 (prod) | docs |
| - | docs(release): 第 105 次测试环境部署报告 (test) | docs |
| - | docs(reviews): PR-2178 生产风险评审 | docs |

## 改动范围

| 模块 | 文件 | 说明 |
|---|---|---|
| 后端认证 | `DataScopeConfigService.java` | `isLocalSystemAccount` 语义放宽 |
| 后端仓库 | `WarehouseWordStyleRegistrar.java`、`useAsyncTask.js` | CO-582 Word 标题样式 + ZIP 下载 |
| 后端安全 | §78 系列修复文件 | OSS 角色识别 + 双数据源根治 |
| 后端测试 | `TenderControllerPermissionTest.java`、`WarehouseWordBundleBuilderTest.java` | 权限与 Word bundle 测试补全 |
| 前端工具 | `useAsyncTask.js` | dev 模式 fetch 拼接 API_BASE_URL |
| E2E 测试 | 9 个 spec 文件 + `auth-helpers.js` | 角色码替换 + closure-flow #3 修复 |
| 文档 | `docs/release/deploy-report-*.md`、`docs/reviews/pr-2178-*.md`、`docs/lessons/lessons-learned.md` | 部署报告 + 风险评审 + 教训沉淀 |
| 配置 | `eslint.config.js`、`scripts/release/rehearsal-env.sh` | lint 配置 + 排练脚本微调 |

## Flyway 预检 3 步法

| 步骤 | 命令 | 结果 |
|---|---|---|
| Step 1: validate | `bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（236 migrations 对齐） |
| Step 2: DB 版本对比 | 查询 `flyway_schema_history` 最新 5 条 | ✅ 最新已应用 V1173（2026-07-20 20:19:05），与源码最新版本一致 |
| Step 3: remote-deploy 内置 | `remote-deploy.sh` 在激活新 jar 前跑 validate | ✅ `Successfully validated 236 migrations` |

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="0277ccb75" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

- 前端构建：8.84s，产物 `assets/index-CKWHQ5Ar.js`
- 后端打包：`mvn clean -DskipTests package` 27.692s，产物 `bid-platform-1.0.3.jar`
- 产物校验：
  - ✅ jar 内 Flyway 迁移版本无重复（235 files）
  - ✅ OBS 直传已启用（Detail chunk `.upload(` 调用数=2）
  - ✅ `release-metadata.json` 中 `obsEnabled=true`、`apiBaseUrl=""`（同源构建）
  - ✅ 前端产物不含 dev API 地址（`check:frontend-api-base` 通过）
- archive 大小：153M

### 2. 上传 + 部署

```bash
scp .release/xiyu-bid-release-0277ccb75.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-0277ccb75.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=0277ccb75 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="..." \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-0277ccb75-*.sql.gz`
- Flyway validate：236 migrations OK
- 后端重启：`xiyu-bid-backend.service` active/running（PID 12464）
- 健康检查：79 次尝试后连续 3/3 通过（Kafka SDK readiness 延迟，属已知行为）
- 前端一致性：`src="/assets/index-CKWHQ5Ar.js"` 与 release 一致

### 3. 前端资源保留（防止跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/ad9f2378c/frontend/assets/* \
  /srv/www/xiyu-bid/assets/ 2>/dev/null && echo "✅ 已从 ad9f2378c 保留旧 assets"'
```

> 注：`deployed-release.json` 已被本次部署覆盖，`releaseDir` 指向当前版本。直接从上一版本 release 目录 `ad9f2378c` 复制。

## 验证结果

### 后端 API Smoke

| 检查项 | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| health | `http://172.16.38.78:8080/actuator/health` | 200 UP | 200 UP（aiProvider: qwen3.7-max） | ✅ |
| readiness | `http://172.16.38.78:8080/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| auth/login | `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| projects | `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| CRM health | `GET /api/integration/crm/health` | 401 | 401 | ✅ |

### 前端页面

| 检查项 | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 首页 | `http://172.16.38.78:8080/` | 200 | 200 | ✅ |
| 登录页 | `http://172.16.38.78:8080/login` | 200 | 200 | ✅ |
| index.html 入口 | `assets/index-CKWHQ5Ar.js` | 与 release 一致 | 一致 | ✅ |

### 迁移应用验证

- 本次无新增迁移文件（`git diff --name-only ad9f2378c..0277ccb75 -- backend/src/main/resources/db/migration-mysql/` 无输出）
- DB 最新版本仍为 V1173，与源码一致，跳过迁移应用验证

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 同步前落后 | 14 commits |
| 同步尝试 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ⚠️ 失败（pre-push 门禁拦截，eslint 有 lint 错误） |
| 影响 | 不影响本次部署（部署已成功），仅 GitHub 镜像落后 |
| 后续 | 修复 eslint 错误后重新同步，或下次部署时一并同步 |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | ⚠️ 保留 | 历史决定保留（第 13/14/15 次部署用户决定），非临时调试配置 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他临时配置 | ✅ 无 | 仅 SHOW_DETAILS=always 一项 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚姿态 | 未需要 |
| 上一版本 Release ID | `ad9f2378c` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/ad9f2378c/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-0277ccb75-*.sql.gz` |
| 回滚命令 | `sudo systemctl stop xiyu-bid-backend && cp /opt/xiyu-bid/releases/ad9f2378c/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，validate + DB 版本对比 + remote-deploy 内置 |
| OBS 直传漏传 VITE_OBS_ENABLED=true（第 84 次 + 第 8 次生产事故） | ✅ 显式传入 `VITE_OBS_ENABLED=true`，产物校验 `.upload(` 调用数=2 |
| macOS `._*` 残留文件（第 10 次） | ✅ 打包时 `COPYFILE_DISABLE=1` |
| 同源构建 baseURL="" （第 3 次经验） | ✅ 显式 `VITE_API_BASE_URL=` 触发同源构建 |
| 前端 hash 资源跨版本 404（第 18 条经验） | ✅ 部署后从 ad9f2378c release 目录 `cp -rn` 保留旧 assets |
| Kafka SDK readiness 延迟（第 8/9/10/13/15 次） | ✅ 健康检查 79 次尝试后通过，属已知行为，未急于回滚 |
| SYSTEMCTL_SUDO=true（第 15 次 PR !1324） | ✅ 显式传入，避免 `Interactive authentication required` |
| 强制 `mvn clean`（V1096 jar 内重复事故） | ✅ package-release.sh 内置 `mvn clean -DskipTests package` |
| jar 内 Flyway 迁移版本无重复校验 | ✅ 打包时自动校验，235 files 无重复 |

## 风险提示

1. **GitHub 镜像落后 14 commits**：本次同步被 pre-push 门禁拦截（eslint 错误）。AI Coding 工具（Cursor/Codex/Claude 等）拉取 GitHub 镜像时会缺少最新代码。建议尽快修复 eslint 错误后同步。
2. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留**：测试环境保留此配置便于排障，但生产环境应评估是否需要收紧（当前生产也保留）。
3. **Kafka SDK readiness 延迟**：本次 79 次尝试（约 2 分 38 秒）才通过健康检查。已沉淀为已知行为，但若延迟持续增长需关注 `OrganizationEventSdkKafkaStarter` 是否需要改 `@Async`。

## 部署确认清单

- [x] 环境门禁确认（ENV=test, 172.16.38.78）
- [x] 早操三连（dev-env + sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = 0277ccb75）
- [x] 服务器现状检查（ad9f2378c, UP）
- [x] Flyway 预检 3 步（validate + DB 版本 + remote-deploy 内置）
- [x] 本地打包（前端 8.84s + 后端 27.692s）
- [x] 产物校验（jar 无重复 + OBS 启用 + 同源构建）
- [x] 上传 + 部署（remote-deploy.sh, SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（ad9f2378c 旧 assets cp -rn）
- [x] 健康检查（79 次尝试后 3/3 通过）
- [x] Smoke 测试（health + readiness + 400/403/401 + 前端 200）
- [x] 迁移应用验证（无新迁移，V1173 一致）
- [ ] GitHub 镜像同步（⚠️ eslint 门禁拦截，待后续修复）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 历史保留）
- [x] 部署报告生成

## 部署统计

- 测试环境累计部署次数：106 次
- 本月（2026-07）测试环境部署次数：8 次
- 上一次部署：第 105 次（2026-07-20 23:15, `ad9f2378c`）
