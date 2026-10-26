# 第 236 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-26 20:01 (UTC+8) |
| 部署脚本 | `ENV=test SKIP_SESSION_GATE=1 XIYU_DEV_CONFIRMED=1 bash scripts/release/deploy-test.sh 33105524a19fbe04fa1fab721da0bb143ad87589` |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `33105524a19fbe04fa1fab721da0bb143ad87589-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `33105524a` |
| 旧版本 | `c9d8a2b4c903da6a3cc40e1a32b703cfb3588486-api8080`（第 235 次，2026-10-25 19:25） |
| 构建包 | `.release/xiyu-bid-release-33105524a...-api8080.tar.gz`（155M，MD5 `ad37327024aab2206bbd54866fe8fffc`） |
| 构建时间 | 2026-10-26T11:59:30Z（19:59:30 UTC+8） |
| 增量 | 36 个 commit，148 个文件（+5275 / -593） |
| 激活时间 | 2026-10-26 20:01:40 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-33105524a...-api8080-20261026200122.sql.gz` |

## PR 列表（增量 36 个 commit，自 c9d8a2b4c..33105524a）

| Commit | 说明 |
|---|---|
| `33105524a` | **!2885** docs(wiki): 补录项目1117 CA生产验收 |
| `2fe6f43a2` | **!2884** fix(scoreparse): 修复纯文本正文引用穿透还原与表格中间列跨行续行判定 |
| `e80ef4f72` | **!2883** fix(scoreparse): 强制证书规则并刷新过期估算 |
| `8f3a541d2` | **!2882** fix(scoreparse): 过滤全页边框伪表格 + 跨页续表合并 + 半开区间 |
| `501bcbdd0` | **!2881** fix(scoreparse): 修复销售额等客观项被误判为主观分 |
| `b7f3dac5e` | **!2880** feat(scoreparse): 080 评分项跨章节穿透资格项业绩检索与精准匹配 |
| `fd4064485` | **!2879** fix(compliance): 修复标书质量核查误判与证据假通过 |
| `1ea7f0c7b` | **!2878** fix(wiki-governance): wiki-backfill-active 收紧为仅刷新 archive 页 |
| `79a5b7bc6` | **!2877** docs(wiki): 全量复核 78 个过期页面 |
| `7e0481cd2` | **!2876** docs(release): 第 30 次生产环境部署报告（docs only） |

> 上表仅列 10 条代表性提交；完整 36 个 commit 见 `git log --oneline c9d8a2b4c..33105524a`。

## 改动范围

- **数据库迁移**：1 个新增 `V1225__score_estimate_freshness.sql`（幂等，仅给 `score_item` 追加两个可空列 `estimate_input_fingerprint` / `estimated_at`，不重算历史数据），配套 `U1225` 回滚脚本齐全
- **后端主源码**：43 个 Java 文件（`scoreparse/` 跨章节穿透、证书规则、伪表格过滤、续行判定、估算新鲜度；`compliance/` 资质核查证据边界）
- **后端测试**：32 个测试文件 + golden 夹具
- **前端**：9 个文件（`ScoreParseDrawer.vue`、`src/api`、`src/composables`、`src/views`）
- **Wiki**：39 个文件（`.wiki/pages/` 回填）
- **规格/文档**：`specs/` 13 个（080 / 081 / 077）、`docs/` 4 个、`scripts/` 2 个
- **Sidecar 源码**：2 个文件（`converter.py` + 测试），已随部署同步并重启（进程 23869 启动于 20:01:31），运行中 `converter.py` sha256 与本地 HEAD 一致

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（284 个迁移校验通过） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ 部署前 DB 最新 V1224，源码 V1225（1 个 pending，预期） |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，仅 pending 新迁移为预期状态） |
| 打包后 jar 内迁移校验 | ✅ 无重复版本号（V1225 + U1225 齐全） |

## 部署步骤

1. 环境门禁：用户确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已是最新）+ `check-git-wrapper.sh`（7/7 通过）
3. 基线确认：`agent/trae-init` @ `33105524a`，工作区干净，与 `origin/main` 零差异
4. 服务器现状：旧版本 `c9d8a2b4c`、健康 UP、服务 active
5. Flyway 预检 3 步（见上表）
6. 本地打包（`deploy-test.sh` 固化）：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `VITE_SENTRY_DSN=`（test 留空）
7. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份
8. 旧版本前端 assets 补齐（防旧标签页跨版本 404）
9. 健康检查 + 迁移验证 + 外部 Smoke
10. 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，PID 24065 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 79 次探测，约 2.6 分钟） |
| Sidecar 进程 | ✅ 20:01:31 重启，加载最新 `converter.py` |

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

### Sidecar 发布冒烟（`smoke-sidecar.sh`）

- ✅ Health + Schema Fingerprint：200 UP，`X-Schema-Version=2.2`
- ✅ Fixture 回放（1371_tables_v1.json）：tables=14 / cells=8215 / scoreTables=1
- ✅ v1 探针 200 / v2 探针 200（DocumentArtifact schemaVersion=2.2）
- **总结：5 项通过 / 0 失败 / 0 警告**

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ 线上 Detail chunk `.upload(` 调用数 = 2

### 前端资产保留

- 部署后旧版本独有 hash 资产缺失 0（`cp -rn` 补齐完成）
- `/srv/www/xiyu-bid/assets` 共 277 个文件
- `index.html` 仍指向新入口 `assets/index-1_idEp5T.js`

### 迁移应用验证

- `flyway_schema_history`：V1225 `score estimate freshness`，success=1，2026-10-26 20:01:47
- `score_item` 新增列已生效：`estimate_input_fingerprint` varchar / `estimated_at` datetime，均 NULL 可空

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `c9d8a2b4c903da6a3cc40e1a32b703cfb3588486-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/c9d8a2b4c.../backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/c9d8a2b4c.../frontend/` |
| 数据库回滚 | 回滚脚本 `U1225`（仅删两列）；数据恢复用 `winbid-33105524a...-20261026200122.sql.gz` |
| 回滚操作 | 重新以 `c9d8a2b4c903da6a3cc40e1a32b703cfb3588486` 为 RELEASE_ID 执行 `deploy-test.sh` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 由 `deploy-test.sh` 固化 |
| OBS 直传漏传（第 84 次 / 生产第 8 次事故） | ✅ 脚本固化 + 产物双校验（`obsEnabled=true`、`.upload(`=2） |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留（第 10 次） | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐，缺失归零 |
| Mac HTTP_PROXY 502（第 19/23 次） | ✅ 所有 curl 加 `--noproxy '*'` |
| 锚点分支 session 门禁 | ✅ 沿用 `SKIP_SESSION_GATE=1`（运维场景） |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属第 13-15 次部署时用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史 9 次一致）。
3. **健康检查耗时约 2.6 分钟**：`Kafka SDK` 启动延迟属已知行为（历史第 8/9/10/13/15 次），未做回滚。
4. **V1225 仅新增可空列**：历史评分记录的估算指纹保持 NULL，不做回填重算；如需让旧评分项获得新鲜度判定，需业务侧重新触发估算。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 早操三连通过
- [x] 基线干净（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] Sidecar 冒烟 5/5
- [x] 前端资产保留完成
- [x] 部署记录 `deployed-release.json` 已更新
- [x] 部署报告产出
