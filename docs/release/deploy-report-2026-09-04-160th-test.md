# 第 160 次部署报告（test 环境）

**日期**：2026-09-04
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`5a763f29`
**部署时间**：15:44 - 15:54 CST
**状态**：✅ 成功

## 部署概览

- 增量 7 个 commit（3 条实质 PR + docs 回填 + auto-merge），**无新增 Flyway 迁移**（DB 与源码最新版本均为 V1210）。

- 本次核心为**AI 案例检索实体切词重构 + 知识案例富媒体渲染 + 标讯中心入口调整**：

  - **PR !2541**：彻底拔除 2-gram 机械滑窗切词并重构实体层级提取（修复 n-gram 泄漏）

  - **PR !2542**：案例详情应答片段按 markdown 安全渲染表格（富媒体支持）

  - **PR !2543**：移除标讯中心顶栏客户商机中心入口按钮

- Flyway 预检及迁移验证全部通过（271 项迁移全量校验，无待应用迁移）。

## 基线信息

| 项目        | 值                                            |
| --------- | -------------------------------------------- |
| 基线 commit | `5a763f293`（origin/main，锚点分支 ff-only 同步，零差异） |
| 前次部署      | `4fd73000`（2026-09-04 14:07 激活，第 159 次部署）    |
| 增量 commit | 7 个（`4fd73000..5a763f293`）                   |
| 新增迁移      | 无（DB 与源码均为 V1210）                            |
| 工作区状态     | 干净（仅第 159 次部署报告未跟踪文件）                        |
| 早操三连      | sync-env 7/7 门禁就绪 + git 包装器生效（sync-env 内置检查） |

## 改动范围（7 个增量 commit / 3 条实质 PR）

| PR    | 描述                                       |
| ----- | ---------------------------------------- |
| !2541 | fix(casework): 彻底拔除2-gram机械滑窗切词并重构实体层级提取 |
| !2542 | feat(knowledge): 案例详情应答片段按markdown安全渲染表格 |
| !2543 | fix(bidding): 移除标讯中心顶栏客户商机中心入口按钮         |

关键代码：`casework` 模块实体 token 提取重构（拔除 2-gram 机械滑窗、重构实体层级）、`knowledge` 模块案例详情应答片段 markdown 表格安全渲染、`bidding` 模块标讯中心顶栏入口按钮移除；另含 1 条知识沉淀 + wiki 回填 commit。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，271 migrations all checksums match。

**Step 2**：DB 最新 applied = V1210（`success=1`，2026-09-03 21:52 已应用），源码最高版本 = V1210 → 本次无待应用迁移。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                         |
| ------ | ------------------------------------------------------------------------------------------ |
| 15:44  | 环境门禁确认（test / 172.16.38.78）+ 早操（sync-env ff-only 至 `5a763f293`）+ 基线零差异确认                   |
| 15:44  | 服务器现状探测（前次 release `4fd73000`，health 200 / frontend 200）+ 增量 commit / 迁移文件 diff 检查（无迁移变更）  |
| 15:45  | Flyway 预检：validate 通过（271 migrations）+ DB 版本对比 V1210                                       |
| 15:47  | 本地打包完成（32.9s，\~154M，obsEnabled=true，同源构建）+ jar 迁移无重复 + OBS .upload=2 校验                    |
| 15:48  | scp 上传归档（155M）到 `/opt/xiyu-bid/incoming/`                                                  |
| 15:48  | remote-deploy 执行：DB 备份 → Flyway validate → Frontend 激活 → 原子替换 jar → systemctl 重启（15:49:01） |
| 15:51  | 脚本内置健康检查通过（consecutive 3/3，total attempts 143，service active/running）+ 前端一致性验证通过           |
| 15:52+ | Smoke 全绿 + 旧 assets 保留（288 个文件）+ 配置清理检查 + DB 备份核验                                          |

## 验证结果

**后端健康（Nginx 8080 代理路径）**：`{"status":"UP"}`，readiness 200（本次无 Kafka readiness 延迟；健康检查累计轮询 143 次 ≈ 2.4 分钟启动窗口，属正常 Spring Boot + Kafka SDK 启动时长，未超 4 分钟上限）。

**迁移应用验证**：本次无新增迁移；V1210 保持 `success=1`（installed\_on 2026-09-03 21:52:25）。

**API Smoke（经 Nginx 8080，`--noproxy '*'`）**：

| 接口                                 | 期望     | 实际       |
| ---------------------------------- | ------ | -------- |
| `/actuator/health`                 | 200 UP | ✅ 200 UP |
| `/actuator/health/readiness`       | 200    | ✅ 200    |
| `POST /api/auth/login`（空 body）     | 400    | ✅ 400    |
| `/api/projects`（未认证）               | 403    | ✅ 403    |
| `/api/integration/crm/health`（未认证） | 401    | ✅ 401    |

**前端 Smoke（经 Nginx 8080）**：

- 根路径 `/`：200

- 登录页 `/login`：200

- 线上入口：`assets/index-Dx4sc6yb.js` 与构建产物完全一致

- 旧版本 hash 资源已从 `releases/4fd73000/frontend/` 以 `cp -rn`（非 sudo，避免引入 root 属主文件）保留到 `/srv/www/xiyu-bid/assets/`（共 288 个文件，防跨版本 404，保留 24h 惯例）

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、`sentryEnabled=false`

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2

- jar 内迁移文件 270 个，无重复版本

- `COPYFILE_DISABLE=1` 生效（防 macOS `._*` 残留）

## 本次异常与处置

**无阻塞性异常。** 全流程（上传 → 部署 → 健康检查 → Smoke）零重试零失败。

## 回滚信息

| 锚点         | 位置                                                                             |
| ---------- | ------------------------------------------------------------------------------ |
| 前后端制品      | `/opt/xiyu-bid/releases/4fd73000/`（jar + frontend）                             |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-5a763f29-20260904154851.sql.gz`（本次部署自动生成，13M） |
| 迁移回滚脚本     | 本次无新增迁移，无需回滚脚本                                                                 |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）                            |
| 回滚 owner   | 部署执行 Agent（trae）                                                               |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。

- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）

- [x] 早操三连 + 基线零差异

- [x] Flyway 预检通过（validate + DB 版本对比）

- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）

- [x] DB 备份先行（winbid-5a763f29-\*.sql.gz）

- [x] 服务重启 + 健康 UP

- [x] 迁移应用验证（本次无新增，V1210 保持 success）

- [x] Smoke 全绿

- [x] 旧 assets 跨版本保留

- [x] 部署报告归档

## 风险提示

- 本次无数据库结构变更，回滚仅需切回前一版本制品（`4fd73000/`）+ DB 备份。

- PR !2541 重构实体 token 提取（拔除 2-gram 滑窗），建议在 AI 案例推荐/案例检索场景做一次人工抽检，确认实体抽取精度提升且无漏召回。

- PR !2542 案例详情应答片段支持 markdown 表格渲染，建议打开含表格的历史案例详情页验证渲染效果与 XSS 防护（安全渲染路径）。

- PR !2543 移除标讯中心顶栏入口按钮，属 UI 行为变更，建议快速目视确认标讯中心顶栏布局正常。

- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包与 `releases/` 旧版目录建议另行清理（保留最近 N 版 + 回滚锚点版本）。

