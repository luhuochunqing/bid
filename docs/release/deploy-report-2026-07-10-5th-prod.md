# 第 5 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-10
> **Release ID**：`2440ad069-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01.prod） |
| 域名 | `https://winbid.ehsy.com/` |
| Release ID | `2440ad069-api8080` |
| 部署时间 | 2026-07-10 21:27:30 CST |
| 健康检查通过 | 部署后连续 3/3 次健康检查通过（总计 15 次尝试） |
| 服务状态 | active (running) |
| 部署次数 | 第 5 次（生产环境） |
| 前一次部署 | 2026-07-10 第 4 次生产 (`b8068ff05`) |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | `origin/main`（部署基线 `2440ad069`） |
| HEAD commit | `2440ad069`（!2002 refactor(crm): 删除全局 03595 happy path，真人操作强制用户 OSS token） |
| 前一次 commit | `b8068ff05`（!1993 docs(release): 第 72/73 次测试环境部署报告） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（Nginx 8080 反代） |
| 数据库 | `winbid` @ `winbid-01.prod.rds.ehsy.com:3306` |
| 增量 commit 数 | 20 |
| 增量 PR 数 | 7（!2002 / !2001 / !1998 / !1997 / !1996 / !1995 / !1994） |

> **注**：部署执行期间 `origin/main` 已前进至 `1621cd32e`（仅追加第 74 次测试环境部署报告文档，无代码改动）。本次生产部署基线仍为 `2440ad069`。

---

## 3. 改动范围

### 3.1 增量 PR 列表（7 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !2002 | refactor | 删除全局 03595 happy path，真人操作强制用户 OSS token |
| !2001 | fix | §4.2 项目结果回调入队写入 operator_username |
| !1998 | docs | 沉淀父权限 403 根因并增加 pre-push 兜底拦截 |
| !1997 | fix | 限制平台账户列表 detail 请求并发数，避免触发 429 限流 |
| !1996 | docs | 第 4 次生产环境部署报告 |
| !1995 | fix | OSS 同步用户默认密码 123456 漏洞修复 |
| !1994 | refactor | 清理 CRM 商机手动输入功能的 dead code 与未使用变量 |

### 3.2 改动主题

1. **CRM 鉴权路径清理（!2002）**：删除全局 `03595` happy path，真人操作强制走用户 OSS token 换取 CRM JWT；删除虚构系统账号，无上下文流量由系统集成账号接管。
2. **Webhook 操作人补全（!2001）**：项目结果确认回调入队时写入 `operator_username`，修复回调链路字段缺失。
3. **父权限 403 根因沉淀（!1998）**：在 `lessons-learned.md` 中补充父权限扩散根因，并新增 `pre-push-gate.sh` 兜底拦截。
4. **并发限流保护（!1997）**：平台账户列表与 BAR 站点列表的 N+1 detail 请求增加并发控制，避免触发后端 429 限流。
5. **OSS 默认密码漏洞修复（!1995）**：通过 `V1164` 迁移将 OSS 同步用户的本地登录密码锁定，禁止 OSS 用户用默认密码本地登录。
6. **CRM 手动输入清理（!1994）**：删除 `CrmOpportunitySelector` 中已废弃的手动输入商机信息入口及相关 dead code。

### 3.3 Flyway 迁移

| 版本 | 描述 | 状态 |
|------|------|------|
| V1164 | lock oss user local passwords | ✅ 已应用 |

新增 1 个迁移文件：
- `backend/src/main/resources/db/migration-mysql/V1164__lock_oss_user_local_passwords.sql`
- 配套回滚脚本：`backend/src/main/resources/db/rollback/migration-mysql/U1164__lock_oss_user_local_passwords.sql`

### 3.4 改动文件统计

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端代码 | 20 | CRM 鉴权/token 路径清理、Webhook 操作人写入、平台账户/BAR 并发限制、OSS 用户本地密码锁定等 |
| 后端测试 | 11 | 覆盖 CRM、Webhook、资源、组织同步等新增/修改逻辑 |
| 数据库迁移 | 1 | V1164（配套 U1164 回滚脚本） |
| 前端代码 | 7 | `src/stores/bar.js`、BAR 站点列表并发控制、`CrmOpportunitySelector` 组件清理、`Resource/Account.vue` 并发控制等 |
| 文档/Wiki | 5 | 部署报告、经验教训沉淀、根因分析文档 |
| 脚本/配置 | 7 | `pre-push-gate.sh` 父权限兜底检查、`check-parent-permission-fallback.mjs`、application*.yml 等 |
| **合计** | **53** | |

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

使用 `flyway-repair-runner.sh validate` 预检通过（部署前已执行，旧 jar 未覆盖时服务仍在线）。

```
VALIDATE OK - all checksums match
Successfully validated 226 migrations
```

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1163（add operator username to webhook delivery tasks） |
| 源码最新版本 | V1164 |
| failed 迁移数 | 0（全部 success=1） |
| checksum mismatch | 无 |
| pending 迁移 | V1164（待应用） |

### Step 3: remote-deploy 内置 validate

remote-deploy.sh 在覆盖 jar 前自动执行 validate，结果通过。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 环境门禁确认 | 21:24 | ✅ 用户确认生产环境 172.16.10.149 |
| 早操 SOP（sync-env + check-git-wrapper） | 21:24 | ✅ 同步到 `2440ad069` |
| 任务分支创建 | 21:24 | ✅ `agent/trae/prod-deploy-20260710-5th` 基于 origin/main 创建 |
| 服务器现状检查 | 21:25 | ✅ 当前 release `b8068ff05-api8080`，health UP，后端端口 18080 |
| Flyway 预检 3 步 | 21:25 | ✅ validate OK，V1164 pending |
| DB 备份 | 21:27 | ✅ 自动生成 `winbid-2440ad069-api8080-202607102127xx.sql.gz` |
| 本地打包（RELEASE_ID=2440ad069-api8080） | 21:25 | ✅ BUILD SUCCESS（28s），jar 内迁移无重复 |
| 产物校验 | 21:26 | ✅ jar 内 V1164 存在，前端入口 `assets/index-HinikF_S.js` |
| 上传 archive + remote-deploy.sh | 21:26 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 21:27 | ✅ Flyway validate 通过、jar 已激活、服务已重启、健康检查通过 |
| Smoke 测试 | 21:28 | ✅ 全部通过 |
| 临时配置检查 | 21:28 | ✅ 无新增临时调试配置 |
| GitHub 镜像同步 | 21:28 | ✅ Gitee ↔ GitHub main 完全一致（HEAD `1621cd32e`） |

---

## 6. 验证结果

### 6.1 后端健康

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {
      "status": "UP",
      "details": {
        "status": "configured",
        "provider": "custom",
        "model": "qwen3.7-max",
        "apiKeyConfigured": true
      }
    },
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": 105553760256, "free": 91646111744, "threshold": 10485760 } },
    "jwt": { "status": "UP", "details": { "algorithm": "HMAC-SHA256", "secretLength": 47, "secretBytes": 47, "strength": "ACCEPTABLE" } },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "6.2.19" } },
    "sidecar": { "status": "UP", "details": { "url": "http://localhost:8000", "status": "reachable", "response": "{\"status\":\"up\"}" } }
  }
}
```

### 6.2 Smoke 测试（服务器内部执行）

| 接口 | 端口 | HTTP Code | 说明 |
|------|------|-----------|------|
| /actuator/health | 18080 | 200 | 后端健康 |
| /actuator/health/readiness | 18080 | 200 | 就绪检查 |
| /api/auth/login (POST empty) | 18080 | 400 | 空请求验证错误（预期） |
| /api/projects (no auth) | 18080 | 403 | 需认证（预期） |
| /api/integration/crm/health | 18080 | 401 | 需认证（预期） |
| / (前端首页) | 8080 (Nginx) | 200 | 前端正常 |
| /login | 8080 (Nginx) | 200 | 登录页正常 |

### 6.3 前端一致性

入口 JS: `assets/index-HinikF_S.js`（与 release 一致）

### 6.4 Flyway 迁移应用验证

| 版本 | 描述 | 状态 | 应用时间 |
|------|------|------|----------|
| 1164 | lock oss user local passwords | success | 2026-07-10 21:27:37 |

### 6.5 deployed-release.json

```json
{
  "releaseId": "2440ad069-api8080",
  "activatedAt": "2026-07-10T13:27:30Z",
  "releaseDir": "/opt/xiyu-bid/releases/2440ad069-api8080",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:18080/actuator/health",
  "packageMetadata": {
    "releaseId": "2440ad069-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T13:25:52Z",
    "sentryEnabled": false
  }
}
```

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| Gitee main（部署时） | `2440ad069` |
| Gitee main（同步后） | `1621cd32e`（仅追加第 74 次测试环境部署报告文档） |
| GitHub main | `1621cd32e` |
| 同步操作 | 部署后执行 `scripts/sync-to-github.sh` |
| 状态 | ✅ 完全一致 |

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `b8068ff05-api8080` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/b8068ff05-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-2440ad069-api8080-202607102127xx.sql.gz` |
| 回滚方式 | 恢复旧 jar `b8068ff05-api8080/backend/app.jar` + `sudo systemctl restart xiyu-bid-backend`；如 V1164 需回滚，执行对应 U1164 rollback 脚本 |
| 回滚风险评估 | 中低（本次有 1 个 Flyway 迁移 V1164，回滚需处理 DB 字段） |

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
| Flyway 预检 3 步法 | ✅ 全部执行，V1164 已正常应用 |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式，apiBaseUrl="" |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo，服务重启成功 |
| backend.env 权限修复 | ✅ 已调整 `root:jetty` + `640` |
| /tmp 目录权限修复 | ✅ flyway-repair-extract 与 FlywayRepairRunner.java 权限正常 |
| 前端目录权限 | ✅ 本次未再触发权限问题 |
| Kafka SDK readiness 延迟 | ✅ 本次未出现，健康检查快速通过 |
| Mac HTTP_PROXY 502 | ✅ curl 统一加 `--noproxy '*'` |

---

## 11. 风险提示

1. **本次包含 Flyway 迁移 V1164**：通过将 OSS 同步用户本地密码置为无效值来锁定本地登录。如需回滚，需执行 U1164 脚本并评估 OSS 用户本地登录风险。
2. **CRM 鉴权路径调整（!2002）**：删除了全局 `03595` happy path，所有 CRM 调用必须携带有效用户 OSS token；无上下文流量已切到系统集成账号，需监控 CRM 回调、webhook 投递及 tender link 等功能。
3. **OSS 默认密码漏洞修复（!1995）**：OSS 同步用户将无法使用默认密码 `123456` 本地登录，符合安全预期。若存在需本地密码登录的 OSS 用户，需单独设计例外机制。
4. **并发限流保护（!1997）**：BAR 站点列表与平台账户列表已增加并发控制，但其他页面若仍存在 N+1 detail 请求，可能继续触发 429，需持续关注。
5. **部署期间 origin/main 前进**：本次部署基线 `2440ad069` 在部署过程中，origin/main 追加了两份测试环境部署报告文档（`0abd4c8d7`、`1621cd32e`），无代码改动，不影响生产功能。
6. **生产有活跃用户**：部署期间后端重启约 30 秒，可能有短暂请求失败。

---

## 12. 部署确认清单

- [x] 环境门禁确认（用户显式确认生产环境 172.16.10.149）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] 任务分支创建（`agent/trae/prod-deploy-20260710-5th`）
- [x] GitHub 镜像已同步
- [x] Flyway 预检 3 步通过
- [x] DB 备份成功
- [x] 本地打包成功（BUILD SUCCESS，jar 内无重复迁移）
- [x] 产物校验通过（前端入口一致）
- [x] 后端重启 + 健康检查（连续 3/3 通过）
- [x] Smoke 测试 7/7 通过
- [x] Flyway V1164 已应用
- [x] 无临时调试配置残留
- [x] 部署报告已生成
