# 第 243 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 19:26 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `dd29f5965-api8080` |
| 部署分支 | 打包在 `agent/trae-init`（锚点分支，纯 ff-only 同步） |
| HEAD Commit | `dd29f5965` |
| 旧版本 | `df41c64a7-api8080`（第 242 次，2026-10-28 15:33） |
| 构建包 | `.release/xiyu-bid-release-dd29f5965-api8080.tar.gz`（155M，MD5 `d9d34721632e2430ecce24eac9824bc0`） |
| 构建时间 | 2026-10-28T11:25:02Z（19:25:02 UTC+8） |
| 增量 | 8 个 commit，20 个文件（+771 / -27）：backend 7 / frontend 2 / scripts 1 / docs 5 / wiki 3 / 其他 2 |
| 激活时间 | 2026-10-28 19:26:48 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-dd29f5965-20261028192639.sql.gz`（15M） |

## PR / Commit 列表（自 df41c64a7..dd29f5965）

| Commit | 说明 |
|---|---|
| `dd29f5965` | **!2905** feat(scoreparse): 实现 BaiduTableArtifactEnricher 文档增强器补齐 PDF 跨页续表与大单元格表格证据 |
| `b267bbe59` | test(scoreparse): CrossFormatDocumentSourceReplayTest 补充 PDF 增强表格多源整合回归契约 |
| `9b06aeb1f` | docs(wiki): 回填 BaiduTableArtifactEnricher 文档增强架构与续表融合机制 |
| `b042eac28` | feat(scoreparse): 实现 BaiduTableArtifactEnricher 文档增强器 |
| `64b196099` | **!2903** docs(release): 第 242 次测试环境部署报告 (test) |
| `4b97d7a33` | **!2904** fix(settings): 规范 defineModel 解构绑定并补齐前端构建门禁与 SFC 规约 |
| `58ba10e75` | fix(settings): 修复 defineModel 解构绑定并补齐前端构建门禁与 SFC 规范 [skip e2e-ui-sync] |
| `e43f08ebc` | docs(release): 第 242 次测试环境部署报告 (test) |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码/测试**：7 个文件，集中在 `scoreparse` 模块（`BaiduTableArtifactEnricher` 新增、`BaiduTableArtifactAdapter`、`SidecarDocumentArtifactExtractor`、3 个测试类 + `project1130.pdf` 测试资源）
- **前端**：2 个文件 —— `src/views/System/settings/AiModelSettingsPanel.vue`（defineModel 解构绑定修复）、`useAiModelSettings.spec.js`
- **脚本 / 门禁**：1 个文件 —— `scripts/pre-push-gate.sh`（补齐构建门禁）
- **文档**：5 个文件 —— `FRONTEND.md`、`RELIABILITY.md`、`docs/lessons/lessons-learned.md`、`docs/references/vue-gotchas.md`、第 242 次部署报告
- **Wiki**：3 个文件（百度表格 OCR 管线、门禁命令范围陷阱、log）
- **Sidecar**：无变更（`sidecarSha256` = `c06803d7...`，与上一版本一致，跳过容器重建）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，19:26:45） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 早操：`sync-env.sh`（init 分支 ff-only，本地门禁 7/7 通过）+ 基线确认 `origin/main` @ `dd29f5965`，工作区干净
3. 服务器现状：旧版本 `df41c64a7-api8080`（当日 15:33 激活）、health UP、服务 active
4. 增量检查：8 commit，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，含容器测试门禁（`FlywayMysqlContainerTest`）与 `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-y3uloFVf.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（`df41c64a7` → 当前，防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，19:26:48 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 80 次探测，约 2.7 分钟） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全部 UP |
| 错误日志（近 10 分钟，priority=err） | ✅ 无记录 |

> 健康检查耗时约 2.7 分钟，属已沉淀的 Kafka SDK readiness 延迟已知行为（第 8/9/10/13/15 次同类现象），非本次缺陷。

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

- 部署后从上一版本 release 目录（`df41c64a7-api8080`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 277 个文件
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- ✅ `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，installed_on = 2026-10-26 20:01:47
- ✅ `failed=0`（`success=0` 记录数为 0）
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `df41c64a7` |
| 回滚 jar | `/opt/xiyu-bid/releases/df41c64a7-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/df41c64a7-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-dd29f5965-20261028192639.sql.gz` |
| 回滚操作 | 以 `df41c64a7` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh`（或直接回切 releases 目录内旧 artifact） |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解析（本次改动区域）报错 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后清理（残留 0） |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（277 个文件） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 前端目录属主（第 10 次） | ✅ remote-deploy 自动 `sudo chown -R jetty` 修复非 jetty 属主条目 |
| Nginx 缓存 / gzip（第 18 条） | ✅ 部署后复查 immutable + gzip 生效 |
| sidecar 确定性指纹（第 228 次） | ✅ 源码指纹未变，跳过容器重建 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：历史用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **本次改动集中在评分解析（`BaiduTableArtifactEnricher`）**：需重点回归「标讯/评分解析」链路中 PDF 跨页续表与大单元格表格的证据抽取结果。
4. **前端仅 `AiModelSettingsPanel.vue` 变更**：与第 242 次已修复的 defineModel 问题同源，本次为规范收口，建议顺手回归「系统设置 → AI 模型配置」页面。
5. **GitHub 镜像与 Gitee 方向告警**：早操时 `sync-env.sh` 报告 GitHub main 领先 Gitee 97 个 commit，属跨远端同步问题，不影响本次从 Gitee main 打包部署，但需单独立项收敛。
6. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `dd29f5965`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `dd29f5965-api8080`）
- [x] 部署报告产出
