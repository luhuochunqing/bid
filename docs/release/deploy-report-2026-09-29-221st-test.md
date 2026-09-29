# 第 221 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `f645780f4` |
| 部署时间 | 2026-09-29 15:35 CST（activatedAt 07:35:26Z） |
| 上一版本 | `658aa15ec` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`f645780f4` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`（与上一版本一致，前端无改动）
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `658aa15ec..f645780f4`，共 5 个 commit）

本次部署核心为 scoreparse 项目 1382 Task291 四大阻断根因修复的合入上线，**无数据库迁移文件变更**：

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 精准修复 1382 Task291 四大阻断根因（!2802）：引文数字汉字空格规整、28 分项范围守护、别名维度匹配、复核预算崩溃 |
| docs | wiki 回填 Task291 四大阻断根因分析 |
| docs(release) | 第 220 次部署报告（test） |

**说明**：本次 release 包含 scoreparse 1382 Task291 修复与文档，后端 jar 重新构建，前端产物无功能改动（入口 hash 与上一版本一致）。无数据库 Schema 变更。

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 增量 `658aa15ec..f645780f4` 无迁移文件变更，DB 版本对齐源码最新（V1220）
- Step 3 remote-deploy 内置 validate → 通过（本次无新迁移）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 服务器现状检查：上一版本 `658aa15ec`，后端 health UP
3. Flyway 预检 3 步 → 通过
4. 本地打包：`RELEASE_ID=f645780f4`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
5. 产物校验：jar 内迁移无重复（279 文件）、**OBS obsEnabled=true**（`.upload(` 调用数 = 2）、index.html 入口 `index-CDf8nFsr.js`
6. 上传 `xiyu-bid-release-f645780f4.tar.gz` + 部署（SYSTEMCTL_SUDO=true）
7. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，第 79 次尝试）

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 79 次尝试通过 |
| Flyway 迁移 | 无新迁移，V1216-V1220 全部 success=1 |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 数据库备份 | 部署前自动 mysqldump 到 `/opt/xiyu-bid/db-backups/winbid-f645780f4-20260929153512.sql.gz` |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `f645780f4`；上一已知良好版本 `658aa15ec`
- 后端旧 jar：`/opt/xiyu-bid/releases/658aa15ec/backend/`
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
- 容器测试门禁：✅ FlywayMysqlContainerTest 打包前置验证 Spring 装配

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 未出现 503（Kafka SDK 延迟为已知行为，本次未发生）
- 本次为 scoreparse 1382 Task291 四大阻断根因修复（!2802）合入，建议在测试环境对项目 1382 评分解析做一次功能回归验证

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致
- [x] 配置清理检查完成
- [x] 数据库备份已生成
