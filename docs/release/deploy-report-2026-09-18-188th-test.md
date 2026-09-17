# 第 188 次部署报告（test 环境）

**日期**：2026-09-18

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`cb1364f37`

**前次 Release**：`99fa214f9`（2026-09-17 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `99fa214f9` 对齐到最新 `origin/main`（`cb1364f37`），含已合入 main 的两个功能修复 PR，**无新增数据库迁移**。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `cb1364f37`（`!2682 fix(scoreparse): 横幅表头单元格提取修复 + Redis 进度清理`） |
| 增量范围 | `99fa214f9..cb1364f37`（11 个 commit，含 2 个功能 PR + 部署报告） |
| 主要合并 PR | `!2682`（scoreparse 横幅表头提取 + Redis 进度清理）、`!2681`（部门树重名节点全选失效 + 414 修复）、`!2680`（第 21 次生产部署报告）、`!2679`（第 187 次部署报告） |
| 新增迁移 | 无 |

## 改动范围

- **!2682**：评分解析修复——含横幅标题行表格的表头定位与内嵌分值列映射；`failTask` 与 `markTimeout` 中通过 `finally` 强制清理 Redis 进度；增加横幅表头单元格提取单测。
- **!2681**：数据分析——根治部门树重名节点全选失效，人员全选超长参数触发 414 报错修复。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-cb1364f37-20260918063354.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `99fa214f9` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过。

## 部署步骤

1. 早操：锚点分支 ff-only 同步至 `origin/main` `cb1364f37`（HEAD = origin/main），工作区干净，本地门禁就绪。
2. 本地打包：`RELEASE_ID=cb1364f37 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；sidecar 打包完成；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、前端 index 入口 `assets/index-D_FjYa43.js` 存在、jar `app.jar` 就位。
4. 上传归档（155MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从前次 Release `99fa214f9` 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404），服务器 assets 193 → 277 个文件。

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

部署过程后端 health 在 80 次尝试后通过，服务 active/running——符合已知的 Kafka/组织事件 SDK 同步注册阻塞 readiness 的行为模式，非本次引入死锁。

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE`/临时调试配置。`SHOW_DETAILS=always` 为历史决策保留项。

## 风险提示

- 本次为增量部署，改动集中在前端评分解析与数据分析全选逻辑，未触碰数据库结构。
- scoreparse 表头定位修复涉及表格解析核心逻辑，建议在测试环境用含横幅标题行的评分表复验一次。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled）
- ✅ 上传 + 部署
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留
