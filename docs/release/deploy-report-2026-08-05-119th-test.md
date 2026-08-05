# 第 119 次测试环境部署报告 — 2026-08-05

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 119 次（测试） |
| 部署时间 | 2026-08-05 18:28:26 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `373cb3625` |
| 上一版本 Release | `223f2a8ef`（2026-08-04 14:53:06 CST，第 118 次测试部署） |
| 基线 commit | `373cb3625`（origin/main） |
| 激活时间 | 2026-08-05T18:28:26 CST |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 9 个 |
| 新增 Flyway 迁移 | 0 个 |
| Smoke 测试 | 8 项全部通过 |
| GitHub 镜像 | ⚠️ 落后 7 个 commit（需在主工作区 trae 同步） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/claude`
- 锚点分支：`agent/claude-init`
- 工作区状态：干净，HEAD = origin/main = `373cb3625`
- 早操三连：sync-env.sh 同步通过，本地门禁 7/7 通过
- GitHub 镜像状态：落后 Gitee 7 个 commit（含 CA 接口 + CO-605 等），需在主工作区 trae 执行 `bash scripts/sync-to-github.sh`

## 增量改动（223f2a8ef → 373cb3625，9 个 commit）

### 关键 PR 列表

| PR | 描述 | 关联 |
|---|---|---|
| !2272 | feat(integration): 新增 CA 证书对外查询接口 + Postman 测试集合 | CA 对外 API |
| !2269 | perf(settings): CO-605 设置页加载优化 — data-scope 去冗余 + endpoints ETag 缓存 + 通知轮询去重 | CO-605 |
| !2271 | fix: 验证脚本适配实际 API 响应格式 | 测试脚本 |
| !2267 | fix(notification): 通知接收人解析排除 admin 超级管理员 | Sentry XIYU-F |
| !2270 | docs(release): 第 16 次生产环境部署报告 | 文档 |
| - | chore(locks): prune stale expired locks | GitHub Actions 自动清理 |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/integration/external | 新增 CaIntegrationController（CA 对外查询接口） |
| backend/admin/permissions | CO-605 ETag 缓存 + PostConstruct 初始化 |
| backend/admin/service | CO-605 DataScopeConfigAssembler 去冗余字段 |
| backend/notification | 接收人解析排除 admin |
| src/composables | useNotifications 多实例轮询去重 |
| postman/ | CA 证书对外 API 测试集合 |
| docs/lessons/ | 第 108 条教训（@RestControllerAdvice basePackages 作用域陷阱） |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（245 migrations） |
| Step 2: DB 最近迁移版本 | V1184（create performance export task，2026-08-04） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

无新增 Flyway 迁移文件。

## 部署步骤

1. ✅ 本地打包（RELEASE_ID=373cb3625, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
2. ✅ 产物校验（jar 内迁移无重复，OBS 直传已启用 .upload( 调用数=2）
3. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`
4. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
5. ✅ Flyway validate 通过
6. ✅ 后端服务重启（active/running）
7. ✅ 健康检查通过（consecutive 3/3, total attempts: 79）
8. ✅ 前端一致性验证（assets/index-BpXD1z1-.js）

## 验证结果

### Smoke 测试（8 项全通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | GET /api/integration/ca-certificates（无 API Key） | ✅ HTTP 401 | 需 API Key |
| 6 | 前端首页 | ✅ HTTP 200 | — |
| 7 | 前端 /login | ✅ HTTP 200 | — |
| 8 | 前端 assets hash | ✅ assets/index-BpXD1z1-.js | 与 release 一致 |

### CA 对外接口专项验证（6 项全通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | CA 列表查询 | ✅ HTTP 200 | 返回 22 条记录，密码字段脱敏 |
| 2 | CA 统计概览 | ✅ HTTP 200 | total=22, expiring=2, expired=14, borrowed=1 |
| 3 | CA 详情（id=1） | ✅ HTTP 200 | 返回完整 CA 证书信息 |
| 4 | CA 详情（id=999 不存在） | ✅ HTTP 404 | {"success":false,"code":404,"msg":"CA证书不存在: 999"} |
| 5 | 错误 API Key | ✅ HTTP 401 | Invalid or expired API Key |
| 6 | 无 ca:read scope 的 Key | ✅ HTTP 401 | Invalid or expired API Key |

### 测试用 API Key

| 字段 | 值 |
|---|---|
| id | 16 |
| name | CA Integration Test |
| scopes | ca:read |
| status | ACTIVE |
| expires_at | 2027-12-31T23:59:59 |

## GitHub 同步

- 当前状态：GitHub 镜像落后 Gitee 7 个 commit
- 原因：本次部署在 claude worktree 完成，非主工作区 trae
- 处理方式：需在主工作区 trae 执行 `bash scripts/sync-to-github.sh`

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `223f2a8ef` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/223f2a8ef` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-373cb3625-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/223f2a8ef/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 健康检查容忍 Kafka 延迟（经验 #2）

## 风险提示

1. **GitHub 镜像落后**：需在主工作区 trae 执行 sync-to-github.sh
2. **测试用 API Key**：id=16 的 `CA Integration Test` Key 仅用于测试验证，生产环境需单独创建
3. **CO-605 ETag 缓存**：max-age=1h，权限变更后最长 1 小时生效

## 部署确认清单

- [x] 环境门禁确认（测试环境）
- [x] 早操三连通过
- [x] Flyway 预检通过
- [x] 打包产物校验通过
- [x] 部署成功
- [x] 健康检查通过
- [x] Smoke 测试通过
- [x] CA 接口专项验证通过
- [ ] GitHub 镜像同步（待主工作区执行）
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-05 | 首次创建，基于第 119 次测试环境部署结果 |
