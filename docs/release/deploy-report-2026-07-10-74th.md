# 第 74 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `2440ad069-api8080` |
| 部署时间 | 2026-07-10 21:13:16 CST |
| 部署人 | trae agent |
| 特殊说明 | Kafka SDK readiness 延迟导致 remote-deploy.sh 健康检查 120 次超时，手动验证后确认已自恢复 UP（已知行为，见经验沉淀第 2 条） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/test-env-upgrade-20260710`（任务分支，HEAD = origin/main） |
| HEAD commit | `2440ad069` |
| origin/main | `2440ad069`（同步） |
| 上次部署 releaseId | `0cb28abf1-api8080`（第 73 次） |
| 增量 commit 数 | 20 |
| 增量 PR 数 | 7（!2002 / !2001 / !1998 / !1997 / !1996 / !1995 / !1994） |
| Flyway 迁移变更 | V1164__lock_oss_user_local_passwords.sql（配套 U1164 回滚脚本） |
| GitHub 镜像落后数 | 0（已同步） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !2002 | refactor(crm): 删除全局 03595 happy path，真人操作强制用户 OSS token | refactor |
| !2001 | fix(webhook): §4.2 项目结果回调入队写入 operator_username | fix |
| !1998 | docs(lessons): 沉淀父权限 403 根因并增加 pre-push 兜底拦截 | docs |
| !1997 | fix(resource): 限制平台账户列表 detail 请求并发数，避免触发 429 限流 | fix |
| !1996 | docs(release): 第 4 次生产环境部署报告 | docs |
| !1995 | fix(security): OSS 同步用户默认密码 123456 漏洞修复 | fix |
| !1994 | refactor: 清理CRM商机手动输入功能的 dead code 与未使用变量 | refactor |

## 改动范围

| 层 | 文件数 | 说明 |
|---|---|---|
| 前端代码 | 7 | `src/stores/bar.js`、BAR 站点列表并发控制、`CrmOpportunitySelector` 组件清理、`Resource/Account.vue` 并发控制等 |
| 后端代码 | 20 | CRM 鉴权/token 路径清理、Webhook 操作人写入、平台账户/BAR 并发限制、OSS 用户本地密码锁定等 |
| 后端测试 | 11 | 覆盖 CRM、Webhook、资源、组织同步等新增/修改逻辑 |
| 数据库迁移 | 1 | `V1164__lock_oss_user_local_passwords.sql`（配套 `U1164` 回滚脚本） |
| 文档/Wiki | 5 | 部署报告、经验教训沉淀、根因分析文档 |
| 脚本/配置 | 7 | `pre-push-gate.sh` 父权限兜底检查、`check-parent-permission-fallback.mjs`、application*.yml 等 |
| **合计** | **53** | |

### 核心改动说明

1. **CRM 鉴权路径清理（!2002）**：删除全局 `03595` happy path，真人操作强制走用户 OSS token 换取 CRM JWT；删除虚构系统账号，无上下文流量由系统集成账号接管。
2. **Webhook 操作人补全（!2001）**：项目结果确认回调入队时写入 `operator_username`，修复回调链路字段缺失。
3. **父权限 403 根因沉淀（!1998）**：在 `lessons-learned.md` 中补充父权限扩散根因，并新增 `pre-push-gate.sh` 兜底拦截。
4. **并发限流保护（!1997）**：平台账户列表与 BAR 站点列表的 N+1 detail 请求增加并发控制，避免触发后端 429 限流。
5. **OSS 默认密码漏洞修复（!1995）**：通过 `V1164` 迁移将 OSS 同步用户的本地登录密码锁定，禁止 OSS 用户用默认密码本地登录。
6. **CRM 手动输入清理（!1994）**：删除 `CrmOpportunitySelector` 中已废弃的手动输入商机信息入口及相关 dead code。

## Flyway 预检结果

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK | 226 migrations validated, all checksums match |
| Step 2: DB 已应用版本对比 | ✅ 待升级 | DB 最新 V1163，源码新增 V1164 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 | 部署时自动 validate 通过 |

## 部署步骤

| 步骤 | 时间 | 结果 |
|---|---|---|
| 环境门禁确认 | 21:07 | ✅ 测试环境 172.16.38.78 已确认 |
| 早操 SOP | 21:07 | ✅ origin/main 已同步到 `2440ad069` |
| Git 安全 wrapper 检查 | 21:08 | ⚠️ 锚点分支 guard 触发，转用任务分支 `agent/trae/test-env-upgrade-20260710` 后 wrapper 就绪 |
| 任务分支创建 | 21:10 | ✅ `agent/trae/test-env-upgrade-20260710` 基于 origin/main 创建 |
| 服务器现状检查 | 21:11 | ✅ 当前 release `0cb28abf1-api8080`，health UP |
| Flyway 预检 3 步 | 21:11 | ✅ 全部通过 |
| 本地打包（package-release.sh） | 21:12 | ✅ BUILD SUCCESS（28s），jar 内 227 迁移文件无重复 |
| 产物校验 | 21:12 | ✅ 前端入口 `assets/index-HinikF_S.js` |
| 上传 archive + remote-deploy.sh | 21:12 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 21:13 | ⚠️ Flyway validate 通过、jar 已激活、服务已重启，但健康检查 120 次超时 |
| 手动健康检查 | 21:17 | ✅ 已自恢复 UP，所有组件正常 |
| GitHub 镜像同步 | 21:17 | ✅ Gitee ↔ GitHub main 完全一致 |

## 验证结果

### 健康检查（手动）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | — |
| aiProvider | UP | provider=custom, model=qwen3.7-max, apiKeyConfigured=true |
| db | UP | MySQL, isValid() |
| diskSpace | UP | free=25.3GB |
| jwt | UP | HMAC-SHA256, secretLength=64, STRONG |
| livenessState | UP | — |
| readinessState | UP | — |
| redis | UP | version=6.2.19 |
| sidecar | UP | url=http://localhost:8000, response={"status":"up"} |

### Smoke 测试

| # | 接口 | 预期 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | GET /actuator/health | 200 UP | 200 UP | ✅ |
| 2 | GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| 3 | POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| 4 | GET /api/projects (无认证) | 403 | 403 | ✅ |
| 5 | GET /api/integration/crm/health | 401 | 401 | ✅ |
| 6 | GET / (前端首页) | 200 | 200 | ✅ |
| 7 | GET /login | 200 | 200 | ✅ |
| 8 | 前端 index.html 入口 | assets/index-HinikF_S.js | assets/index-HinikF_S.js | ✅ |

### Flyway 迁移应用验证

| 版本 | 描述 | 状态 | 应用时间 |
|---|---|---|---|
| 1164 | lock oss user local passwords | success | 2026-07-10 21:13:25 |

### deployed-release.json

```json
{
  "releaseId": "2440ad069-api8080",
  "activatedAt": "2026-07-10T13:13:16Z",
  "releaseDir": "/opt/xiyu-bid/releases/2440ad069-api8080",
  "packageMetadata": {
    "releaseId": "2440ad069-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T13:12:18Z",
    "sentryEnabled": false
  }
}
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| github/main vs origin/main | 0 commit 落后（已同步） |
| 同步操作 | 部署后执行 `scripts/sync-to-github.sh` |

## 临时配置清理

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS / DEBUG / TRACE | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 用户历史决定保留，非临时配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | 恢复 `/opt/xiyu-bid/releases/0cb28abf1-api8080/backend/app.jar` + sudo systemctl restart xiyu-bid-backend |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-2440ad069-api8080-202607102113xx.sql.gz` |
| 回滚必要性 | 无需回滚（部署验证通过） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 第 2 条：Kafka SDK readiness 延迟 | ✅ 已识别，120 次超时后手动验证确认自恢复 UP |
| 第 1 条：Flyway 预检 3 步法 | ✅ 全部执行，227 migrations validate OK |
| 第 3 条：前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空，apiBaseUrl="" |
| 第 8 条：SYSTEMCTL_SUDO=true | ✅ 已设置，服务重启成功 |
| 第 16 条：Mac HTTP_PROXY 502 | ✅ curl 统一加 --noproxy '*' |
| 第 7 条：幂等迁移设计 | ✅ V1164 使用存储过程 + information_schema 检查，具备幂等性 |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次延迟超过 4 分钟（remote-deploy.sh 健康检查窗口），建议未来考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池，避免阻塞主线程影响 AvailabilityChangeEvent 处理。
2. **OSS 本地密码锁定**：V1164 迁移会锁定 OSS 同步用户的本地密码。若后续有 OSS 用户需要本地密码登录的场景，需额外设计开关或白名单机制。
3. **CRM 鉴权路径调整**：!2002 删除了全局 `03595` happy path，所有 CRM 调用必须携带有效用户 OSS token；无上下文流量已切到系统集成账号，需监控相关集成点。
4. **并发限流保护**：BAR 站点列表与平台账户列表已增加并发控制，但其他页面若仍存在 N+1 detail 请求，可能继续触发 429，需持续关注。
5. **任务分支集中度**：当前 trae 有 2 个活跃任务分支，本次部署任务完成后应及时清理 `agent/trae/test-env-upgrade-20260710`。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操 SOP 完成
- [x] Git 安全 wrapper 检查通过
- [x] GitHub 镜像已同步
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（BUILD SUCCESS，jar 内无重复迁移）
- [x] 产物校验通过（前端入口一致）
- [x] 服务重启成功（systemctl active running）
- [x] 健康检查 UP（所有组件正常）
- [x] Smoke 测试 8/8 通过
- [x] Flyway V1164 已应用
- [x] 无临时调试配置残留
- [x] 部署报告已生成
