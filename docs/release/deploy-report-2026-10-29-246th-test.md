# 第 246 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-29 07:34 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `233f9921c-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，纯 ff-only 同步，工作区干净） |
| HEAD Commit | `233f9921c` |
| 旧版本 | `de14363ad-api8080`（第 245 次，2026-10-28 22:07） |
| 构建包 | `.release/xiyu-bid-release-233f9921c-api8080.tar.gz`（155M，MD5 `7325c82d3e7b942110b6735ac2daf520`） |
| 构建时间 | 2026-10-28T23:32:38Z（07:32:38 UTC+8） |
| 增量 | 9 个 commit，16 个文件（+1289 / -167）：backend 9 / wiki 2 / sidecar 2 / docs 2 / 决策记录 1 |
| 激活时间 | 2026-10-29 07:34:39 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-233f9921c-20261029073324.sql.gz`（15M） |

## PR / Commit 列表（自 de14363ad..233f9921c）

| Commit | 说明 |
|---|---|
| `233f9921c` | **!2913** feat(scoreparse): 百度OCR作为评分主干接管表格抽取与2D拓扑网格修复 |
| `9d12e1305` | **!2912** fix(sidecar): 按表头动态识别规则列与跨页逻辑行精准缝合，根治非末列规则断裂导致的95分问题 |
| `4d240f89c` | **!2911** fix(scoreparse): 支持单元格内细则确定性对账回补，根治大模型漏抽吞分导致的95分问题 |
| `91d7e66d3` | **!2910** docs(release): 第 245 次测试环境部署报告 (test) |
| `f5b300e13` / `1c9a1f245` / `e103bdeaf` / `8bd703c20` / `853c02380` | 上述 PR 对应的分支提交与文档提交 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：6 个文件 —— `scoreparse/application/CellSubRuleRecovery.java`、`MultimodalExtractionReviewPolicy.java`、`scoreparse/infrastructure/baidu/BaiduCrossPageTableLinker.java`、`BaiduTableArtifactAdapter.java`、`BaiduTableArtifactEnricher.java`、`BaiduTableGridBuilder.java`
- **后端测试**：3 个文件 —— `CellSubRuleRecoveryTest.java`、`BaiduTableArtifactAdapterTest.java`、`BaiduTableArtifactEnricherTest.java`
- **Sidecar**：2 个文件 —— `converter.py`（按表头动态识别规则列 + 跨页逻辑行缝合）、`tests/test_pdf_logical_rows.py`；sidecar 源码指纹 `c06803d7…` → `5eacdf5c…`，**容器已自动重建并重启**
- **Wiki**：2 个文件 —— `baidu-table-ocr-pipeline.md`、`scoreparse-text-paragraph-and-continuation-fix.md`
- **文档**：2 个文件 —— `docs/lessons/lessons-learned.md`、第 245 次部署报告
- **前端**：无源码变更（入口 `assets/index-y3uloFVf.js` 与上一版本一致，assets 193 个，hash 未漂移 → 无跨版本 404 风险）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225 `score estimate freshness`，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，07:34:36） |
| 打包后 jar 内迁移校验 | ✅ `db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 基线确认：`agent/trae-init` HEAD = `origin/main` @ `233f9921c`，工作区干净，git wrapper 就绪
3. 服务器现状：旧版本 `de14363ad-api8080`（2026-10-28 22:07 激活）、backend active
4. 增量检查：9 commit / 16 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，`mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、`git.commit.id=233f9921c`、前端入口 `index-y3uloFVf.js`、jar 迁移 284 无重复
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. Sidecar 增量升级：源码指纹变化 → 镜像重建 + 容器重启 + 健康门禁通过
10. 旧版本前端 assets 补齐（`de14363ad` → 当前，防旧标签页跨版本 404）
11. 健康检查 + 迁移验证 + 外部 Smoke + Nginx 缓存复查 + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，07:34:40 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 80 次探测 ≈ 2.7 分钟） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全部 UP |
| 错误日志（近 10 分钟） | ✅ 仅本次 Smoke 空包登录产生的 400 校验告警（预期） |

> 80 次探测属已沉淀的 Kafka SDK readiness 延迟已知行为，非本次缺陷。

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

- ✅ 指纹变更触发升级：`c06803d78c…` → `5eacdf5c19…`
- ✅ `schemaVersion=2.2`，容器 `xiyu-sidecar` 重启后 `/health` 健康检查通过
- ✅ 后端 health 中 `sidecar` 组件 UP（`http://localhost:8000` reachable）

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### Nginx 缓存 / gzip（第 18 条教训复查）

- ✅ `/assets/index-y3uloFVf.js` → `Content-Encoding: gzip` + `Cache-Control: public, immutable`（`max-age=31536000`）
- ✅ `/index.html` → `Cache-Control: no-cache, no-store, must-revalidate`

### 前端资产保留

- 部署后从上一版本 release 目录（`de14363ad-api8080`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 193 个文件（新旧 release assets 数量一致，本版本前端源码未变、hash 未漂移）
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- ✅ `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，installed_on = 2026-10-26 20:01:47
- ✅ 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `de14363ad` |
| 回滚 jar | `/opt/xiyu-bid/releases/de14363ad-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/de14363ad-api8080/frontend/` |
| 回滚 Sidecar | 源码需回退到 `c06803d7…` 对应版本后重建镜像（当前 `xiyu-sidecar` 容器为 `5eacdf5c…`） |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-233f9921c-20261029073324.sql.gz` |
| 回滚操作 | 以 `de14363ad` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh`（或直接回切 releases 目录内旧 artifact）；前端须随同 release 重铺 `/srv/www/xiyu-bid` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解析（本次改动区域）报错 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后检查残留 0 |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（本版本无 hash 漂移） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 前端目录属主（第 10 次） | ✅ remote-deploy 自动修复非 jetty 属主条目 |
| Nginx 缓存 / gzip（第 18 条） | ✅ 部署后复查 immutable + gzip 生效 |
| sidecar 确定性指纹（第 228 次） | ✅ 指纹变化自动重建镜像 + 容器重启 + 健康门禁 |
| readiness 延迟 | ✅ 已知行为，未误判为故障 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：历史保留配置，非本次新引入。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **本次改动集中在评分解析（百度 OCR 表格主干 + 2D 网格）与 Sidecar 表格抽取**：需重点回归「评分解析」链路的非末列规则断裂、单元格内细则漏抽、跨页逻辑行缝合三个已知 95 分问题，并验证 2D 拓扑网格修复效果。
4. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。
5. **增量含 `decisions.tsv`（决策记录）**：属仓库治理文件，不影响运行时。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `233f9921c`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口 / git commit id）
- [x] DB 预部署备份完成
- [x] Sidecar 变更升级并健康通过
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `233f9921c-api8080`）
- [x] 部署报告产出
