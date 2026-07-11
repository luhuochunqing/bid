# 第 78 次测试环境部署报告

> **环境**：测试（test）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-11
> **Release ID**：`e857e37ef-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 测试（test） |
| 服务器 | `172.16.38.78`（winbid-01） |
| Release ID | `e857e37ef-api8080` |
| 部署时间 | 2026-07-11 11:25:57 CST |
| 健康检查通过 | 11:30:26 CST（Kafka SDK readiness 延迟约 4 分钟，已知行为） |
| 服务状态 | active (running) |
| 部署次数 | 第 78 次（测试环境） |
| 前一次部署 | `f0508f264-api8080`（2026-07-10 16:47 UTC） |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | `origin/main`（部署基线 `e857e37ef`） |
| HEAD commit | `e857e37ef`（!2016 fix(crm): 修复 CRM 推送"半关联"状态导致去重校验失效） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `8080`（Nginx 反代） |
| 数据库 | `xiyu_bid_main` @ `winbid-01.test.rds.ehsy.com:3306` |
| 增量 commit 数 | 3 |
| 增量 PR 数 | 1（!2016） |

---

## 3. 改动范围

### 3.1 增量 PR 列表（1 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !2016 | fix | 修复 CRM 推送"半关联"状态导致去重校验失效 |

### 3.2 改动主题

**CRM"半关联"状态修复（!2016）**：

- **根因**：CRM 推送只传 `crmId`（数字主键）未传 `crmOpportunityId`（CC 编号）时，若 CRM detail 接口返回的 code 为空，`applyLeaderAndStatus` 仍会存入 `crm_opportunity_name` 但 `crm_opportunity_id` 保持 NULL，形成"半关联"状态。后续 BD 账号通过"关联标讯"按钮关联同一商机时，去重校验只查 `crm_opportunity_id` 列，查不到"半关联"记录，导致重复关联成功。
- **修复**：
  1. `CrmTenderLinkService.applyLeaderAndStatus`：仅当 `leader.opportunityCode()` 非空时才设置 `crmOpportunityId` 和 `crmOpportunityName`
  2. `TenderIntegrationCommandSupport.applyCrmFallback`：仅当 `crmOpportunityId` 已设置时才存入 `crmOpportunityName`，防止 `hasCrmId=true && hasCode=false` 路径仍形成"半关联"
- **测试**：新增 3 个回归用例，覆盖防半关联场景。

### 3.3 Flyway 迁移

无新增迁移。

### 3.4 改动文件统计

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端代码 | 2 | `CrmTenderLinkService.java`、`TenderIntegrationCommandSupport.java` |
| 后端测试 | 2 | `CrmTenderLinkServiceTest.java`、`TenderIntegrationCommandSupportTest.java` |
| **合计** | **4** | |

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 227 migrations
```

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1164 |
| 源码最新版本 | V1164 |
| failed 迁移数 | 0 |
| checksum mismatch | 无 |
| pending 迁移 | 无 |

### Step 3: remote-deploy 内置 validate

remote-deploy.sh 在覆盖 jar 前自动执行 validate，结果通过。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 环境门禁确认 | 11:20 | ✅ 用户确认测试环境 172.16.38.78 |
| 早操三连 | 11:20 | ✅ sync-env + check-git-wrapper |
| PR !2016 合并 | 11:22 | ✅ squash merge 到 main |
| 锚点同步 | 11:23 | ✅ agent/trae-init rebase 到 e857e37ef |
| 服务器现状检查 | 11:24 | ✅ 当前 release f0508f264-api8080，health UP |
| Flyway 预检 3 步 | 11:24 | ✅ validate OK，227 migrations |
| 本地打包 | 11:25 | ✅ BUILD SUCCESS（25.8s） |
| 上传 archive + remote-deploy.sh | 11:25 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 11:25:57 | ✅ Flyway validate 通过、jar 已激活、服务已重启 |
| 健康检查 | 11:30:26 | ✅ UP（Kafka SDK readiness 延迟约 4 分钟，已知行为） |
| Smoke 测试 | 11:31 | ✅ 7/7 通过 |
| GitHub 镜像同步 | 11:32 | ✅ Gitee ↔ GitHub main 完全一致 |

---

## 6. 验证结果

### 6.1 后端健康

- health: 200 UP
- readiness: 200 UP（恢复后）

### 6.2 Smoke 测试（服务器内部执行）

| 接口 | HTTP Code | 说明 |
|------|-----------|------|
| /actuator/health | 200 | 后端健康 |
| /actuator/health/readiness | 200 | 就绪检查 |
| /api/auth/login (POST empty) | 400 | 空请求验证错误（预期） |
| /api/projects (no auth) | 403 | 需认证（预期） |
| /api/integration/crm/health | 401 | 需认证（预期） |
| / (前端首页) | 200 | 前端正常 |
| /login | 200 | 登录页正常 |

### 6.3 前端一致性

入口 JS: `assets/index-CiPPyhC3.js`

### 6.4 GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| Gitee main | `e857e37ef` |
| GitHub main | `e857e37ef` |
| 状态 | ✅ 完全一致 |

---

## 7. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `f0508f264-api8080` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/f0508f264-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-e857e37ef-api8080-*.sql.gz` |
| 回滚方式 | 恢复旧 jar `f0508f264-api8080/backend/app.jar` + `sudo systemctl restart xiyu-bid-backend` |
| 回滚风险评估 | 低（无 Flyway 迁移，纯代码改动） |

---

## 8. 临时配置检查

| 配置项 | 值 | 状态 |
|--------|------|------|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | 已知保留（首次生产部署决定） |
| `DEBUG` / `TRACE` / `LOG_LEVEL` | 未设置 | ✅ 无临时调试配置 |

---

## 9. 经验沉淀应用

| 经验 | 应用情况 |
|------|----------|
| Flyway 预检 3 步法 | ✅ 全部执行，227 migrations validate OK |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式，apiBaseUrl="" |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo，服务重启成功 |
| Mac HTTP_PROXY 502 | ✅ curl 统一加 `--noproxy '*'` |
| Kafka SDK readiness 延迟 | ✅ 11:25:57 重启 → 11:30:26 恢复 UP（约 4 分钟），已知行为 |

---

## 10. 风险提示

1. **Kafka SDK readiness 延迟**：部署脚本 120 次健康检查（4 分钟）未通过，但实际在 11:30:26 恢复 UP。这是已知行为（第 8/9/10/13/15 次均出现），无需回滚。
2. **无 Flyway 迁移**：本次部署纯代码改动，无数据库 schema 变更，回滚风险低。
3. **生产环境待部署**：本次修复仅部署到测试环境，生产环境（172.16.10.149）仍有此 bug。需在测试环境验证修复效果后，再部署到生产。

---

## 11. 部署确认清单

- [x] 环境门禁确认（用户显式确认测试环境 172.16.38.78）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] PR !2016 已合并到 main
- [x] GitHub 镜像已同步
- [x] Flyway 预检 3 步通过（227 migrations validate OK）
- [x] DB 备份成功
- [x] 本地打包成功（BUILD SUCCESS）
- [x] 后端重启 + 健康检查（UP，Kafka 延迟恢复后通过）
- [x] Smoke 测试 7/7 通过
- [x] 无临时调试配置残留
- [x] 部署报告已生成
