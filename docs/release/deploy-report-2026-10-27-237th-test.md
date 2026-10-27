# 第 237 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-27 22:19 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `7d700af3e9d70d0426c00370e008c1d3810b1436-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `7d700af3e` |
| 旧版本 | `33105524a19fbe04fa1fab721da0bb143ad87589-api8080`（第 236 次，2026-10-26 20:01） |
| 构建包 | `.release/xiyu-bid-release-7d700af3e...-api8080.tar.gz`（156M，MD5 `6f634c6d5ec37f9c82bdcc2415df9782`） |
| 构建时间 | 2026-10-27T14:14:25Z（22:14:25 UTC+8） |
| 增量 | 12 个 commit，10 个文件（+701 / -26） |
| 激活时间 | 2026-10-27 22:19:39 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-7d700af3e-20261027221508.sql.gz`（15MB） |

## PR 列表（增量 12 个 commit，自 33105524a..7d700af3e）

| Commit | 说明 |
|---|---|
| `7d700af3e` | **!2887** docs(release): 第 31 次生产环境部署报告（docs only） |
| `86d1d19b4` | **!2890** feat(scoreparse): 跨页续表合并容忍偶发噪声表并引入空间拓扑边框判定 |
| `08f5a2ea7` | feat(scoreparse): 同上（源提交） |
| `5d2d1a581` | **!2889** test(scoreparse): 增加真实全页边框 PDF 金标用例并增强双列边框与表尾注释防御 |
| `432df681a` | test(scoreparse): 同上（源提交） |
| `ad247a55a` | auto-merge：**!2888** 授权冲突文法修复 |
| `c00d98062` | docs(wiki): 回填授权冲突比对漏判与三态文法扩充 |
| `dbe3f762a` | fix(compliance): 授权冲突比对覆盖跨行写法并收紧掩码身份证位数 |
| `6e704b0e7` | fix(compliance): 授权冲突比对覆盖无冒号写法并扩充三态文法 |
| `21b966b0a` | docs(release): 第 31 次生产环境部署报告（docs only） |
| `045ee5948` | **!2886** docs(release): 第 236 次测试环境部署报告 |
| `bb3cda93b` | docs(release): 第 236 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：2 个文件 —— `compliance/domain/policy/AuthorizationFieldPolicy.java`、`AuthorizationLetterCheckPolicy.java`（授权冲突比对无冒号/跨行写法 + 三态文法扩充 + 掩码身份证位数收紧）
- **后端测试**：1 个文件 —— `AuthorizationLetterCheckPolicyTest.java`
- **Sidecar 源码**：2 个文件 —— `converter.py`（跨页续表合并容忍噪声表、空间拓扑边框判定）、`tests/test_pdf_table_artifact.py` + 新夹具 `page_border_continuation.pdf`；已随部署重启（22:19:32）
- **Wiki**：1 个文件（`.wiki/pages/bid-quality-check-evidence-conflicts.md`）
- **文档**：2 个部署报告（第 236 次 test / 第 31 次 prod）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ⚠️ 首次失败（`1225 not resolved locally`），根因为服务器 `/tmp/migration-mysql/` 过时；同步目录后 ✅ `VALIDATE OK - all checksums match`（285 个迁移） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行） |
| 打包后 jar 内迁移校验 | ✅ 284 个迁移文件，无重复版本号 |

## 部署步骤

1. 环境门禁：用户确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已是最新）+ 基线确认 `agent/trae-init` @ `7d700af3e`，工作区干净
3. 服务器现状：旧版本 `33105524a...-api8080`、health 200 UP
4. 增量检查：12 commit / 10 文件，无迁移变更
5. Flyway 预检 3 步（含同步服务器 `/tmp/migration-mysql/` 到 284 个文件）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-1_idEp5T.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，PID 22338 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测，约 2.6 分钟） |
| Sidecar 进程 | ✅ 22:19:32 重启（PID 22146），`/health` = `{"status":"up"}` |

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

- 部署后从上一版本 `releases/33105524a.../frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件
- `index.html` 指向新入口 `assets/index-1_idEp5T.js`

### 迁移应用验证

- `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，2026-10-26 20:01:47
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `33105524a19fbe04fa1fab721da0bb143ad87589-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/33105524a...-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/33105524a...-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `winbid-7d700af3e-20261027221508.sql.gz` |
| 回滚操作 | 以 `33105524a19fbe04fa1fab721da0bb143ad87589` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / Sidecar 转换异常 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 服务器 `/tmp/migration-mysql/` 过时（第 15 次） | ⚠️ 本次再次触发 validate 误报；已同步目录（284 个文件）后通过，建议把同步动作固化进部署脚本 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐 |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| Sidecar 随源码变更需重启 | ✅ `converter.py` 变更，部署流程已重启 Sidecar（22:19:32） |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属第 13-15 次部署时用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **健康检查耗时约 2.6 分钟**：Kafka SDK 启动延迟属已知行为，未做回滚。
4. **服务器 `/tmp/migration-mysql/` 会再次过时**：下次部署前若 validate 报 `not resolved locally`，先同步该目录，避免误判为迁移异常。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `7d700af3e`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] Sidecar 健康 UP
- [x] 前端资产保留完成
- [x] 部署记录 `deployed-release.json` 已更新
- [x] 部署报告产出
