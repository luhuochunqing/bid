# 第 62 次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 62 次 |
| 部署环境 | **测试环境**（test） |
| 部署日期 | 2026-07-09 |
| Release ID | `d4a8e5ad7-api8080-obs` |
| 部署时间 | 2026-07-09 11:38:41 CST |
| 前置 Release | `c15b54834-api8080`（2026-07-09 02:44 UTC 激活） |
| 部署结果 | ✅ 成功（remote-deploy.sh 健康检查因 Kafka SDK 延迟超时，服务后续自恢复） |
| 新增 Flyway 迁移 | 无 |
| 回滚状态 | 未需回滚 |
| 部署性质 | 正常增量部署 + 前端 OBS 直传开关启用 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/drop-duplicate-roles-code-index`（HEAD = origin/main） |
| 部署 commit | `d4a8e5ad7`（!1921 紧急回退 audit-logs 鉴权 hasAuthority→hasAnyRole） |
| 前置 commit | `c15b54834` |
| 增量 commit 数 | 8 |
| GitHub 镜像 | 部署前落后 8 commit（待同步） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |
| 构建命令 | `RELEASE_ID="d4a8e5ad7-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh` |

## 本次部署重点：启用前端 OBS 直传

### 背景
- 第 61 次部署已完成后端 OBS 配置（XIYU_OBS_ENABLED=true, bucket=ehsy-widbid, AK/SK 已配）
- 但前端构建未启用 OBS 开关（VITE_OBS_ENABLED=false），导致前端走传统 multipart 上传
- 本次部署通过 `VITE_OBS_ENABLED=true` 构建前端，启用华为云 OBS 大文件直传

### OBS 配置现状（部署后）
| 配置项 | 值 | 位置 |
|---|---|---|
| `XIYU_OBS_ENABLED` | `true` | 服务器 `/etc/xiyu-bid/backend.env` |
| `XIYU_OBS_ENDPOINT` | `https://obs.cn-east-3.myhuaweicloud.com` | 服务器 `/etc/xiyu-bid/backend.env` |
| `XIYU_OBS_BUCKET` | `ehsy-widbid` | 服务器 `/etc/xiyu-bid/backend.env` |
| `XIYU_OBS_ACCESS_KEY` | `HPUAY6VTA7LGOI08PNYA` | 服务器 `/etc/xiyu-bid/backend.env` |
| `XIYU_OBS_AGENCY_NAME` | （空，使用 AK/SK 直传模式，用户明确决定） | 服务器 `/etc/xiyu-bid/backend.env` |
| `VITE_OBS_ENABLED` | `true`（构建时注入） | 前端产物（已编译为常量） |

### OBS 直传工作流
1. 前端 `isObsEnabled = import.meta.env.VITE_OBS_ENABLED === 'true'`（构建时常量，已替换为 `true`）
2. 用户上传大文件时，前端先调 `/api/files/upload-token` 获取 OBS 临时凭证
3. 前端直接 PUT 到 OBS，返回 `obs-direct:{uploadId}` 标识
4. 后端通过 `obs-direct:` 前缀识别直传文件，处理后续业务
5. 失败时自动回退到 multipart 上传（`callApiWithObsFallback` + 415 检测）

## PR 列表

本次部署涵盖 8 个增量 commit：

| Commit | PR | 描述 | 类型 |
|---|---|---|---|
| `d4a8e5ad7` | !1921 | fix(tender): 紧急回退 audit-logs 鉴权 hasAuthority→hasAnyRole，修复所有 OSS 用户标讯详情页 403 | fix(perm) |
| `81c670dff` | !1919 | fix(bidding): 标讯列表与项目列表导出支持选中数据 (CO-563) | fix |
| `6035ec0dd` | !1920 | fix(CO-546 v2): CA 到期预警定时扫描每日通知 + 接收人补齐 CA 保管员 | fix(alerts) |
| `89ab50ebc` | - | fix(CO-546 v3): Review 修复 — DedupPolicy 参数化 + 类型隔离 + 即时性恢复 | fix(alerts) |
| `49d5be88d` | - | fix(CO-546 v2): CA 到期预警定时扫描每日通知 + 接收人补齐 CA 保管员 | fix(alerts) |
| `bc66805e1` | - | fix(project): 导出 ids 用逗号串匹配后端 @RequestParam List (CO-563) | fix |
| `1ef8eb4af` | - | fix(bidding): 标讯列表与项目列表导出支持选中数据 (CO-563) | fix |
| `30d33ceb8` | - | fix(tender): 紧急回退 audit-logs 鉴权 hasAuthority→hasAnyRole | fix(perm) |

## 改动范围

- **数据库**：无 Flyway 迁移变更（DB 风险低）
- **后端**：
  - `alerts/` 模块：CO-546 CA 到期预警定时扫描每日通知 + 接收人补齐 CA 保管员（DedupPolicy 参数化）
  - `project/` 模块：CO-563 项目列表导出支持选中数据
  - `tender/` 模块：CO-563 标讯列表导出支持选中数据
  - `resources/` 模块：CaBorrowService / CaExpiryScanService 配套调整
  - **紧急修复**：audit-logs 鉴权 hasAuthority→hasAnyRole，修复所有 OSS 用户标讯详情页 403
- **前端**：
  - `src/views/Bidding/list/useTenderListPage.js`：标讯列表导出选中数据
  - `src/views/Project/List.vue`：项目列表导出选中数据
  - **OBS 直传开关启用**：`VITE_OBS_ENABLED=true` 构建注入，标讯上传/项目初始化/任务附件等大文件走 OBS 直传

## Flyway 预检结果

### Step 1: Flyway validate（部署前）
```
VALIDATE OK - all checksums match
Successfully validated 222 migrations (execution time 00:00.090s)
```

### Step 2: DB 已应用版本（部署前）
```
version  description                                  success  installed_on
1159     drop duplicate roles code index              1        2026-07-09 10:44:25
1158     cleanup duplicate roles add unique constraint 1        2026-07-09 10:44:25
1157     add unique index to warehouse name            1        2026-07-09 08:08:53
```

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

## 部署步骤

### 1. 环境门禁
- 用户声明：测试环境
- AI 确认：172.16.38.78（winbid-01.test）
- AskUserQuestion 确认：✅ 通过

### 2. 早操三连
- `source scripts/dev-env.sh` ✅
- `bash scripts/sync-env.sh .` ✅（rebase origin/main 成功，无冲突）
- `bash scripts/check-git-wrapper.sh` ✅（7/7 门禁就绪）

### 3. 本地打包
```bash
RELEASE_ID="d4a8e5ad7-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh
```
- 前端构建：✅ 9.72s，产物不含 dev API 地址
- 后端打包：✅ BUILD SUCCESS，25.544s（mvn clean -DskipTests package）
- jar 内迁移校验：✅ 无重复版本
- 产物校验：✅ 前端包含 `obs-direct:` 前缀和 ObsUploadProgress 组件
- Release archive：153MB

### 4. 上传 + 部署
```bash
COPYFILE_DISABLE=1 scp .release/xiyu-bid-release-d4a8e5ad7-api8080-obs.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```
- 上传：✅ 153MB
- Flyway validate：✅ 222 migrations validated
- 后端停止：✅
- 后端 artifact 更新：✅
- 后端启动：✅ PID 10209
- 健康检查：❌ 120 次尝试失败（readiness 延迟，见下方"问题与处理"）

## 验证结果

### 健康检查（自恢复后）
```
status: UP
  aiProvider: UP
  db: UP
  diskSpace: UP
  jwt: UP
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP
  sidecar: UP
```

### readiness/liveness
- readiness: HTTP 200 ✅
- liveness: HTTP 200 ✅

### API Smoke
| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| POST /api/auth/login（空密码） | 400 | 400 | ✅ |
| GET /api/projects（需认证） | 403 | 403 | ✅ |
| GET /api/integration/crm/health（需认证） | 401 | 401 | ✅ |

### 前端页面
| 路径 | 期望 | 实际 | 结果 |
|---|---|---|---|
| GET / | 200 | 200 | ✅ |
| GET /login | 200 | 200 | ✅ |
| 入口 JS | `assets/index-DUjOI1C_.js` | `assets/index-DUjOI1C_.js` | ✅ 一致 |

### OBS 配置验证
- 服务器 `XIYU_OBS_ENABLED=true` ✅
- 服务器 `XIYU_OBS_BUCKET=ehsy-widbid` ✅
- 服务器 `XIYU_OBS_ENDPOINT=https://obs.cn-east-3.myhuaweicloud.com` ✅
- 前端产物包含 `obs-direct:` 前缀 ✅
- 前端产物包含 ObsUploadProgress 组件 ✅

### deployed-release.json
```json
{
  "releaseId": "d4a8e5ad7-api8080-obs",
  "activatedAt": "2026-07-09T03:38:41Z",
  "releaseDir": "/opt/xiyu-bid/releases/d4a8e5ad7-api8080-obs",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "packageMetadata": {
    "releaseId": "d4a8e5ad7-api8080-obs",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-09T03:37:20Z",
    "sentryEnabled": false
  }
}
```

## 问题与处理

### 1. Kafka SDK readiness 延迟（已知行为）

**现象**：remote-deploy.sh 健康检查 120 次尝试（约 4 分钟）后失败，报 "Health check failed"。
但后端进程 PID 10209 一直存活，业务 API（`/api/notifications/unread-count`）返回 200，用户能正常登录。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`。
Kafka 初始化阻塞主线程导致 `AvailabilityChangeEvent` 处理延迟，readiness 长时间停留在 OUT_OF_SERVICE。

**处理**：等待自恢复。手动检查时第 1 次即 UP（`readinessState: UP`）。

**历史出现**：第 8、9、10、13、15、61、62 次均出现，已沉淀为已知行为，不必急于回滚。

**修复方向**：考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行。

### 2. macOS `._*` 残留文件（184 个）

**现象**：scp 从 macOS 传输到 Linux 服务器时生成 `._*` 元数据残留文件，共 184 个。

**处理**：`sudo find /srv/www/xiyu-bid/ -name "._*" -delete` 清理完毕。

**预防**：打包时已设置 `COPYFILE_DISABLE=1`，但 scp 阶段仍会生成。建议后续在 remote-deploy.sh 中增加清理步骤，或使用 rsync 替代 scp。

### 3. SHOW_DETAILS=always 保留（用户决定）

服务器 `/etc/xiyu-bid/backend.env` 中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留，这是用户在第 13、14、15 次部署时明确决定保留的配置，不影响功能。

## GitHub 镜像同步

- 部署前 GitHub 镜像落后 8 commit
- 待同步命令：`bash scripts/sync-to-github.sh`

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需回滚 |
| 上一版本 Release ID | `c15b54834-api8080` |
| 上一版本 jar 路径 | `/opt/xiyu-bid/releases/c15b54834-api8080/backend/app.jar` |
| 上一版本前端目录 | `/opt/xiyu-bid/releases/c15b54834-api8080/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-d4a8e5ad7-*.sql.gz` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/c15b54834-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验条目 | 本次应用 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 识别为已知行为，等待自恢复，未误判为 crash-loop |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，触发同源构建 |
| #4 Smoke 测试 admin 密码限制 | ✅ 用 400/403/401 替代完整登录 smoke |
| #6 临时调试配置清理 | ✅ 检查 SHOW_DETAILS/DEBUG/TRACE，SHOW_DETAILS=always 为用户决定保留 |
| #13 前端目录权限 | ✅ remote-deploy.sh 用 sudo 操作 /srv/www/xiyu-bid/ |
| #14 macOS ._* 残留 | ✅ 发现并清理 184 个残留文件（scp 阶段生成） |
| #15 Flyway 防护体系 | ✅ 3 步预检全部通过 |

## 风险提示

1. **OBS AK/SK 直传模式**：`XIYU_OBS_AGENCY_NAME` 为空，使用 AK/SK 直传模式（用户明确决定，无需配 IAM 委托）。
2. **GitHub 镜像落后 8 commit**：需执行 `bash scripts/sync-to-github.sh` 同步。
3. **readiness 延迟未根治**：Kafka SDK 时序竞争问题仍存在，每次部署都会有 2-5 分钟 readiness 503 窗口。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连完成
- [x] 基线确认（HEAD = origin/main = d4a8e5ad7）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（前端 + 后端）
- [x] jar 内迁移无重复
- [x] 前端产物校验（OBS 开关注入 + API base 同源）
- [x] 上传 + 部署成功
- [x] 健康检查 UP（9/9 组件）
- [x] API Smoke 通过（400/403/401）
- [x] 前端页面正常（200 + 入口 JS 一致）
- [x] OBS 配置验证（后端 + 前端开关）
- [x] deployed-release.json 已更新
- [x] macOS ._* 残留已清理
- [x] 临时调试配置检查（SHOW_DETAILS=always 用户保留）
- [ ] GitHub 镜像同步（待执行）
- [x] 部署报告生成
