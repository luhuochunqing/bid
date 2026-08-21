# 第 139 次测试环境部署报告 — 2026-08-21

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | AI 案例推荐数据源修复（score_item 新契约 + 二次解包）+ 重复造轮子门禁 + 138 期报告归档 |
| 部署序号 | 第 139 次（测试） |
| 部署时间 | 2026-08-21 13:39 CST（backend 激活，activatedAt=2026-08-21T05:39:49Z） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `c14ccbf7f` |
| 上一版本 Release | `849dfb281`（2026-08-21 08:17 CST 激活） |
| 基线 commit | `c14ccbf7f`（= origin/main，本次打包源码） |
| 健康检查 | ⚠️ remote-deploy 窗口内未通过（120 次 × 2s），**根因为服务器内存危机**（见下），外部验证后恢复 ✅ |
| 部署结果 | ✅ 成功（含一次 sidecar OOM 应急处置） |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **无**（增量 commit 不含 `migration-mysql/` 改动） |

## 变更内容（10 个 commit）

### AI 案例推荐数据源修复（PR !2370）
- `34fbd064f` 项目详情案例推荐抽屉切换到 score_item 新契约
- `93c15a2be` 修复 score-items/cases-recommend 裸响应被二次解包导致数据丢失
- `fb5663d9d` getCaseDetail/reuseCase 同款二次解包修复 + E2E 断言时序加固
- `b7f996328` PRD 4.2 复用定位弹窗默认选中当前评分项对应章节
- `159ae6849` wiki 知识回填（score_item 契约 + 裸响应陷阱）

### 工程门禁
- `65adcccb4` 本地 PR 门禁拦截重复造轮子（PR !2369）

### 文档归档
- `7c63cca0f` 第 138 次测试环境部署报告（随 PR !2368 合入）

## ⚠️ 特殊事件：sidecar 内存膨胀 → 后端 JVM native OOM（一次 crash，已恢复）

### 时间线（CST）

| 时间 | 事件 |
|---|---|
| 13:39:26 | remote-deploy 开始，DB 备份完成（11M） |
| 13:39:49 | 新 jar 激活，服务重启 |
| 13:42:10 | **第一实例 crash**：JVM native 内存分配失败（`mmap failed to map 1332740096 bytes, errno=12 Not enough space`） |
| 13:42:15 | systemd `Restart=on-failure` 自动拉起第二实例 |
| 13:42:39 | 第二实例启动成功（PID 32703） |
| 13:43+ | remote-deploy 健康检查 120 次探测窗口耗尽报失败；外部探测显示 readiness=OUT_OF_SERVICE、其余组件全 UP |
| 13:45+ | 排查定位 sidecar 内存膨胀并重启容器；readiness 自恢复 UP（Kafka SDK 延迟，已知行为） |

### 根因

- 服务器总内存仅 **7.5GB、无 swap**，部署时可用内存只剩 **800MB**。
- **文档转换 sidecar**（Docker 容器 `a2e43f7fe7e1`，镜像 `document-converter-sidecar:latest`，uvicorn :8000）运行约 20 分钟即膨胀至 **4.7GB（61.5%）**，挤占系统内存。
- 后端 JVM 启动期 native 内存分配（Metaspace/CodeCache mmap）失败 → 第一实例 crash；systemd 自动重启的第二实例在内存压力下仍完成启动，但 readiness 因 Kafka SDK 初始化延迟短暂 OUT_OF_SERVICE（技能已知行为 #2，2-5 分钟自恢复）。

### 应急处置

- 确认 sidecar **无状态**（转换任务不落盘、后端对其有优雅降级，WARN 日志已证实）后，重启 Docker 容器。
- 结果：可用内存 **800MB → 5350MB**，sidecar 健康 `{"status":"up"}`，后端 readiness 恢复 UP。
- 未回滚、未重发，部署继续有效。

## 验证结果

### Flyway

| 检查项 | 结果 |
|---|---|
| validate（flyway-repair-runner） | ✅ VALIDATE OK（260 迁移 checksum 全匹配） |
| DB 已应用最新版本 | ✅ V1199 |
| 本地源码最新版本 | ✅ V1199（一致，无新增迁移） |
| 失败迁移记录 | ✅ 无（`success=0` 为空） |

### 部署后 Smoke（经 Nginx 8080）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP |
| `/actuator/health/readiness` | ✅ 200（恢复后） |
| deployed-release.json | ✅ `c14ccbf7f` |
| OBS 直传启用 | ✅ `.upload(` 调用数=2 |
| 前端入口与 release 一致 | ✅ `assets/index-DkA4SwVN.js` |
| `POST /api/auth/login`（空密码） | ✅ 400（路由正常） |
| `GET /api/projects`（未认证） | ✅ 403（权限守卫） |
| `GET /api/integration/crm/health`（未认证） | ✅ 401 |
| 前端根路径 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| DB 备份 | ✅ `winbid-c14ccbf7f-20260821133926.sql.gz`（11M） |

## 部署步骤

1. 环境门禁确认：测试环境 172.16.38.78（用户通过 AskUserQuestion 确认）
2. 早操三连：dev-env + sync-env + check-git-wrapper ✅
3. 基线确认：HEAD=c14ccbf7f（= origin/main），服务器现状（849dfb281，健康 UP）
4. Flyway 预检 3 步：validate OK + DB 版本 V1199 + 增量迁移变更=空
5. 本地打包：`RELEASE_ID=c14ccbf7f VITE_API_BASE_URL= VITE_OBS_ENABLED=true`（同源构建 + OBS 直传）
6. 产物校验：obsEnabled=true + apiBaseUrl="" + jar 内迁移无重复（至 V1199）+ 包体 161M
7. scp 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true + DB_BACKUP_COMMAND）
8. remote-deploy：Flyway validate → 备份 → 停服 → 换 jar → 重启 → ⚠️ 健康检查窗口内未通过（见特殊事件）
9. 应急处置：journalctl 定位 native OOM → free/ps 定位 sidecar 膨胀 → 重启 sidecar 容器 → 内存恢复
10. 保留上一版本 assets（`cp -rn` 自 849dfb281，防止跨版本 404）+ 清理 macOS `._*` 污染
11. 部署后验证：Flyway 无 failed + Smoke 全绿
12. GitHub 镜像同步（两边 main 一致）

## GitHub 镜像状态

- ✅ 已同步：`sync-to-github.sh` 执行成功，Gitee main = GitHub main = `c14ccbf7f`，完全一致。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：**保留**（技能明确记录"3 次决定保留"项，非临时 DEBUG/TRACE）。
- 无 `DEBUG` / `TRACE` 临时配置残留。

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/849dfb281/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = c14ccbf7f）
- [x] Flyway 预检 3 步（validate + DB 对比 + 增量迁移=空）
- [x] 本地打包 + 产物校验（同源 API + OBS + 前端入口）
- [x] backend 部署（含 sidecar OOM 应急处置与恢复）
- [x] 保留上一版本 assets（防止跨版本 404）
- [x] API Smoke + 前端验证（全部通过）
- [x] OBS 直传启用校验（.upload( 数=2）
- [x] 配置清理检查（SHOW_DETAILS=always 保留）
- [x] GitHub 镜像同步（两边 main 一致）

## 待办

- [ ] **sidecar 长期治理（高优先）**：为 `document-converter-sidecar` Docker 容器增加内存限制（如 `--memory=1g --memory-swap=1g`）或排查修复其内存泄漏；当前 7.5GB 无 swap 的主机在 sidecar 膨胀时会再次挤爆后端 JVM
- [ ] 复测 AI 案例推荐：项目详情案例推荐抽屉（score_item 新契约）+ 复用定位弹窗默认选中当前评分项对应章节
- [ ] 本报告提交 PR 合入 main
