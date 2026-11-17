# 测试环境第 278 次部署报告（评分抽取覆盖缺口降级修复增量）

## 部署基本信息

- **部署时间**: 2026-11-17 07:43 CST 开始 → 07:43:42 DB 备份 → 07:43:52 激活 → 健康检查连续 3/3 通过（总尝试 79 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`，公网入口 `https://winbid-test.ehsy.com`）
- **发布版本 ID**: `566b933cc`（第 278 次，PR !3036 合并提交）
- **上一版本**: `96c3be67e`（第 277 次）
- **部署方式**: 标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + fail-closed DB 备份）
- **数据库迁移**: 无新增，`flyway validate` 285 个迁移校验和全部通过，DB 最新版本 `V1225` 未变

## 本轮发布内容

| 次序 | Commit / PR | 内容 |
|---|---|---|
| 1 | `8b1b5696a`（!3036） | 抽取覆盖缺口不再整任务失败：仅无可信结果的评分项降级为 `EXTRACT_FAILED` 待人工确认 |
| 2 | `8b1b5696a`（!3036） | 失败文案含原因分类（EMF 矢量图 / 图片引用无法定位 / 源文件解码失败 / OCR 不可用或超时） |
| 3 | `8b1b5696a`（!3036） | `ScoringRunReportCodec.taskQuality` 识别逐项 FAILED（单项失败 → `PARTIAL`，任务不失败） |
| 4 | `2472cbc22` / `0fff34e52`（!3035） | 第 277 次测试环境部署报告（docs） |
| 5 | `566b933cc` | PR !3036 合并提交（无额外代码） |

- 增量范围：`96c3be67e..566b933cc` 共 4 个 commit（含 1 个合并提交）
- 改动面：backend 3 个文件（2 main + 1 test）、docs 1 个文件；前端无改动（入口 hash 与上一版一致）

## 部署前预检

| 项 | 结果 |
|---|---|
| 基线 | `agent/trae-init` HEAD = `566b933cc` = `origin/main`，工作区干净，早操三连通过 |
| 服务器现状 | 上一版 `96c3be67e` 运行中，backend active，健康 `UP`（db/aiProvider/redis/sidecar/jwt 均 UP） |
| Flyway validate | ✅ `Successfully validated 285 migrations`，checksums 全部一致 |
| DB 已应用版本 | 最新 `1225 score estimate freshness`，源码最新同为 `V1225`，本次无 pending 新迁移 |
| 磁盘 | health 组件显示 `/opt/xiyu-bid/shared/backend` 剩余 21G |

## 打包与产物校验

| 校验项 | 结果 |
|---|---|
| jar 构建 | `BUILD SUCCESS`（39s，skipTests） |
| jar 内 Flyway 迁移 | 无重复版本 |
| `release-metadata.json` | `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false` |
| OBS 直传 | `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| sidecar | SHA-256 `b52dabc9…6052c`，schemaVersion 2.2（与上一版一致） |
| 前端入口 | `assets/index-HCuW1PLc.js` |
| 发布包 | `xiyu-bid-release-566b933cc.tar.gz`（156M） |

## 部署执行

1. `scp` 发布包 + `remote-deploy.sh` 到 `/opt/xiyu-bid/incoming/`
2. DB 备份（fail-closed）：`winbid-566b933cc-20261117074342.sql.gz`（16M）
3. Flyway validate 预检通过（任何版本切换之前）
4. 前端激活：自动保留上一版及再前一版 assets（`96c3be67e`、`040742974`），防止旧标签页 404
5. 停止 → 原子替换 jar → 写 `deployed-release.json` → 启动 `xiyu-bid-backend`
6. 健康检查连续 3/3 通过（总尝试 79 次，启动耗时 22.7s，Kafka 启动期延迟属已知行为）
7. 前端一致性校验通过：`index.html` 引用 `assets/index-HCuW1PLc.js` 与 release 一致

## 验证结果

| 检查 | 期望 | 实测 |
|---|---|---|
| `/actuator/health` | 200 UP | 200，`db`/`aiProvider`/`diskSpace`/`redis`/`sidecar`/`jwt` 均 UP |
| `/actuator/health/readiness` | UP | 200 UP |
| `POST /api/auth/login`（空体） | 400 参数校验 | 400 |
| `/api/projects`（未认证） | 401/403 | 401 |
| `/api/integration/crm/health`（未认证） | 401 | 401 |
| 前端 `/` 与 `/login` | 200 | 200 / 200 |
| 入口 JS | `index-HCuW1PLc.js` | 与 release 一致 |
| 后端日志 | 无 ERROR | 启动窗口 0 条 ERROR（仅 smoke 登录 400 的预期 WARN） |
| 启动后 DB 迁移 | 无新增 | 最新仍为 `V1225` |
| 旧 assets 保留 | 保留最近 2 个已激活版本 | `/srv/www/xiyu-bid/assets` 共 868 个文件 |

## E2E 复测（项目 1360 评分降级验收）

- **入口**：项目 1360 阶段 2 →「AI 评分标准解析」→「⚡ AI 实际打分」（MANUAL，全量 12 项）
- **新任务**：`747f6b93-e032-43da-8cc9-052dc9bd1da6`，07:48:29 创建 → 08:12:19 `COMPLETED`，耗时 1430s（约 23.8 分钟）
- **对照基线**：旧代码任务 `498b2ee5`（07:17:29 失败，`SCORING_EXTRACTION_FAILED`，同一原件同样 7 处失败单元）

| 验收项 | 期望 | 实测 |
|---|---|---|
| 任务终态 | `COMPLETED`（非 FAILED） | ✅ `COMPLETED`，`error_message=NULL`，`taskQuality=PARTIAL` |
| 抽取覆盖诊断 | 保留失败单元明细 | 20259/20259 单元已处理，`failedUnitCount=7`（3 处 EMF 矢量图 + 4 处图片引用无法定位）、识别为空 243、`coverageStatus=PARTIAL` |
| 3 个客观项降级 | `EXTRACT_FAILED` + 待人工确认 | ✅ 1105 投标人业绩 / 1106 投标人体系认证 / 1107 价格体系承诺：`reuse_kind=EXTRACT_FAILED`、`status_stage2=PENDING`、`actual_score=NULL` |
| 原因文案可读 | 明确 EMF / 图片引用原因 | ✅「投标原件有 7 处内容无法解析（3 处EMF 矢量图、4 处图片引用无法定位），相关评分项证据未完整读取，请重试本项；可核验证据不足，请人工确认（…）」 |
| 主观项 | 待专家评审 | 9 项均为 `SUBJECTIVE` 待专家评审（含修改建议） |
| 得分合规 | 不超过规则分值 | `max(actual_score - weight) = NULL`，客观项 0 条落分值，无超权得分 |
| UI 展示 | 详情显示待确认 + 原因 | 详情弹窗：满足状态 `● 待确认`、实际得分 `待确认`、缺失说明展示上述可读原因 |
| E2E 窗口后端日志 | 无 ERROR | 0 条 ERROR |

- **呈现口径说明**：汇总行沿用既有 `outcome=FAILED` 映射（单测既定：`EXTRACT_FAILED→FAILED`、`taskQuality=PARTIAL`），故显示「失败3项」；逐项详情与数据库状态均为「待确认」。若希望汇总行显示「待确认3项」，属后续体验优化项，不在本次增量内。

## 配置与清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留在 `/etc/xiyu-bid/backend.env`（测试环境历史决定项，非本次引入）。
- 无其他 `DEBUG` / `TRACE` 临时配置。

## 风险与回滚

- 无新增迁移，无破坏性 schema 变更，DB 回滚压力低。
- 回滚锚点：
  - 上一版 release：`/opt/xiyu-bid/releases/96c3be67e`（jar + 前端完整保留）
  - DB 备份：`/opt/xiyu-bid/db-backups/winbid-566b933cc-20261117074342.sql.gz`（16M）
  - 回滚步骤见 `docs/release/ROLLBACK.md`
- 前端旧 hash 资源已按机制保留最近 2 个已激活版本，跨版本 preload 404 风险已覆盖。

## 待跟进

- 该原件全量评分耗时约 24 分钟（20k+ 单元含 OCR），评分 worker 单并发；后续同类任务需预留队列时间，避免 30 分钟超时扫描。
- 汇总行「失败 N 项」与逐项「待确认」的口径差异可评估统一（需另行确认，不在本次范围）。
- 本报告需提 PR 合入 main（建议标题：`docs(release): 第 278 次测试环境部署报告`）。
