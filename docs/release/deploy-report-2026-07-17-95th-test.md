# 第 95 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `c7aed82c1-api8080` |
| 上一版本 | `dd4cac47a-api8080`（第 94 次，2026-07-17 20:40） |
| 部署时间 | 2026-07-17 21:41 CST |
| 增量 | 4 commit（!2118 warehouse.attachment.root 配置修复 + !2117 第 94 次部署报告） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（健康检查 78 次，约 2.5 分钟通过） |
| 回滚 | 未需要 |

## 部署原因

第 94 次部署缺少 `warehouse.attachment.root` 环境变量配置，导致仓库附件下载功能异常。PR !2118 在 `application-prod.yml` 中补全该配置，本次重新打包部署后端修复此问题。

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-test-95th（基于 origin/main） |
| HEAD commit | `c7aed82c104b783aeb6b25f1b4bccf12e5a47d9d` |
| GitHub 镜像 | 完全一致（c7aed82c1） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2118 | fix(warehouse) | application-prod.yml 补全 warehouse.attachment.root 配置 |
| !2117 | docs(release) | 第 94 次测试环境部署报告 |

## 改动范围

### 1. 仓库附件配置修复（!2118）
- **application-prod.yml**：新增 `warehouse.attachment.root: ${WAREHOUSE_ATTACHMENT_ROOT:/data/attachments/warehouse}` 配置项
- 修复第 94 次部署缺少该环境变量导致仓库附件下载功能异常的问题

### 2. 部署报告归档（!2117）
- 第 94 次测试环境部署报告合入 main

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 vs 源码版本 | ✅ 一致（V1168，无新增迁移） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

## 部署步骤

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 清理旧任务分支 + 拉取最新 main | ✅ agent/trae-init ff-only 到 c7aed82c1 |
| 2 | 创建任务分支 agent/trae/deploy-test-95th | ✅ 基于 origin/main |
| 3 | 服务器现状检查 | ✅ 上一版本 dd4cac47a，UP |
| 4 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 5 | 本地打包（RELEASE_ID=c7aed82c1-api8080, OBS=true, 同源构建） | ✅ BUILD SUCCESS |
| 6 | 产物校验（obsEnabled=true, 230迁移文件, **warehouse.attachment.root 已包含**） | ✅ 全部通过 |
| 7 | scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功 |
| 8 | 前端资源保留（从 dd4cac47a-api8080 cp -rn 旧 assets） | ✅ 已保留 |

## 验证结果

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP |
| /actuator/health/readiness | 200 UP |
| 健康检查通过次数 | 78 次（约 2.5 分钟） |

### Smoke 测试

| 测试项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 UP | 200 UP | ✅ |
| /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| /api/auth/login（空密码） | 400 | 400 参数校验失败 | ✅ |
| /api/projects（需认证） | 403 | 403 | ✅ |
| /api/integration/crm/health | 401 | 401 | ✅ |
| 前端首页 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口 JS | index-lq6saM7R.js | index-lq6saM7R.js | ✅ |

### GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | c7aed82c1 |
| GitHub main | c7aed82c1 |
| 状态 | ✅ 完全一致 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/dd4cac47a-api8080/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/dd4cac47a-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-c7aed82c1-*.sql.gz` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/dd4cac47a-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true + 产物校验 obsEnabled=true |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从 dd4cac47a-api8080 cp -rn 旧 assets |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |

## 风险提示

无新风险。本次部署为配置补全修复，健康检查 2.5 分钟通过（优于上次 5 分钟）。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过（**warehouse.attachment.root 已包含在 jar 内**）
- [x] 远程部署成功
- [x] 健康检查 UP
- [x] Smoke 测试全通过
- [x] 前端资源保留完成
- [x] GitHub 镜像同步一致
- [x] 部署报告已生成
