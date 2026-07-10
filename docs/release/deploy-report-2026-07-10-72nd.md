# 第 72 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `19a1248c7-api8080` |
| 部署时间 | 2026-07-10 18:23:38 CST |
| 部署人 | trae agent |
| 特殊说明 | Kafka SDK readiness 延迟导致 remote-deploy.sh 健康检查 120 次超时，手动验证后确认已自恢复 UP（已知行为，见经验沉淀第 2 条） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/github-sync-20260710181613`（HEAD = origin/main） |
| HEAD commit | `19a1248c7` |
| origin/main | `19a1248c7`（同步） |
| 上次部署 releaseId | `460ccb5d7-api8080`（第 71 次） |
| 增量 commit 数 | 17 |
| 增量 PR 数 | 7（!1985-!1991） |
| Flyway 迁移变更 | V1163__add_operator_username_to_webhook_delivery_tasks.sql |
| GitHub 镜像落后数 | 0（已同步） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1991 | chore: 同步 GitHub 过期锁清理改动（GitHub → Gitee） | chore |
| !1990 | feat(crm-webhook): CRM 回调链路改为按用户身份调用（CO-152 补齐） | feature |
| !1989 | fix(auth): OSS 父菜单缺失时自动补 resource 父权限 | bugfix |
| !1988 | docs(release): 第 3 次生产部署报告 + 第 71 次测试部署报告 + 鉴权审计报告 | docs |
| !1987 | fix(auth): OSS 登录本地无记录时自动创建 User，恢复"OSS 实时鉴权为唯一真相源"设计意图 | bugfix |
| !1986 | fix(ai): jsonObjectPrompt 加入小写 json 关键词，适配 dashscope 网关 | bugfix |
| !1985 | fix(sentry): 过滤 Vite chunk 404 噪声，避免自愈 reload 触发 Sentry 误报 | bugfix |

## 改动范围

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端主代码 | ~22 | CRM webhook 用户身份调用、OSS 自动创建用户、token 缓存、webhook operator_username 等 |
| 后端测试 | ~10 | OssDirectLoginServiceTest, OssUserTokenCacheTest, UserDetailsServiceImplTest 等 |
| 迁移脚本 | 2 | V1163 + U1163（webhook_delivery_tasks 增加 operator_username） |
| 前端代码 | 2 | sentry.js, sentry.spec.js |
| 文档/Wiki | 3 | 第 3 次生产部署报告、第 71 次测试部署报告、鉴权审计报告 |
| 治理脚本 | 1 | pre-push-gate.sh 增加 hasAnyRole 检查 |
| **合计** | **54** | |

### 核心改动说明

1. **CRM webhook 用户身份调用（!1990）**：CRM 回调链路改为按用户身份调用，涉及 CrmAuthService、WebhookCrmTokenResolver 等。
2. **OSS 登录自动创建用户（!1987）**：OSS 登录本地无记录时自动创建 User，恢复"OSS 实时鉴权为唯一真相源"设计意图，新增 OssUserAutoCreator、UserProfileCache、OssUserTokenCache 等。
3. **OSS 父菜单权限补齐（!1989）**：OSS 父菜单缺失时自动补 resource 父权限。
4. **Sentry 噪声过滤（!1985）**：过滤 Vite chunk 404 噪声，避免自愈 reload 触发 Sentry 误报。
5. **AI json schema 小写适配（!1986）**：jsonObjectPrompt 加入小写 json 关键词，适配 dashscope 网关。
6. **Webhook operator_username（V1163）**：webhook_delivery_tasks 表增加 operator_username 字段。
7. **GitHub 锁清理同步（!1991）**：删除过期 agent lock 文件。

## Flyway 预检结果

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK | 225 migrations validated, all checksums match |
| Step 2: DB 已应用版本对比 | ✅ 一致 | DB 最新 V1162，源码新增 V1163 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 | 部署时自动 validate 通过 |

## 部署步骤

| 步骤 | 时间 | 结果 |
|---|---|---|
| 环境门禁确认 | 18:15 | ✅ 用户确认测试环境 172.16.38.78 |
| 早操 SOP（sync-env.sh） | 18:19 | ✅ 门禁 7/7 通过 |
| PR !1991 合并 | 18:20 | ✅ squash 合并，删除源分支 |
| Flyway 预检 3 步 | 18:21 | ✅ 全部通过 |
| 本地打包（package-release.sh） | 18:22 | ✅ BUILD SUCCESS（28s），jar 内 225 迁移文件无重复 |
| 产物校验 | 18:22 | ✅ 前端入口 assets/index-B6kzrguT.js，153M |
| 上传 archive + remote-deploy.sh | 18:23 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 18:23:38 | ✅ Flyway validate 通过，服务重启，V1163 迁移执行 |
| 健康检查（120 次 * 2s） | 18:23-18:27 | ⚠️ 超时（Kafka SDK readiness 延迟） |
| 手动健康检查 | 18:27+ | ✅ 已自恢复 UP，所有组件正常 |

## 验证结果

### 健康检查（手动）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | — |
| aiProvider | UP | provider=custom, model=qwen3.7-max, apiKeyConfigured=true |
| db | UP | MySQL, isValid() |
| diskSpace | UP | free=27.8GB |
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
| 8 | 前端 index.html 入口 | assets/index-B6kzrguT.js | assets/index-B6kzrguT.js | ✅ |

### Flyway 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1163 | add operator username to webhook delivery tasks | 1 | 2026-07-10 18:23:46 |

### deployed-release.json

```json
{
  "releaseId": "19a1248c7-api8080",
  "activatedAt": "2026-07-10T10:23:38Z",
  "releaseDir": "/opt/xiyu-bid/releases/19a1248c7-api8080",
  "packageMetadata": {
    "releaseId": "19a1248c7-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T10:22:30Z",
    "sentryEnabled": false
  }
}
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| github/main vs origin/main | 0 commit 落后（已同步） |
| 同步操作 | `bash scripts/sync-to-github.sh` 成功 |
| Gitee main | 19a1248c7bbf843dcd4e3211a6466b932792e413 |
| GitHub main | 19a1248c7bbf843dcd4e3211a6466b932792e413 |

## 临时配置清理

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS / DEBUG / TRACE | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 用户第 13/14/15 次决定保留，非临时配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | 恢复 `/opt/xiyu-bid/releases/460ccb5d7-api8080/backend/app.jar` + sudo systemctl restart xiyu-bid-backend |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-19a1248c7-api8080-<timestamp>.sql.gz` |
| 回滚必要性 | 无需回滚（部署验证通过） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 第 2 条：Kafka SDK readiness 延迟 | ✅ 已识别，120 次超时后手动验证确认自恢复 UP |
| 第 1 条：Flyway 预检 3 步法 | ✅ 全部执行，225 migrations validate OK |
| 第 3 条：前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空，apiBaseUrl="" |
| 第 8 条：SYSTEMCTL_SUDO=true | ✅ 已设置，服务重启成功 |
| 第 16 条：Mac HTTP_PROXY 502 | ✅ curl 统一加 --noproxy '*' |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次延迟超过 4 分钟（remote-deploy.sh 健康检查窗口），建议未来考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池，避免阻塞主线程影响 AvailabilityChangeEvent 处理。
2. **V1163 新增列**：webhook_delivery_tasks 表新增 operator_username 字段，为可空列，不影响历史数据。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操 SOP 完成（sync-env.sh + 7/7 门禁）
- [x] PR !1991 已合并到 main
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（BUILD SUCCESS，jar 内无重复迁移）
- [x] 产物校验通过（前端入口一致）
- [x] 服务重启成功（systemctl active running）
- [x] 健康检查 UP（所有组件正常）
- [x] Smoke 测试 8/8 通过
- [x] V1163 迁移已应用
- [x] GitHub 镜像已同步
- [x] 无临时调试配置残留
- [x] 部署报告已生成
