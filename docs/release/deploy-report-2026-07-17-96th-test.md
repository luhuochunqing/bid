# 第 96 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `883cade40-api8080` |
| 上一版本 | `c7aed82c1-api8080`（第 95 次，2026-07-17 21:41） |
| 部署时间 | 2026-07-17 23:00 CST |
| 增量 | 4 commit（!2120 Word 合订本导出 OOM 根治——buildBundle 改为流式写入 + !2119 第 95 次部署报告） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（健康检查因 Kafka readiness 延迟失败 120 次，自恢复后 readiness 2 秒切换 UP） |
| 回滚 | 未需要 |
| 配置变更 | ✅ backend.env 新增 `JAVA_OPTS=-Xmx2g`，systemd ExecStart 引用 `${JAVA_OPTS}` |

## 部署原因

第 95 次部署后，Word 合订本导出功能存在 OOM 风险。本次部署从两个层面修复：

1. **代码层面**（PR !2120）：`buildBundle` 方法改为流式写入，避免一次性加载所有内容到内存
2. **JVM 层面**（本次部署配置变更）：在 `/etc/xiyu-bid/backend.env` 新增 `JAVA_OPTS=-Xmx2g`，并将 systemd 服务文件 ExecStart 改为引用 `${JAVA_OPTS}`，将 JVM MaxHeapSize 从默认值 1.85GB 提升到 2GB

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-94th-test-java-opts（基于 origin/main） |
| HEAD commit | `883cade40` |
| origin/main | `ab888f040`（多出 2 个 docs commit，仅文档不影响代码） |
| GitHub 镜像 | ✅ 完全一致（ab888f040） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2120 | fix(warehouse) | Word 合订本导出 OOM 根治——buildBundle 改为流式写入 |
| !2119 | docs(release) | 第 95 次测试环境部署报告 |

## 改动范围

### 1. Word 合订本导出 OOM 根治（!2120）
- **buildBundle 改为流式写入**：避免一次性加载所有内容到内存，从代码层面根治 OOM 问题

### 2. JVM 堆内存配置变更（本次部署运维操作）
- **`/etc/xiyu-bid/backend.env`** 新增：
  ```
  # JVM heap limit (added 2026-07-17 96th deploy)
  JAVA_OPTS=-Xmx2g
  ```
- **`/etc/systemd/system/xiyu-bid-backend.service`** ExecStart 修改：
  - 原始：`ExecStart=...java -Djava.awt.headless=true ... -jar /opt/xiyu-bid/shared/backend/app.jar`
  - 修改后：`ExecStart=...java -Djava.awt.headless=true ... ${JAVA_OPTS} -jar /opt/xiyu-bid/shared/backend/app.jar`
- **systemctl daemon-reload** 完成
- 配置变更已备份：`backend.env.bak.<timestamp>` 和 `xiyu-bid-backend.service.bak.<timestamp>`

### 3. 部署报告归档（!2119）
- 第 95 次测试环境部署报告合入 main

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
| 2 | 创建任务分支 agent/trae/deploy-94th-test-java-opts | ✅ 基于 origin/main |
| 3 | 服务器现状检查 | ✅ 上一版本 c7aed82c1，UP |
| 4 | backend.env 新增 JAVA_OPTS=-Xmx2g + systemd 服务文件修改 | ✅ 完成，daemon-reload 成功 |
| 5 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 6 | 本地打包（RELEASE_ID=883cade40-api8080, OBS=true, 同源构建） | ✅ BUILD SUCCESS |
| 7 | 产物校验（obsEnabled=true, 230 迁移文件无重复） | ✅ 全部通过 |
| 8 | scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功 |
| 9 | 前端资源保留（从 c7aed82c1-api8080 cp -rn 旧 assets） | ✅ 已保留 |
| 10 | macOS ._* 残留清理 | ✅ find -delete 完成 |

## 验证结果

### JVM 堆配置验证

| 检查项 | 部署前 | 部署后 | 结果 |
|---|---|---|---|
| MaxHeapSize | 1988100096 (≈1.85GB, JVM 默认) | 2147483648 (2GB, -Xmx2g) | ✅ 生效 |
| InitialHeapSize | 125829120 (≈120MB) | 125829120 (≈120MB) | - (未指定 -Xms) |
| 进程命令行 | 无 -Xmx 参数 | 包含 `-Xmx2g` | ✅ 确认 |

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP |
| /actuator/health/readiness | 200 UP |
| 健康检查通过 | remote-deploy.sh 内置 120 次重试失败（Kafka readiness 延迟），手动检查后 2 秒切换 UP |
| 所有组件状态 | db UP, redis UP, aiProvider UP, diskSpace UP, jwt UP, ping UP, livenessState UP, readinessState UP, sidecar UP |

### Smoke 测试

| 测试项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 UP | 200 UP | ✅ |
| /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| /api/auth/login（空密码） | 400 | 400 参数校验失败 | ✅ |
| /api/projects（需认证） | 403 | 403 | ✅ |
| 前端首页 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口 JS | index-lq6saM7R.js | index-lq6saM7R.js | ✅ |
| 业务接口（/api/notifications/unread-count） | 200 | 200（用户 06234 已登录） | ✅ |

### GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | ab888f040 |
| GitHub main | ab888f040 |
| 状态 | ✅ 完全一致 |
| 推送 commit | c7aed82c1..ab888f040（4 个 commit） |

### 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always | 保留 | 用户已决定保留（第 13/14/15 次延续） |
| DEBUG / TRACE 临时配置 | 无 | ✅ 干净 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/c7aed82c1-api8080/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/c7aed82c1-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-883cade40-api8080-*.sql.gz` |
| 回滚命令（代码） | `cp /opt/xiyu-bid/releases/c7aed82c1-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| 回滚命令（JAVA_OPTS） | `sudo sed -i '/^JAVA_OPTS=/d' /etc/xiyu-bid/backend.env && sudo systemctl daemon-reload && sudo systemctl restart xiyu-bid-backend` |
| 配置备份 | `backend.env.bak.<timestamp>` 和 `xiyu-bid-backend.service.bak.<timestamp>` 在服务器上 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，自恢复后 readiness 2 秒切换 UP |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true + 产物校验 obsEnabled=true |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 + find -delete |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从 c7aed82c1-api8080 cp -rn 旧 assets |
| #15 前端 hash 资源跨版本 404（PREV 变量失效） | ⚠️ deployed-release.json 已被覆盖导致 PREV 取值为空，手动从 releases 目录查找上一版本 |

## 风险提示

1. **JVM 堆内存设置**：服务器总内存 7.4G，-Xmx2g 占用约 27%。当前可用内存 4.7G，余量充足。但 Word 合订本导出等大内存操作仍需关注，代码层面已通过流式写入根治。
2. **Kafka SDK readiness 延迟**：本次 120 次重试（4 分钟）未通过，但实际 2 秒后自恢复。建议后续考虑：
   - 延长 remote-deploy.sh 健康检查重试次数到 180 次（6 分钟）
   - 或将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 避免阻塞主线程
3. **PREV 变量失效问题**：remote-deploy.sh 覆盖 deployed-release.json 后无法读取上一版本 releaseDir，需手动从 releases 目录查找。建议后续修复脚本逻辑。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] backend.env 新增 JAVA_OPTS=-Xmx2g
- [x] systemd 服务文件修改引用 ${JAVA_OPTS}
- [x] systemctl daemon-reload 完成
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过（230 迁移文件无重复）
- [x] 远程部署成功
- [x] JVM MaxHeapSize=2g 生效验证
- [x] 健康检查 UP（readiness 自恢复）
- [x] Smoke 测试全通过
- [x] 前端资源保留完成
- [x] GitHub 镜像同步一致
- [x] 配置清理检查（仅 SHOW_DETAILS=always 保留）
- [x] 部署报告已生成
