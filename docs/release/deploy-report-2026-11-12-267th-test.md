# 测试环境第 267 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-12 14:17 CST 开始 → 14:17:59 激活（`activatedAt=2026-11-12T06:17:59Z`）→ 健康检查连续 3/3 通过
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `0e71c2f0f-api8080`
- **提交 Commit**: `0e71c2f0f`
- **上一版本**: `6c9b7f851-api8080`（2026-11-12 07:15:35 CST 激活，对应第 266 次）
- **部署方式**: 手工标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `scripts/release/remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + `DB_BACKUP_COMMAND` 部署前备份）
- **增量规模**: 12 个 commit / 4 个功能 PR，40 个文件变更（+1717 / -65）

---

## 变更核心内容

| PR | 类型 | 内容 |
|---|---|---|
| !3009 | fix(scoreparse) | 解决大跨度 Rowspan 与平级项解析失真（Project 1123） |
| !3010 | fix(auth) | 登录失败提示反映 OSS 真实原因 |
| !3008 | fix(scoreparse) | 修复知识库预判全量刷新与结果反馈，刷新返回可验证回执 |
| !3007 | docs(release) | 第 40 次生产环境部署报告（`5370cbad2`） |
| !3006 | docs(release) | 第 266 次测试环境部署报告与升级验收矩阵 |

- **非 PR 直提 commit**: `c9d2f16a8`（网格跨行下沉 / 虚构大项剪枝 / 补漏幂等去重）、`5c608abb2`（OSS 登录失败错误分类）、`2315b7c86` / `787a31908`（wiki CRM 集成踩坑集 §9.8 回填）。
- **改动分布**: `backend/main` 17 个文件、`backend/test` 5 个、`frontend`（`src` 4 + `specs` 5）共 9 个、`.wiki` 5 个、`docs` 3 个。
- **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`），`flyway_schema_history` 无 failed 记录。
- **前端产物**: 入口 hash `assets/index-CE9VR_gc.js`（上一版 `index--adUF0Cy.js`）。
- **Sidecar**: `sidecarSha256=e9bcf69a…`，与上一版一致，`remote-deploy.sh` 跳过重建；容器 `/health` 返回 `{"status":"up"}`。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | `agent/trae-init` 锚点分支，HEAD=`0e71c2f0f`=`origin/main`，工作区干净（0 ahead / 0 behind） |
| 服务器连通性 | `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01`，系统时间 2026-11-12 14:13 CST |
| 服务器部署前状态 | `deployed-release.json` releaseId=`6c9b7f851-api8080`，health=UP（db / redis / aiProvider / sidecar 均 UP） |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（2026-10-26 应用），与源码一致，无 pending |
| 增量迁移文件 | `git diff 6c9b7f851..0e71c2f0f -- backend/src/main/resources/db/migration-mysql/` 为空 |
| 增量规模核对 | `git rev-list --count 6c9b7f851..HEAD` = 12，`git diff --stat` 40 文件 |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`
  - `mvn clean -DskipTests package` BUILD SUCCESS（37.2s），jar 内 Flyway 迁移文件 284 个、无重复版本
  - Sidecar 打包完成，SHA-256 `e9bcf69ac9aea71b94ac34f1a58aedca057761a1b49197bf782c91f5b86dd782`，`schemaVersion=2.2`
  - OBS 直传校验：Detail chunk `.upload(` 调用数 = 2
  - 归档 `xiyu-bid-release-0e71c2f0f-api8080.tar.gz`（163,330,787 bytes），本地 MD5 `375453ba16919b159d2e3687e912ca16`
- **产物校验**：`release-metadata.json` → `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、`sidecarSchemaVersion=2.2`；前端入口 `assets/index-CE9VR_gc.js`
- **远程部署**（`remote-deploy.sh`）：
  1. 部署前数据库备份 → `/opt/xiyu-bid/db-backups/winbid-0e71c2f0f-20261112141749.sql.gz`（16.4 MB）
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend`
  3. 前端目录切换，入口一致性校验通过（`/assets/index-CE9VR_gc.js`）
  4. 健康检查连续 3/3 通过（共 80 次尝试，无 Kafka readiness 长延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置策略（保留当前公开 assets + 最近 2 个已激活版本 `6c9b7f851-api8080`、`d204051c5-api8080`），`/srv/www/xiyu-bid/assets` 共 448 个文件（上一版 364 个）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`0e71c2f0f-api8080`，activatedAt=`2026-11-12T06:17:59Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 UP |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200、HTTP 200 |
| 前端入口一致性 | `assets/index-CE9VR_gc.js` 与 release 一致 |
| 旧 hash 资产保留 | assets 目录 448 个文件 |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移；`success=0` 记录数 0 |
| Sidecar | `/health` → `{"status":"up"}`；hash 未变，本次未重建镜像 |
| 后端服务状态 | `systemctl is-active xiyu-bid-backend` = active；近 10 分钟 err 级日志 0 条（152 行正常日志） |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 已知项与风险提示

1. `/etc/xiyu-bid/backend.env` 中仍存在历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历次部署决定保留），本次未新增调试配置。
2. 本次仍走 skill 标准手工链路（package → scp → remote-deploy）；`package-release.sh` 在锚点分支 `agent/trae-init` 上会被 session-gate 拦截，需显式 `export SKIP_SESSION_GATE=1`（规则 5 只豁免 `XYYU_SYNC_ENV_ACTIVE=1`，`XIYU_DEV_CONFIRMED=1` 不足以放行）。
3. 业务验证重点：大跨度 Rowspan / 平级评分项解析（Project 1123 素材）、知识库预判全量刷新回执、OSS 登录失败提示文案是否反映真实原因。
4. 旧标签页跨版本 404 风险由「保留最近 2 个版本资产」覆盖，用户侧仍建议刷新页面获取新入口。

---

## 回滚信息

- **上一版本产物**: `/opt/xiyu-bid/releases/6c9b7f851-api8080/`（jar + frontend 均保留）
- **上一版 Sidecar 镜像**: 本次未重建（hash 一致），无需回滚
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-0e71c2f0f-20261112141749.sql.gz`（16.4 MB，部署前快照）
- **回滚步骤**:
  1. `cp /opt/xiyu-bid/releases/6c9b7f851-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`
  2. `sudo systemctl restart xiyu-bid-backend`
  3. 恢复 `6c9b7f851-api8080/frontend` 到 `/srv/www/xiyu-bid`
  4. 健康检查 + Smoke
- **回滚状态**: 未触发

---

## 部署确认清单

- [x] 环境门禁确认（测试 172.16.38.78）
- [x] 基线确认（HEAD = origin/main = 0e71c2f0f，工作区干净）
- [x] Flyway 三步预检通过，无 pending 迁移
- [x] 打包产物校验（同源构建 / OBS 直传 / 迁移无重复）
- [x] DB 部署前备份完成（16.4 MB）
- [x] 后端健康检查 + 前端入口一致性校验通过
- [x] 冒烟测试（health / readiness / 三个接口路由 / 前端页面）全部符合预期
- [x] 旧版本 assets 保留生效
- [x] 无新增临时调试配置，无 macOS `._*` 残留，无 err 级日志
