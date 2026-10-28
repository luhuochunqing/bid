# 第 240 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 09:27 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `7fec3671d-api8080` |
| 部署分支 | `agent/trae/deploy-test-20261028`（基于 `origin/main`） |
| HEAD Commit | `7fec3671d` |
| 旧版本 | `093b7ce7c35e6982936d197ccc492810f19f4544-api8080`（第 239 次，2026-10-28 07:37） |
| 构建包 | `.release/xiyu-bid-release-7fec3671d-api8080.tar.gz`（155M，MD5 `07b2f97f5f2260d609c124db9e265d29`） |
| 构建时间 | 2026-10-28T01:26:41Z（09:26:41 UTC+8） |
| 增量 | 9 个 commit，42 个文件（+1050 / -134），其中代码变更 5 个 commit |
| 激活时间 | 2026-10-28 09:27:32 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-7fec3671d-20261028092722.sql.gz`（15M） |

## PR 列表（增量 9 个 commit，自 093b7ce7c..7fec3671d）

| Commit | 说明 |
|---|---|
| `7fec3671d` | **!2896** fix(scoreparse): 保留风险提示并允许人工采纳评分候选 |
| `cd8f035e8` | fix(scoreparse): 允许人工核对未验证评分候选 |
| `4cf44d2ea` | fix(scoreparse): 允许人工确认后采纳评分候选 |
| `09eba6507` | **!2895** docs(release): 第 239 次测试环境部署报告 |
| `46f0c0948` | fix(scoreparse): 评分行覆盖须关联原文规则 |
| `fbc68a8ce` | fix(scoreparse): 补充解析后校验漏识别行 |
| `6e85b6f95` | fix(scoreparse): 校验父子关系并恢复完整评分规则 |
| `84d4cf281` | docs(release): 修正第 239 次报告本地包保留与门禁记录 |
| `c8068725f` | docs(release): 第 239 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码**：16 个文件，集中在 `scoreparse` 模块（`application` 12 个 + `domain` 5 个 + `dto` 1 个），属评分解析人工采纳链路（保留风险提示、未验证候选人工核对、父子关系校验、评分行覆盖关联原文规则、省略号单例扣分证据绑定）
- **后端测试 / 金标**：15 个测试文件 + 1 个金标用例（`case-ellipsis-singleton-deduction.md` / `.expected.json`），`GoldenCasesReplayTest` 增用例
- **前端**：6 个文件 —— `useScoreCandidateAdoption.js` / `manualScoreCandidate.js` 及其单测、`ScoreCandidateReviewCard.vue` 及其单测
- **文档**：1 个部署报告（第 239 次 test）
- **Sidecar**：无变更（`sidecarSha256` = `c06803d7...`，与上一版本一致），容器未重建（`xiyu-sidecar` Up 11 hours）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，09:27:29） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：用户确认 `ENV=test` / 172.16.38.78
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已是最新）+ 基线确认 `origin/main` @ `7fec3671d`，工作区干净，本地门禁 7/7 通过
3. 服务器现状：旧版本 `093b7ce7c...-api8080`、health 200 UP、服务 active
4. 增量检查：9 commit / 42 文件，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `mvn clean -DskipTests package`
7. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-WidgtE95.js`
8. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
9. 旧版本前端 assets 补齐（防旧标签页跨版本 404）
10. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，MainPID 31912 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 80 次探测） |
| Sidecar 容器 `xiyu-sidecar` | ✅ Up 11 hours（本次无 sidecar 源码变更，未重建，符合预期） |

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200（status=UP） |
| `/actuator/health/readiness` | ✅ HTTP 200（Kafka 延迟本次未出现） |
| `/`（前端首页） | ✅ HTTP 200 |
| `/login` | ✅ HTTP 200 |
| `POST /api/auth/login`（空包） | ✅ HTTP 400（参数校验生效） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（认证守卫生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端入口 | ✅ `assets/index-WidgtE95.js`，与 release 一致 |

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### 前端资产保留

- 部署后从上一版本 release 目录（`093b7ce7c...`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 277 个文件
- `._*` macOS 残留文件数 = 0

### 迁移应用验证

- `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，2026-10-26 20:01:47
- `failed_count` = 0
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `093b7ce7c35e6982936d197ccc492810f19f4544-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/093b7ce7c...-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/093b7ce7c...-api8080/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-7fec3671d-20261028092722.sql.gz` |
| 回滚操作 | 以 `093b7ce7c35e6982936d197ccc492810f19f4544` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh` |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / 评分解读异常 |

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
| 锚点分支 session 门禁 | ✅ 按规范新建任务分支 `agent/trae/deploy-test-20261028`，未在锚点分支直接操作 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：属历史用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **前端产物有变更**：本次含 `ScoreCandidateReviewCard.vue` 等前端改动，入口 `index-WidgtE95.js` 与上一版本不同；部署后已补齐旧 assets，旧标签页仍建议刷新。
4. **本地 `.release/` 保留最近 3 个包**：本次清理了 `7d700af3e...`，上一版本 `093b7ce7c...-api8080.tar.gz` 仍在 `.release/`，回滚可走本地 tarball 或服务器 release 目录双路径。
5. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署；建议后续同步更新。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `7fec3671d`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新
- [x] 部署报告产出
