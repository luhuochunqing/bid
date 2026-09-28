# 第 218 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `9120cd06a` |
| 部署时间 | 2026-09-29 06:43 CST（activatedAt 22:43:15Z） |
| 上一版本 | `cdf19b510` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae-init`（锚点，HEAD = origin/main）
- HEAD commit：`9120cd06a` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`（前端无改动，与上一版本一致）
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `cdf19b510..9120cd06a`，共 14 个 commit）

本次部署核心为 scoreparse 项目 1382 计分逻辑迭代，**无数据库迁移文件变更**：

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 零分指引和空模型总分不再否决已闭合的 100 分（项目 1382，!2795） |
| fix(scoreparse) | 价格同义词、换行归一和方案外判定（!2796 合入） |
| fix(scoreparse) | resolve project 1382 modelTotal 与 zero-weight guidelines 自动批准 |
| fix(hooks) | pre-push 合并 unified-dev-flow S6 审查门禁（恢复被覆写的 5 道传统门禁，!2793） |
| chore | gitignore 忽略 Qoder CLI 本地运行时目录 `.qoder/`（!2794） |
| docs | 第 217 次部署报告、wiki 回填、scoreparse 3 个月修复路线图精简 |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本对比：DB 最新 = V1220，源码最高 = **V1220**，完全对齐
- Step 3 remote-deploy 内置 validate → 通过（本次无新迁移，仅 pending 状态预期）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 早操三连：sync-env.sh 同步基线（HEAD=origin/main，init 分支 ff-only 同步）
3. 服务器现状检查：上一版本 `cdf19b510`，后端 health UP
4. Flyway 预检 3 步 → 通过
5. 本地打包：`RELEASE_ID=9120cd06a`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
6. 产物校验：jar 内迁移无重复、**OBS obsEnabled=true**、index.html 入口 `index-CDf8nFsr.js`
7. 上传 `xiyu-bid-release-9120cd06a.tar.gz` + 部署（SYSTEMCTL_SUDO=true）
8. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（3/3）

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 79 次尝试通过 |
| Flyway 迁移 | 无新迁移，版本对齐 V1220（V1219/V1220 success=1） |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 前端资源保留 | 已从上一版本 `cdf19b510` 保留 assets |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `9120cd06a`；上一已知良好版本 `cdf19b510`
- 后端旧 jar：`/opt/xiyu-bid/releases/cdf19b510/backend/`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`remote-deploy.sh` 在部署前自动执行 mysqldump 到 `/opt/xiyu-bid/db-backups/`
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 确认测试环境 172.16.38.78 后才执行
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- 前端资源保留：✅ 防 hash 资源跨版本 404（skill 经验第 17 条）
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ COPYFILE_DISABLE=1
- 锚点分支早操：✅ 使用 sync-env.sh 自带 `XYYU_SYNC_ENV_ACTIVE=1` 维护模式，绕过 session-gate 锚点拦截（ff-only 同步，非开发）

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 若出现短暂 503（Kafka SDK 延迟）为已知行为，本次未出现
- 本次为 scoreparse 1382 计分逻辑迭代（零分指引/空模型总分不再否决已闭合 100 分），建议在测试环境对项目 1382 做一次 100 分自动落库的功能回归验证

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致 + 资源保留
- [x] 配置清理检查完成
