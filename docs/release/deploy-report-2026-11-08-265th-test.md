# 测试环境第 265 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-08 18:54:05 CST（开始）→ 18:54:18 激活（`activatedAt=2026-11-08T10:54:18Z`）→ 18:56:56 完成（健康检查连续 3/3 通过，共 79 次尝试）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `d204051c5-api8080`
- **提交 Commit**: `d204051c5`
- **上一版本**: `1a7a534d1-api8080`（2026-11-08 15:04:36 CST 激活，对应第 264 次）
- **部署方式**: `XYYU_SYNC_ENV_ACTIVE=1 ENV=test bash scripts/release/deploy-test.sh`（固化同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、fail-closed 预部署备份、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 4 个 commit，8 个文件变更（+314 / -50）

---

## 变更核心内容

1. **投标函通用称谓与资质文案优化（PR !2991 / commit `d204051c5`）**
   - `BidLetterCheckPolicy`：支持投标函通用称谓（不再强依赖具体法定代表人/授权代表称谓）。
   - `QualificationDetailReconciler`：资质文案去技术化，降低对内部术语的硬匹配。
   - `SemanticEvidenceBoundary`：收窄证据边界判定。
   - `TenderRequirementsExtractor`：评分提取增加长度约束。
   - 回归覆盖扩展：`Quality1360UnblockTest`（+164 行）。
2. **文档 / Wiki**
   - 第 264 次测试环境部署报告入库（PR !2990）。
   - `docs/lessons/lessons-learned.md` 与 `.wiki/pages/quality1360-diagnosis.md` 知识沉淀同步。
3. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
4. **前端产物**: 入口 hash `assets/index-ZLT6PNtE.js`（与上一版一致，本次无前端代码改动）。
5. **Sidecar**: 源码无变动，SHA-256 `e5fc03f3...` 与上一版一致，跳过容器构建。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | `agent/trae-init` 锚点分支，HEAD=`d204051c5`=`origin/main`，工作区干净（0 ahead / 0 behind） |
| 服务器连通性 | `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01`，系统时间 2026-11-08 18:49 CST |
| 服务器部署前状态 | `deployed-release.json` releaseId=`1a7a534d1-api8080`，health 全组件 UP（db / redis / aiProvider / sidecar / jwt） |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（`score estimate freshness`，2026-10-26 应用），与源码一致，无 pending |
| 增量迁移文件 | `git diff 1a7a534d1..d204051c5 -- migration-mysql/ rollback/` 为空 |

### 执行过程中遇到的门禁问题

锚点分支 `agent/trae-init` 上执行部署，`deploy-test.sh` 启动即 source `dev-env.sh`，会被 session-gate 规则 1（锚点分支阻断）拦下并直接终止脚本。首次尝试未带例外变量，脚本在打印门禁横幅后退出（未执行任何打包或远程操作）。按既有沉淀传入 `XYYU_SYNC_ENV_ACTIVE=1`（仅跳过锚点分支检查，保留 session 互斥）后顺利执行。

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`
  - 前端 Vite 构建 23.06s，产物校验「不含 dev API 地址」通过
  - 容器测试门禁 `FlywayMysqlContainerTest` 通过（Spring 全量上下文装配正常）
  - `mvn clean -DskipTests package` BUILD SUCCESS（39.5s）
- **产物校验**：
  - `release-metadata.json`：`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、`sidecarSchemaVersion=2.2`
  - jar 内 Flyway 迁移文件无重复版本
  - OBS 直传：Detail chunk `.upload(` 调用数 = 2
  - 归档 161 MB
- **上传**：`scp` 归档 + `remote-deploy.sh` + `smoke-sidecar.sh` + `backup-db-pre-deploy.sh` + 1371 fixture + `flyway-repair-runner.sh` → `/opt/xiyu-bid/`
- **远程部署**：
  1. 部署前数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-d204051c5-api8080-20261108185406.sql.gz`（16,189,895 bytes）—— fail-closed 备份链路生效
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend`
  3. 前端目录切换，入口一致性校验通过（`/assets/index-ZLT6PNtE.js`）
  4. 健康检查连续 3/3 通过（79 次尝试，约 2.5 分钟，无 Kafka readiness 延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置策略（最近 2 个版本合并，本次保留 `1a7a534d1-api8080`、`1c5490b0a-api8080`），`assets` 目录 278 个文件，`Detail-*` chunk 共 13 个（新旧并存）
- **Sidecar**：容器未重建，SHA-256 与上一版一致

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`d204051c5-api8080`，activatedAt=`2026-11-08T10:54:18Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200、HTTP 200 |
| 前端入口一致性 | `assets/index-ZLT6PNtE.js` 与 release 一致 |
| 旧 hash 资产保留 | `assets` 目录 278 个文件，`Detail-*` 13 个（旧标签页可继续加载） |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| Sidecar 发布冒烟 | 5/5 通过（health 指纹 2.2 / 1371 fixture 回放 tables=14 cells=8215 / v1+v2 版本探针） |
| 后端服务状态 | `systemctl is-active xiyu-bid-backend` = active；近 10 分钟无 err 级日志 |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 已知项与风险提示

1. `/etc/xiyu-bid/backend.env` 中仍存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史遗留，历次部署决定保留）；本次未新增调试配置。
2. 锚点分支执行部署需显式传 `XYYU_SYNC_ENV_ACTIVE=1`，建议在 `deploy-test.sh` 内部直接处理该门禁，避免手工拼环境变量（与第 264 次报告建议一致，仍未落地）。
3. 旧标签页跨版本 404 风险由「保留最近 2 个版本资产」策略覆盖，用户侧仍建议刷新页面获取新入口。
4. 本次改动集中在标书质量核查后端纯核心逻辑（投标函称谓 / 资质文案 / 评分提取长度约束），前端无变更。业务验证重点：通用称谓场景下投标函核查是否误报，以及资质文案去技术化后裁决结果是否稳定。

---

## 回滚信息

- **上一版本产物**: `/opt/xiyu-bid/releases/1a7a534d1-api8080/`（jar + frontend 均保留）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-d204051c5-api8080-20261108185406.sql.gz`（16 MB，部署前快照）
- **回滚步骤**: 将 `1a7a534d1-api8080/backend/app.jar` 覆盖 `/opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` → 恢复 `1a7a534d1-api8080/frontend` 到 `/srv/www/xiyu-bid` → 健康检查
- **回滚状态**: 未触发

---

## 部署确认清单

- [x] 环境门禁确认（测试 172.16.38.78）
- [x] 基线确认（HEAD = origin/main = d204051c5，工作区干净）
- [x] Flyway 三步预检通过，无 pending 迁移
- [x] 打包产物校验（同源构建 / OBS 直传 / 迁移无重复）
- [x] DB 部署前 fail-closed 备份完成
- [x] 后端健康检查 + 前端入口一致性校验通过
- [x] 冒烟测试（health / readiness / 三个接口路由 / 前端页面）全部符合预期
- [x] 旧版本 assets 保留生效
- [x] Sidecar 发布冒烟 5/5 通过
- [x] 无新增临时调试配置，无 macOS `._*` 残留
