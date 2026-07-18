# 第 98 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `6d95c5946` |
| 上一版本 | `1e0f04812-api8080`（第 97 次，2026-07-18 09:05） |
| 部署时间 | 2026-07-18 12:57 CST |
| 增量 | 14 commit（warehouse 下载导出包前端超时根治——axios blob 改为浏览器原生导航下载 + 文档归档） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（健康检查 80 次尝试 / 3 次连续成功，约 2 分 40 秒，Kafka SDK readiness 延迟已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用第 97 次配置 |

## 部署原因

第 97 次部署修复了下载导出包 OOM 根因（Files.readAllBytes 改为流式输出），但**前端下载导出包仍存在超时问题**——axios blob 下载方式在大文件场景下超时。本次部署改用浏览器原生导航下载（window.location.href），彻底绕过 axios 超时限制。

同时合入最近一周的知识归档文档（lessons-learned §62/§63/§64/§65/§70/§71 + AI Coding playbook）。

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae-init（锚点分支，部署不创建任务分支） |
| HEAD commit | `6d95c5946` |
| origin/main | `6d95c5946`（完全一致） |
| GitHub 镜像 | ✅ 完全一致（github/main..origin/main = 0） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2131 | docs(lessons) | 每周知识归档 2026-07-18 — 新增 §63/§64/§65 + shell-gotchas §2 |
| !2132 | docs(lessons) | 2026-W29 每周知识归档（4 条新增） |
| (无 PR) | docs(AGENTS) | 优化"收尾"暗号展开为五步流程 + 文件树概览补全 (#2128) |
| !2127 | docs(lessons) | 每周知识归档 §62 — 错误消息引导必须可执行 |
| !2125 | docs(release) | 第 97 次测试环境部署报告 |
| !2126 | fix(warehouse) | 下载导出包前端超时——axios blob 改为浏览器原生导航下载 |

## 改动范围

### 1. 下载导出包前端超时根治（!2126，核心代码修复）
- **axios blob 改为浏览器原生导航下载**：使用 `window.location.href` 直接导航到下载 URL，绕过 axios 的超时限制
- **handleDownload bug 修复**：误把 `summary.fileName` 当 task id 调用 `downloadFile`
- **代码审查全修复**：5 Major + 3 Minor 问题全部修复
- **进度条增强（feat）**：fetch + ReadableStream 流式下载，支持实时进度显示

### 2. 文档归档（!2125、!2127、!2131、!2132 + 多个 docs commit）
- 第 97 次测试环境部署报告合入 main
- lessons-learned 新增 §62/§63/§64/§65/§70/§71
- AI Coding playbook 经验手册归档
- AGENTS.md "收尾"暗号展开为五步流程

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 vs 源码版本 | ✅ 一致（V1168，无新增迁移） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（231 migrations validated） |

## 部署步骤

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 早操三连（sync-env + git wrapper 检查） | ✅ 门禁全部就绪 |
| 2 | 基线确认（HEAD=origin/main=6d95c5946，GitHub 同步） | ✅ 干净 |
| 3 | 服务器现状检查 | ✅ 上一版本 1e0f04812-api8080，UP（含 readiness UP） |
| 4 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 5 | 本地打包（RELEASE_ID=6d95c5946, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1） | ✅ BUILD SUCCESS（前端 8.96s + 后端 27.75s） |
| 6 | 产物校验（obsEnabled=true, 230 迁移文件无重复, Detail .upload=2） | ✅ 全部通过 |
| 7 | scp 上传（154M archive + remote-deploy.sh） | ✅ 完成 |
| 8 | remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功 |
| 9 | 前端资源保留（从 1e0f04812-api8080 cp -rn 旧 assets） | ✅ 178 → 255 文件 |

## 验证结果

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP |
| /actuator/health/readiness | 200 UP |
| 健康检查通过 | remote-deploy.sh 内置 80 次尝试 / 3 次连续成功（约 2 分 40 秒） |
| 所有组件状态 | db UP, redis UP, aiProvider UP, diskSpace UP, jwt UP, ping UP, livenessState UP, readinessState UP, sidecar UP |

### Smoke 测试

| 测试项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 UP | 200 UP | ✅ |
| /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| /api/auth/login（空密码） | 400 | 400 参数校验失败 | ✅ |
| /api/projects（需认证） | 403 | 403 | ✅ |
| /api/integration/crm/health（需认证） | 401 | 401 | ✅ |
| 前端首页 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口 JS | index-C3Ta5kd1.js | index-C3Ta5kd1.js | ✅ |

### GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | 6d95c5946 |
| GitHub main | 6d95c5946（同步，0 落后） |
| 状态 | ✅ 完全一致 |

### 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always | 保留 | 用户已决定保留（第 13/14/15 次延续） |
| JAVA_OPTS=-Xmx2g | 保留 | 第 96 次添加，本次沿用 |
| DEBUG / TRACE 临时配置 | 无 | ✅ 干净 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/1e0f04812-api8080/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/1e0f04812-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-6d95c5946-*.sql.gz` |
| 回滚命令（代码） | `cp /opt/xiyu-bid/releases/1e0f04812-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，80 次重试（2 分 40 秒）后通过 |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true + 产物校验 obsEnabled=true |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从 1e0f04812-api8080 cp -rn 旧 assets（178 → 255 文件） |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次 80 次重试（2 分 40 秒）通过，比第 97 次（120 次失败后自恢复）有所改善，但仍是已知行为。后续可考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 避免阻塞主线程。
2. **下载导出包前端超时修复闭环**：本次修复后，下载导出包路径从前端 axios blob 改为浏览器原生导航 + fetch ReadableStream 进度条，彻底绕过 axios 超时限制。
3. **前端资源保留脚本缺陷**：`deployed-release.json` 已被本次部署覆盖导致 `PREV` 变量取值失效，需手动从 `releases/` 目录定位上一版本（本次为 `1e0f04812-api8080`）。已记录在 skill 教训 #18。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过（230 迁移文件无重复，Detail .upload=2）
- [x] 远程部署成功
- [x] JVM MaxHeapSize=2g 沿用生效
- [x] 健康检查 UP（80 次重试通过）
- [x] Smoke 测试全通过（8/8）
- [x] 前端资源保留完成（178 → 255 文件）
- [x] GitHub 镜像同步一致（0 落后）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 和 JAVA_OPTS=-Xmx2g 保留）
- [x] 部署报告已生成
