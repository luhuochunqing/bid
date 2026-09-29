# 第 220 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `658aa15ec` |
| 部署时间 | 2026-09-29 12:21 CST（activatedAt 04:21:53Z） |
| 上一版本 | `5cc6d4db9` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`658aa15ec` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`（前端无改动，与上一版本一致）
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `5cc6d4db9..658aa15ec`，共 5 个 commit）

本次部署核心为 scoreparse 项目 1382 计分逻辑父项跨列提取修复的落地合入及知识沉淀，**无数据库迁移文件变更**：

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 项目 1382 父项编码歧义、0 权重合同项范围逃逸根治（!2800）：Task 290 全量真实回放闭环 |
| docs | Task 290 真实排查根因沉淀 + Wiki 回填 + Sidecar 探测加固 |
| docs(release) | 第 219 次部署报告（test） |

**说明**：本次 release 包含 scoreparse 1382 修复与 docs，前端与后端 jar 均重新构建（同源）。改动均为评分解析逻辑与文档，无数据库 Schema 变更。

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 增量 `5cc6d4db9..658aa15ec` 无迁移文件变更，DB 版本对齐源码最新
- Step 3 remote-deploy 内置 validate → 通过（本次无新迁移）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 服务器现状检查：上一版本 `5cc6d4db9`，后端 health UP
3. Flyway 预检 3 步 → 通过
4. 本地打包：`RELEASE_ID=658aa15ec`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
5. 产物校验：jar 内迁移无重复（279 文件）、**OBS obsEnabled=true**（`.upload(` 调用数 = 2）、index.html 入口 `index-CDf8nFsr.js`
6. 上传 `xiyu-bid-release-658aa15ec.tar.gz` + 部署（SYSTEMCTL_SUDO=true）
7. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，第 80 次尝试）
8. 前端资源保留：从上一版本 `5cc6d4db9` release 目录 `cp -rn` 旧 assets 保留

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 80 次尝试通过 |
| Flyway 迁移 | 无新迁移，版本对齐 |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 前端资源保留 | ✅ 已从 5cc6d4db9 保留上一版本 assets |
| 数据库备份 | 部署前自动 mysqldump 到 `/opt/xiyu-bid/db-backups/` |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `658aa15ec`；上一已知良好版本 `5cc6d4db9`
- 后端旧 jar：`/opt/xiyu-bid/releases/5cc6d4db9/backend/`
- 前端旧 assets：已保留至 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`remote-deploy.sh` 在部署前自动执行 mysqldump 到 `/opt/xiyu-bid/db-backups/`
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 确认测试环境 172.16.38.78 后才执行
- 锚点分支保护：✅ 全程在锚点分支仅做只读部署，未进行任何开发
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ COPYFILE_DISABLE=1
- 打包强制 clean：✅ jar 内迁移无重复（防 V1096 事故复发）
- 前端 hash 资源保留：✅ 已保留上一版本 assets（skill 经验第 17 条）

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 未出现 503（Kafka SDK 延迟为已知行为，本次未发生）
- 本次为 scoreparse 1382 父项跨列提取修复（!2800）合入及 Task 290 全量回放，建议在测试环境对项目 1382 做一次评分解析功能回归验证

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致
- [x] 前端资源保留完成
- [x] 配置清理检查完成
- [x] 数据库备份已生成