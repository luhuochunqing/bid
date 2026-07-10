# 第 77 次测试环境部署报告

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | 测试（test） |
| 目标主机 | 172.16.38.78（winbid-01） |
| Release ID | `f0508f264-api8080` |
| 部署时间 | 2026-07-11 00:47:05 CST |
| 上次部署 | `c7496cdcd-api8080`（第 76 次，2026-07-10 15:53:40 CST） |

## 部署概览

第 77 次测试环境部署。本次部署包含 3 个 PR 的改动：webhook operatorUsername 修复（#1641）、CRM 字段分离（消除 firstNonBlank 合并根因）、以及紧急修复 main 分支 Git 合并冲突标记残留。

**特殊事件**：部署前发现 `origin/main` 中 `WebhookEventListener.java` 和 `ProjectResultConfirmedWebhookListener.java` 残留 Git stash apply 冲突标记，导致 main 分支编译失败。先开任务分支修复（PR !2012）合入 main 后才继续部署。

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `git@gitee.com:allinai888/bid.git` |
| 锚点分支 | `agent/trae-init` |
| 部署 commit | `f0508f264072171a34c0724b98ae33dac8ed59ad` |
| 上次部署 commit | `c7496cdcd` |
| GitHub 镜像 | ✅ 已同步（`f0508f264`） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !2010 | fix(webhook): operatorUsername fallback 到标讯创建人修复 #1641 | bug fix |
| !2011 | refactor(integration): crmId 与 crmOpportunityId 字段分离，消除 firstNonBlank 合并根因 | refactor |
| !2012 | fix(webhook): 清除 Git 合并冲突标记残留，修复 main 编译失败 | bug fix（紧急） |

## 改动范围

### PR !2010 — webhook operatorUsername 修复 #1641

- **根因**：CRM 通过 API Key 认证回调时，`operatorUsername=admin`（API Key 创建者）没有 OSS token，导致 webhook 反查 CRM 商机编号失败
- **修复**：`TenderStatusChangedEvent` 的 `operatorId` 改用 `tender.getCreatorId()`（有 OSS token 的真实用户）

### PR !2011 — CRM 字段分离

- **根因**：`TenderPushRequest` 用 `firstNonBlank(crmOpportunityId, crmId)` 合并取值，把 CRM 数字主键 id 和 CC 格式编号 code 当成同一字段
- **修复**：
  - `crmId`（数字主键）与 `crmOpportunityId`（CC 格式编号 code）作为独立字段
  - code 非空时直接存入 `tender.crm_opportunity_id`，不再反查
  - crmId 仅用于 `findProjectLeaderByChanceId` 查项目负责人
  - 删除 `tryParseChanceId` 方法

### PR !2012 — 清除 Git 合并冲突标记（紧急）

- **根因**：`origin/main` 中 `WebhookEventListener.java`（第 95-107 行）和 `ProjectResultConfirmedWebhookListener.java`（第 93-108 行）残留 Git stash apply 冲突标记
- **冲突分析**：stash 代码引用不存在的 `resolveOperatorUsername` 方法且重复声明 `operatorUsername` 变量；正确版本（Updated upstream）已在 PR !2010 合入
- **修复**：5 文件 109 行删除
  - `WebhookEventListener.java` — 删除冲突块，保留 Updated upstream 空版本
  - `ProjectResultConfirmedWebhookListener.java` — 删除冲突块，保留 Updated upstream 版本
  - `WebhookEventListenerTest.java` — 合并 import 冲突 + 删除为已删除 fallback 逻辑写的 3 个测试用例
  - `ProjectResultConfirmedWebhookListenerTest.java` — 删除为已删除 fallback 逻辑写的 1 个测试用例 + 清理 unused import
  - `docs/lessons/lessons-learned.md` — 删除孤立的 `<<<<<<< HEAD` 标记

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ 227 migrations, all checksums match |
| Step 2: DB 版本对比 | ✅ 无新增迁移文件 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

**新增迁移**：无

## 部署步骤

| 步骤 | 结果 |
|---|---|
| 早操三连（fetch + ff-only） | ✅ HEAD = origin/main = `f0508f264` |
| Flyway 预检 3 步法 | ✅ 全通过 |
| 本地打包 | ✅ `f0508f264-api8080`，jar 内 Flyway 迁移版本无重复 |
| 上传 + 部署 | ✅ scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 健康检查 | ⚠️ Readiness 延迟恢复（见下方说明） |

## 验证结果

### 健康检查

| 端点 | HTTP 状态 | 说明 |
|---|---|---|
| `/actuator/health` | 200 UP | 全组件 UP（aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar） |
| `/actuator/health/readiness` | 200 UP | Readiness 已恢复 |

### Readiness 延迟恢复说明

部署脚本健康检查在 120 次（240秒）内未捕获到 3 次连续成功，报 `❌ Health check failed`。但后端服务实际正常运行：
- 用户 11484、06234 业务请求正常处理（status=200）
- `/actuator/health` 返回 503（readinessState 未恢复）

这是 **Kafka SDK 启动时序竞争**导致的 readiness 延迟恢复（skill 文档第 2 条经验）。部署完成后手动检查 health 已恢复 UP。非真实故障，无需回滚。

### API Smoke 测试

| 接口 | 期望 | 实际 | 说明 |
|---|---|---|---|
| `GET /actuator/health` | 200 | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 | 200 UP | ✅ |
| `POST /api/auth/login`（空体） | 400 | 400 | ✅ 空密码验证错误 |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ 需认证 |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ 需认证 |

### 前端验证

| 检查项 | 结果 |
|---|---|
| `GET /`（index.html） | 200 ✅ |
| `GET /login` | 200 ✅ |
| assets 入口 | `assets/index-CiPPyhC3.js` ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 7 commit |
| 同步后状态 | ✅ 两边 main 完全一致（`f0508f264`） |
| 同步方式 | `bash scripts/sync-to-github.sh`（Gitee → GitHub 单向镜像） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上次部署 jar | `/opt/xiyu-bid/releases/c7496cdcd-api8080/backend/app.jar` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-f0508f264-*.sql.gz` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/c7496cdcd-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

1. **Flyway 预检 3 步法**（第 1 条）：部署前主动 validate + DB 版本对比，227 migrations 全通过
2. **Readiness 延迟恢复**（第 2 条）：识别 Kafka SDK 时序竞争导致的 503，非真实故障，未误判为回滚
3. **Git 合并冲突标记清理**：发现 main 分支残留 stash apply 冲突标记，先修复后部署，避免在损坏的 main 上继续

## 风险提示

1. **main 分支冲突标记根因未查**：PR !2012 修复了冲突标记，但**未追查 stash apply 冲突标记是如何进入 origin/main 的**。可能是某次 rebase/stash pop 操作残留。建议后续排查 git reflog 确认根因，避免复发。
2. **#1641 修复需验证**：webhook operatorUsername 改用标讯创建人后，需在实际 CRM 回调场景中验证 OSS token 获取成功。上一次部署（c7496cdcd）后标讯 1643 webhook 投递成功但 CRM 商机状态未变，本次部署后需重新验证。

## 部署确认清单

- [x] 环境门禁确认（test = 172.16.38.78）
- [x] 早操三连 + 锚点 ff-only 同步
- [x] Flyway 预检 3 步法全通过
- [x] 本地打包成功（jar 内迁移无重复）
- [x] 上传 + 部署成功
- [x] 健康检查 UP（readiness 延迟恢复后确认）
- [x] API Smoke 全绿（health/readiness/login/projects/crm）
- [x] 前端验证通过（index/login/assets）
- [x] GitHub 镜像同步完成
- [x] 部署报告已生成
