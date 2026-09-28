# 第 216 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `6f8b6ed21` |
| 部署时间 | 2026-09-28 14:38 CST（activatedAt 06:38:47Z） |
| 上一版本 | `917e0404d` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae-init`（锚点，HEAD = origin/main）
- HEAD commit：`6f8b6ed21` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `917e0404d..HEAD`）

本次部署包含 2 个 PR，**无数据库迁移文件变更**：

| PR | commit | 内容 |
|---|---|---|
| !2787 | `07466ced7` ~ `3c44a2fc6` | 标讯创建推送接口 `customerName`（招标主体）改为必填；去重键 customerName 截断上限与落库口径对齐 255 |
| !2788 | `15ad2516a` ~ `6f8b6ed21` | fix(scoreparse)：修复跨章节隔离、PDF 表内续行与同格多 Peer 切分，完成 1382 全链路 100 分自动落库 |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本对比：DB 最新 = V1220，源码最高 = **V1220**，完全对齐
- Step 3 remote-deploy 内置 validate → 通过（仅 pending 新迁移为预期状态，本次无新迁移）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 早操三连：sync-env.sh 同步基线（HEAD=origin/main）
3. 服务器现状检查：上一版本 `917e0404d`，后端 health UP
4. Flyway 预检 3 步 → 通过
5. 本地打包：`RELEASE_ID=6f8b6ed21`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
6. 产物校验：jar 内迁移无重复、前端无 dev API、**OBS obsEnabled=true**、index.html 入口一致
7. 上传 `xiyu-bid-release-6f8b6ed21.tar.gz`（163MB）+ 部署（SYSTEMCTL_SUDO=true）
8. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP，redis/sidecar/db/jwt 均 UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 81 次尝试通过 |
| Flyway 迁移 | 无新迁移，版本对齐 V1220 |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 前端资源保留 | 已从上一版本 `917e0404d` 保留 assets（当前 193 个） |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `6f8b6ed21`；上一已知良好版本 `917e0404d`
- 后端旧 jar：`/opt/xiyu-bid/releases/917e0404d/backend/`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`remote-deploy.sh` 在部署前自动执行 mysqldump 到 `/opt/xiyu-bid/db-backups/`
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 未确认不执行
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- 前端资源保留：✅ 防 hash 资源跨版本 404
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ COPYFILE_DISABLE=1

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 若出现短暂 503（Kafka SDK 延迟）为已知行为，本次未出现

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致 + 资源保留
- [x] 配置清理检查完成