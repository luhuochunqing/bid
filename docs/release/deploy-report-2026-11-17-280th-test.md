# 测试环境第 280 次部署报告（中标时间字段 + 评分解析/案例推荐修复）

## 部署基本信息

- **部署时间**: 2026-11-17 20:36 CST 开始 → 20:41:49 DB 备份 → 20:42:05 激活 → 健康检查连续 3/3 通过（总尝试 80 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`，公网入口 `https://winbid-test.ehsy.com`）
- **发布版本 ID**: `69ba418cf`（第 280 次，PR !3046 合并提交）
- **上一版本**: `92d522f8b`（第 279 次）
- **部署方式**: 标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + fail-closed DB 备份）
- **数据库迁移**: 新增 `V1226__add_win_date_to_project_result.sql`，部署后已成功应用（`success=1`，2026-11-17 20:42:12）

## 本轮发布内容

| 次序 | Commit / PR | 内容 |
|---|---|---|
| 1 | `a26c4e636` / `a20d1a7b4`（!3041） | 第 279 次测试环境部署报告（docs） |
| 2 | `26e0c1da3`（!3043） | 案例推荐：修复配送能力误推荐等保证书；先校验内容相关性再推荐历史案例 |
| 3 | `b8cb3ebb0` / `a15850ae3` / `dd9f62d8d` / `c290eaa0d` / `a6c58d80a`（!3044） | 评分解析：修复业绩日期误计并补齐标书核查硬条件；按标书硬条件筛选业绩并收紧核查证据；1133 真实 PDF 离线贯通验收 |
| 4 | `68e19583e` / `53f55e313`（!3042） | 评分解析：修复项目 1133 目录方案误识别及评分规则来源边界 |
| 5 | `9f9355829` / `896e517b1`（!3045） | 案例推荐：补齐诊断日志与合入记录（docs） |
| 6 | `1ff90806c` / `69ba418cf`（!3046） | 项目结果确认合同信息新增中标时间字段（仅中标必填，不回传 CRM） |

- 增量范围：`92d522f8b..69ba418cf` 共 16 个 commit（含 3 个合并提交）
- 改动面：100 个文件，+2877 / -173；主要涉及 `backend`、`src`、`document-converter-sidecar`、`scripts`、`specs`、`docs`
- 前端有改动（`ResultConfirmStage.vue`）：入口 hash 由 `index-DJL8lZxB.js` 变为 `index-CTAQhG95.js`
- sidecar 有改动（`converter.py` 3 行）：SHA-256 由 `b52dabc9…6052c` 变为 `5a1f94aa…646b7`，schemaVersion 仍为 2.2，随包发布

## 部署前预检

| 项 | 结果 |
|---|---|
| 基线 | `agent/trae-init` HEAD = `69ba418cf` = `origin/main`（0/0），工作区干净，早操三连通过（7/7 门禁） |
| 服务器现状 | 上一版 `92d522f8b` 运行中，backend active，健康 `UP`（db/aiProvider/redis/sidecar/jwt 均 UP） |
| Flyway validate | ✅ `Successfully validated 285 migrations`，checksums 全部一致 |
| DB 已应用版本 | 最新 `1225 score estimate freshness`，源码最新 `V1226`，1 个 pending 新迁移 |
| 回滚脚本 | `backend/src/main/resources/db/rollback/migration-mysql/U1226__add_win_date_to_project_result.sql` 存在 |

## 打包与产物校验

| 校验项 | 结果 |
|---|---|
| 容器测试门禁 | ✅ `FlywayMysqlContainerTest` 通过（Spring 全量上下文装配正常） |
| jar 构建 | `BUILD SUCCESS`（36.1s，skipTests） |
| jar 内 Flyway 迁移 | 285 个文件，重复版本 0 |
| `release-metadata.json` | `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false` |
| OBS 直传 | `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| 前端入口 | `assets/index-CTAQhG95.js` |
| 发布包 | `xiyu-bid-release-69ba418cf.tar.gz`（156M） |

## 部署执行

1. `scp` 发布包 + `remote-deploy.sh` 到 `/opt/xiyu-bid/incoming/`
2. DB 备份（fail-closed）：`winbid-69ba418cf-20261117204149.sql.gz`（16M）
3. Flyway validate 预检通过（任何版本切换之前，仅 pending 新迁移为预期状态）
4. 前端激活：自动保留上一版及再前一版 assets（`92d522f8b`、`566b933cc`），防止旧标签页 404
5. 停止 → 原子替换 jar → 写 `deployed-release.json` → 启动 `xiyu-bid-backend`
6. 健康检查连续 3/3 通过（总尝试 80 次，Kafka 启动期 readiness 延迟属已知行为）
7. 前端一致性校验通过：`index.html` 引用 `/assets/index-CTAQhG95.js` 与 release 一致

## 验证结果

| 检查 | 期望 | 实测 |
|---|---|---|
| `/actuator/health` | 200 UP | 200 |
| `/actuator/health/readiness` | UP | 200 UP |
| `POST /api/auth/login`（空体） | 400 参数校验 | 400 |
| `/api/projects`（未认证） | 401/403 | 401 |
| `/api/integration/crm/health`（未认证） | 401 | 401 |
| 前端 `/` 与 `/login` | 200 | 200 / 200 |
| 入口 JS | `index-CTAQhG95.js` | 与 release 一致 |
| 静态资源缓存 | gzip + 强缓存 | 200，`Content-Encoding: gzip`、`Cache-Control: public, immutable` |
| 启动后 DB 迁移 | V1226 应用成功 | `1226 add win date to project result`，`success=1` |
| 后端日志 | 无 ERROR | 启动后 ERROR 计数 0 |
| 旧 assets 保留 | 保留最近 2 个已激活版本 | `/srv/www/xiyu-bid/assets` 共 1037 个文件 |
| macOS `._*` 残留 | 0 | `find` 无输出 |
| 磁盘 | 充足 | `/` 剩余 19G（80% 已用） |

## 配置与清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留在 `/etc/xiyu-bid/backend.env`（测试环境历史决定项，非本次引入）。
- 无其他 `DEBUG` / `TRACE` 临时配置。

## 风险与回滚

- `V1226` 为纯 `ADD COLUMN`（`DATE NULL`，无默认值回填），非破坏性变更，历史数据无需迁移，DB 回滚压力低。
- 回滚锚点：
  - 上一版 release：`/opt/xiyu-bid/releases/92d522f8b`（jar + 前端完整保留）
  - DB 备份：`/opt/xiyu-bid/db-backups/winbid-69ba418cf-20261117204149.sql.gz`（16M）
  - 回滚迁移脚本：`U1226__add_win_date_to_project_result.sql`
  - 回滚步骤见 `docs/release/ROLLBACK.md`
- 前端旧 hash 资源已按机制保留最近 2 个已激活版本，跨版本 preload 404 风险已覆盖。
- E2E 验收已完成，见下节。

## E2E 验收（真实浏览器，2026-11-17 20:50–21:02）

| 用例 | 对应修复 | 结论 | 关键证据 |
|---|---|---|---|
| T1 | !3046 中标时间字段 | PASS | 中标显示必填、未中标隐藏；WON 不填被前端拦截 `请选择中标时间` 且未发请求；LOST 不要求；提交 `201` 后 DB `project_result.win_date=2026-11-10`，重载回填并锁定 |
| T2 | !3043 案例推荐相关性 | PASS | 项目 1377「配送能力」全量 172 条无证书/等保证书，仅 2 条业绩类（4/2 分）；反向「绿色管理体系认证」仍推荐 ISO 认证案例 |
| T3 | !3042 目录/方法说明误识别 | PASS | 受控招标文件（目录 + 编号方法说明 + 4 行评分表 + 合计行）解析仅得 4 项、合计 80 分；目录四章、方法说明、合计行均未成项；分档「服务方案」判为主观项 |
| T4a | !3044 业绩日期硬条件 | PASS | 项目 1388 项 1886 预判依据含 `签约日期不早于2022-01-01`、发票待核验；项 1881 含仓库面积/省市条件；状态 PENDING，`estimated_at`/指纹已封存 |
| T4b | !3044 资格审查不得凭证书判通过 | PASS（限定） | 项目 1364 重跑 25 项：`QUALIFICATION_VETO`/`QUALIFICATION_REVIEW`/`QUALIFICATION_MATCH` 全部 MANUAL_REVIEW，无 PASS；本轮未触发「证书齐全仍判人工」正路径 |

E2E 期间测试数据变更（未清理，便于复现）：1354 已登记 WON 结果；1378 新增 `e2e-toc-tender.pdf` 及解析记录；1364 新增质量核查报告；1388 预判已刷新。

## 待跟进

- 沿用第 279 次参数：`score_parse_fact.confidence` 为 `DECIMAL(5,4)`，模型返回置信度 ≥10 时整轮解析回滚（P1，历史遗留，本次未改动该写入路径）。
- 测试环境 `apihub-test.ehsy.com` OCR search 返回 `code=1/50000` 需排查（P2，同第 279 次）；本次受控 PDF 第 1 页同样报表格 OCR 未完成，确定性表格抽取仍成功。
- E2E 残留盲区：生产案例 331（等保证书）不在测试库，T2 以全量推荐集替代单案重放；T4b 正向证书路径未触发。
