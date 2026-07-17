# 第 94 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `dd4cac47a-api8080` |
| 上一版本 | `a33c1339d-api8080`（第 93 次，2026-07-17 16:17） |
| 部署时间 | 2026-07-17 20:40 CST |
| 增量 | 4 commit（仓库附件下载修复 + 第 93 次部署报告） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（Kafka SDK readiness 延迟约 5 分钟自恢复） |
| 回滚 | 未需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-test-94th（基于 origin/main） |
| HEAD commit | `dd4cac47a8e48b707bec45f896aaa3032444e3c3` |
| GitHub 镜像 | 完全一致（dd4cac47a） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2116 | fix(warehouse) | 修复附件下载无反应与导出合订本文件缺失 |
| !2114 | docs(release) | 第 93 次测试环境部署报告 |

## 改动范围

本次部署为纯代码修复，无数据库迁移：

### 1. 仓库模块附件修复（!2116）
- **WarehouseWordBundleBuilder.java**：修复 Word 合订本构建逻辑，解决附件下载无反应与导出合订本文件缺失问题
- **application-dev.yml**：开发环境配置调整
- **WarehouseDrawer.vue**：仓库抽屉组件交互修复
- **Warehouse.vue / CaseSearchCard.vue**：仓库与案例搜索卡片 UI 微调

### 2. 部署报告归档（!2114）
- 第 93 次测试环境部署报告合入 main

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 vs 源码版本 | ✅ 一致（V1168，无新增迁移） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（仅 pending 新迁移为预期状态） |

## 部署步骤

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 早操三连（sync-env.sh + check-git-wrapper.sh） | ✅ 门禁 7/7 通过 |
| 2 | 创建任务分支 agent/trae/deploy-test-94th | ✅ 基于 origin/main |
| 3 | 服务器现状检查（deployed-release.json + health） | ✅ 上一版本 a33c1339d，UP |
| 4 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 5 | 本地打包（RELEASE_ID=dd4cac47a-api8080, VITE_OBS_ENABLED=true, VITE_API_BASE_URL=） | ✅ BUILD SUCCESS |
| 6 | 产物校验（obsEnabled=true, jar 230迁移文件无重复, 前端入口 index-lq6saM7R.js） | ✅ 全部通过 |
| 7 | scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功 |
| 8 | 前端资源保留（从 a33c1339d-api8080 cp -rn 旧 assets） | ✅ 已保留 |

## 验证结果

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP（所有组件 UP） |
| /actuator/health/readiness | 200 UP |
| 恢复时间 | 约 4 分 52 秒（Kafka SDK readiness 延迟，已知行为） |

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
| Gitee main | dd4cac47a |
| GitHub main | dd4cac47a |
| 状态 | ✅ 完全一致 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/a33c1339d-api8080/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/a33c1339d-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-dd4cac47a-*.sql.gz` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/a33c1339d-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，健康检查 120 次未通过后手动验证已恢复 |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true 显式传入 + 产物校验 obsEnabled=true |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从上一版本 release 目录 cp -rn 旧 assets |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次恢复约 5 分钟，remote-deploy.sh 内置 120 次重试（4 分钟）未覆盖。建议延长重试次数至 180 次（6 分钟）
2. **deployed-release.json 覆盖问题**：remote-deploy.sh 覆盖后 PREV 变量取值失效，需手动指定旧 release 目录进行前端资源保留

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过
- [x] 远程部署成功
- [x] 健康检查 UP
- [x] Smoke 测试全通过
- [x] 前端资源保留完成
- [x] GitHub 镜像同步一致
- [x] 部署报告已生成
