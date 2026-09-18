# 第 189 次部署报告（test 环境）

**日期**：2026-09-18

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`eeb524522`

**前次 Release**：`cb1364f37`（2026-09-18 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `cb1364f37` 对齐到最新 `origin/main`（`eeb524522`），核心为 PR !2685 的跨页续表大类总分修复，**无新增数据库迁移**。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `eeb524522`（`!2685 fix(scoreparse): 根治跨页续表大类总分劫持与无表头分值列推断`） |
| 增量范围 | `cb1364f37..eeb524522`（6 个 commit，核心 1 个功能 PR + 部署报告） |
| 主要合并 PR | `!2685`（scoreparse 跨页续表修复）、`!2684`（第 22 次生产部署报告）、`!2683`（第 188 次部署报告） |
| 新增迁移 | 无 |

## 改动范围

- **!2685**：评分解析根治——跨页续表大类总分劫持修复；无表头分值列推断（湖北中烟项目 33 与招商积余项目 1117 案例）；更新 `DeterministicScoreFactExtractor` / `HeuristicTableColumnMapper` 与相关知识库文档、lessons-learned；新增 `ScoreParseContinuationRowTest` 及 `CellAnchoredScoreExtractorTest` 增强。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-eeb524522-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `cb1364f37` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过。

## 部署步骤

1. 早操：锚点分支 `agent/trae-init` ff-only 同步至 `origin/main` `eeb524522`（HEAD = origin/main），工作区干净，本地门禁就绪。
2. 本地打包：`RELEASE_ID=eeb524522 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；sidecar 打包完成；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、frontend index 入口存在、jar `app.jar` 就位。
4. 上传归档（162MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从前次 Release `cb1364f37` 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404），服务器 assets 共 193 个。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP（remote-deploy 内 80 次探测 3/3 连贯） |
| `/actuator/health/readiness` | ✅ 200（readiness 正常，无 Kafka 延迟卡死） |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 403 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-D_FjYa43.js` 一致 |
| 迁移应用 | ✅ 无新迁移，基线 V1215 保持 |

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE`/临时调试配置。`SHOW_DETAILS=always` 为历史决策保留项。

## 风险提示

- 本次为增量部署，改动集中在评分解析核心逻辑，未触碰数据库结构。
- 跨页续表大类总分修复涉及表格解析核心逻辑，建议在测试环境用湖北中烟项目 33 / 招商积余项目 1117 的评分表复验一次新断言（`ScoreParseContinuationRowTest`）。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled）
- ✅ 上传 + 部署
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留