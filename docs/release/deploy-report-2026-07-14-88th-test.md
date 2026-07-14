# 第 88 次部署报告 - 测试环境

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试 (`ENV=test`) |
| 目标主机 | `172.16.38.78` (winbid-01) |
| Release ID | `59d3763cd-api8080` |
| Commit | `59d3763cd6e4139d8897eb2470b7564fc9d4722e` |
| 部署时间 | 2026-07-14 19:52:15 CST |
| 前一次部署 | `398da5de5-api8080`（第 87 次）|
| 新增迁移 | 无 |
| 健康检查 | ✅ UP（全组件，consecutive 3/3）|
| Smoke 测试 | ✅ 7 项全通过 |
| 回滚 posture | ready（无需执行）|

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !2078 | fix(tender): 第三方推送路径去重改用四字段策略（+projectType） | bug fix + refactor |

## 改动范围

### 核心修复：标讯去重 projectType 维度全链路覆盖

**背景**：PR !2076 为标讯去重规则新增了 `projectType` 维度（招标主体+项目类型+报名截止+开标时间），但只覆盖了人工录入路径，第三方推送路径仍走旧的三字段去重，导致同招标主体+同时间但不同项目类型的标讯被误拦截。

**根因**：`TenderIntegrationCommandService.rejectDuplicateBusinessTender` 仍走 `tenderRepository.findFirstByPurchaserNameAndRegistrationDeadlineAndBidOpeningTime` 派生查询，未使用新的 `TenderDeduplicationPolicy`。

**修复**：
1. `TenderDeduplicationService` 新增字段级 API：`findDuplicates(String, String, LocalDateTime, LocalDateTime)` 和 `rejectIfDuplicate(...)`
2. `TenderIntegrationCommandService.rejectDuplicateBusinessTender` 改用 `tenderDeduplicationService.rejectIfDuplicate(...)`，消除探针实体 anti-pattern
3. 异常类型统一为 `TenderDuplicateException`（与人工录入路径一致）
4. 日志关键字保持 `business key rejected`（监控兼容）

### 文件变更

| 文件 | 改动 |
|---|---|
| `TenderIntegrationCommandService.java` | 简化 `rejectDuplicateBusinessTender`（300→291 行）|
| `TenderDeduplicationService.java` | 新增字段级 `findDuplicates` + `rejectIfDuplicate` |
| `TenderIntegrationCommandServiceDedupProjectTypeTest.java` | 新增 5 个 projectType 维度测试场景 |
| `TenderIntegrationServicePushEvaluationTest.java` | 异常断言类型更新 |
| `TenderIntegrationCommandServiceCrmDuplicateTest.java` | 构造函数更新 |
| `TenderIntegrationCommandServiceEventTest.java` | 构造函数更新 |
| `TenderIntegrationServiceUpdateCrmLinkTest.java` | 构造函数更新 |

## Flyway 预检

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ OK（228 migrations，checksums match）|
| Step 2: DB 最新版本 | V1165（无新增迁移）|
| Step 3: remote-deploy 内置 validate | ✅ OK |

## 部署步骤

1. ✅ 早操三连（sync-env + git-wrapper + status）
2. ✅ 基线确认（HEAD=`59d3763cd`，工作区干净）
3. ✅ 服务器现状检查（健康 200，Flyway validate OK）
4. ✅ 本地打包（`obsEnabled=true`，227 迁移文件，OBS 直传 2 个 .upload( 调用）
5. ✅ 上传 + 部署（Flyway validate 通过，服务重启成功）
6. ✅ 健康检查（consecutive 3/3，无 Kafka 延迟）
7. ✅ Smoke 测试（7 项全通过）
8. ✅ GitHub 镜像同步（两边 main 完全一致）

## 验证结果

### 健康检查

```json
{
  "status": "UP",
  "components": {
    "aiProvider": "UP (qwen3.7-max)",
    "db": "UP (MySQL)",
    "diskSpace": "UP (20G free)",
    "jwt": "UP (STRONG, 64 bytes)",
    "livenessState": "UP",
    "readinessState": "UP",
    "redis": "UP (6.2.19)",
    "sidecar": "UP (reachable)"
  }
}
```

### Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login` | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /` (前端首页) | 200 | 200 | ✅ |
| 前端入口 JS | `index-Cb5KSg_0.js` | `index-Cb5KSg_0.js` | ✅ |

## GitHub 同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 3 个 commit |
| 同步后状态 | ✅ 两边 main 完全一致 |
| Gitee main | `59d3763cd6e4139d8897eb2470b7564fc9d4722e` |
| GitHub main | `59d3763cd6e4139d8897eb2470b7564fc9d4722e` |

## 临时配置检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（用户决定，第 13/14/15 次均保留）
- 无其他临时调试配置

## 经验沉淀应用

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，无新增迁移 |
| Kafka SDK readiness 延迟 | ✅ 本次未出现（秒级恢复）|
| OBS 直传 VITE_OBS_ENABLED=true | ✅ 显式传入，产物校验通过 |
| COPYFILE_DISABLE=1 | ✅ 打包时设置 |
| SYSTEMCTL_SUDO=true | ✅ 部署命令设置 |
| 前端资源保留 | ✅ 执行（无上一版本 assets 需保留）|
| GitHub 镜像同步 | ✅ 部署后同步 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 posture | ready（无需执行）|
| 上一版本 Release ID | `398da5de5-api8080` |
| 上一版本 commit | `398da5de5b8da3b9e2660cd14f2a847c336ef83b` |
| 上一版本 jar | `/opt/xiyu-bid/releases/398da5de5-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-59d3763cd-*.sql.gz` |

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连执行
- [x] Flyway 预检 3 步法
- [x] 本地打包 + 产物校验（OBS 启用）
- [x] 上传 + 部署 + 服务重启
- [x] 健康检查通过（UP，3/3 consecutive）
- [x] Smoke 测试 7 项全通过
- [x] GitHub 镜像同步
- [x] 临时配置检查
- [x] 部署报告生成

## 部署后验证建议

需在测试环境重新推送 `projectType=办公` 的标讯（与已存在 `projectType=综合` 的标讯同 purchaser+deadline+bidOpen），验证：
1. 推送不再被误拦截（返回 201 CREATED）
2. 推送 `projectType=综合`（与已存在相同）仍被拦截（返回 400 + TenderDuplicateException）
