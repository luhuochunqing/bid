# 第 75 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `d673fced0-api8080` |
| 部署时间 | 2026-07-10 23:16:44 CST |
| 部署人 | trae agent |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/fix-webhook-crm-code-lookup-username`（任务分支，部署从 origin/main 构建） |
| HEAD commit | `d673fced0`（origin/main） |
| origin/main | `d673fced0`（同步） |
| 上次部署 releaseId | `2440ad069-api8080`（第 74 次） |
| 增量 commit 数 | 7 |
| 增量 PR 数 | 2（!2007 / !2005） |
| Flyway 迁移变更 | 无 |
| GitHub 镜像落后数 | 0（已同步） |
| 构建方式 | 临时 worktree（`/tmp/xiyu-deploy-tmp` → `origin/main`），未触碰本地未提交文件 |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !2007 | fix(integration): 标讯修改接口支持项目负责人字段 | fix |
| !2005 | fix(resource): 平台账户列表 detail 并发从 5 降至 2，避免生产环境仍触发 429 | fix |

## 改动范围

| 层 | 文件数 | 说明 |
|---|---|---|
| 前端代码 | 1 | `src/views/Resource/Account.vue`（detail 并发从 5 降至 2） |
| 前端测试 | 1 | `src/views/Resource/__tests__/Account.spec.js`（并发控制测试） |
| 后端代码 | 1 | 标讯修改接口支持项目负责人字段 |
| 文档 | 3 | 第 74 次测试部署报告、第 5 次生产部署报告等 |
| 数据库迁移 | 0 | 无 |
| **合计** | **8** | |

### 核心改动说明

1. **标讯修改接口支持项目负责人字段（!2007）**：标讯修改接口新增项目负责人字段支持，完善 CRM 集成链路。
2. **平台账户列表 detail 并发降至 2（!2005）**：将前端 N+1 detail 请求并发从 5 降至 2，进一步避免生产环境触发 429 限流。

## Flyway 预检结果

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK | 227 migrations validated, all checksums match |
| Step 2: DB 已应用版本对比 | ✅ 无新增 | DB 最新 V1164，源码无新增迁移 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 | 部署时自动 validate 通过 |

## 部署步骤

| 步骤 | 时间 | 结果 |
|---|---|---|
| 环境门禁确认 | 23:11 | ✅ 测试环境 172.16.38.78 已确认 |
| 早操 SOP | 23:11 | ✅ sync-env.sh 完成，origin/main 已同步 |
| 服务器现状检查 | 23:12 | ✅ 当前 release `2440ad069-api8080`，health UP |
| Flyway 预检 3 步 | 23:14 | ✅ 全部通过 |
| 临时 worktree 创建 | 23:14 | ✅ `/tmp/xiyu-deploy-tmp` → `origin/main`（保护本地未提交文件） |
| 本地打包（package-release.sh） | 23:15 | ✅ 前端 8.34s + 后端 25.28s，jar 内 226 迁移文件无重复 |
| 产物校验 | 23:15 | ✅ 前端入口 `assets/index-CiPPyhC3.js` |
| 上传 archive + remote-deploy.sh | 23:16 | ✅ scp 完成 |
| remote-deploy.sh 执行 | 23:16 | ✅ Flyway validate 通过、jar 已激活、服务已重启 |
| 健康检查 | 23:18 | ✅ 79 次尝试，3/3 连续通过 |
| Smoke 测试 | 23:19 | ✅ 8/8 通过 |
| GitHub 镜像同步 | 23:20 | ✅ Gitee ↔ GitHub main 完全一致 |
| 临时 worktree 清理 | 23:20 | ✅ 已清理 |

## 验证结果

### 健康检查

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | — |
| aiProvider | UP | provider=custom, model=qwen3.7-max, apiKeyConfigured=true |
| db | UP | MySQL, isValid() |
| diskSpace | UP | free=27.1GB |
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
| 8 | 前端 index.html 入口 | assets/index-CiPPyhC3.js | assets/index-CiPPyhC3.js | ✅ |

### deployed-release.json

```json
{
  "releaseId": "d673fced0-api8080",
  "activatedAt": "2026-07-10T15:16:44Z",
  "releaseDir": "/opt/xiyu-bid/releases/d673fced0-api8080",
  "packageMetadata": {
    "releaseId": "d673fced0-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-10T15:15:48Z",
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
| 回滚方式 | 恢复 `/opt/xiyu-bid/releases/2440ad069-api8080/backend/app.jar` + sudo systemctl restart xiyu-bid-backend |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-d673fced0-api8080-202607102316xx.sql.gz` |
| 回滚必要性 | 无需回滚（部署验证通过） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 第 1 条：Flyway 预检 3 步法 | ✅ 全部执行，227 migrations validate OK |
| 第 3 条：前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空，apiBaseUrl="" |
| 第 8 条：SYSTEMCTL_SUDO=true | ✅ 已设置，服务重启成功 |
| 第 16 条：Mac HTTP_PROXY 502 | ✅ curl 统一加 --noproxy '*' |
| 第 14 条：macOS ._* 残留文件 | ✅ 打包时设置 COPYFILE_DISABLE=1 |

## 风险提示

1. **任务分支未推送**：当前任务分支 `agent/trae/fix-webhook-crm-code-lookup-username` 有 1 个未推送 commit + 未提交文件，需后续处理。
2. **并发限流保护**：平台账户列表 detail 并发已降至 2，若其他页面仍存在 N+1 detail 请求，可能继续触发 429，需持续关注。

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
- [x] 无临时调试配置残留
- [x] 部署报告已生成
