# 第 76 次测试环境部署报告

## 部署环境

- **环境**：test（测试环境）
- **服务器**：winbid-01（172.16.38.78）
- **部署次数**：第 76 次（测试环境）

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `c7496cdcd-api8080` |
| 基线 commit | `c7496cdcd631a1b0bba8f91aec4af8ab248969a2` |
| 上一版本 | `d673fced0-api8080`（2026-07-10 15:16:44 UTC） |
| 部署时间 | 2026-07-10 23:53:40 CST |
| 健康检查通过 | 2026-07-10 23:58 CST（Kafka readiness 延迟约 4 分钟） |
| 新增迁移 | 无 |
| 增量 commit | 8 个 |
| 增量 PR | 1 个（!2008） |

## 基线信息

- **分支**：`agent/trae-init`（锚点分支，ff-only 同步到 origin/main）
- **HEAD**：`c7496cdcd` — !2008 fix(webhook): CRM 回调用标讯创建者而非 API Key 创建者反查 CRM token
- **GitHub 镜像**：已同步，两边 main 完全一致

## PR 列表

| PR | 标题 | 改动 |
|---|---|---|
| !2008 | fix(webhook): CRM 回调用标讯创建者而非 API Key 创建者反查 CRM token | TenderIntegrationCommandService.java |

## 改动范围

### 核心修复

**根因**：CRM 通过 API Key 认证调用 `PUT /api/integration/tenders/_/_` 时，`resolveApiKeyUserId()` 返回 API Key 创建者（`admin`），而非标讯实际创建者（如 `06234`）。webhook 回调时用 `admin` 取 OSS token → `TokenUnavailableException`。

**修复**：`TenderStatusChangedEvent` 的 `operatorId` 改用 `tender.getCreatorId()`（标讯实际创建者），而非 API Key 的 `userId`。提取 `publishEvaluatedEvent` 方法消除三处重复代码。

### 影响文件

- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandService.java`

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ 227 migrations, all checksums match |
| Step 2: DB 版本对比 | 无新增迁移 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK |

## 部署步骤

| 步骤 | 结果 |
|---|---|
| 1. 早操三连 + ff-only 同步 | ✅ HEAD = c7496cdcd |
| 2. 服务器现状确认 | ✅ 上一版本 d673fced0-api8080 |
| 3. Flyway 预检 | ✅ 227 migrations validate OK |
| 4. 本地打包 | ✅ BUILD SUCCESS（26.3s） |
| 5. 上传 + 部署 | ✅ Flyway validate 通过 → jar 覆盖 → 服务重启 |
| 6. 健康检查 | ✅ 第 1 次尝试即 UP（Kafka readiness 约 4 分钟恢复） |
| 7. Smoke 测试 | ✅ 8/8 通过 |
| 8. GitHub 镜像同步 | ✅ 两边 main 完全一致 |

## 验证结果

### Smoke 测试

| 检查项 | HTTP Code | 预期 |
|---|---|---|
| /actuator/health | 200 | ✅ UP |
| /actuator/health/readiness | 200 | ✅ UP |
| /api/auth/login (POST {}) | 400 | ✅ 路由 OK |
| /api/projects | 403 | ✅ 需认证 |
| /api/integration/crm/health | 401 | ✅ 需认证 |
| / (前端首页) | 200 | ✅ |
| /login (前端登录页) | 200 | ✅ |

### 健康检查详情

- **status**: UP
- **components**: 全部 UP（db, redis, diskSpace, jwt, ping, livenessState, readinessState, aiProvider, sidecar）
- **Kafka readiness 延迟**：约 4 分钟恢复（已知行为，非故障）

## GitHub 同步

| 项目 | 值 |
|---|---|
| Gitee main | `c7496cdcd631a1b0bba8f91aec4af8ab248969a2` |
| GitHub main | `c7496cdcd631a1b0bba8f91aec4af8ab248969a2` |
| 状态 | ✅ 完全一致 |

## 回滚信息

- **回滚版本**：`d673fced0-api8080`
- **回滚操作**：恢复 `/opt/xiyu-bid/releases/d673fced0-api8080/backend/app.jar` → 重启 `xiyu-bid-backend`
- **DB 回滚**：无需（无新增迁移）

## 经验沉淀应用

- ✅ Kafka SDK readiness 延迟（第 8/9/10/13/15 次出现）— 已知行为，不急于回滚
- ✅ Mac HTTP_PROXY 502 — curl 统一加 `--noproxy '*'`
- ✅ Flyway 预检 3 步法 — 全部通过
- ✅ SYSTEMCTL_SUDO=true — 服务正常重启

## 风险提示

- 本次修复仅影响 webhook 回调的 operatorId 取值逻辑，不影响 API 路由和数据库结构
- 需关注下次 CRM 回调标讯时 webhook 是否能成功反查 CRM token（标讯 1641/1642 后续重试时应成功）

## 部署确认清单

- [x] 环境门禁确认（test）
- [x] 早操三连 + ff-only 同步
- [x] Flyway 预检通过
- [x] 本地打包成功
- [x] 服务重启成功
- [x] 健康检查 UP
- [x] Smoke 8/8 通过
- [x] GitHub 镜像同步
- [x] 部署报告已生成
- [x] 回滚就绪
