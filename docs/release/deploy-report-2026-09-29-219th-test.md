# 第 219 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `5cc6d4db9` |
| 部署时间 | 2026-09-29 09:33 CST（activatedAt 01:33:51Z） |
| 上一版本 | `9120cd06a` |
| 部署人 | trae（xiyu-server-deploy skill） |

## 基线信息

- 分支：`agent/trae/deploy-test-system`（临时 worktree，base = origin/main）
- HEAD commit：`5cc6d4db9` = origin/main
- 前端入口：`assets/index-CDf8nFsr.js`（前端无改动，与上一版本一致）
- 后端 jar：`bid-platform-1.0.3.jar`
- sidecar schemaVersion：2.2

## 改动范围（增量 `9120cd06a..5cc6d4db9`，共 6 个 commit）

本次部署核心为 scoreparse 项目 1382 计分逻辑迭代（父项跨列提取修复），**无数据库迁移文件变更**：

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 项目 1382 父项跨列分值提取与合同考核项范围误判根治（!2798）：父项标题扫描加列守卫（anchor.column）、declaredDimensionSubtotal 剥离标准后缀、OUT_OF_SCHEME 项不限权重直接短路放行、无表考核项识别扩展（service 维度及 _KH/_PJ 指标） |
| test(scoreparse) | Project1382FullPipelineIntegrationTest：Task 289 线上全量 37 项真实事实无清洗夹具，验证 13 项叶子项 100 分 AUTO_APPROVED 闭环 |
| docs | 第 415 条 lessons（Task 289 父项跨列分值提取根因）+ wiki 第 8 节回填 |
| docs(release) | 第 218 次部署报告（test） |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本对比：DB 最新 = V1220，源码最高 = **V1220**，完全对齐
- Step 3 remote-deploy 内置 validate → 通过（本次无新迁移）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户确认测试环境 172.16.38.78
2. 开任务 worktree：`agent-start-task.sh trae deploy-test-system origin/main`（避免锚点分支直接操作）
3. 服务器现状检查：上一版本 `9120cd06a`，后端 health UP
4. Flyway 预检 3 步 → 通过
5. 本地打包：`RELEASE_ID=5cc6d4db9`，VITE_API_BASE_URL（同源），VITE_OBS_ENABLED=true，COPYFILE_DISABLE=1
6. 产物校验：jar 内迁移无重复、**OBS obsEnabled=true**、index.html 入口 `index-CDf8nFsr.js`
7. 上传 `xiyu-bid-release-5cc6d4db9.tar.gz` + 部署（SYSTEMCTL_SUDO=true）
8. Remote deploy：Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（3/3）

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（本地直连 18080） | **UP**（含 readiness UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 81 次尝试通过 |
| Flyway 迁移 | 无新迁移，版本对齐 V1220 |
| 前端一致性 | `src="/assets/index-CDf8nFsr.js"` 匹配 release |
| Smoke - health | HTTP 200 |
| Smoke - readiness | HTTP 200 |
| Smoke - login 空密码 | HTTP 400（预期） |
| Smoke - /api/projects 未认证 | HTTP 401（预期） |
| Smoke - /api/integration/crm/health | HTTP 401（预期） |
| 前端 / 和 /login | HTTP 200 |
| 前端资源保留 | 本次前端无改动（assets 与上版一致），无需保留 |
| 数据库备份 | `winbid-5cc6d4db9-20260929093337.sql.gz`（14M） |
| 配置清理检查 | 仅 `SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）

## 回滚信息

- 回滚锚点：release tag `5cc6d4db9`；上一已知良好版本 `9120cd06a`
- 后端旧 jar：`/opt/xiyu-bid/releases/9120cd06a/backend/`
- 前端旧 assets：与上版一致，无跨版本差异
- 数据库备份：`remote-deploy.sh` 在部署前自动执行 mysqldump 到 `/opt/xiyu-bid/db-backups/`
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 确认测试环境 172.16.38.78 后才执行
- 锚点分支保护：✅ 通过 `agent-start-task.sh` 开临时 worktree，不在锚点分支直接开发
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ COPYFILE_DISABLE=1
- 打包强制 clean：✅ jar 内迁移无重复（防 V1096 事故复发）

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置
- readiness 未出现 503（Kafka SDK 延迟为已知行为，本次未发生）
- 本次为 scoreparse 1382 父项跨列提取修复（!2798），建议在测试环境对项目 1382 Task 289 做一次叶子项 100 分自动落库的功能回归验证

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] 前端入口一致
- [x] 配置清理检查完成
- [x] 数据库备份已生成
