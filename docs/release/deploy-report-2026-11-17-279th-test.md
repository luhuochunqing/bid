# 测试环境第 279 次部署报告（评分来源闭合修复 + 部署脚本 ._* 残留治理）

## 部署基本信息

- **部署时间**: 2026-11-17 14:52 CST 开始 → 14:52:17 DB 备份 → 14:52:28 激活 → 健康检查连续 3/3 通过（总尝试 79 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`，公网入口 `https://winbid-test.ehsy.com`）
- **发布版本 ID**: `92d522f8b`（第 279 次，PR !3040 合并提交）
- **上一版本**: `566b933cc`（第 278 次）
- **部署方式**: 标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + fail-closed DB 备份）
- **数据库迁移**: 无新增，`flyway validate` 285 个迁移校验和全部通过，DB 最新版本 `V1225` 未变

## 本轮发布内容

| 次序 | Commit / PR | 内容 |
|---|---|---|
| 1 | `99981ac0b` / `5cbbf4925`（!3037） | 第 278 次测试环境部署报告（docs） |
| 2 | `198e22384` / `4a61249e2`（!3038） | 第 43 次生产部署报告 + E2E 验收结论（docs） |
| 3 | `e8df9badd`（!3038） | `remote-deploy.sh` 历史 assets 保留时过滤 macOS `._*`，阻断 AppleDouble 逐版本复刻 |
| 4 | `4976df2d3` / `eb4607644`（!3039） | 评分解析历史失败不再重复弹错；技术错误码直出 |
| 5 | `9e61685c4` / `81b699965` / `0294b93b9` / `92d522f8b`（!3040） | 评分解析跨页来源闭合、重复计分修复；保留方案来源映射、修复平级项误挂；保留显式最高分列兼容 |

- 增量范围：`566b933cc..92d522f8b` 共 12 个 commit（含 3 个合并提交）
- 改动面：43 个文件（backend 17、src 10、specs 7、docs 3、scripts 1、e2e 1、其他 4），+1505 / -100
- 前端有改动：入口 hash 由 `index-HCuW1PLc.js` 变为 `index-DJL8lZxB.js`

## 部署前预检

| 项 | 结果 |
|---|---|
| 基线 | `agent/trae-init` HEAD = `92d522f8b` = `origin/main`，工作区干净，早操三连通过（7/7 门禁） |
| 服务器现状 | 上一版 `566b933cc` 运行中，backend active，健康 `UP`（db/aiProvider/redis/sidecar/jwt 均 UP） |
| Flyway validate | ✅ `Successfully validated 285 migrations`，checksums 全部一致 |
| DB 已应用版本 | 最新 `1225 score estimate freshness`，源码最新同为 `V1225`，本次无 pending 新迁移 |

## 打包与产物校验

| 校验项 | 结果 |
|---|---|
| jar 构建 | `BUILD SUCCESS`（38.7s，skipTests） |
| jar 内 Flyway 迁移 | 284 个，无重复版本 |
| `release-metadata.json` | `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false` |
| OBS 直传 | `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| sidecar | SHA-256 `b52dabc9…6052c`，schemaVersion 2.2（与上一版一致） |
| 前端入口 | `assets/index-DJL8lZxB.js` |
| 发布包 | `xiyu-bid-release-92d522f8b.tar.gz`（156M） |

## 部署执行

1. `scp` 发布包 + `remote-deploy.sh` 到 `/opt/xiyu-bid/incoming/`
2. DB 备份（fail-closed）：`winbid-92d522f8b-20261117145217.sql.gz`（16M）
3. Flyway validate 预检通过（任何版本切换之前）
4. 前端激活：自动保留上一版及再前一版 assets（`566b933cc`、`96c3be67e`），防止旧标签页 404
5. 停止 → 原子替换 jar → 写 `deployed-release.json` → 启动 `xiyu-bid-backend`
6. 健康检查连续 3/3 通过（总尝试 79 次，Kafka 启动期 readiness 延迟属已知行为）
7. 前端一致性校验通过：`index.html` 引用 `assets/index-DJL8lZxB.js` 与 release 一致

## 验证结果

| 检查 | 期望 | 实测 |
|---|---|---|
| `/actuator/health` | 200 UP | 200，`db`/`aiProvider`/`diskSpace`/`redis`/`sidecar`/`jwt` 均 UP |
| `/actuator/health/readiness` | UP | 200 UP |
| `POST /api/auth/login`（空体） | 400 参数校验 | 400 |
| `/api/projects`（未认证） | 401/403 | 401 |
| `/api/integration/crm/health`（未认证） | 401 | 401 |
| 前端 `/` 与 `/login` | 200 | 200 / 200 |
| 入口 JS | `index-DJL8lZxB.js` | 与 release 一致 |
| 静态资源缓存 | gzip + 强缓存 | 200，`Content-Encoding: gzip`、`Cache-Control: public, immutable` |
| 后端日志 | 无 ERROR | 启动后 ERROR 计数 0 |
| 启动后 DB 迁移 | 无新增 | 最新仍为 `V1225` |
| 旧 assets 保留 | 保留最近 2 个已激活版本 | `/srv/www/xiyu-bid/assets` 共 953 个文件 |
| macOS `._*` 残留 | 0 | `find` 无输出 |
| 磁盘 | 充足 | `/` 剩余 20G（80% 已用） |

## 配置与清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留在 `/etc/xiyu-bid/backend.env`（测试环境历史决定项，非本次引入）。
- 无其他 `DEBUG` / `TRACE` 临时配置。

## 风险与回滚

- 无新增迁移，无破坏性 schema 变更，DB 回滚压力低。
- 回滚锚点：
  - 上一版 release：`/opt/xiyu-bid/releases/566b933cc`（jar + 前端完整保留）
  - DB 备份：`/opt/xiyu-bid/db-backups/winbid-92d522f8b-20261117145217.sql.gz`（16M）
  - 回滚步骤见 `docs/release/ROLLBACK.md`
- 前端旧 hash 资源已按机制保留最近 2 个已激活版本，跨版本 preload 404 风险已覆盖。

## E2E 验收（真实浏览器，ego-browser）

| 修复项 | 结论 | 证据 |
|---|---|---|
| 历史失败重开抽屉重复弹错（!3039） | PASS | 项目 1355 连续 3 轮「关闭→重开」，`el-message` 计数 0 |
| 技术码直出（!3039） | PASS | 抽屉全文无 `*_*` 技术码与 UUID；提示为中文「上次打分未完成（2026/8/28 16:11:55）：打分超时…」，`role=status` |
| 旧失败提示清理（!3039） | PASS | 项目 1360/1364（最新任务 COMPLETED）`scoring-hint` 为空 |
| 跨页来源闭合 / 100 分闭合（!3040） | BLOCKED | 生产 1124 原件在测试解析：25–36 页 `APIHub OCR search failed (code=1/50000)`，候选仅 56 分；生产同文件同页 OCR 每页识别 1 张表 |
| 重复计分 / 空规则 / 物流配送缺失（!3040） | PASS（局部） | 重试解析 23 条候选：重复分组 0、空规则 0、物流配送 6 分存在 |
| 部署脚本 `._*` 残留（e8df9badd） | PASS | 部署后 `/srv/www/xiyu-bid` 无 `._*`；旧 assets 保留 953 个文件 |
| 解析任务稳定性 | FAIL（新发现） | 首次解析任务 384 `FAILED`：`Out of range value for column 'confidence'`；重试任务 385 `COMPLETED` |

测试数据变更：项目 1394 原招标文件已替换为生产 1124 原件（`xy-1124-tender.pdf`，类别 TENDER）；解析任务 384 FAILED、385 COMPLETED（23 条候选 56 分，未采纳）。未新建标讯/项目。

## 待跟进

- **P1**：`score_parse_fact.confidence` 为 `DECIMAL(5,4)`，模型返回置信度 ≥10 时整轮解析回滚。写入与抽取代码本次未改动（`git diff 566b933cc..HEAD` 为空），属历史遗留健壮性缺陷，需增加置信度归一化/夹取。
- **P2**：测试环境 `apihub-test.ehsy.com` OCR 登录鉴权成功，但 search 全部 `code=1/50000`，需核查中转账号额度与授权；修复后重跑 1124 原件，验证 37 项/100 分闭合。
- **P3（观察）**：失败项详情「缺失说明」带 `COVERAGE_PROOF_INSUFFICIENT` 原码，按定义为「原码留诊断详情」，非本次回归。
- 本报告需提 PR 合入 main（建议标题：`docs(release): 第 279 次测试环境部署报告`）。
