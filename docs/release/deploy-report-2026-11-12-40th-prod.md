# 第 40 次生产环境部署报告 — 2026-11-12

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `5370cbad2` |
| 基线 commit | `5370cbad2`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `2960e7ea5`（2026-11-07 激活） |
| 部署时间 | 2026-11-12 08:31:29–08:31:41 CST（备份 → 激活 → 重启），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + 公网 Smoke + Sidecar 冒烟全通过，**零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/2960e7ea5` + 本次部署前 DB 备份） |

环境门禁：用户通过问答卡片明确选择「确认：生产环境 172.16.10.149」。

---

## 部署概览

从生产版本 `2960e7ea5` 追平至 `origin/main` 最新 `5370cbad2`，增量 **86 个 commit / 31 个合并 / PR !2975–!3006**。

- **零数据库迁移**：`2960e7ea5..5370cbad2` 无 `db/migration-mysql/` 与 `db/rollback/` 变更；部署前后 `flyway_schema_history` 最高版本仍为 `V1225`。
- **Sidecar 升级**：sidecar SHA `e5fc03f3…` → `e9bcf69a…`（schemaVersion 仍为 `2.2`），部署中自动重建 `document-converter-sidecar:latest` 并重启 `xiyu-sidecar` 容器。
- **前端产物**：同源构建（`apiBaseUrl=""`），入口 `assets/index--adUF0Cy.js`；`obsEnabled=true`，Detail chunk `.upload(` = 2。
- **Sentry**：本次 `sentryEnabled=false`（沿用上一版现状，保持关闭）。
- **旧资源保留**：`remote-deploy.sh` 的 `ASSETS_RETAIN_RELEASES=2` 生效，激活时合并历史版本 hash 资源，`/srv/www/xiyu-bid/assets/` 共 364 个文件，避免旧标签页 preload 404。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !2975 | 第 39 次生产环境部署报告（2960e7ea5） |
| !2976–!2985 | 部署脚本修复：session-gate 被 source 时改用 return、保留最近 2 版前端资产、DB 备份 fail-closed、部署脚本两类陷阱 wiki 回填 |
| !2986–!2989 | compliance：长文本裁决 JSON 截断根治、资质证书身份证据归属、资质与保证金拦截锁解锁、截断容错 |
| !2990–!2991 | compliance：投标函通用称谓、文案去技术化、评分提取长度约束 |
| !2992–!2994 | 测试环境部署报告；OCR 转写要件与企业资质库一票否决通道；三技能审查门禁跨轮次历史与熔断 |
| !2995–!2997 | compliance：跨句证书推断收口与分层契约测试；偏离表识别门禁收敛、DOCX 页码定位提示 |
| !2998 | casework：标书解析配额提升至 3GiB、失败通知展示优化 |
| !2999 | scoreparse：扫描投标文件补齐 OCR 并区分预估状态 |
| !3000–!3001 | scoreparse 证书规则表格管道符剥离与三体系断句；casework 结项写入案例子主题与质量分 |
| !3002–!3006 | scoreparse：非计分零分条款拦截、满分汇总行防重复计分、跨页续行证据收集、候选总分落库硬门禁、客观项过度防御短路解除 |

> 说明：本批次以 scoreparse（评分解析）与 compliance（标书合规核查）修复为主，另有部署脚本与审查门禁治理。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操基线同步 | ✅ HEAD = `5370cbad2` = `origin/main`，工作区干净（锚点分支以 ff-only 只读同步） |
| git wrapper 检查 | ⚠️ 当前 shell 未激活 `scripts/git` 包装器（未执行任何 `--no-verify` 操作，不影响本次部署） |
| 服务器健康（部署前） | `/actuator/health` UP（db / redis / sidecar / aiProvider 全 UP），`xiyu-bid-backend` active |
| 部署前版本 | `deployed-release.json` → `releaseId=2960e7ea5` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | `V1225`（2026-10-27 应用），与源码最新版本一致，无 pending |
| 增量迁移文件 | 无（`git diff --name-only 2960e7ea5..HEAD -- db/migration-mysql/` 为空） |

> 注：本次开工时 aTrust 零信任隧道短暂未就绪（`kex_exchange_identification: Connection closed`），等待隧道恢复后重连成功（`ssh` 返回 `winbid-01`）；属已知网络前置条件，非服务故障。

---

## 部署执行

按 `xiyu-server-deploy` 技能标准 SOP 手工执行（未使用 `deploy-prod.sh` 一键脚本，因其在非 main 分支会交互式询问）：

- 打包：`RELEASE_ID=5370cbad2 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
  - 同源构建 + `mvn clean -DskipTests package` + Flyway 容器测试，发布包 156 M
  - 产物校验：jar 内 284 个 `V*.sql` 无重复；`obsEnabled=true`；`sentryEnabled=false`；`apiBaseUrl=""`
- 上传：发布包 + `remote-deploy.sh` + `smoke-sidecar.sh` + `backup-db-pre-deploy.sh` + `flyway-repair-runner.sh` + `1371_tables_v1.json` 至 `/opt/xiyu-bid/{incoming,bin}/`
- 远程部署（`remote-deploy.sh`，`SYSTEMCTL_SUDO=true`）：
  1. 数据库备份（fail-closed）：`winbid-5370cbad2-20261112083129.sql.gz`（7,248,644 bytes）
  2. Flyway validate 通过（285 个迁移，仅 pending 为预期状态）
  3. 前端目录属主修复（`sudo chown -R jetty:jetty`）→ 激活前端
  4. 停服务 → 原子替换 jar → 写 `deployed-release.json` → 启动服务
  5. Sidecar SHA 变动 → 重建镜像并重启容器 → `/health` 通过
  6. 健康检查连续 3/3 通过（第 15 次探测，约 30s）
  7. 前端一致性校验：`src="/assets/index--adUF0Cy.js"` 与 release 一致

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（服务器本地 18080） | HTTP 200，`status=UP`（db / redis / sidecar / aiProvider / jwt 全 UP） |
| `/actuator/health/readiness` | HTTP 200 UP |
| 公网 `https://winbid.ehsy.com/actuator/health` | HTTP 200 UP |
| 公网 `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，路由正常） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health` | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 | `assets/index--adUF0Cy.js`（与 release 一致） |
| 部署记录 | `deployed-release.json` → `releaseId=5370cbad2`，`obsEnabled=true`，`sentryEnabled=false` |
| OBS 直传校验 | 当前 release Detail chunk `.upload(` = 2（≥2 通过） |
| Sidecar 冒烟（`smoke-sidecar.sh`） | 5/5 通过：v1 探针 200（`X-Schema-Version=1.0`）、v2 探针 200（`schemaVersion=2.2`） |
| 数据库版本 | `flyway_schema_history` 最高 `V1225 success=1`（与部署前一致，无 pending 应用） |
| 旧版 assets | ✅ 保留最近 2 个版本，`assets/` 共 364 个文件 |
| 临时调试配置 | `/etc/xiyu-bid/backend.env` 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项） |
| 服务状态 | `xiyu-bid-backend` active/running |

> 说明：本地 Mac 直连 `172.16.10.149:8080` 时通时不通（aTrust 隧道抖动），本次 Smoke 以公网域名 `https://winbid.ehsy.com`（200 + 上述状态码）为对外可达性证据，服务器本地与 Sidecar 冒烟均在服务器内执行。

---

## 回滚信息

- 代码回滚锚点：`/opt/xiyu-bid/releases/2960e7ea5`（前后端完整产物）；旧 jar 可从该目录取回。
- 数据库回滚：**不需要**（本次零迁移）。
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-5370cbad2-20261112083129.sql.gz`（7.2 M，由 `backup-db-pre-deploy.sh` 校验通过后写入，fail-closed）。
- 回滚操作：`remote-deploy.sh` 以 `RELEASE_ARCHIVE` 指回上一版本包重放即可；如需恢复数据，用上述备份 `gunzip | mysql` 导入（需用户显式确认目标库）。

---

## 经验与风险提示

1. **DB 备份缺陷已闭环**：第 36/39 次报告记录的 `mysqldump | gzip` 假备份问题，已由 PR !2982（`backup-db-pre-deploy.sh` fail-closed）根治。本次产出 7.2 M 真实备份并在部署前校验通过。
2. **aTrust 隧道是部署前置条件**：开工时 `ssh` 在 kex 阶段被断开（`Connection closed`），隧道恢复后正常。裸 `172.16.10.149` 与 `winbid-01.prod`（`240.0.0.47`）同受影响；排障先看隧道，不要误判为服务器故障。
3. **Sidecar SHA 变动会触发镜像重建**：本次 sidecar 源码有改动（`e5fc03f3…` → `e9bcf69a…`），部署耗时含 docker build；schemaVersion 未变（2.2），后端兼容。
4. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 继续保持**：历史多次决定保留（便于健康检查排障），本次未调整。
5. **锚点分支不可直接开发**：`sync-env.sh` 在 `agent/trae-init` 上被开发门禁拦截，本次以 `git fetch + git merge --ff-only origin/main` 完成基线同步。

---

## 部署确认清单

- [x] 环境门禁通过（用户确认部署到生产 172.16.10.149）
- [x] 基线 = `origin/main` HEAD（`5370cbad2`），工作区干净
- [x] Flyway validate 通过（285 个迁移，checksum 全对）
- [x] 无待应用迁移（DB 与源码均为 V1225）
- [x] 部署前数据库备份完成（7.2 M，fail-closed 校验）
- [x] 发布包产物校验（284 个 V*.sql 无重复 / obsEnabled=true / 同源构建）
- [x] 部署成功（jar 激活 + 服务重启）
- [x] 健康检查 3/3 通过
- [x] 公网 Smoke 全通过（health / readiness / login / 401 路由 / 前端页面）
- [x] Sidecar 发布冒烟 5/5 通过
- [x] 旧版前端 assets 保留（最近 2 个版本）
- [x] 回滚锚点与 DB 备份就绪
