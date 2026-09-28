# 第 217 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `cdf19b510` |
| 部署时间 | 2026-09-28 20:35 CST（activatedAt 12:35:30Z） |
| 上一版本 | `6f8b6ed21` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae-init`（锚点，HEAD = origin/main）
- HEAD commit：`cdf19b510` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `6f8b6ed21..cdf19b510`）

本次部署包含 6 个 commit 的 scoreparse 项目 1382 修复系列，**无数据库迁移文件变更**：

| commit | 内容 |
|---|---|
| `3b38f72d8` | docs(release)：第 216 次部署报告（test）合入（!2789） |
| `a7dacfbed` | auto-merge !2789（第 216 次部署报告） |
| `392188f9f` | fix(scoreparse)：mult-table outline 父项证据 + 声明总分门禁（项目 1382） |
| `86d07ed7f` | docs(wiki)：回填 1382 Task 285 线上双表拓扑与同格多分值解耦根因 |
| `b0d8363d4` | fix(scoreparse)：恢复严格 document total 正则 + 清理尾部空格 |
| `cdf19b510` | **!2790** fix(scoreparse)：解决项目 1382 大纲父项权重匹配与双表门禁阻断，实现 100 分自动落库 |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本对比：DB 最新 = V1220，源码最高 = **V1220**，完全对齐
- Step 3 remote-deploy 内置 validate → 通过（本次无新迁移，仅 pending 状态预期）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 早操三连：sync-env.sh 同步基线（HEAD=origin/main）
3. 服务器现状检查：上一版本 `6f8b6ed21`，后端 health UP
4. Flyway 预检 3 步 → 通过
5. 本地打包：`RELEASE_ID=cdf19b510`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
6. 产物校验：jar 内迁移无重复、**OBS obsEnabled=true**、index.html 入口 `index-CDf8nFsr.js`
7. 上传 `xiyu-bid-release-cdf19b510.tar.gz`（156M）+ 部署（SYSTEMCTL_SUDO=true）
8. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（3/3）

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 80 次尝试通过 |
| Flyway 迁移 | 无新迁移，版本对齐 V1220（V1219/V1220 success=1） |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 前端资源保留 | 已从上一版本 `6f8b6ed21` 保留 assets |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `cdf19b510`；上一已知良好版本 `6f8b6ed21`
- 后端旧 jar：`/opt/xiyu-bid/releases/6f8b6ed21/backend/`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`remote-deploy.sh` 在部署前自动执行 mysqldump 到 `/opt/xiyu-bid/db-backups/`
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 确认测试环境 172.16.38.78 后才执行
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- 前端资源保留：✅ 防 hash 资源跨版本 404（skill 经验第 17 条）
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ COPYFILE_DISABLE=1（首次出现逐条 xattr 警告，为可忽略噪音）
- 工作区门禁异常：✅ 部署前发现 `.githooks/pre-push` 未提交改动，经用户确认「保留」后继续

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 若出现短暂 503（Kafka SDK 延迟）为已知行为，本次未出现
- 本次为 scoreparse 1382 计分逻辑迭代，建议在测试环境对项目 1382 做一次 100 分自动落库的功能回归

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致 + 资源保留
- [x] 配置清理检查完成