# 第 248 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-29 11:14 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `a44808464` |
| 部署分支 | `agent/trae-init`（锚点分支，ff-only 同步，工作区干净） |
| HEAD Commit | `a44808464`（= `origin/main`） |
| 旧版本 | `d4125b1d2`（第 247 次，2026-10-29 10:06） |
| 构建包 | `.release/xiyu-bid-release-a44808464.tar.gz`（156M） |
| 构建时间 | 2026-10-29T03:13:10Z（11:13:10 UTC+8） |
| 增量 | 5 个 commit，7 个文件（+320 / -16）：backend-main 2 / backend-test 3 / wiki 1 / docs 1 |
| 激活时间 | 2026-10-29 11:14:10 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-a44808464-20261029111359.sql.gz`（15M） |

## PR / Commit 列表（自 d4125b1d2..a44808464）

| Commit | 说明 |
|---|---|
| `a44808464` | **!2918** fix(scoreparse): 恢复百度OCR同行中文句号子项标书原文并隔离评审因素列名混入 |
| `5833f90e1` | docs(wiki): 回填百度OCR同行中文句号子项切分与名称列隔离契约 |
| `a4016e987` | fix(scoreparse): 恢复百度OCR同行中文句号子项标书原文并隔离评审因素列名混入 |
| `438608488` | Merge pull request !2917 from 炉火纯青/agent/trae/docs-deploy-report-247 |
| `ad753481a` | docs(release): 第 247 次测试环境部署报告 (test) |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：2 个文件 —— `scoreparse/domain/`（`OriginalRuleRestoration`、`OriginalRuleUnitText`）
- **后端测试**：3 个文件 —— `Project1382FullPipelineIntegrationTest`、`OriginalRuleRestorationTest`、`OriginalRuleUnitTextTest`
- **Sidecar**：无变更（源码指纹 `5eacdf5c19…` 与上一版本一致，未重建镜像）
- **Wiki**：1 个文件 —— `baidu-table-ocr-pipeline.md`
- **文档**：1 个文件 —— 第 247 次部署报告
- **前端**：无源码变更（入口 `assets/index-y3uloFVf.js` 与上一版本一致，assets 193 个，hash 未漂移 → 无跨版本 404 风险）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225 `score estimate freshness`（success=1），源码最大 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，11:14:06） |
| 打包后 jar 内迁移校验 | ✅ `db/migration-mysql/V*.sql` 无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 up-to-date）+ `check-git-wrapper.sh`（7/7 通过）
3. 基线确认：`agent/trae-init` HEAD = `origin/main` @ `a44808464`，工作区干净
4. 服务器现状：旧版本 `d4125b1d2`（2026-10-29 10:06 激活）、health UP
5. 增量检查：5 commit / 7 文件，无迁移变更
6. Flyway 预检 3 步（validate 一次通过）
7. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，`mvn clean -DskipTests package`
8. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-y3uloFVf.js`、jar 迁移无重复
9. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
10. 旧版本前端 assets 补齐（`d4125b1d2` → 当前，防旧标签页跨版本 404）
11. 健康检查 + 迁移验证 + 外部 Smoke + Nginx 缓存复查 + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，11:14:10 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测 ≈ 2.6 分钟） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全 UP |
| 错误日志（近 10 分钟） | ✅ `ERROR` 命中 0 行 |

> 79 次探测属已沉淀的 Kafka SDK readiness 延迟已知行为，非本次缺陷。

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（未出现 503） |
| `/`（前端首页） | ✅ HTTP 200 |
| `/login` | ✅ HTTP 200 |
| `POST /api/auth/login`（空包） | ✅ HTTP 400（参数校验生效） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（认证守卫生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端入口 | ✅ `assets/index-y3uloFVf.js`，与 release 一致 |

### Sidecar

- ✅ 源码指纹未变（`5eacdf5c19…`，`schemaVersion=2.2`），未触发镜像重建
- ✅ 后端 health 中 `sidecar` 组件 UP

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ 打包脚本校验 Detail chunk `.upload(` 调用数 = 2

### Nginx 缓存 / gzip（第 18 条教训复查）

- ✅ `/assets/index-y3uloFVf.js` → `Content-Encoding: gzip` + `Cache-Control: public, immutable`（`max-age=31536000`）
- ✅ `/index.html` → `Cache-Control: no-cache, no-store, must-revalidate`

### 前端资产保留

- 部署后从上一版本 release 目录（`d4125b1d2`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件（本版本前端源码未变、hash 未漂移）
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- ✅ `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，installed_on = 2026-10-26 20:01:47
- ✅ `success=0` 记录数 = 0
- ✅ 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `d4125b1d2` |
| 回滚 jar | `/opt/xiyu-bid/releases/d4125b1d2/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/d4125b1d2/frontend/` |
| 回滚 Sidecar | 本次未变更，无需回滚（当前 `5eacdf5c19…`） |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-a44808464-20261029111359.sql.gz` |
| 回滚操作 | 以 `d4125b1d2` 为 RELEASE_ID 重跑部署（或直接回切 releases 目录内旧 artifact）；前端须随同 release 重铺 `/srv/www/xiyu-bid` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解析（本次改动区域）报错 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=`，`apiBaseUrl=""` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后检查残留 0 |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（本版本无 hash 漂移） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| Nginx 缓存 / gzip（第 18 条） | ✅ 部署后复查 immutable + gzip 生效 |
| readiness 延迟 | ✅ 已知行为，未误判为故障 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：历史保留配置，非本次新引入。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **本次改动集中在评分解析（百度 OCR 同行中文句号子项 + 评审因素列名隔离）**：需重点回归「评分解析」链路的项目 1382 全闭环、同行句号子项标书原文恢复、评审因素列名混入隔离三类问题。
4. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `a44808464`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `a44808464`）
- [x] 部署报告产出
