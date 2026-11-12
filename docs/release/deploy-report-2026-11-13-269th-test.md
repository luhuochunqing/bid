# 测试环境第 269 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-13 06:48 CST 开始 → 06:52:25 CST 激活（`activatedAt=2026-11-12T22:52:25Z`）→ 健康检查连续 3/3 通过
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `59ae5ef8f`
- **提交 Commit**: `59ae5ef8f`
- **上一版本**: `68224194a-api8080`（2026-11-12 18:35:41 CST 激活，对应第 268 次）
- **部署方式**: 手工标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `scripts/release/remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + `DB_BACKUP_COMMAND` fail-closed 备份）
- **增量规模**: 14 个 commit / 6 个 PR，19 个文件变更（+1333 / -116）

---

## 变更核心内容

| PR | 类型 | 内容 |
|---|---|---|
| !3018 | fix(scoreparse) | 500MB 标书先交正文再补少量大图 |
| !3017 | fix(scoreparse) | 投标文件评分接入现有 OCR |
| !3016 | fix(scoreparse) | 打分进行中不再锁死评分抽屉 |
| !3015 | docs | 知识沉淀 + wiki 回填（DB 备份缺陷第 3 次复发） |
| !3014 | docs(release) | 第 41 次生产环境部署报告（6d843c212） |
| !3013 | docs(release) | 第 268 次测试环境部署报告 |

- **非 PR 直提 commit**: `5f754c19b`（500MB 标书先交正文）、`519edabaf`（透明图合成白底 + 旧版 DOC 保留原提取）、`834071d0e`（评分接入 OCR）、`3e8de4f60`（打分中不锁抽屉）。
- **改动分布**: `document-converter-sidecar`（3 个源文件 + 1 个测试，Sidecar SHA 变化根因）、前端 `ScoreParseDrawer.vue` / `scoreParseDrawer.css`、`backend/src`、`src/views`、`.wiki/pages` 4 篇、`docs/lessons`。
- **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`），`flyway_schema_history` 无 failed 记录。
- **前端产物**: 入口 hash `assets/index-CcRi3DAX.js`（上一版 `index-BF6NLc4o.js`）。
- **Sidecar**: `sidecarSha256=75b2ffc2…`（上一版 `e9bcf69a…`），因本次含 Sidecar 源码改动而变更；health 组件 `sidecar` = UP。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | `agent/trae-init` 锚点分支，HEAD=`59ae5ef8f`=`origin/main`，工作区干净（0 ahead / 0 behind） |
| 早操三连 | `dev-env.sh`（trae 环境，前端 1323 / 后端 18089）、`sync-env.sh` ff-only 同步成功、`check-git-wrapper.sh` 通过 |
| 服务器连通性 | `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01`，系统时间 2026-11-13 06:48 CST |
| 服务器部署前状态 | `deployed-release.json` releaseId=`68224194a-api8080`，本地 health=UP，service active |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（2026-10-26 应用），与源码最新迁移一致，无 pending |
| 增量迁移文件 | `git diff 68224194a..59ae5ef8f -- backend/src/main/resources/db/migration-mysql/` 为空 |
| 增量规模核对 | `git rev-list --count 68224194a..HEAD` = 14，`git diff --stat` 19 文件 |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`
  - `mvn clean -DskipTests package` BUILD SUCCESS（42.6s），jar 内 Flyway 迁移文件 284 个、无重复版本
  - Sidecar 打包完成，SHA-256 `75b2ffc24a255faf797c2daca95c59c3888bb464e66b803bafe45ecb73bd545b`，`schemaVersion=2.2`
  - OBS 直传校验：Detail chunk `.upload(` 调用数 = 2
  - 归档 `xiyu-bid-release-59ae5ef8f.tar.gz`（163,339,003 bytes），本地 MD5 `dcbd7afea369a5acf027ee647177d17c`
- **产物校验**：`release-metadata.json` → `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、`sidecarSchemaVersion=2.2`；前端入口 `assets/index-CcRi3DAX.js`
- **远程部署**（`remote-deploy.sh`）：
  1. 部署前数据库备份 → `/opt/xiyu-bid/db-backups/winbid-59ae5ef8f-20261113065209.sql.gz`（16 MB，`gzip -t` OK、`-- Dump completed` 尾部校验 OK、`CREATE TABLE` 221 条，非假备份）
  2. Flyway validate 通过（285 个迁移）→ 原子切换 jar → 停止并重启 `xiyu-bid-backend`
  3. 前端目录切换，入口一致性校验通过（`/assets/index-CcRi3DAX.js`）
  4. 健康检查连续 3/3 通过（共 80 次尝试，无 Kafka readiness 长延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置 `ASSETS_RETAIN_RELEASES=2`，合并 `68224194a-api8080`、`0e71c2f0f-api8080` 的 assets，`/srv/www/xiyu-bid/assets` 共 616 个文件

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`59ae5ef8f`，activatedAt=`2026-11-12T22:52:25Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200、HTTP 200 |
| 前端入口一致性 | `assets/index-CcRi3DAX.js` 与 release 一致 |
| 旧 hash 资产保留 | assets 目录 616 个文件 |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| 后端服务状态 | `systemctl is-active xiyu-bid-backend` = active；近 10 分钟 ERROR 日志 0 条 |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 升级内容实机验证（ego-browser E2E + Sidecar 直测）

部署后对本次 scoreparse 修复做了实机验证，环境 `https://winbid-test.ehsy.com`（管理员 admin）。

### 1. 扫描件 OCR（!3017 核心 bug：「转换 200 但 0 字符」）

| 验证 | 证据 |
|---|---|
| 构造样本 | 3 页图片型 PDF（无文本层，`get_text()` = 0 字符，229 KB） |
| 对照（不带 OCR 头） | `/convert-stream` 返回 **0 字节** —— 复现旧 bug |
| 修复路径（`X-Pdf-Ocr: true`） | 返回 **437 字节**，1.2s；正文含「投标文件 / 西域智慧供应链股份有限公司 / 投标报价 1230000 元 / 统一社会信用代码 91310000… / 仓储面积 12000 平方米」，关键数字全部正确 |
| UI 端到端 | 将该扫描件上传为项目 1392 投标文件 → `bid_file` 328 COMPLETED → 触发「⚡ 重新打分」→ 任务 367 **COMPLETED（132s，无 error）**，`file_name=scanned-bid-e2e.pdf`、`bid_content_hash=82ffe184…`（非空），19 项全部产出判定 |

按 FR002「整卷没有任何文字才失败」：任务成功完成本身即证明 OCR 正文非空。

### 2. 打分中抽屉不再锁死（!3016）

| 验收点 | 实测 |
|---|---|
| 遮罩形态 | `.scoring-overlay` = `position: sticky`，实测矩形 **904×73 px**（抽屉顶部横幅），不再是全屏白遮罩 |
| 滚动锁定 | 抽屉内容 `overflow-y:auto`，`body overflow=visible`（未锁页面） |
| 关闭抽屉 | 「关闭抽屉」按钮可见可点，点击后抽屉关闭，页面可交互 |
| 后台任务 | 关闭抽屉后 `score_parse_task` 367 保持 PROCESSING 并最终 COMPLETED（未取消） |

### 3. 透明图合成白底（519edabaf）

构造 68 KB DOCX（透明底 PNG + 黑色中文文字）：`X-Pdf-Ocr: true` 识别出「营业执照（副本）/ 名称：西域智慧供应链股份有限公司 / 注册资本：人民币伍仟万元整」；不带头时仅返回「【已附图片】」。旧实现（透明合成黑底 → 全黑）无法得到该结果。

### 4. 0 分与主观项展示（US2）

投标文件评分表中：客观项 DANGER → 显示「✗ 不满足，实际得分 0」（0 分显示为 0）；主观项 → 「● 待确认，待确认」（不编造分值）。

### 未在本轮 E2E 覆盖

- **500MB / 上千图分片识别（!3018）**：受限于测试样本体积与 30 分钟评分窗口，本轮未实机构造 500MB 标书；该路径由 Sidecar 单测（`test_scanned_bid_ocr.py`，+296 行）覆盖，建议后续用项目 580 原文件做一次专项耗时验收。
- **旧版 .doc 透明图 / DocInsight 回退**：spec 明确本次不宣称 Linux 扫描 .doc OCR 支持。

### 测试数据清理（验证后已全部回收）

| 对象 | 处理 |
|---|---|
| 项目 1392 投标文件引用 | UI 删除全部 3 处引用，上传区恢复为「将投标文件拖到此处，或点击上传」 |
| `bid_file` 325-327（失败上传残留） | 已删除 |
| `bid_file` 328（`scanned-bid-e2e.pdf`） | 已删除 |
| `score_parse_task` 367 / `score_result` 19 行 | 已删除（facts / reviews / evidence / drafts / analyses 本来即为 0） |
| OBS 对象 `bids/2026/11/e67c43fd-…/scanned-bid-e2e.pdf` | 已删除（虚拟主机风格 SigV4 DELETE 返回 204，复查 HEAD 404） |
| 服务器 `/tmp` 样本、本地临时样本与 venv | 已清理 |

抽屉中已无 `scanned-bid-e2e.pdf` 痕迹，项目 1392 恢复为验证前状态。

---

## 回滚信息

- **回滚锚点**: release `68224194a-api8080`（`/opt/xiyu-bid/releases/68224194a-api8080`，frontend + backend jar 均在）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-59ae5ef8f-20261113065209.sql.gz`（16 MB，完整性校验通过）
- **回滚方式**: 重新激活 `68224194a-api8080` release（jar + 前端目录），本次无 schema 变更，无需 DB 恢复
- **回滚触发条件**: 登录/核心接口不可用、health 持续非 UP、前端白屏
- **当前回滚态势**: 就绪，未启用

---

## 经验沉淀应用

| 经验 | 本次应用情况 |
|---|---|
| 环境门禁（第 0 步） | ✅ AskUserQuestion 确认测试环境 `172.16.38.78` 后才执行 |
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置 validate |
| DB 预备份 fail-closed（第 41 次生产事故复发教训） | ✅ 使用 `backup-db-pre-deploy.sh`，16 MB + 尾部校验，未出现 371 字节假备份 |
| `SYSTEMCTL_SUDO=true` | ✅ 显式传入，服务正常重启 |
| `VITE_OBS_ENABLED=true` | ✅ 显式传入，产物 `obsEnabled=true`、`.upload(` = 2 |
| 同源构建 `VITE_API_BASE_URL=` | ✅ metadata `apiBaseUrl=""` |
| `COPYFILE_DISABLE=1` | ✅ 服务器无 `._*` 残留 |
| 前端旧 assets 保留 | ✅ 内置保留策略生效，616 个文件 |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'`，无 502 |
| Kafka readiness 延迟 | ✅ 本次未出现，80 次尝试内通过 |

---

## 风险提示

1. **Sidecar 同步变更**：本次 Sidecar SHA 变更（`e9bcf69a` → `75b2ffc2`），涉及 OCR 接入与透明图合成白底。建议验证扫描件评分链路（图片 PDF / 旧版 DOC / 500MB 大标书）实际表现。
2. **500MB 标书处理链路**：`!3018` 改为"先交正文再补少量大图"，大文件解析耗时分段返回，需关注前端评分抽屉展示是否完整。
3. **无新增迁移**：DB 结构未变，回滚无需数据恢复，风险低。

---

## 部署确认清单

- [x] 环境门禁已确认（测试 `172.16.38.78`）
- [x] 基线 = origin/main，工作区干净
- [x] Flyway validate 通过，无 failed 记录
- [x] 打包 BUILD SUCCESS，产物校验通过（OBS / 迁移无重复 / 入口）
- [x] 部署前 DB 备份有效（16 MB + 尾部校验）
- [x] 健康检查 3/3 通过
- [x] Smoke 7 项全部符合预期
- [x] 前端入口与 release 一致
- [x] 旧 assets 保留，无 `._*` 残留
- [x] 无新增临时调试配置
- [x] 回滚锚点与备份就绪
