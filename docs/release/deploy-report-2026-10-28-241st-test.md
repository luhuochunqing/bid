# 第 241 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 13:48 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `8c219056d` |
| 部署分支 | `agent/trae-init`（锚点分支，纯 ff-only 同步，未在其上改代码） |
| HEAD Commit | `8c219056d` |
| 旧版本 | `7fec3671d-api8080`（第 240 次，2026-10-28 09:27） |
| 构建包 | `.release/xiyu-bid-release-8c219056d.tar.gz`（155M，MD5 `c3e20dcaab6203438cf523010a78da1c`） |
| 构建时间 | 2026-10-28T05:47:23Z（13:47:23 UTC+8） |
| 增量 | 7 个 commit，46 个文件（+1215 / -100），代码变更 5 个 commit |
| 激活时间 | 2026-10-28 13:48:28 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-8c219056d-20261028134819.sql.gz`（15M） |

## PR 列表（增量 7 个 commit，自 7fec3671d..8c219056d）

| Commit | 说明 |
|---|---|
| `8c219056d` | **!2898** fix(scoreparse): 恢复名称中错位的评分行编号 |
| `e6a5e48cf` | fix(scoreparse): 保留跨表自动发布的证据门槛 |
| `2368d8c8d` | fix(scoreparse): 保全评分原文并补回遗漏项目 |
| `0899c4aeb` | fix(scoreparse): 恢复名称中错位的评分行编号 |
| `768c3ee85` | **!2897** docs(release): 第 240 次测试环境部署报告 (test) |
| `dee7e177d` | docs(release): 修正第 240 次报告文件计数 |
| `7bbf4e4ce` | docs(release): 第 240 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：19 个文件，集中在 `scoreparse` 模块（评分原文保全、遗漏项目补回、跨表自动发布证据门槛、评分行编号错位修复）
- **后端测试 / 金标**：14 个文件（评分解析相关单测与金标用例）
- **前端**：7 个文件 —— `src/views/Project` 4 个 + `src/composables/projectDetail` 3 个（评分候选展示与项目详情联动）
- **Specs**：1 个 feature 目录（`20261013-score-source-consistency` 的 spec/plan/tasks 各 1）
- **Wiki**：2 个文件 —— `.wiki/log.md`、`.wiki/pages/score-subtotal-peritem-semantics-provenance-snapshot.md`
- **文档**：1 个部署报告（第 240 次 test）
- **Sidecar**：无变更（`sidecarSha256` = `c06803d7...`，与上一版本一致），容器未重建（`xiyu-sidecar` Up 16 hours）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，13:48:25） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 早操：`sync-env.sh`（init 分支 ff-only，本地门禁 7/7 通过）+ 基线确认 `origin/main` @ `8c219056d`，工作区干净
3. 服务器现状：旧版本 `7fec3671d-api8080`、health UP、服务 active
4. 增量检查：7 commit / 46 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-DhPvgRNM.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（`7fec3671d-api8080` → 当前，防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，13:48:28 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测，约 2.6 分钟） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全部 UP |
| Sidecar 容器 `xiyu-sidecar` | ✅ Up 16 hours（本次无 sidecar 源码变更，未重建，符合预期） |

> 健康检查耗时约 2.6 分钟，属已沉淀的 Kafka SDK readiness 延迟已知行为（第 8/9/10/13/15 次同类现象），非本次缺陷。

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（本次未出现 503） |
| `/`（前端首页） | ✅ HTTP 200 |
| `/login` | ✅ HTTP 200 |
| `POST /api/auth/login`（空包） | ✅ HTTP 400（参数校验生效） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（认证守卫生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端入口 | ✅ `assets/index-DhPvgRNM.js`，与 release 一致 |

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### 前端资产保留

- 部署后从上一版本 release 目录（`7fec3671d-api8080`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 277 个文件
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `7fec3671d-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/7fec3671d-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/7fec3671d-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-8c219056d-20261028134819.sql.gz` |
| 回滚操作 | 以 `7fec3671d` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh`（或直接回切 releases 目录内旧 artifact） |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解析异常 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后检查残留 = 0 |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（277 个文件） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 前端目录属主（第 10 次） | ✅ remote-deploy 自动 `sudo chown -R jetty` 修复非 jetty 属主条目 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属历史用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **前端产物有变更**：本次含 `src/views/Project` 与 `projectDetail` 组合式改动，入口 `index-DhPvgRNM.js` 与上一版本不同；已补齐旧 assets，旧标签页仍建议刷新。
4. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。
5. **`agent/trae-init` 锚点分支上执行了部署操作**：仅涉及 ff-only 同步与打包，未在锚点分支产生代码提交，符合规范。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `8c219056d`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `8c219056d`）
- [x] 部署报告产出
