# 第 6 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-11
> **Release ID**：`4dd914ea2-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01） |
| Release ID | `4dd914ea2-api8080` |
| 部署时间 | 2026-07-11 01:19:45 CST |
| 健康检查通过 | 部署后连续 3/3 次健康检查通过（总计 14 次尝试，约 28 秒） |
| 服务状态 | active (running) |
| 部署次数 | 第 6 次（生产环境） |
| 前一次部署 | 2026-07-10 第 5 次生产 (`2440ad069`) |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | `origin/main`（部署基线 `4dd914ea2`） |
| HEAD commit | `4dd914ea2`（!2014 fix(scripts): pre-push-gate 新增编译检查 + 冲突标记扫描） |
| 前一次生产 commit | `2440ad069`（!2002 refactor(crm): 删除全局 03595 happy path） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（`SERVER_PORT=18080`，Nginx 80 反代） |
| 数据库 | `xiyu_bid_main` |
| 增量 commit 数 | 25 |
| 增量 PR 数 | 12（!2003 ~ !2014） |

---

## 3. 改动范围

### 3.1 增量 PR 列表（12 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !2014 | fix(scripts) | pre-push-gate 新增编译检查 + 冲突标记扫描 |
| !2013 | docs | 第 77 次测试环境部署报告 |
| !2012 | fix(webhook) | 清除 Git 合并冲突标记残留，修复 main 编译失败 |
| !2011 | refactor(integration) | crmId 与 crmOpportunityId 字段分离，消除 firstNonBlank 合并根因 |
| !2010 | fix(webhook) | operatorUsername fallback 到标讯创建人修复 #1641 |
| !2009 | docs | 第 76 次测试环境部署报告 |
| !2008 | fix(webhook) | CRM 回调用标讯创建者而非 API Key 创建者反查 CRM token |
| !2007 | fix(integration) | 标讯修改接口支持项目负责人字段 |
| !2006 | refactor(webhook) | 提取 ExternalSystemPrefix 枚举 + OperatorUsernameResolver 公共组件 (CO-152/CO-277) |
| !2005 | fix(resource) | 平台账户列表 detail 并发从 5 降至 2，避免生产环境仍触发 429 |
| !2004 | docs | 第 5 次生产环境部署报告 |
| !2003 | docs | 第 74 次测试环境部署报告 |

### 3.2 改动主题

1. **CRM Webhook 操作人链路修复（!2006/!2008/!2010/!2011）**：系统性修复 CRM 回调中 operatorUsername 传递问题。提取 `OperatorUsernameResolver` 公共组件和 `ExternalSystemPrefix` 枚举；将 operatorUsername 从 API Key 创建者（admin）改为标讯创建者；分离 `crmId`（数字主键）与 `crmOpportunityId`（CC 格式商机编号），消除 `firstNonBlank` 合并根因。修复 #1641 CRM webhook 回调 TokenUnavailableException。
2. **CRM 标讯修改接口增强（!2007）**：标讯修改接口支持项目负责人字段，CRM 集成推送时携带项目负责人信息。
3. **平台账户 429 限流修复（!2005）**：平台账户列表 detail 请求并发从 5 降至 2，避免生产环境触发后端 429 限流。
4. **main 分支冲突标记事故修复（!2012）**：PR !2010 的 commit 92f241ac8 直接把 stash apply 产生的冲突标记提交到 main，导致编译失败。PR !2012 清除 5 个文件 109 行冲突标记残留。
5. **pre-push-gate 增强（!2014）**：新增 §0.5 Git 冲突标记扫描 + §0.6 后端编译检查，防止冲突标记残留和编译错误再次进入 main。

### 3.3 Flyway 迁移

**本次无新增 Flyway 迁移**。DB 版本保持 V1164（第 5 次生产部署已应用）。

### 3.4 改动文件统计

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端代码 | ~15 | CRM webhook 操作人解析、crmId/crmOpportunityId 字段分离、标讯修改接口项目负责人 |
| 后端测试 | ~10 | OperatorUsernameResolver、ExternalSystemPrefix、WebhookEventListener 测试 |
| 前端代码 | ~2 | 平台账户列表 detail 并发控制 |
| 脚本/配置 | 3 | pre-push-gate.sh 编译检查 + 冲突标记扫描、remote-deploy.sh |
| 文档 | 5 | 第 74/76/77 次测试报告、第 5 次生产报告、本报告 |
| **合计** | **~35** | |

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 227 migrations (execution time 00:00.089s)
```

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1164（lock oss user local passwords） |
| 源码最新版本 | V1164 |
| failed 迁移数 | 0（全部 success=1） |
| checksum mismatch | 无 |
| pending 迁移 | 无（已全部应用） |

### Step 3: remote-deploy 内置 validate

remote-deploy.sh 在覆盖 jar 前自动执行 validate，结果通过。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 环境门禁确认 | 01:13 | ✅ 用户确认生产环境 172.16.10.149 |
| 早操 SOP（sync-env） | 01:12 | ✅ 锚点 `agent/trae-init` ff-only 同步到 `4dd914ea2` |
| 服务器现状检查 | 01:14 | ✅ 当前 release `2440ad069-api8080`，health UP，端口 18080 |
| Flyway 预检 3 步 | 01:17 | ✅ validate OK，227 migrations，V1164 已最新 |
| 本地打包（RELEASE_ID=4dd914ea2-api8080） | 01:18 | ✅ BUILD SUCCESS（26s），jar 内 226 迁移无重复 |
| 产物校验 | 01:18 | ✅ jar V1164 最新，前端入口 `assets/index-CiPPyhC3.js` |
| 上传 archive + remote-deploy.sh | 01:19 | ✅ scp 154M 完成 |
| remote-deploy.sh 执行 | 01:19 | ✅ Flyway validate 通过、jar 已激活、服务 01:19:45 重启 |
| 健康检查 | 01:20 | ✅ 连续 3/3 通过（14 次尝试，约 28 秒） |
| Smoke 测试 | 01:20 | ✅ 全部通过 |
| GitHub 镜像同步 | 01:21 | ✅ Gitee ↔ GitHub main 完全一致 |

---

## 6. 验证结果

### 6.1 Smoke 测试（服务器内部执行）

| 接口 | 端口 | HTTP Code | 说明 |
|------|------|-----------|------|
| /actuator/health | 18080 | 200 | 后端健康 |
| /actuator/health/readiness | 18080 | 200 | 就绪检查 |
| /api/auth/login (POST empty) | 18080 | 400 | 空请求验证错误（预期） |
| /api/projects (no auth) | 18080 | 403 | 需认证（预期） |
| /api/integration/crm/health | 18080 | 401 | 需认证（预期） |
| / (前端首页) | 80 (Nginx) | 200 | 前端正常 |
| /login | 80 (Nginx) | 200 | 登录页正常 |

### 6.2 前端一致性

入口 JS: `assets/index-CiPPyhC3.js`（与 release 一致）

### 6.3 deployed-release.json

```json
{
  "releaseId": "4dd914ea2-api8080",
  "activatedAt": "2026-07-10T17:19:45Z",
  "releaseDir": "/opt/xiyu-bid/releases/4dd914ea2-api8080",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:18080/actuator/health",
  "packageMetadata": {
    "releaseId": "4dd914ea2-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T17:18:04Z",
    "sentryEnabled": false
  }
}
```

### 6.4 DB 备份

| 备份文件 | 大小 |
|----------|------|
| `/opt/xiyu-bid/db-backups/winbid-4dd914ea2-api8080-20260711011938.sql.gz` | 1.1M |

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| Gitee main | `4dd914ea2` |
| GitHub main | `4dd914ea2` |
| 同步操作 | 部署后执行 `scripts/sync-to-github.sh` |
| 状态 | ✅ 完全一致 |

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `2440ad069-api8080` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/2440ad069-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-4dd914ea2-api8080-20260711011938.sql.gz` |
| 回滚方式 | 恢复旧 jar `2440ad069-api8080/backend/app.jar` + `sudo systemctl restart xiyu-bid-backend` |
| 回滚风险评估 | 低（本次无 Flyway 迁移，纯代码回滚） |

---

## 9. 临时配置检查

| 配置项 | 值 | 状态 |
|--------|-----|------|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | 已知保留（首次生产部署决定） |
| `DEBUG` / `TRACE` / `LOG_LEVEL` | 未设置 | ✅ 无临时调试配置 |

---

## 10. 经验沉淀应用

| 经验 | 应用情况 |
|------|----------|
| Flyway 预检 3 步法 | ✅ 全部执行，无新迁移 |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式，apiBaseUrl="" |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo，服务重启成功 |
| Kafka SDK readiness 延迟 | ✅ 本次未出现，健康检查 28 秒通过 |
| Mac HTTP_PROXY 502 | ✅ curl 统一加 `--noproxy '*'` |
| pre-push-gate 编译检查 | ✅ 新增（!2014），防止冲突标记和编译错误进入 main |
| GitHub 镜像同步 | ✅ 部署后同步完成 |

---

## 11. 风险提示

1. **CRM webhook 回调链路重大修复**：本次部署包含 4 个 CRM webhook 相关 PR（!2006/!2008/!2010/!2011），系统性修复了 operatorUsername 传递和 crmId/crmOpportunityId 字段分离。需重点关注 CRM 回调是否正常触发、webhook 投递成功率、商机状态同步是否正确。
2. **#1641 CRM webhook TokenUnavailableException**：根因是 webhook 回调使用 API Key 创建者（admin）而非标讯创建者反查 CRM token。本次修复后需验证标讯状态变更是否成功触发 CRM 商机状态更新。
3. **main 分支冲突标记事故**：PR !2010 的 commit 直接把 stash apply 产生的冲突标记提交到 main，导致 main 编译失败阻断第 77 次部署。PR !2012 清理后，PR !2014 新增 pre-push-gate 编译检查 + 冲突标记扫描防止复发。
4. **生产有活跃用户**：部署期间后端重启约 28 秒，可能有短暂请求失败。部署时间选择在凌晨低峰期，影响最小化。
5. **无 Flyway 迁移**：本次无 DB 变更，回滚风险低。如需回滚，直接恢复旧 jar 即可。

---

## 12. 部署确认清单

- [x] 环境门禁确认（用户显式确认生产环境 172.16.10.149）
- [x] 早操 SOP（sync-env + check-git-wrapper）
- [x] GitHub 镜像已同步
- [x] Flyway 预检 3 步通过（无新迁移）
- [x] DB 备份成功
- [x] 本地打包成功（BUILD SUCCESS，jar 内无重复迁移）
- [x] 产物校验通过（前端入口一致）
- [x] 后端重启 + 健康检查（连续 3/3 通过，28 秒）
- [x] Smoke 测试 7/7 通过
- [x] 无临时调试配置残留
- [x] 部署报告已生成
