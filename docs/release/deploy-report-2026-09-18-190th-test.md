# 第 190 次部署报告（test 环境）

**日期**：2026-09-18

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`d348ce609`

**前次 Release**：`eeb524522`（2026-09-17 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `eeb524522` 对齐到最新 `origin/main`（`d348ce609`），核心为 PR !2696 招标文件解析放行超 50MB、!2698 授权拒绝审计日志、!2697 docx 截断自愈等 42 个增量 commit，**无新增数据库迁移**。

> ⚠️ 首次部署尝试因 Sidecar 容器 crash-loop 自动停止（旧版本继续运行，服务未中断）；修复 Dockerfile 后重试成功。详见「Sidecar 故障与修复」。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `d348ce609`（`!2696 feat(scoreparse): 招标文件解析放行超50MB（spec 065）`） |
| 增量范围 | `eeb524522..d348ce609`（42 个 commit） |
| 主要合并 PR | `!2696`（scoreparse 超 50MB 放行）、`!2698`（授权拒绝审计日志）、`!2697`（docx 截断自愈）、`!2695/!2694/!2693`（CO-615 中标率口径）、`!2692`（候选评分标准采纳）、`!2691`（跨页表头扫描） |
| 新增迁移 | 无 |

## 改动范围

- **!2696**：招标文件解析放行超 50MB——流式 + 文本量口径（spec 065），含 T018 真机 80MB 全链路验证。
- **!2698**：`ProjectAccessScopeService` 授权拒绝时记录 `projectId/userId/username` 审计日志（CO-551 根因排查可观测性）。
- **!2697**：docx 截断容器解析挂死与乱码修复——EOCD 自愈 + POI 原生降级，新增 `docx_repair.py` 模块。
- **!2695/!2694/!2693**：CO-615 中标率口径改为 中标数/(已中标+未中标)，趋势下钻负责人空串回退标讯（NULLIF）。
- **!2692/!2691**：候选评分标准前端预览与人机一键采纳；跨页表头扫描深度放宽防误吞。

## Sidecar 故障与修复（本次部署关键事件）

| 阶段 | 现象 | 根因 | 处置 |
| --- | --- | --- | --- |
| 首次部署 | Sidecar 健康检查失败（30 次重试），rollout 自动停止，旧版本继续运行 | 服务器构建镜像用 `Dockerfile.cn`（国内源版），其 `COPY` 列表缺 `docx_repair.py`（!2697 新增模块漏更新两个 Dockerfile）→ `ModuleNotFoundError` crash-loop | 修改 [Dockerfile](file:///Users/user/xiyu/worktrees/trae/document-converter-sidecar/Dockerfile) 与 [Dockerfile.cn](file:///Users/user/xiyu/worktrees/trae/document-converter-sidecar/Dockerfile.cn) 的 COPY 行补入 `docx_repair.py`，提交 PR #2699 |
| 重试部署 | — | — | 重新打包 + 上传 + 部署成功，Sidecar 镜像重建（含修复）后健康门禁通过 |

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-d348ce609-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `eeb524522` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过（`VALIDATE OK - all checksums match`）。

## 部署步骤

1. 早操：锚点分支 `agent/trae-init` ff-only 同步至 `origin/main` `d348ce609`（HEAD = origin/main），工作区干净。
2. 本地打包：`RELEASE_ID=d348ce609 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；OBS 直传 `.upload()`=2；sidecar SHA 随修复更新。
3. 产物校验：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）、frontend index 入口存在、sidecar.tar.gz 内 `Dockerfile.cn` 含 `docx_repair.py`。
4. 上传归档（155MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从上一版本 `eeb524522` release 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404），服务器 assets 共 278 个。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP（remote-deploy 内 81 次探测 3/3 连贯） |
| `/actuator/health/readiness` | ✅ 200 |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 403 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-B3UmirfL.js` 一致 |
| Sidecar 健康（127.0.0.1:8000） | ✅ `{"status":"up"}`，容器 Up |
| 迁移应用 | ✅ 无新迁移，基线 V1215 保持 |

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE` 临时调试配置。`SHOW_DETAILS=always` 为历史决策保留项。

## 风险提示

- 本次为增量部署，改动集中在评分解析、文档解析与权限审计，未触碰数据库结构。
- **Dockerfile / Dockerfile.cn 双文件不同步是隐患**：服务器构建实际使用 `Dockerfile.cn`（国内源版）。本次已同步修复，但建议后续新增 sidecar Python 模块时同时更新两个文件（PR #2699 已合入前的过渡期留意）。
- 招标文件解析放行超 50MB 与 docx 截断自愈涉及核心解析链路，建议在测试环境用超 50MB 招标文件与截断 docx 样例复验（T018 真机 80MB 已由 CI 覆盖，可人工抽查）。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled + sidecar Dockerfile 校验）
- ✅ 上传 + 部署（含 Sidecar 故障修复重试）
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留
