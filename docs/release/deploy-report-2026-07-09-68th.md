# 第 68 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `dceae804e-api8080` |
| 部署时间 | 2026-07-09 20:42:53 CST |
| 部署人 | trae agent |
| 特殊说明 | 第 67 次部署后的增量部署，无新增 Flyway 迁移 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，仅同步不开发） |
| HEAD commit | `dceae804e` |
| origin/main | `dceae804e`（同步） |
| 上次部署 releaseId | `7eafbe7f7-api8080`（第 67 次） |
| 增量 commit 数 | 10 |
| 增量 PR 数 | 5（!1959-!1963） |
| 新增 Flyway 迁移 | 无 |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1959 | feat(drafting): 评分项为空时提示前往AI评分标准解析 | feat |
| !1961 | chore(ui): 隐藏企业微信登录和AI自动拆解任务按钮 | chore |
| !1962 | fix(sidebar): 父菜单仅有 1 个子菜单时直接可点击直达 | bugfix |
| !1963 | fix(bidding-list): 统一标讯列表分页默认值为 10 条/页 | bugfix |
| !1960 | docs(release): 第 67 次部署报告 (test) | docs |

## 改动范围

- **标讯列表分页默认值统一**（PR !1963）
  - 统一标讯列表分页默认值为 10 条/页
- **侧边栏菜单交互优化**（PR !1962）
  - 父菜单仅有 1 个子菜单时直接可点击直达，不再展开二级
- **评分项空提示**（PR !1959）
  - 评分项为空时提示前往 AI 评分标准解析
- **UI 清理**（PR !1961）
  - 隐藏企业微信登录和 AI 自动拆解任务按钮
- **部署报告**（PR !1960）
  - 第 67 次部署报告归档

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（224 migrations） |
| Step 2: DB 已应用版本 | V1161（最新，与源码一致，无 pending） |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 环境门禁确认（test / 172.16.38.78）
2. ✅ 早操三连（dev-env.sh + sync-env.sh + check-git-wrapper.sh）
3. ✅ 基线确认（HEAD = origin/main = dceae804e，工作区干净）
4. ✅ 服务器现状检查（7eafbe7f7 健康 UP，readinessState UP）
5. ✅ Flyway 预检 3 步法全部通过（无新增迁移）
6. ✅ 本地打包（RELEASE_ID=dceae804e-api8080，VITE_API_BASE_URL= 同源构建，28s）
7. ✅ 产物校验（jar 内 223 个 V*.sql 迁移文件无重复，前端入口 index-DEO9zLX7.js）
8. ✅ 上传 + 部署（scp + remote-deploy.sh SYSTEMCTL_SUDO=true）
9. ✅ 健康检查通过（连续 3/3，总尝试 78 次，约 2 分 36 秒）
10. ✅ 前端一致性验证（`/assets/index-DEO9zLX7.js` 与 release 一致）

## 验证结果

### 后端健康检查

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP（provider=custom, model=qwen3.7-max） |
| db | UP（MySQL, isValid()） |
| diskSpace | UP（free 29.2GB / total 105.5GB） |
| jwt | UP（HMAC-SHA256, secretLength=64, STRONG） |
| livenessState | UP |
| readinessState | UP（无 Kafka SDK 延迟） |
| redis | UP（version 6.2.19） |
| sidecar | UP（http://localhost:8000, reachable） |

### Smoke 测试

| 接口 | 预期 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects (无认证) | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |

### 前端验证

| 检查项 | 结果 |
|---|---|
| 首页 / HTTP | 200 ✅ |
| 登录页 /login HTTP | 200 ✅ |
| 前端入口 | assets/index-DEO9zLX7.js（与 release 一致）✅ |

### 部署记录

```json
{
  "releaseId": "dceae804e-api8080",
  "activatedAt": "2026-07-09T12:42:53Z",
  "releaseDir": "/opt/xiyu-bid/releases/dceae804e-api8080",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "packageMetadata": {
    "releaseId": "dceae804e-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "sentryEnabled": false
  }
}
```

## 配置清理检查

| 项目 | 结果 |
|---|---|
| `SPRING_CONFIG_IMPORT` | ✅ 无（已在前次部署清理） |
| `SHOW_DETAILS=always` | ℹ️ 保留（用户已决定，非临时配置） |
| DEBUG/TRACE 临时配置 | ✅ 无 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 10 commit |
| 同步后状态 | ✅ 完全一致（Gitee main = GitHub main = dceae804e） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 releaseId | `7eafbe7f7-api8080`（第 67 次） |
| 回滚 releaseDir | `/opt/xiyu-bid/releases/7eafbe7f7-api8080` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-dceae804e-*.sql.gz` |
| 回滚所需操作 | 1) 恢复旧 jar: `cp /opt/xiyu-bid/releases/7eafbe7f7-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`; 2) 恢复旧前端: `rsync -a /opt/xiyu-bid/releases/7eafbe7f7-api8080/frontend/ /srv/www/xiyu-bid/`; 3) 重启服务: `sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 全部执行（无新增迁移，仍执行确认 DB 状态健康） |
| #3 生产前端同源构建（baseURL=""） | ✅ VITE_API_BASE_URL= 显式设空 |
| #6 临时调试配置清理 | ✅ 检查 backend.env，仅 SHOW_DETAILS=always（历史保留） |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #17 SentryAppender crash-loop | ✅ 未引入手动声明，sentryEnabled=false |

## 风险提示

- 本次部署无新增 Flyway 迁移，回滚不涉及 DB schema 回退（仅需恢复 jar + 前端）
- 本次改动均为前端 UI/交互优化，不涉及后端业务逻辑变更，风险较低
- 侧边栏菜单交互变更（PR !1962）可能影响用户操作习惯，需关注用户反馈

## 部署确认清单

- [x] 环境门禁确认
- [x] 早操三连（sync-env + git-wrapper）
- [x] 基线确认（HEAD = origin/main）
- [x] 服务器现状检查
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查通过
- [x] Smoke 测试通过
- [x] 前端一致性验证通过
- [x] 配置清理检查通过
- [x] GitHub 镜像同步
- [x] 部署报告生成
