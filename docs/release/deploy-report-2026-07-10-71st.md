# 第 71 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `460ccb5d7-api8080` |
| 部署时间 | 2026-07-10 14:13:50 CST |
| 部署人 | trae agent |
| 特殊说明 | Kafka SDK readiness 延迟导致 remote-deploy.sh 健康检查 120 次超时，手动验证后确认已自恢复 UP（已知行为，见经验沉淀第 2 条） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD commit | `460ccb5d7` |
| origin/main | `460ccb5d7`（同步） |
| 上次部署 releaseId | `e43709eea-api8080`（第 70 次） |
| 增量 commit 数 | 10 |
| 增量 PR 数 | 5（!1980-!1984） |
| Flyway 迁移变更 | 无（本次无新增迁移文件） |
| GitHub 镜像落后数 | 0（已同步） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1980 | refactor(spec-033): 设计评估修复 — 异常类型 + 逻辑重复 + 测试冗余 | refactor |
| !1981 | docs(release): 第 2 次生产环境部署报告 (prod) | docs |
| !1982 | fix(ai): 缓存 json_schema 不支持状态，避免双倍 AI 调用 | bugfix |
| !1983 | fix(resource): 账户/CA 页面向投标项目负责人开放只读全量视图 | bugfix |
| !1984 | test(ai): 补全 json_schema 缓存机制根因行为测试 + 沉淀教训 | test |

## 改动范围

| 层 | 文件数 | 说明 |
|---|---|---|
| 后端主代码 | 4 | UserDetailsServiceImpl, OpenAiSdkStructuredOutputTransport, PlatformAccountService, PlatformAccountViewerPolicy |
| 后端测试 | 5 | OssRoleNonDiffusionTest, OpenAiSdkStructuredOutputTransportTest(新增), PlatformAccountServiceTest, PlatformAccountViewerPolicyTest |
| 前端代码 | 3 | CAManagement.vue, CAManagement.spec.js, useCaBorrowEligibility.js |
| 文档/Wiki | 1 | engineering-discipline.md, deploy-report-2026-07-10-2nd-prod.md(新增) |
| **合计** | **13** | |

### 核心改动说明

1. **AI 缓存修复（!1982 + !1984）**：`OpenAiSdkStructuredOutputTransport` 缓存 json_schema 不支持状态，避免双倍 AI 调用。补全根因行为测试。
2. **资源权限调整（!1983）**：账户/CA 页面向投标项目负责人开放只读全量视图，调整 `PlatformAccountViewerPolicy` 和 `CAManagement.vue`。
3. **spec-033 重构（!1980）**：设计评估修复，异常类型 + 逻辑重复 + 测试冗余清理。
4. **OSS 权限不扩散（!1982 相关）**：`UserDetailsServiceImpl` 和 `OssRoleNonDiffusionTest` 调整。

## Flyway 预检结果

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK | 225 migrations validated, all checksums match |
| Step 2: DB 已应用版本对比 | ✅ 一致 | DB 最新 V1162（2026-07-10 12:22），源码无新增迁移 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 | 部署时自动 validate 通过 |

## 部署步骤

| 步骤 | 时间 | 结果 |
|---|---|---|
| 早操 SOP（sync-env.sh） | 14:04 | ✅ rebase 3 commit，门禁 7/7 通过 |
| Flyway 预检 3 步 | 14:11 | ✅ 全部通过 |
| 本地打包（package-release.sh） | 14:12 | ✅ BUILD SUCCESS（28s），jar 内 224 迁移文件无重复 |
| 产物校验 | 14:12 | ✅ 前端入口 assets/index-B6kzrguT.js，153M |
| 上传 archive + remote-deploy.sh | 14:13 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 14:13:50 | ✅ Flyway validate 通过，服务重启 |
| 健康检查（120 次 * 2s） | 14:13-14:17 | ⚠️ 超时（Kafka SDK readiness 延迟） |
| 手动健康检查 | 14:17+ | ✅ 已自恢复 UP，所有组件正常 |

## 验证结果

### 健康检查（手动）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | — |
| aiProvider | UP | provider=custom, model=qwen3.7-max, apiKeyConfigured=true |
| db | UP | MySQL, isValid() |
| diskSpace | UP | free=28.2GB |
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

### deployed-release.json

```json
{
  "releaseId": "460ccb5d7-api8080",
  "activatedAt": "2026-07-10T06:13:50Z",
  "releaseDir": "/opt/xiyu-bid/releases/460ccb5d7-api8080",
  "packageMetadata": {
    "releaseId": "460ccb5d7-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T06:12:51Z",
    "sentryEnabled": false
  }
}
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| github/main vs origin/main | 0 commit 落后（已同步） |
| 同步操作 | 无需操作 |

## 临时配置清理

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS / DEBUG / TRACE | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 用户第 13/14/15 次决定保留，非临时配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | 恢复 `/opt/xiyu-bid/releases/e43709eea-api8080/backend/app.jar` + sudo systemctl restart xiyu-bid-backend |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-460ccb5d7-api8080-<timestamp>.sql.gz` |
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
2. **无新增迁移**：本次无 Flyway 迁移文件变更，DB 版本保持 V1162。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操 SOP 完成（sync-env.sh + 7/7 门禁）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（BUILD SUCCESS，jar 内无重复迁移）
- [x] 产物校验通过（前端入口一致）
- [x] 服务重启成功（systemctl active running）
- [x] 健康检查 UP（所有组件正常）
- [x] Smoke 测试 8/8 通过
- [x] GitHub 镜像已同步
- [x] 无临时调试配置残留
- [x] 部署报告已生成
