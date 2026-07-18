# 第 97 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `1e0f04812-api8080` |
| 上一版本 | `883cade40-api8080`（第 96 次，2026-07-17 23:00） |
| 部署时间 | 2026-07-18 09:05 CST |
| 增量 | 8 commit（!2124 下载导出包 OOM 根治——Files.readAllBytes 改为流式输出 + docs 类 commit） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（健康检查因 Kafka readiness 延迟失败 120 次，自恢复后 readiness 4 秒切换 UP） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用第 96 次配置，MaxHeapSize=2GB 生效 |

## 部署原因

第 96 次部署修复了 Word 合订本**导出** OOM（buildBundle 流式写入），但**下载导出包**路径仍存在 OOM 风险——使用 `Files.readAllBytes` 一次性加载整个导出包到内存。本次部署修复下载路径的 OOM 根因。

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-97th-test（基于 origin/main） |
| HEAD commit | `1e0f04812` |
| origin/main | `1e0f04812`（完全一致） |
| GitHub 镜像 | ✅ 完全一致（1e0f04812） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2124 | fix(warehouse) | 下载导出包 OOM 根治——Files.readAllBytes 改为流式输出 |
| !2123 | docs(release) | 第 96 次测试环境部署报告 |
| !2122 | docs(playbook) | 新增 07 迁移指南——经验复用四层模型与 Day-0 拷贝清单 |
| !2121 | docs(playbook) | 新增 AI Coding 经验手册——2059 个 PR 的根因模式/协作/透传/门禁/回退纪律沉淀 |

## 改动范围

### 1. 下载导出包 OOM 根治（!2124，核心代码修复）
- **Files.readAllBytes 改为流式输出**：避免一次性加载整个导出包到内存，从代码层面根治下载路径 OOM 问题
- 与第 96 次的 buildBundle 流式写入修复形成完整闭环：导出（生成）+ 下载（读取）两条路径均流式化

### 2. 文档归档（!2121、!2122、!2123）
- AI Coding 经验手册（playbook 01-07）合入 main
- 第 96 次测试环境部署报告合入 main

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 vs 源码版本 | ✅ 一致（V1168，无新增迁移） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

## 部署步骤

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 早操三连（sync-env + dev-env + check-git-wrapper） | ✅ 门禁全部就绪 |
| 2 | 创建任务分支 agent/trae/deploy-97th-test | ✅ 基于 origin/main |
| 3 | 服务器现状检查 | ✅ 上一版本 883cade40，UP |
| 4 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 5 | 本地打包（RELEASE_ID=1e0f04812-api8080, OBS=true, 同源构建） | ✅ BUILD SUCCESS |
| 6 | 产物校验（obsEnabled=true, 230 迁移文件无重复） | ✅ 全部通过 |
| 7 | scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功 |
| 8 | 前端资源保留（从 883cade40-api8080 cp -rn 旧 assets） | ✅ 已保留 |
| 9 | macOS ._* 残留清理 | ✅ find -delete 完成 |

## 验证结果

### JVM 堆配置验证

| 检查项 | 部署前 | 部署后 | 结果 |
|---|---|---|---|
| MaxHeapSize | 2147483648 (2GB) | 2147483648 (2GB) | ✅ 沿用第 96 次配置 |
| InitialHeapSize | 125829120 (≈120MB) | 125829120 (≈120MB) | - (未指定 -Xms) |
| 进程命令行 | 包含 `-Xmx2g` | 包含 `-Xmx2g` | ✅ 确认 |

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP |
| /actuator/health/readiness | 200 UP |
| 健康检查通过 | remote-deploy.sh 内置 120 次重试失败（Kafka readiness 延迟），手动检查后 4 秒切换 UP |
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
| 前端入口 JS | index-lq6saM7R.js | index-lq6saM7R.js | ✅ |
| 业务接口（/api/notifications/unread-count） | 200 | 200（用户 06234 已登录） | ✅ |

### GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | 1e0f04812 |
| GitHub main | 1e0f04812 |
| 状态 | ✅ 完全一致 |
| 推送 commit | ab888f040..1e0f04812（4 个 commit） |

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
| 上一版本 release | `/opt/xiyu-bid/releases/883cade40-api8080/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/883cade40-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-1e0f04812-api8080-*.sql.gz` |
| 回滚命令（代码） | `cp /opt/xiyu-bid/releases/883cade40-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，自恢复后 readiness 4 秒切换 UP |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true + 产物校验 obsEnabled=true |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 + find -delete |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从 883cade40-api8080 cp -rn 旧 assets |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次 120 次重试（4 分钟）未通过，但实际 4 秒后自恢复。建议后续考虑：
   - 延长 remote-deploy.sh 健康检查重试次数到 180 次（6 分钟）
   - 或将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 避免阻塞主线程
2. **OOM 修复闭环**：本次修复后，Word 合订本导出（生成）+ 下载（读取）两条路径均已完成流式化改造，OOM 风险从代码层面根治。配合第 96 次的 `-Xmx2g` JVM 配置，形成双重兜底。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过（230 迁移文件无重复）
- [x] 远程部署成功
- [x] JVM MaxHeapSize=2g 沿用生效
- [x] 健康检查 UP（readiness 自恢复）
- [x] Smoke 测试全通过
- [x] 前端资源保留完成
- [x] GitHub 镜像同步一致
- [x] 配置清理检查（仅 SHOW_DETAILS=always 和 JAVA_OPTS=-Xmx2g 保留）
- [x] 部署报告已生成
