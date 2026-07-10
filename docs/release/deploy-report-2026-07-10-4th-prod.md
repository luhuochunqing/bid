# 第 4 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-10
> **Release ID**：`b8068ff05-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01.prod） |
| 域名 | `https://winbid.ehsy.com/` |
| Release ID | `b8068ff05-api8080` |
| 部署时间 | 2026-07-10 19:07:00 CST |
| 健康检查通过 | 部署后连续 3/3 次健康检查通过 |
| 服务状态 | active (running) |
| 部署次数 | 第 4 次（生产环境） |
| 前一次部署 | 2026-07-10 第 3 次生产 (`20e680c52`) |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | origin/main（部署基线 `b8068ff05`） |
| HEAD commit | `b8068ff05`（!1993 docs(release): 第 72/73 次测试环境部署报告） |
| 前一次 commit | `20e680c52`（!1986 fix(ai): jsonObjectPrompt） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（Nginx 8080 反代） |
| 数据库 | `winbid` @ `winbid-01.prod.rds.ehsy.com:3306` |
| 增量 commit 数 | 18 |

---

## 3. 改动范围

### 3.1 增量 PR 列表（6 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !1993 | docs | 第 72/73 次测试环境部署报告 |
| !1992 | ui | 删除 CRM 商机关联弹窗中的手动输入商机信息按钮 |
| !1990 | feat | CRM 回调链路改为按用户身份调用（CO-152 补齐） |
| !1989 | fix | OSS 父菜单缺失时自动补 resource 父权限 |
| !1988 | docs | 第 3 次生产部署报告 + 第 71 次测试部署报告 + 鉴权审计报告 |
| !1987 | fix | OSS 登录本地无记录时自动创建 User，恢复"OSS 实时鉴权为唯一真相源"设计意图 |

### 3.2 改动主题

1. **CRM Webhook 用户身份化（!1990）**：CRM 回调链路改为按用户身份调用，补齐 CO-152 要求。新增/修改文件包括 `CrmAuthService`、`OssDirectLoginService`、`OssUserTokenCache`、`WebhookCrmTokenResolver`、`WebhookDeliveryJobService`、`WebhookHttpSender` 等。
2. **OSS 鉴权修复（!1987, !1989）**：
   - OSS 登录本地无记录时自动创建 User，恢复 OSS 实时鉴权为唯一真相源。
   - OSS 父菜单缺失时自动补 resource 父权限，修复权限扩散导致的菜单访问问题。
3. **UI 调整（!1992）**：删除 CRM 商机关联弹窗中的手动输入商机信息按钮。
4. **权限修复（misc）**：允许评标管理员删除评标文档；spec-024 鉴权不匹配缓解；告警规则/告警历史/审计日志等控制器权限调整。
5. **文档（!1988, !1993）**：第 3 次生产部署报告、第 71/72/73 次测试部署报告、鉴权审计报告。
6. **锁清理（19a1248c7）**：同步 GitHub 过期锁清理改动（GitHub → Gitee）。

### 3.3 Flyway 迁移

| 版本 | 描述 | 状态 |
|------|------|------|
| V1163 | add operator username to webhook delivery tasks | ✅ 已应用 |

新增 1 个迁移文件：
- `backend/src/main/resources/db/migration-mysql/V1163__add_operator_username_to_webhook_delivery_tasks.sql`

### 3.4 改动文件统计

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端代码 | ~29 | CRM webhook、OSS 登录、鉴权、控制器权限等 |
| 后端测试 | ~12 | 新增/更新单元测试 |
| 数据库迁移 | 1 | V1163 |
| 前端代码 | 1 | `CrmOpportunitySelector.vue` |
| 文档/Wiki | 6 | 部署报告、鉴权审计报告 |
| 锁文件/脚本 | 3 | 锁清理、pre-push-gate |
| **合计** | **~54** | |

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

使用 `flyway-repair-runner.sh validate` 预检通过（部署前已执行，旧 jar 未覆盖时服务仍在线）。

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1163（add operator username to webhook delivery tasks，2026-07-10 19:07:06） |
| 源码最新版本 | V1163 |
| failed 迁移数 | 0（全部 success=1） |
| checksum mismatch | 无 |
| pending 迁移 | 无（V1163 已应用） |

### Step 3: remote-deploy 内置 validate

remote-deploy.sh 在覆盖 jar 前自动执行 validate，结果通过。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 环境门禁确认 | ~19:00 | ✅ 用户确认生产环境 172.16.10.149 |
| 早操 SOP（sync-env + check-git-wrapper） | ~19:01 | ✅ 同步到 `b8068ff05` |
| 服务器现状检查 | ~19:02 | ✅ 旧 release `20e680c52-api8080` 运行中，健康 UP |
| Flyway 预检 3 步 | ~19:03 | ✅ validate OK |
| DB 备份 | ~19:05-19:06 | ✅ 生成 3 份备份，最新 `winbid-b8068ff05-api8080-20260710190653.sql.gz` |
| 本地打包（RELEASE_ID=b8068ff05-api8080） | ~19:04 | ✅ BUILD SUCCESS |
| 产物校验 | ~19:05 | ✅ jar 内迁移无重复，前端入口 `assets/index-BzNMto7W.js` |
| 上传 archive + remote-deploy.sh | ~19:05 | ✅ scp 完成 |
| remote-deploy.sh 执行 | ~19:06-19:07 | ✅ 服务重启 + 前端激活 + 健康检查通过 |
| Smoke 测试 | ~19:07 | ✅ 全部通过 |
| 临时配置检查 | ~19:07 | ✅ 无临时调试配置 |

### 权限问题处理

- `backend.env` 权限 denied：通过 `sudo chmod 640` + `sudo chown root:jetty` 修复。
- `/tmp/flyway-repair-extract` 目录权限 denied：通过 `sudo rm -rf` + `sudo mkdir -p` + `sudo chown -R jetty:jetty` 修复。
- `/tmp/FlywayRepairRunner.java` 写权限 denied：通过清理旧文件后重试修复。

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
    "diskSpace": { "status": "UP", "details": { "free": "92GB" } },
    "jwt": { "status": "UP", "details": { "algorithm": "HMAC-SHA256", "secretLength": 47, "strength": "ACCEPTABLE" } },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "6.2.19" } },
    "sidecar": { "status": "UP", "details": { "url": "http://localhost:8000", "status": "reachable" } }
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
| / (前端首页) | 8080 (Nginx) | 200 | 前端正常 |
| /login | 8080 (Nginx) | 200 | 登录页正常 |
| /actuator/health (via Nginx) | 8080 | 200 | actuator 代理正常 |

### 6.3 前端一致性

入口 JS: `assets/index-BzNMto7W.js`（与 release 一致）

### 6.4 deployed-release.json

```json
{
  "releaseId": "b8068ff05-api8080",
  "activatedAt": "2026-07-10T11:07:00Z",
  "releaseDir": "/opt/xiyu-bid/releases/b8068ff05-api8080",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:18080/actuator/health",
  "packageMetadata": {
    "releaseId": "b8068ff05-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T11:04:37Z",
    "sentryEnabled": false
  }
}
```

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| Gitee main（部署时） | `b8068ff05` |
| GitHub main | `b8068ff05` |
| 部署时同步状态 | ✅ 已同步（GitHub 镜像与部署基线一致） |
| 当前 origin/main | `8559a2c2f`（!1994 refactor: 清理CRM商机手动输入功能的 dead code） |
| GitHub 落后 | 2 commit（!1994 及对应 refactor commit） |
| 待处理 | 部署后需执行 `bash scripts/sync-to-github.sh` 同步 !1994 到 GitHub |

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `20e680c52-api8080` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/20e680c52-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-b8068ff05-api8080-20260710190653.sql.gz` |
| 回滚方式 | 恢复旧 jar `20e680c52-api8080/backend/app.jar` + `sudo systemctl restart xiyu-bid-backend`；如 V1163 需回滚，执行对应 rollback 脚本 |
| 回滚风险评估 | 中低（本次有 1 个 Flyway 迁移 V1163，回滚需处理 DB 字段） |

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
| Flyway 预检 3 步法 | ✅ 全部执行，V1163 已正常应用 |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式，apiBaseUrl="" |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo |
| backend.env 权限修复 | ✅ 已调整 `root:jetty` + `640` |
| /tmp 目录权限修复 | ✅ flyway-repair-extract 与 FlywayRepairRunner.java 权限已修复 |
| 前端目录权限 | ✅ 本次未再触发权限问题 |
| Kafka SDK readiness 延迟 | ✅ 本次未出现，健康检查快速通过 |

---

## 11. 风险提示

1. **本次包含 Flyway 迁移 V1163**：新增 `operator_username` 字段到 `webhook_delivery_tasks`。如需回滚，需同时回滚 DB 字段，建议保留本次 DB 备份。
2. **CRM Webhook 用户身份化变更（!1990）**：涉及 CRM token 按用户获取的较大改动，需监控 CRM 回调与 webhook 投递功能。
3. **OSS 鉴权修复（!1987, !1989）**：影响 OSS 用户登录与权限计算，需关注 SSO 登录与菜单权限表现。
4. **GitHub 镜像待同步**：origin/main 已前进 2 个 commit 到 `8559a2c2f`，GitHub 仍停在 `b8068ff05`，需尽快同步。
5. **生产有活跃用户**：部署期间后端重启约 30 秒，可能有短暂请求失败。

---

## 12. 部署确认清单

- [x] 环境门禁确认（用户显式确认生产环境 172.16.10.149）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] Flyway 预检 3 步法
- [x] DB 备份
- [x] 本地打包（同源构建）
- [x] 产物校验（jar 内迁移 + 前端入口）
- [x] 后端重启 + 健康检查（连续 3/3 通过）
- [x] Smoke 测试（health + readiness + API + 前端）
- [x] 临时配置检查
- [x] 部署报告生成
- [ ] GitHub 镜像同步（待处理：origin/main 领先 2 commit）
