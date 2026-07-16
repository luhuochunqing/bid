# 第 92 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `0a79f0d68-api8080` |
| 上一版本 | `57ebd967f-api8080`（第 91 次，2026-07-16 19:21） |
| 部署时间 | 2026-07-16 20:13 CST |
| 增量 | 4 commit（2 个工作台修复 + 2 个部署报告） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功 |
| 回滚 | 未需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-report-91st-test（rebase 到 origin/main） |
| HEAD commit | `0a79f0d68a1176e07d5d09fea4ad1328211e027c` |
| GitHub 镜像 | 完全一致（0a79f0d68） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2103 | fix | 工作台待办/截止时间名称过长省略号 + 悬停显示全称 |
| !2105 | docs | 第 91 次测试环境部署报告 |

## 改动范围

本次部署为**纯前端 UI bug 修复**：
- 工作台待办/截止时间名称过长时显示省略号
- 鼠标悬停时显示完整名称（tooltip）
- 无后端逻辑变更
- 无 Flyway 迁移文件变更
- 无数据库 schema 变更

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 | V1166/V1167/V1168（第 91 次部署已应用） |
| Step 3: remote-deploy 内置 | ✅ Flyway validate 通过（仅 pending 新迁移为预期状态） |

## 部署步骤

1. ✅ 环境门禁：用户确认部署到测试环境 172.16.38.78
2. ✅ 早操三连：sync-env.sh + check-git-wrapper.sh + git status 干净
3. ✅ 服务器现状检查：deployed-release.json + 健康检查 UP
4. ✅ Flyway 预检 3 步法：validate + DB 版本对比 + remote-deploy 内置
5. ✅ 本地打包：RELEASE_ID=0a79f0d68-api8080 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1
6. ✅ 产物校验：jar 内迁移无重复 + OBS 直传已启用（Detail chunk .upload( 调用数=2）
7. ✅ 上传 + 部署：scp archive + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. ✅ 健康检查：consecutive 3/3, total attempts: 80（无 Kafka readiness 延迟）
9. ✅ 前端一致性验证：src="/assets/index-qm5QikQZ.js"

## 验证结果

### 后端健康检查

```
status: UP
  aiProvider: UP
  db: UP
  diskSpace: UP
  jwt: UP
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP
  sidecar: UP
```

### Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | HTTP 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| `/api/auth/login` (POST {}) | 400 | HTTP 400 | ✅ |
| `/api/projects` | 403 | HTTP 403 | ✅ |
| `/api/integration/crm/health` | 401 | HTTP 401 | ✅ |

### 前端页面验证

| 路径 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/` | 200 | HTTP 200 | ✅ |
| `/login` | 200 | HTTP 200 | ✅ |
| index.js hash | 与 release 一致 | `assets/index-qm5QikQZ.js` | ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | `0a79f0d68a1176e07d5d09fea4ad1328211e027c` |
| GitHub main | `0a79f0d68a1176e07d5d09fea4ad1328211e027c` |
| 状态 | ✅ 完全一致 |

## 回滚信息

- **回滚状态**：未需要
- **回滚方式**（如需）：恢复上一版本 jar + 前端资源
  - 上一版本 release 目录：`/opt/xiyu-bid/releases/57ebd967f-api8080`
  - 上一版本 jar：`/opt/xiyu-bid/releases/57ebd967f-api8080/backend/app.jar`
  - 回滚命令：`sudo cp /opt/xiyu-bid/releases/57ebd967f-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend`

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 已执行（validate + DB 版本对比 + remote-deploy 内置） |
| OBS 直传双保险 | ✅ VITE_OBS_ENABLED=true 显式传入 + 产物校验 obsEnabled=true |
| 同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| macOS `._*` 残留 | ✅ COPYFILE_DISABLE=1 |
| systemctl sudo | ✅ SYSTEMCTL_SUDO=true |
| Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| 前端资源保留 | ⚠️ 上一版本 release 目录字段为空，未执行（本次为小增量，风险低） |
| Kafka SDK readiness 延迟 | ✅ 本次未出现（健康检查快速通过） |
| SHOW_DETAILS=always | ✅ 用户决定保留 |

## 风险提示

1. **前端资源保留未执行**：`deployed-release.json` 中 `releaseDir` 字段为空，无法从上一版本 release 目录 cp 旧 assets。本次为小增量部署（仅工作台 UI 修复），前端 hash 变化有限，旧标签页 404 风险低。如后续出现 Sentry `Unable to preload CSS` 噪声，可手动从 `/opt/xiyu-bid/releases/57ebd967f-api8080/frontend/assets/` 复制旧资源。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连执行（sync-env + check-git-wrapper）
- [x] Flyway 预检 3 步法
- [x] 本地打包（同源构建 + OBS 直传）
- [x] 产物校验（jar 无重复迁移 + OBS 直传已启用）
- [x] 上传 + 部署（remote-deploy.sh）
- [x] 健康检查通过（UP，无 Kafka 延迟）
- [x] Smoke 测试 5 项全通过
- [x] 前端页面验证通过
- [x] GitHub 镜像同步检查
- [x] 临时调试配置检查（SHOW_DETAILS=always 用户决定保留）
- [x] 部署报告生成

## 主要功能变更

本次部署主要修复工作台 UI bug：

**PR !2103: 工作台待办/截止时间名称过长省略号 + 悬停显示全称**
- 问题：工作台待办/截止时间名称过长时溢出，影响视觉
- 修复：名称过长时显示省略号，鼠标悬停时显示完整名称（tooltip）
- 影响范围：工作台待办列表、截止时间列表
