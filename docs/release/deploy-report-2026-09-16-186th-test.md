# 第 186 次部署报告（test 环境）

**日期**：2026-09-16

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`39230f4f4`

**前次 Release**：`c83ef88e1`（今日 12:57 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `c83ef88e1` 对齐到最新 `origin/main`（`39230f4f4`），含已合入 main 的多个 PR 改动，无新增数据库迁移。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `39230f4f4`（`auto-merge !2672 feat(scripts): check-testing-standards 支持 HERDR_DEFER_HEAVY_TESTS`） |
| 增量范围 | `c83ef88e1..39230f4f4`（约 20 个 commit） |
| 主要合并 PR | `!2672`（HERDR_DEFER_HEAVY_TESTS 测试门禁）、`!2669`（项目详情页横条返回按钮 @back 修复）、`!2671`（业绩评分项案例推荐准入 spec 064）、`!2670`（评标与结项角色权限判定收敛 bid-SystemAdmin）、`!2668`（知识案例详情排版/来源/解析修复）、`!2667`（第 185 次部署报告） |
| 新增迁移 | 无 |

## 改动范围

- **!2672**：`check-testing-standards` 支持 `HERDR_DEFER_HEAVY_TESTS` 环境变量（测试门禁调度）。
- **!2669**：项目详情页横条返回按钮绑定 `@back` 事件，修复点击无响应（含规格验收）。
- **!2671**：业绩评分项案例推荐准入、金额门槛与资格项回退（spec 064，含知识沉淀与 wiki 回填）。
- **!2670**：评标与结项阶段角色权限判定收敛，支持 `bid-SystemAdmin` 角色。
- **!2668**：知识案例详情排版（大段无换行/单行表格/来源标题多级兜底）+ 存量数据安全回填。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 服务器 app.jar SHA-256 | `149f97924015a4fef177be6d56ced32baac6add4f18dba0fd0b2ff64bb841b18` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-39230f4f4-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `c83ef88e1` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- **处置的过时目录问题**：部署首次 validate 报 `Detected applied migration not resolved locally: 1215`——根因是服务器 `/tmp/migration-mysql/` 目录缺失 V1215（skill 已知经验 10）。将 `V1215__add_source_provenance_to_knowledge_case.sql` 补传至服务器 `/tmp/migration-mysql/` 后重跑，275 个迁移校验全部通过，随后部署成功。

## 部署步骤

1. 早操：锚点分支 ff-only 同步至 `origin/main` `39230f4f4`，本地门禁就绪。
2. 本地打包：`RELEASE_ID=39230f4f4 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；sidecar 打包完成；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、jar 内含 V1214/V1215、前端 index 入口 `assets/index-B_J4HQgK.js` 存在。
4. 上传归档（162MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从前次 Release `c83ef88e1` 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404）。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP |
| `/actuator/health/readiness` | ✅ 200（readiness 正常，无 Kafka 延迟卡死） |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 403 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-B_J4HQgK.js` 一致 |
| 迁移应用 | ✅ V1215 `success=1`（本次无新迁移，基线已齐） |

部署过程健康检查在 81 次尝试后通过——符合已知的 Kafka/组织事件 SDK 同步注册阻塞 readiness 的行为，非本次引入的死锁。

## 配置清理检查

`/etc/xiyu-bid/backend.env` 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`——为历史保留项（第 13-15 次多次决定保留），非本次 TEMP 调试配置（无 `DEBUG`/`TRACE`），未改动。

## 风险提示

- 完整后端套件（约 9,735 tests）在未改动模块存在存量基线失败（competition-intel、BidDocumentQualityEngine、共享 MySQL Testcontainers 生命周期），本次为增量部署未触碰这些模块，不声明全套件全绿。
- `SHOW_DETAILS=always` 保持历史决策，如需关闭请另行授权。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步（含 `/tmp/migration-mysql/` 过时目录修复）
- ✅ 本地打包 + 产物校验
- ✅ 上传 + 部署
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留