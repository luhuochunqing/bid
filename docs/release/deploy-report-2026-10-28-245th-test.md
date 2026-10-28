# 第 245 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 22:07 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `de14363ad-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，纯 ff-only 同步，工作区干净） |
| HEAD Commit | `de14363ad` |
| 旧版本 | `0609edc0e-api8080`（第 244 次，2026-10-28 20:17） |
| 构建包 | `.release/xiyu-bid-release-de14363ad-api8080.tar.gz`（155M，MD5 `a145061ecf7afcd22a087c4c95353e6b`） |
| 构建时间 | 2026-10-28T14:06:14Z（22:06:14 UTC+8） |
| 增量 | 4 个 commit，6 个文件（+217 / -6）：backend 3 / docs 2 / wiki 1 |
| 激活时间 | 2026-10-28 22:07:02 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-de14363ad-api8080-20261028220652.sql.gz`（15M） |

## PR / Commit 列表（自 0609edc0e..de14363ad）

| Commit | 说明 |
|---|---|
| `de14363ad` | **!2909** fix(scoreparse): 支持跨页截断单元格引文容错与正文细则得分保护，消除方案外误判 |
| `eec4f01a4` | fix(scoreparse): 支持跨页截断单元格引文容错与正文细则得分保护，消除方案外误判 |
| `a9052ce09` | **!2908** docs(release): 第 244 次测试环境部署报告 (test) |
| `659011076` | docs(release): 第 244 次测试环境部署报告 (test) |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：2 个文件 —— `scoreparse/domain/OriginalScoreEvidencePolicy.java`、`OriginalScoreWeightEvidencePolicy.java`（跨页截断单元格引文容错 + 正文细则得分保护）
- **后端测试**：1 个文件 —— `OriginalScoreEvidencePolicyTest.java`（新增 21 行用例）
- **Wiki**：1 个文件 —— `.wiki/pages/baidu-table-ocr-pipeline.md`
- **文档**：2 个文件 —— `docs/lessons/lessons-learned.md`、第 244 次部署报告
- **前端**：无源码变更（入口 `assets/index-y3uloFVf.js` 与上一版本一致，assets 193 个，hash 未漂移 → 无跨版本 404 风险）
- **Sidecar**：无变更（`sidecarSha256` = `c06803d7...`，与上一版本一致，跳过容器重建）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225 `score estimate freshness`，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，22:06:59） |
| 打包后 jar 内迁移校验 | ✅ `db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 早操：`sync-env.sh`（init 分支 ff-only）+ 本地门禁 7/7 通过；基线确认 `origin/main` @ `de14363ad`，工作区干净
3. 服务器现状：旧版本 `0609edc0e-api8080`（当日 20:17 激活）、health HTTP 200
4. 增量检查：4 commit / 6 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，含容器测试门禁（`FlywayMysqlContainerTest`）+ `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-y3uloFVf.js`、jar 迁移 284 无重复
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（`0609edc0e` → 当前，防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，22:07:02 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全部 UP |
| 错误日志（近 10 分钟，priority=err） | ✅ `No entries` |

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

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### Nginx 缓存 / gzip（第 18 条教训复查）

- ✅ `/assets/index-y3uloFVf.js` → `Content-Encoding: gzip` + `Cache-Control: public, immutable`（`max-age=31536000`）

### 前端资产保留

- 部署后从上一版本 release 目录（`0609edc0e-api8080`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件（与新旧 release 的 assets 数量一致，本版本前端源码未变、hash 未漂移）
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- ✅ `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，installed_on = 2026-10-26 20:01:47
- ✅ `failed_count = 0`
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `0609edc0e` |
| 回滚 jar | `/opt/xiyu-bid/releases/0609edc0e-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/0609edc0e-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-de14363ad-api8080-20261028220652.sql.gz` |
| 回滚操作 | 以 `0609edc0e` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh`（或直接回切 releases 目录内旧 artifact）；前端须随同 release 重铺 `/srv/www/xiyu-bid`，只拷目录会导致 `index.html` 与 assets 版本不匹配 |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解析（本次改动区域）报错 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后检查残留 0 |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（193 个文件，本版本无 hash 漂移） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 前端目录属主（第 10 次） | ✅ remote-deploy 自动修复非 jetty 属主条目 |
| Nginx 缓存 / gzip（第 18 条） | ✅ 部署后复查 immutable + gzip 生效 |
| sidecar 确定性指纹（第 228 次） | ✅ 源码指纹未变，跳过容器重建 |
| 容器测试门禁（第 111 次事故） | ✅ `FlywayMysqlContainerTest` 通过后再打包 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：历史用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **本次改动集中在评分解析域**：需重点回归「评分解析」链路中跨页截断单元格引文容错与正文细则得分保护，确认方案外误判已消除。
4. **GitHub 镜像与 Gitee 方向告警**：早操时 `sync-env.sh` 报告 GitHub main 领先 Gitee 97 个 commit，属跨远端同步问题，不影响本次从 Gitee main 打包部署，但需单独立项收敛。
5. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `de14363ad`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `de14363ad-api8080`）
- [x] 部署报告产出
