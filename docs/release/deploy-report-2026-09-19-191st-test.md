# 第 191 次部署报告（test 环境）

**日期**：2026-09-19

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`ec98498984`

**前次 Release**：`d348ce609`（2026-09-18 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `d348ce609` 对齐到最新 `origin/main`（`ec98498984`），核心为 !2705 分表完整性校验范围误判 + 部分通过不再覆盖正式评分项，以及评分解析 Sidecar 超时降级、未认证统一 401 等 29 个增量 commit，**无新增数据库迁移**。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `ec98498984`（`!2705 fix(scoreparse): 分表完整性校验范围误判 + 部分通过不再覆盖正式评分项`） |
| 增量范围 | `d348ce609..ec98498984`（29 个 commit） |
| 主要合并 PR | `!2705`（评分分表完整性范围误判 + 部分通过保护正式结果）、`!2704`（项目30评分解析修复）、`!2703`（第25次生产部署报告）、`!2702`（扫描版 PDF 空正文语义修复）、`!2701`（评分解析超时降级防放大、未认证统一401、前端防抖并发防护）、`!2700/!2699`（Sidecar Dockerfile 双文件同步修复） |
| 新增迁移 | 无 |

## 改动范围

- **!2705**：评分解析分表完整性校验不再误用整份文档正文长度；部分表通过不再覆盖正式评分项，事实删除按 `parseTaskId` 隔离（CO-386/CO-387）。
- **!2704**：项目30评分解析 215 分虚高与续行维度丢失缺陷修复；支持独立汇总父项行与横纵合并覆盖场景下的父项关联与末级过滤；解耦合并单元格名称继承与父子层级关系。
- **!2702**：扫描版 PDF 空正文解析失败改提示"扫描版/图片版"而非"请先上传"（CO-358/CO-384 语义误导修复）。
- **!2701**：Sidecar 超时降级防放大、未认证统一返回 401、前端解析防抖并发拦截（CO-383 治理）。
- **!2699/!2700**：Sidecar Dockerfile 补入 `docx_repair.py`（!2697 引入但漏更新 COPY 列表导致容器 crash-loop），并同步修复 `Dockerfile.cn`；第190次部署事故整改。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| Sidecar 镜像 | SHA-256 `c7e8a8d7a180ba4792d670978388dd939fc4384dc60892e6fd1561cf28800c2a`（schemaVersion 2.0） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-ec98498984-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `d348ce609` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过。

## 部署步骤

1. 早操：锚点分支 `agent/trae-init` ff-only 同步至 `origin/main` `ec98498984`（HEAD = origin/main），工作区干净。
2. 本地打包：`RELEASE_ID=ec98498984 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、frontend index 入口 `index-C9c0lzz8.js`。
4. 上传归档（155MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从上一版本 `d348ce609` release 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404）。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP（remote-deploy 内 80 次探测 3/3 连贯） |
| `/actuator/health/readiness` | ✅ 200 |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 401（本次 !2701 未认证统一 401 契约） |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-C9c0lzz8.js` 一致 |
| Sidecar 健康（127.0.0.1:8000） | ✅ 健康检查通过，容器升级完成 |
| 迁移应用 | ✅ 无新迁移，基线 V1215 保持 |

> 注：`/api/projects` 返回 401 而非历史上的 403，是本批 !2701 "未认证统一 401" 契约修复的预期变化，非回归。

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE` 临时调试配置。`SHOW_DETAILS=always` 为历史决策保留项。

## 风险提示

- 本次为增量部署，改动集中在评分解析、文档解析语义与未认证契约，未触碰数据库结构。
- 评分解析涉及多条修复（范围判定、正式结果保护、合并单元格父子解耦），涉及核心解析链路，建议在测试环境用含合并单元格与独立汇总父项的真实评分表复验。
- 未认证从 403 → 401 是契约变更，若前端或其他客户端依赖 403 判断未登录，需同步确认前端适配（前端防抖并发防护已含在本批）。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled）
- ✅ 上传 + 部署（含 Sidecar 镜像升级 + 健康门禁）
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留