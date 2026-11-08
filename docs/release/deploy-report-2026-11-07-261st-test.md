# 测试环境第 261 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-07 22:45:02 CST（激活）→ 22:47 左右健康检查连续 3/3 通过（脚本 81 次尝试，约 2.7 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `b08b1760c2`
- **提交 Commit**: `b08b1760c2`（!2977）
- **上一版本**: `0d833ae84-api8080`（2026-11-07 10:12 CST 激活，对应第 260 次）
- **部署方式**: 按 `xiyu-server-deploy` SOP 执行 `scripts/release/package-release.sh` + `scripts/release/remote-deploy.sh`（`VITE_API_BASE_URL=""` 同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 14 个 commit（含 PR !2977 及 260 次部署报告、39 次生产部署报告入库），32 个文件变更（+1567 / -93）

---

## 变更核心内容

1. **标书核查根因修复（PR !2977）**
   - 修复项目 1360 标书核查资格提取与评分留白短路等 6 类待确认根因；新增 `Quality1360UnblockTest`（234 行）作为回归护栏。
2. **三技能审查修复（同 PR 内提交）**
   - 单图 OCR 回退、同页证书实证防串页、`SemanticEvidenceBoundary` / `ScanPageTextPolicy` 边界收紧，同步更新 `.wiki/pages/bid-quality-check-realization.md` 与模块 README。
3. **质量核查解封 + OCR 能力扩展**
   - 解封质量核查后置硬编码待确认；接通百度 OCR 与大体积 DOCX 流式抽图，新增 `DocxComplianceImageExtractor`（297 行）及其单测（127 行）。
4. **文档（PR !2976 等）**
   - 第 260 次测试环境部署报告、第 39 次生产环境部署报告、`lessons-learned.md` 与 `decisions.tsv` 沉淀。
5. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
6. **前端产物**: 入口 hash 为 `assets/index-ZLT6PNtE.js`。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ⚠️ 在锚点分支 `agent/trae-init` 被守卫拦截（提示禁止直接开发），改为手动 `git fetch origin` + 基线对比 |
| 基线确认 | `agent/trae-init`，HEAD=`b08b1760c2`=`origin/main`，工作区干净，落后 0 个提交 |
| `check-git-wrapper.sh` | ⚠️ 独立 shell 中显示 `Wrapper NOT active`（`dev-env.sh` 的 PATH 不跨命令持久）；本次无 push 操作，不涉及 `--no-verify` 绕过 |
| 服务器连通性（环境门禁后） | ✅ `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01` |
| 服务器部署前状态 | `deployed-release.json` releaseId=`0d833ae84-api8080`，`xiyu-bid-backend` active，health UP |
| 磁盘余量 | `/` 27G 可用（72% 已用） |
| Flyway validate（服务器，jetty 身份） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新 `V1225__score_estimate_freshness.sql` 一致，无 pending |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`（`mvn clean -DskipTests package` BUILD SUCCESS 37.767s）
- **产物校验**：`release-metadata.json` 中 `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；jar 内 284 个 Flyway 迁移文件无重复版本；Detail chunk `.upload(` 调用数 = 2；归档 156 MB
- **上传**：`scp` 归档 + `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
  - 首次把归档与脚本合并为一次 `scp` 时失败（exit 1），拆分为单文件上传后两次均成功，文件到位并校验大小 156M
- **远程部署**：
  1. 数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-b08b1760c2-20261107224452.sql.gz`（16 MB）
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend` → 健康检查连续 3/3 通过（81 次尝试）
  3. 前端目录切换并通过入口一致性校验（`/assets/index-ZLT6PNtE.js`）
  4. 前端目录激活前脚本自动修复了少量非 jetty 属主条目（遗留 root 拷贝）
- **旧版本 assets 保留**：从 `0d833ae84-api8080` release 目录 `cp -rn` 旧 hash 资源；随后发现两版前的 `Detail-L_BB6tCw.js`（属 `82b9dbe75`）仍 404，按上次报告的风险提示，再从 `82b9dbe75-api8080`、`3291a9015-api8080` 补拷一次，`assets` 目录现 278 个文件，两个旧 chunk 复验 HTTP 200
- **Sidecar**：`http://127.0.0.1:8000/health` 返回 200（容器未重建，SHA-256 与上一版一致）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`b08b1760c2`，activatedAt=`2026-11-07T14:45:02Z` |
| `/actuator/health`（服务器本地） | UP（db UP、aiProvider UP、diskSpace UP） |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200（无 Kafka readiness 延迟） |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200 |
| 前端入口一致性 | `assets/index-ZLT6PNtE.js` 与 release 一致 |
| 前端缓存策略 | `assets/index-ZLT6PNtE.js`：`Content-Encoding: gzip`、`Cache-Control: public, immutable` |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| 后端启动日志 | 新进程 PID 3229，`Started XiyuBidApplication in 23.386 seconds`，启动后 `level=ERROR` 计数 = 0 |
| 旧进程停止噪声 | 22:45:00 两条 `ZkOffsetManager: This consumer has already been closed` 来自旧进程（PID 23424）关闭阶段，属历史已知噪声 |
| Sidecar `/health`（8000） | HTTP 200 |
| OBS 直传 | `obsEnabled=true`，Detail chunk `.upload(` = 2 |
| 前端资产保留 | 278 个 assets 文件；`Detail-L_BB6tCw.js`、`List-DXE_9Zj6.js` 均 HTTP 200 |
| 临时调试配置 | 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留），无 DEBUG/TRACE 残留 |

---

## 回滚信息

- 回滚锚点：release id `0d833ae84-api8080`（上一已知良好版本）
- 后端旧 jar：`/opt/xiyu-bid/releases/0d833ae84-api8080/backend/app.jar`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-b08b1760c2-20261107224452.sql.gz`（16 MB）
- 回滚命令：
  ```bash
  ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
    sudo cp /opt/xiyu-bid/releases/0d833ae84-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo cp -r /opt/xiyu-bid/releases/0d833ae84-api8080/frontend/* /srv/www/xiyu-bid/ && \
    sudo systemctl start xiyu-bid-backend'
  ```
- 回滚状态：**就绪（未执行）**

---

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置预检，全部通过 |
| 同源构建（`baseURL=""`） | ✅ `apiBaseUrl=""`，入口一致性校验通过 |
| OBS 直传双保险 | ✅ 显式 `VITE_OBS_ENABLED=true`，且校验 `.upload(` = 2 |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资源跨版本 404 | ⚠️ 仍只保留"上一版本"；本次手动补齐 `82b9dbe75` / `3291a9015`，建议在 `remote-deploy.sh` 固定保留最近 2 个版本 |
| `SYSTEMCTL_SUDO=true` | ✅ 显式传参，重启成功 |
| 锚点分支 `sync-env.sh` 守卫 | ⚠️ 新发现：`scripts/sync-env.sh` 在 `agent/trae-init` 上直接拒绝执行，与 skill 文档"锚点分支可 ff-only 同步"不符；本次以手动 `git fetch` + 基线对比替代，建议修正脚本判断或文档口径 |

---

## 风险提示

1. **锚点分支同步方式**：`sync-env.sh` 在锚点分支被守卫拦截，后续部署需手动 `git fetch origin` 并确认 `HEAD == origin/main`，否则可能基于旧基线打包。
2. **跨版本 assets 保留范围**：脚本只保留"上一版本"资源；两版以上的旧标签页仍可能 404，本次已手动补齐，建议后续在 `remote-deploy.sh` 中固定保留最近 2 个版本。
3. **本次无数据库结构变更**：DB 保持 V1225，回滚仅需切回旧 jar + 旧前端，无需 DB 回滚。
4. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`** 仍保留（历史决定），health 输出含组件细节，测试环境可接受，生产不得沿用。

---

## 部署确认清单

- [x] 环境门禁确认（用户选择「确认：测试环境 172.16.38.78」）
- [x] 基线与 origin/main 一致（HEAD=`b08b1760c2`，工作区干净）
- [x] 服务器连通性正常
- [x] Flyway 预检 3 步通过
- [x] 本地打包 + 产物校验（OBS、迁移无重复、同源 baseURL、无 `._*`）
- [x] 部署前数据库备份
- [x] 原子切换 + 服务重启 + 健康检查 3/3
- [x] Smoke 测试与 Sidecar 健康验证
- [x] 旧版本 assets 保留
- [x] 部署报告归档
