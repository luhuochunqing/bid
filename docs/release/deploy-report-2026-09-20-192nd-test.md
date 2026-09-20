# 第 192 次部署报告（test 环境）

**日期**：2026-09-20

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`86815c9de`

**前次 Release**：`ec98498984`（2026-09-19 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `ec98498984` 对齐到最新 `origin/main`（`86815c9de`），核心为 !2708 央企商机预测提醒窗口修复、!2709 大文件标书配额提升，以及第 26 次生产部署报告归档，共 8 个增量 commit，**无新增数据库迁移**。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `86815c9de`（`!2709 fix(score-parse): raise large bid file quota` / `auto-merge by gitee-pr-helper.sh`） |
| 增量范围 | `ec98498984..86815c9de`（8 个 commit） |
| 主要合并 PR | `!2709`（score-parse 大投标文件配额提升）、`!2708`（央企商机预测提醒窗口修复）、`!2707`（第 26 次生产部署报告） |
| 新增迁移 | 无 |

## 改动范围

- **!2709（`4eb0af736`）**：评分解析提升大投标文件解析配额（CO 相关），配套 `a4f6b0ef4` 沉淀评分解析配额根因文档。
- **!2708（`130738a7c`）**：工作台商机提醒对客户类型做归一化（normalize customer type），修复央企商机预测提醒窗口；配套 `cf14696c5` 记录客户类型预测规则。
- **!2707（`1508fac8d`）**：第 26 次生产环境部署报告归档（docs 变更）。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| Sidecar 镜像 | SHA-256 `609a7bf135c3e2faba38ed81010f4df67d330fbbb46dc4eac9860edfea9e0a8c`（schemaVersion 2.0） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-86815c9de-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `ec98498984` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK - all checksums match`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过。

## 部署步骤

1. 早操：锚点分支 `agent/trae-init` 校验，HEAD 落后 `origin/main` 8 commit；切换 `origin/main`（`86815c9de`）确保按最新源码打包。
2. 本地打包：`RELEASE_ID=86815c9de VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；容器测试门禁（FlywayMysqlContainerTest）通过；jar 内迁移无重复；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、frontend index 入口 `index-C9c0lzz8.js`、jar 内 274 个迁移文件无重复。
4. 上传归档（155MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署脚本处理前端目录属主统一（sudo chown）后激活，从上一版本 `ec98498984` release 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404）。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP（remote-deploy 内 80 次探测 3/3 连贯） |
| `/actuator/health/readiness` | ✅ 200 |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 401 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-C9c0lzz8.js` 一致 |
| Sidecar 健康（127.0.0.1:8000） | ✅ 健康检查通过，容器升级完成 |
| 迁移应用 | ✅ 无新迁移，基线 V1215 保持 |

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE` 临时调试配置。`SHOW_DETAILS=always` 为历史决策保留项。

## 风险提示

- 本次为增量部署，改动集中在评分解析配额与商机提醒客户类型归一化，未触碰数据库结构。
- 配额提升涉及评分解析内存边界，建议在测试环境用超大投标文件复验一次解析链路，确认配额提升后无 OOM/超时降级副作用。
- 客户类型归一化影响商机预测提醒口径，若预期"仅央企触发提醒"，请在测试环境用含多种客户类型的测试数据回归提醒窗口。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled）
- ✅ 上传 + 部署（含 Sidecar 镜像升级 + 健康门禁）
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留