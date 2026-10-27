# 第 238 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 06:42 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `2ef3f18df5d50b06553fae7127397de9e4557b4b-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `2ef3f18df` |
| 旧版本 | `7d700af3e9d70d0426c00370e008c1d3810b1436-api8080`（第 237 次，2026-10-27 22:19） |
| 构建包 | `.release/xiyu-bid-release-2ef3f18df...-api8080.tar.gz`（162.9M，MD5 `fff9e291250ddfb9375af0b1fe409754`） |
| 构建时间 | 2026-10-27T22:41:23Z（06:41:23 UTC+8） |
| 增量 | 8 个 commit，17 个文件（+346 / -5） |
| 激活时间 | 2026-10-28 06:42:13 (UTC+8) |
| DB 备份 | `db-backups/winbid-2ef3f18df-20261028064204.sql.gz`（15MB） |

## PR 列表（增量 8 个 commit，自 7d700af3e..2ef3f18df）

| Commit | 说明 |
|---|---|
| `2ef3f18df` | **!2892** fix(scoreparse): 修正跨行细项满分总分误判 |
| `5b296c8f5` | test(scoreparse): 增加跨行细项满分金标 |
| `ba0027eed` | auto-merge：**!2891** 第 237 次测试环境部署报告 |
| `48427744f` | fix(scoreparse): 恢复跨行评分项原文 |
| `1882dd965` | docs(wiki): 记录评分细项跨行误判 |
| `d689a8b62` | fix(scoreparse): 排除评分细项跨行满分误识别 |
| `aa8002321` | docs(release): 补全第 237 次部署报告文件清单与 Sidecar 冒烟证据 |
| `ce74b4eac` | docs(release): 第 237 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：6 个文件 —— `scoreparse/domain/` 下 `EvidenceText.java`、`LogicalRuleSourceUnit.java`、`OriginalRuleRestoration.java`、`OriginalRuleUnitText.java`、`OriginalScoringContext.java`、`ScoreSubtotalColumnPolicy.java`（跨行评分细项满分与总分误判修复、原文恢复）
- **后端测试 / 金标**：6 个文件 —— `TextPrimaryScoreFactMapperTest`、`EvidenceTextTest`、`OriginalRuleUnitTextTest`、`OriginalScoreEvidenceRoleAndHeadingTest`、`GoldenCasesReplayTest`，新增金标 `case-wrapped-item-maximum.md` + `case-wrapped-item-maximum.expected.json`
- **Wiki**：2 个文件（`.wiki/log.md`、`.wiki/pages/_index.md`）+ 1 个页面更新（`score-subtotal-peritem-semantics-provenance-snapshot.md`）
- **文档**：1 个部署报告（第 237 次 test）
- **Sidecar**：无变更（`sidecarSha256` 与上一版本一致 `c06803d7...`），容器未重建

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，06:42:10） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

> 与第 237 次不同，本次服务器 `/tmp/migration-mysql/` 已是最新，validate 一次通过，未出现 `1225 not resolved locally` 误报。

## 部署步骤

1. 环境门禁：用户确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已是最新）+ 基线确认 `agent/trae-init` @ `2ef3f18df`，工作区干净（HEAD..origin/main 0/0）
3. 服务器现状：旧版本 `7d700af3e...-api8080`、health 200 UP
4. 增量检查：8 commit / 17 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + 容器门禁 `FlywayMysqlContainerTest` + `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-1_idEp5T.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，PID 19970 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测） |
| Sidecar 容器 `xiyu-sidecar` | ✅ Up 8 hours（本次无 sidecar 源码变更，未重建，符合预期） |

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
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

- 部署后从上一版本 release 目录的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件
- `index.html` 指向入口 `assets/index-1_idEp5T.js`（本次前端产物与上一版本一致）

### 迁移应用验证

- `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，2026-10-26 20:01:47
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `7d700af3e9d70d0426c00370e008c1d3810b1436-api8080` |
| 回滚 jar | 上一版本 release 目录下的 `backend/app.jar` |
| 回滚前端 | 上一版本 release 目录下的 `frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `db-backups/winbid-2ef3f18df-20261028064204.sql.gz` |
| 回滚操作 | 以 `7d700af3e9d70d0426c00370e008c1d3810b1436` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh` |
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
| 容器测试门禁 | ✅ 打包内置 `FlywayMysqlContainerTest` 通过 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属第 13-15 次部署时用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **前端产物与上一版本一致**：本次为后端评分解析修复，前端无变更，旧标签页无需强刷；仍按惯例补齐旧 assets。
4. **`.release/` 已自动清理只保留最近 3 个包**：上一版本本地 tarball 已删除，回滚依赖服务器 release 目录内的 jar + 前端产物（已核实存在）。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `2ef3f18df`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新
- [x] 部署报告产出
