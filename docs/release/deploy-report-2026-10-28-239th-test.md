# 第 239 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 07:37 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `093b7ce7c35e6982936d197ccc492810f19f4544-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `093b7ce7c` |
| 旧版本 | `2ef3f18df5d50b06553fae7127397de9e4557b4b-api8080`（第 238 次，2026-10-28 06:42） |
| 构建包 | `.release/xiyu-bid-release-093b7ce7c...-api8080.tar.gz`（155M，MD5 `ce8d9865bc421ed98d49cad44876b827`） |
| 构建时间 | 2026-10-27T23:36:37Z（07:36:37 UTC+8） |
| 增量 | 5 个 commit，9 个文件（+304 / -2），其中代码变更 1 个 commit |
| 激活时间 | 2026-10-28 07:37:23 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-093b7ce7c-20261028073713.sql.gz`（15M） |

## PR 列表（增量 5 个 commit，自 2ef3f18df..093b7ce7c）

| Commit | 说明 |
|---|---|
| `093b7ce7c` | **!2894** fix(scoreparse): 修复评分项名称引文错插编号 |
| `f92e1480d` | fix(scoreparse): 修复评分项名称引文错插编号 |
| `b3c239b14` | auto-merge：**!2893** 第 238 次测试环境部署报告 |
| `eb9f722b7` | docs(release): 按评审意见修正第 238 次报告文件计数与绝对路径 |
| `749279f63` | docs(release): 第 238 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：2 个文件 —— `scoreparse/application/PdfRowEvidenceBinder.java`（+56）、`scoreparse/application/TextPrimaryEvidenceBinder.java`（+1），修复评分项名称引文错插编号
- **后端测试 / 金标**：2 个测试 + 1 个金标用例 —— 新增 `PdfOrdinalContaminationEvidenceTest`、`GoldenCasesReplayTest` 增用例、新增 `case-pdf-ordinal-contamination.md` / `.expected.json`
- **Wiki**：2 个文件（`.wiki/log.md`、`score-subtotal-peritem-semantics-provenance-snapshot.md`）
- **文档**：1 个部署报告（第 238 次 test）
- **Sidecar**：无变更（`sidecarSha256` 与上一版本一致 `c06803d7...`），容器未重建（`xiyu-sidecar` Up 9 hours）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，07:37:20） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：用户确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已是最新）+ 基线确认 `agent/trae-init` @ `093b7ce7c`，工作区干净（HEAD..origin/main 0/0），本地门禁 7/7 通过
3. 服务器现状：旧版本 `2ef3f18df...-api8080`、health 200 UP、服务 active
4. 增量检查：5 commit / 9 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-1_idEp5T.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，MainPID 24346 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测） |
| Sidecar 容器 `xiyu-sidecar` | ✅ Up 9 hours（本次无 sidecar 源码变更，未重建，符合预期） |

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200（status=UP） |
| `/actuator/health/readiness` | ✅ HTTP 200 |
| `/`（前端首页） | ✅ HTTP 200 |
| `/login` | ✅ HTTP 200 |
| `POST /api/auth/login`（空包） | ✅ HTTP 400（参数校验生效） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（认证守卫生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端入口 | ✅ `assets/index-1_idEp5T.js`，与 release 一致 |

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### 前端资产保留

- 部署后从上一版本 release 目录（`2ef3f18df...`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件
- `index.html` 指向入口 `assets/index-1_idEp5T.js`（本次前端产物与上一版本一致）

### 迁移应用验证

- `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，2026-10-26 20:01:47
- `failed_count` = 0
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `2ef3f18df5d50b06553fae7127397de9e4557b4b-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/2ef3f18df...-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/2ef3f18df...-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-093b7ce7c-20261028073713.sql.gz` |
| 回滚操作 | 以 `2ef3f18df5d50b06553fae7127397de9e4557b4b` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解读异常 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 服务器迁移目录过时（第 15 次） | ✅ 本次未触发，validate 一次通过 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（193 个文件） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 锚点分支 sync-env 豁免 | ✅ 直接用 `sync-env.sh`（脚本自设 `XYYU_SYNC_ENV_ACTIVE=1`），避免 dev-env 门禁误拦 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属第 13-15 次部署时用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **前端产物与上一版本一致**：本次为后端评分解析修复，前端无变更，旧标签页无需强刷；仍按惯例补齐旧 assets。
4. **`.release/` 已自动清理只保留最近 3 个包**：上一版本本地 tarball 已删除，回滚依赖服务器 release 目录内的 jar + 前端产物（已核实存在）。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `093b7ce7c`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新
- [x] 部署报告产出
