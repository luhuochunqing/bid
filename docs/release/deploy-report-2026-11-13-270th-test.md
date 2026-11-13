# 测试环境第 270 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-13 14:47 CST 开始 → 14:48:02 CST 激活（`activatedAt=2026-11-13T06:48:02Z`）→ 健康检查连续 3/3 通过（总尝试 81 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`）
- **发布版本 ID**: `b77a644d4`
- **提交 Commit**: `b77a644d4`
- **上一版本**: `59ae5ef8f`（2026-11-13 06:52:25 CST 激活，第 269 次）
- **部署方式**: 手工标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `scripts/release/remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + `DB_BACKUP_COMMAND` fail-closed 备份）
- **增量规模**: 8 个 commit / 3 个 PR，108 个文件变更（+7065 / -888）

---

## 变更核心内容

| PR | 类型 | 内容 |
|---|---|---|
| !3021 | fix(scoreparse) | 恢复实际评分并收敛证据补图链路 |
| !3020 | docs(wiki) | 测试环境 OBS 直传与投标文件删除残留 |
| !3019 | docs(release) | 第 269 次测试环境部署报告（59ae5ef8f） |

- **非 PR 直提 commit**: `1016acb63`（评分简化和源偏移保留）、`31be1b692`（恢复实际投标评分）、`ff53c0c63`（wiki 回填）、`7887729ee`（第 269 次报告）。
- **改动分布**: `backend/src/main/java/com/xiyu/bid/scoreparse/*`（application / domain / dto / infrastructure 多处）、`biddraftagent/infrastructure/openai`、`document-converter-sidecar`（app.py / converter.py / scoring_extraction.py / tests）、前端 `ScoreParseTable.vue` / `ScoreParseV3.qa.spec.js`、`.wiki/pages` 3 篇、`docs/release`。
- **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`），`flyway_schema_history` 无 failed 记录。
- **前端产物**: 入口 hash `assets/index-DShiZcZw.js`（上一版 `index-CcRi3DAX.js`）。
- **Sidecar**: `sidecarSha256=f30b7d0d…`（上一版 `75b2ffc2…`），本次含 Sidecar 源码改动；服务器容器升级完成，health 组件 `sidecar` = UP。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | `agent/trae-init` 锚点分支，HEAD=`b77a644d4`=`origin/main`，工作区干净（0 ahead / 0 behind） |
| 早操 | `dev-env.sh` 在锚点分支被 session gate 拦截（非部署阻塞项），改为 `check-git-wrapper.sh` + `git fetch` 基线校验；包装器**未激活**（`/usr/bin/git` 裸 git），仅影响本地提交钩子，不影响部署 |
| 服务器连通性 | `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01`，系统时间 2026-11-13 14:43 CST |
| 服务器部署前状态 | `deployed-release.json` releaseId=`59ae5ef8f`，本地 health=UP，service active |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（2026-10-26 应用），与源码一致，无 pending |
| 增量迁移文件 | `git diff 59ae5ef8f..b77a644d4 -- backend/src/main/resources/db/migration-mysql/` 为空 |
| 增量规模核对 | `git rev-list --count 59ae5ef8f..HEAD` = 8，`git diff --stat` 108 文件 |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`
  - `mvn clean -DskipTests package` BUILD SUCCESS（37.2s），jar 内 Flyway 迁移文件 284 个、无重复版本
  - Sidecar 打包完成，SHA-256 `f30b7d0d4849af2a1fdff2b05d9f5211a202cfa8a6d2597f68335019ba7696b1`，`schemaVersion=2.2`
  - OBS 直传校验：Detail chunk `.upload(` 调用数 = 2
  - 归档 `xiyu-bid-release-b77a644d4.tar.gz`（163,417,983 bytes），本地 MD5 `33f8558ab137e3de4916ae35bf405ee4`
- **产物校验**：`release-metadata.json` → `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、`sidecarSchemaVersion=2.2`；前端入口 `assets/index-DShiZcZw.js`
- **远程部署**（`remote-deploy.sh`）：
  1. 部署前数据库备份 → `/opt/xiyu-bid/db-backups/winbid-b77a644d4-20261113144747.sql.gz`（16 MB，`gzip -t` OK、`-- Dump completed` 尾部校验 OK、`CREATE TABLE` 221 条，非假备份）
  2. Sidecar 容器升级完成，`http://127.0.0.1:8000/health` 通过；`AI_PROVIDER=custom` 守卫通过
  3. Flyway validate 通过（285 个迁移）→ 原子切换 jar → 停止并重启 `xiyu-bid-backend`
  4. 前端目录切换，入口一致性校验通过（`/assets/index-DShiZcZw.js`）
  5. 健康检查连续 3/3 通过（总尝试 81 次，约 2.5 分钟，无 Kafka readiness 长延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置 `ASSETS_RETAIN_RELEASES=2`，合并 `59ae5ef8f`、`68224194a-api8080` 的 assets，`/srv/www/xiyu-bid/assets` 共 700 个文件

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`b77a644d4`，activatedAt=`2026-11-13T06:48:02Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200、HTTP 200 |
| 前端入口一致性 | `assets/index-DShiZcZw.js` 与 release 一致 |
| 旧 hash 资产保留 | assets 目录 700 个文件 |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，failed 记录 0 条 |
| 后端服务状态 | `systemctl is-active xiyu-bid-backend` = active；重启后 `-p err` 日志 0 条 |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 升级内容实机验证（ego-browser E2E）

部署后对本次「实际评分恢复 / 证据补图收敛」做了真实浏览器 + 真实后端 + 真实模型验证，环境 `https://winbid-test.ehsy.com`（系统管理员）。验证项目 1364（6 项：客观 4 项 28 分、主观 2 项 72 分），通过 UI「AI 评分标准解析 → ⚡ AI 实际打分」触发 3 轮打分。

**升级前现场（bug 状态）**：最近一次任务 `7a4fa461` 为 `SKIPPED`（2026-08-27），6 项全部 `LEGACY_UNVERIFIED`，客观项显示 6 分但合计 0 分，质量未知。

### 实测结果

| 轮次 | 原件 | 任务 | 耗时 | 执行质量 | 客观项出分 |
|---|---|---|---|---|---|
| 1 | 项目原 BID PDF 1MB | `dc5d543c` FULL / COMPLETED | 70s | 21/21 单元，COMPLETE，失败 0 | 0 项（4 项待确认） |
| 2 | 合成 DOCX（明写 OMS/WMS/TMS + 5 项体系证书） | `605dd0d0` FULL / COMPLETED | 46s | 21/21 单元，COMPLETE | 0 项（4 项待确认） |
| 3 | 同内容 PDF（带页码） | `3d774081` FULL / COMPLETED | 81s | 1/1 单元，COMPLETE | 0 项（4 项待确认） |

### 已验证修复

| 验证点 | 证据 |
|---|---|
| 手动重评不再被跳过 | 旧任务 `stage=SKIPPED` → 新任务 `stage=FULL`，三轮均重下原件真实打分 |
| 历史未验证分被清除 | `LEGACY_UNVERIFIED` 的 6 分不再展示，`reuseKind=FRESH`，绑定新任务/规则指纹 |
| 质量与执行分离、可诊断 | UI「客观已评分 0 项、待确认 4 项、主观待评 2 项、失败 0 项；原件处理完整」；`run_summary` 含 coverage / engineVersion / 每项 reasonCode，`diagnosticsKnown=true` |
| 失败任务可见 | 项目 226：「投标文件解析失败，无法完成打分」+「规则已更新，实际评分尚未执行」 |
| 抽屉不锁 | 打分中可继续浏览/关闭抽屉，任务后台继续 |
| 单项详情透明 | 详情显示 评分依据 /「标书引用：无」/ 缺失说明 / 修改建议 |

### 未通过项（已提缺陷单 CO-661）

第 2、3 轮原件明写「已建设并上线 OMS/WMS/TMS 并附软著登记号」「五项体系认证证书」，模型 evidence 也写「完全符合评分标准要求」，但 `actualScore` 为空、`quote=NULL`，客观项全部落为「可核验证据不足，请人工确认」。

根因定位：[ScoreAssessmentGuard.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreAssessmentGuard.java#L28-L31) 的 `canPublish()` 硬性要求 `quoteVerified=true`（引用 ≥8 字且逐字命中原文与 12K 节选），否则 [ScoreItemAssessor.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java#L101-L117) 清空分数。当前 gemini-3.5-flash 三轮均未产出可核验 quote（或 quote 未命中节选），因此「实际评分恢复」**未取得正向证据**。

### 结论

本次升级修掉了「重评跳过、伪造历史分、状态不透明、失败不可见」四类问题，但客观项真实出分未被证明。已开缺陷单 **CO-661**（Linear）跟踪，待确认是模型未返回 quote，还是节选未覆盖被引用段落。

### 测试痕迹（待清理）

- 项目 1364 新增合成 BID 文档：`bid-evidence-e2e-20261113.docx`（id 1254）、`bid-evidence-e2e-20261113.pdf`（id 1255），当前评分结果绑定 1255。
- 本地证据：`/tmp/xiyu-e2e-scoring/`（合成 docx/pdf、`final-results.png`）。

---

## 回滚信息

- **回滚锚点**: release `59ae5ef8f`（`/opt/xiyu-bid/releases/59ae5ef8f`，frontend + backend jar 均在）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-b77a644d4-20261113144747.sql.gz`（16 MB，gzip 完整性 + 尾部标记 + 221 张表校验通过）
- **回滚方式**: 重新激活 `59ae5ef8f` release（jar + 前端目录），本次无 schema 变更，无需 DB 恢复
- **回滚触发条件**: 登录/核心接口不可用、health 持续非 UP、前端白屏
- **当前回滚态势**: 就绪，未启用

---

## 经验沉淀应用

| 经验 | 本次应用情况 |
|---|---|
| 环境门禁（第 0 步） | ✅ AskUserQuestion 确认测试环境 `172.16.38.78` 后才执行 |
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置 validate |
| DB 预备份 fail-closed（第 41 次生产事故教训） | ✅ 使用 `backup-db-pre-deploy.sh`，16 MB + 尾部校验 + 221 表，无假备份 |
| `SYSTEMCTL_SUDO=true` | ✅ 显式传入，服务正常重启 |
| `VITE_OBS_ENABLED=true` | ✅ 显式传入，产物 `obsEnabled=true`、`.upload(` = 2 |
| 同源构建 `VITE_API_BASE_URL=` | ✅ metadata `apiBaseUrl=""` |
| `COPYFILE_DISABLE=1` | ✅ 服务器无 `._*` 残留 |
| 前端旧 assets 保留 | ✅ 内置保留策略生效，700 个文件 |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'`，无 502 |
| Kafka readiness 延迟 | ✅ 本次未出现 |
| 早操三连 | ⚠️ `dev-env.sh` 被锚点分支 session gate 拦截，改用等价基线校验（见预检） |

---

## 风险提示

1. **Sidecar 同步变更**：本次 Sidecar SHA 变更（`75b2ffc2` → `f30b7d0d`），涉及评分抽取链路；容器已升级且 `/health` 通过。实机验证见上，抽取执行完整但客观项未出分。
2. **评分恢复逻辑（!3021）**：E2E 已确认重评跳过、历史伪造分、状态不透明、失败不可见四类问题修复；**客观项正向出分未通过**（全部降级待确认），已提 CO-661，详见上方实机验证段。
3. **无新增迁移**：DB 结构未变，回滚无需数据恢复，风险低。

---

## 部署确认清单

- [x] 环境门禁已确认（测试 `172.16.38.78`）
- [x] 基线 = origin/main，工作区干净
- [x] Flyway validate 通过，无 failed 记录
- [x] 打包 BUILD SUCCESS，产物校验通过（OBS / 迁移无重复 / 入口）
- [x] 部署前 DB 备份有效（16 MB + 尾部校验 + 221 表）
- [x] Sidecar 容器升级且健康
- [x] 健康检查 3/3 通过
- [x] Smoke 7 项全部符合预期
- [x] 前端入口与 release 一致
- [x] 旧 assets 保留，无 `._*` 残留
- [x] 无新增临时调试配置
- [x] 回滚锚点与备份就绪
- [x] E2E 实机验证已执行，结论见「升级内容实机验证」段
- [ ] 客观项正向出分验收未通过（CO-661 跟进）
