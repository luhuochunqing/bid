# 测试环境第 264 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-08 15:04:25 CST（DB 备份）→ 15:04:36 激活 → 15:07:18 健康检查连续 3/3 通过（脚本 81 次尝试）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `1a7a534d1-api8080`
- **提交 Commit**: `1a7a534d1`
- **上一版本**: `1c5490b0a`（2026-11-08 12:26:12 CST 激活，对应第 263 次）
- **部署方式**: `ENV=test XYYU_SYNC_ENV_ACTIVE=1 bash scripts/release/deploy-test.sh`（固化同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、fail-closed 预部署备份、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 6 个 commit，15 个文件变更（+588 / -118）

---

## 变更核心内容

1. **标书质量核查拦截锁解锁（PR !2989）**
   - 解锁资质与保证金拦截锁，优化截断容错与未决条件处理。
   - 依据自动化三技能审查规范调整证据边界与截断校验（`SemanticEvidenceBoundary`、`QualificationDetailReconciler`、`AuthorizationLetterCheckPolicy`、`DepositReceiptVisionFusionPolicy`、`AdjudicationGuidelines`、`TenderRequirements`）。
   - `LlmVerdictParser` / `TenderRequirementsParser` / `BidQualityCheckAsyncExecutor` 配套调整。
   - 新增回归覆盖：`Quality1360UnblockTest`（271 行）、`DepositReceiptVisionFusionPolicyTest`。
2. **文档 / Wiki**
   - 标书质量核查拦截锁解锁与截断容错经验沉淀（`docs/lessons/lessons-learned.md`）及 wiki 回填。
   - 第 263 次测试环境部署报告入库（PR !2988）。
3. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
4. **前端产物**: 入口 hash `assets/index-ZLT6PNtE.js`（与上一版一致，本次无前端代码改动）。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操基线同步 `sync-env.sh` | ✅ 通过（init 分支 ff-only，HEAD=`1a7a534d1`=`origin/main`，0 ahead / 0 behind） |
| 基线确认 | `agent/trae-init`，HEAD=`1a7a534d1`，工作区干净 |
| `check-git-wrapper.sh` | ⚠️ 独立 shell 中显示 `Wrapper NOT active`（`dev-env.sh` 的 PATH 不跨命令持久）；本次无 commit/push，不涉及绕过；`deploy-test.sh` 内部 source 后门禁正常通过 |
| 服务器连通性（环境门禁确认后） | ✅ `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01` |
| 服务器部署前状态 | `deployed-release.json` releaseId=`1c5490b0a-api8080`，health 全组件 UP |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新版一致，无 pending；`success=0` 记录数 = 0 |
| 增量迁移文件 | `git diff 1c5490b0a..1a7a534d1 -- migration-mysql/` 为空 |

### 执行过程中遇到的门禁问题

锚点分支 `agent/trae-init` 上执行部署，`deploy-test.sh` 启动即 source `dev-env.sh`，会被 session-gate 规则 1 拦截。按第 263 次沉淀的做法传入 `XYYU_SYNC_ENV_ACTIVE=1`（仅跳过锚点分支检查，保留 session 互斥）后顺利执行。首次本地打包为验证产物先行启动，与 `deploy-test.sh` 内置打包重复，已主动中止并清理 `.release/1a7a534d1-api8080` 残留后再统一走脚本。

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`（容器测试门禁 `FlywayMysqlContainerTest` 通过 → `mvn clean -DskipTests package` BUILD SUCCESS，38.8s）
- **产物校验**：`release-metadata.json` 中 `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、sidecar schema 2.2；jar 内 Flyway 迁移文件无重复版本；归档 163 MB
- **上传**：`scp` 归档 + `remote-deploy.sh` + `smoke-sidecar.sh` + `backup-db-pre-deploy.sh` + 1371 fixture + `flyway-repair-runner.sh` → `/opt/xiyu-bid/`
- **远程部署**：
  1. 部署前数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-1a7a534d1-api8080-20261108150425.sql.gz`（16,175,760 bytes）—— fail-closed 备份链路生效
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend`
  3. 前端目录切换，入口一致性校验通过（`/assets/index-ZLT6PNtE.js`）
  4. 健康检查连续 3/3 通过（81 次尝试，约 2.7 分钟，无 Kafka readiness 延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置策略（最近 2 个版本合并），`assets` 目录现 278 个文件，`Detail-*` chunk 共 7 个（新旧并存）
- **Sidecar**：容器未重建，SHA-256 与上一版一致（`e5fc03f3...`）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`1a7a534d1-api8080`，activatedAt=`2026-11-08T07:04:36Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200（db / redis / aiProvider / sidecar / jwt 全 UP） |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200 |
| 前端入口一致性 | `assets/index-ZLT6PNtE.js` 与 release 一致 |
| 前端缓存策略 | 200 + `Content-Encoding: gzip` + `Cache-Control: public, immutable` + `max-age=31536000` |
| 旧 hash 资产保留 | `assets` 目录 278 个文件，`Detail-*` 7 个（旧标签页可继续加载） |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移，`success=0` = 0 |
| Sidecar 发布冒烟 | 5/5 通过（health 指纹 2.2 / 1371 fixture 回放 tables=14 cells=8215 / v1+v2 版本探针） |
| OBS 直传 | `obsEnabled=true`，Detail chunk `.upload(` 调用数 = 2 |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 已知项与风险提示

1. `/etc/xiyu-bid/backend.env` 中仍存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史遗留，历次部署决定保留）；本次**未新增**任何临时调试配置。
2. `/assets/*` 响应同时带 `max-age=31536000`（nginx `expires`）与 `public, immutable`（`add_header`）两条头，浏览器合并处理无功能影响。
3. 锚点分支执行部署需 `XYYU_SYNC_ENV_ACTIVE=1`，建议后续在 `deploy-test.sh` / `deploy-prod.sh` 中显式处理该门禁，避免手工拼环境变量。
4. 旧标签页跨版本 404 风险由「保留最近 2 个版本资产」策略覆盖，仍建议用户侧刷新页面获取新入口。
5. 本次改动集中在标书质量核查后端纯核心逻辑，前端无变更；后续业务验证重点为「资质/保证金拦截锁解锁后裁决结果与截断容错表现」。

---

## 回滚信息

- **上一版本产物**: `/opt/xiyu-bid/releases/1c5490b0a-api8080/`（jar + frontend 均保留）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-1a7a534d1-api8080-20261108150425.sql.gz`（16 MB，部署前快照）
- **回滚步骤**: 将 `1c5490b0a-api8080/backend/app.jar` 覆盖 `/opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` → 恢复 `1c5490b0a-api8080/frontend` 到 `/srv/www/xiyu-bid` → 健康检查
- **回滚状态**: 未触发

---

## 部署确认清单

- [x] 环境门禁确认（测试 172.16.38.78）
- [x] 早操基线同步完成
- [x] Flyway 三步预检通过，无 pending 迁移
- [x] 打包产物校验（同源构建 / OBS 直传 / 迁移无重复）
- [x] DB 部署前 fail-closed 备份完成
- [x] 后端健康检查 + 前端入口一致性校验通过
- [x] 冒烟测试（health / readiness / 三个接口路由 / 前端页面）全部符合预期
- [x] 旧版本 assets 保留生效
- [x] Sidecar 发布冒烟 5/5 通过
