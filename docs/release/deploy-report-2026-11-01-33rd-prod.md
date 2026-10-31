# 第 33 次生产环境部署报告 — 2026-11-01

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `f0958752c` |
| 基线 commit | `f0958752c`（`origin/main` HEAD） |
| 前一版本 | `9f468fedb`（2026-10-30 第 32 次生产） |
| 部署时间 | 2026-11-01 07:45–07:53 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`9f468fedb` 发布目录 + DB 备份 `winbid-f0958752c-20261101075015.sql.gz`） |

环境门禁：用户指令「部署到生产系统」，确认框选择「确认：生产环境 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `9f468fedb` 追平至 `origin/main` 最新 `f0958752c`，增量 **75 个 commit**，覆盖 PR `!2927`–`!2945` 共 **19 个**。

**与上次生产部署的关键差异：本次零数据库迁移**（部署前后 DB 成功迁移数均为 285 条，最高版本仍为 `V1225`）。

代码内容与测试环境第 254 次部署验证版（`09ccb7a84`）**完全一致**——`git diff 09ccb7a84..HEAD -- backend/src src document-converter-sidecar` 为空，HEAD 仅多 1 个部署报告文档 commit。

**本次新增 Sidecar 变更**：`document-converter-sidecar/converter.py` 有改动，`remote-deploy.sh` 自动重建镜像并重启 `xiyu-sidecar` 容器（schemaVersion=2.2，健康检查通过）。

---

## 变更范围与 PR

增量范围 `9f468fedb..f0958752c` 共 75 个 commit，覆盖 PR：`!2927 !2928 !2929 !2930 !2931 !2932 !2933 !2934 !2935 !2936 !2937 !2938 !2939 !2940 !2941 !2942 !2943 !2944 !2945`。

commit 类型分布（非 merge）：`fix` 22、`docs` 20、`feat` 4、`test` 3、`refactor` 3、`style` 1。

scope 分布：`casework` 21、`scoreparse` 13、`compliance` 7、`release` 5、`wiki` 2、`tender` 1、`reminder` 1。

核心主题：

- **casework（案例库，本次主线）**：
  - 资质证书评分项一票否决方案案例并直连企业资质库原件推荐（CO-652，!2938）
  - 案例生成失败原因明确化并持续展示（!2937）
  - 根治案例来源标题丢失：AI 匹配前后保留 provenance、按源偏移定位（!2935）
  - 生产安全的案例来源回填与只读预演：先预演后写入、可审计产物、生产写保护（!2939）
- **scoreparse（评分解析）**：
  - 隔离模型请求预算并准确上报失败（!2932）
  - PDF OCR 与 Word 结构代码在统一入口优先执行（!2930）
  - 双轨/树感知与 OCR 切片、token 边界处理（!2928、!2929）
  - 跨页评分表与 OCR 条目归属修复（!2933）
- **compliance（标书质量核查）**：资格条件/证书适用性/材料事实三层判定分离、响应性检查排除招标规则引用误判、资格证书与偏离证据判定校准（!2940、!2942、!2944）
- **tender / reminder**：标讯批量导入校验失败计数回写与错误明细展示修复（!2936）；企微接收人邮箱透传与自动提醒失败诊断（!2934）

### 改动文件分布

| 目录 | 文件数 | 增删 |
|---|---|---|
| `backend` | 162 | +7405 / -716 |
| `src`（前端） | 12 | +327 / -27 |
| `docs` | 9 | +1236 / -0 |
| `specs` | 29 | +883 / -0 |
| `.wiki` | 14 | +324 / -25 |
| `document-converter-sidecar` | 3 | +102 / -3 |
| `scripts` | 0 | 0 / 0 |
| **合计** | **230** | **+10278 / -772** |

### 数据库迁移（本次 0 个）

无新增迁移文件、无回滚脚本变更。DB 部署前最高版本 `V1225`（成功迁移 285 条），部署后一致。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate`（部署前） | ✅ `VALIDATE OK - all checksums match`（285 migrations） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ DB 成功迁移 285 条 = 源码 284 个 `V*.sql` + `B73` 基线，零 pending |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ 二次 validate 通过（`Successfully validated 285 migrations`） |

> 说明：本次服务器 `/tmp/migration-mysql/` 与源码一致，第 32 次报告的「`/tmp` 过时导致 validate 阻断」问题未复发。服务器 `/tmp` 中残留若干 macOS `._*` 元数据文件，Flyway 会自动过滤，不影响 validate。

打包阶段校验：jar 内 284 个 `V*.sql`，无重复版本。

---

## 部署步骤

| 阶段 | 动作 | 结果 |
|---|---|---|
| 0 | 环境门禁确认（prod） | ✅ |
| 1 | 基线确认（`git fetch`，HEAD = `origin/main` = `f0958752c`，工作区干净） | ✅ `sync-env.sh` 被锚点分支守卫拒绝，改用 fetch 校验等效达成 |
| 2 | 服务器现状核对 | 旧版本 `9f468fedb`，服务 active 2 天，health/readiness/前端均 200；增量 75 commit / 零迁移 |
| 3 | 打包 `RELEASE_ID=f0958752c VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1` | ✅ 容器测试门禁通过，`apiBaseUrl=""`、`obsEnabled=true`、Detail chunk `.upload(` = 2、sidecar schemaVersion=2.2 |
| 4 | scp 发布包（156 MB）+ `remote-deploy.sh` | ✅ 磁盘余量 71 G |
| 5 | `remote-deploy.sh` 执行（`SYSTEMCTL_SUDO=true`） | ✅ DB 备份 → Sidecar 重建重启 → validate → 前端激活 → 停服 → 原子换 jar → 启服 |
| 6 | 健康检查 | ✅ 连续 3/3，15 次尝试内通过，无 Kafka readiness 长延迟 |
| 7 | 上一版本 assets 保留 | ✅ 从 `9f468fedb/frontend/assets` `cp -rn` 回填 193 个旧 hash 文件（当前 assets 278 个） |
| 8 | `._*` 残留清理 | ✅ 已清理 |
| 9 | 部署报告 | 本文件 |

脚本自动处理事项：前端目录属主统一为 `jetty` 后完成激活（经验 #12 防护生效）；Sidecar 源码 SHA 变化（`5eacdf5c…` → `e5fc03f3…`）触发镜像重建，容器 `xiyu-sidecar` 状态 Up，健康检查通过。

---

## 验证结果

### 后端 / API

本地 Mac 直连 `172.16.10.149:8080` 不可达（已知行为，第 31/32 次已沉淀：防火墙仅放通 80/443）。Smoke 改走 `https://winbid.ehsy.com`，并在服务器本机经 Nginx `:8080` 补验，两处结果一致。

| 检查 | 期望 | 实际（域名 443 / 服务器本机 8080） |
|---|---|---|
| `/actuator/health` | 200 UP | ✅ 200 / 200 |
| `/actuator/health/readiness` | 200 UP | ✅ 200 / 200 |
| `POST /api/auth/login`（空 body） | 400 参数校验 | ✅ 400 |
| `/api/projects`（未认证） | 401/403 | ✅ 401 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 |

### 前端

| 检查 | 实际 |
|---|---|
| `https://winbid.ehsy.com/` | ✅ 200 |
| `https://winbid.ehsy.com/login` | ✅ 200 |
| 服务器本机 `http://127.0.0.1:8080/`、`/login` | ✅ 200 / 200 |
| 入口 asset | `assets/index-xSKpZ2Mv.js`，与发布包 / `deployed-release.json` 一致 |

### 数据库

| 检查 | 实际 |
|---|---|
| `flyway_schema_history` 成功迁移数 | ✅ 285 条（部署前后一致，零新迁移） |
| 最高版本（按 `installed_rank`） | ✅ `V1225`，`success=1` |
| 部署前备份 | ✅ `winbid-f0958752c-20261101075015.sql.gz`（6.5 MB） |

### 服务与配置

| 检查 | 实际 |
|---|---|
| `xiyu-bid-backend` | ✅ active (running) |
| `xiyu-sidecar` 容器 | ✅ Up，`schemaVersion=2.2`，健康检查通过 |
| `deployed-release.json` | ✅ `releaseId=f0958752c`，`activatedAt=2026-10-31T23:50:28Z`（07:50 CST） |
| 临时调试配置清理 | ✅ 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项） |

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | release 目录 `/opt/xiyu-bid/releases/9f468fedb` |
| 后端回滚 | `cp /opt/xiyu-bid/releases/9f468fedb/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` |
| 前端回滚 | 用 `9f468fedb/frontend` 覆盖 `/srv/www/xiyu-bid`（旧 hash 资源已在本次部署中保留） |
| Sidecar 回滚 | 使用 `9f468fedb/sidecar` 源码重建镜像并重启 `xiyu-sidecar` 容器 |
| DB 回滚 | 本次零迁移，无需 DB 回滚；部署前全量备份 `winbid-f0958752c-20261101075015.sql.gz` 就绪 |
| 是否执行 | ❌ 未执行（验证全通过） |

---

## 经验沉淀应用情况

1. **同源构建**：显式传 `VITE_API_BASE_URL=`，产物 `apiBaseUrl=""`。
2. **OBS 直传双保险**：显式传 `VITE_OBS_ENABLED=true`，产物校验 `obsEnabled=true` + Detail chunk `.upload(` = 2。
3. **DB 备份前置**：`DB_BACKUP_COMMAND` 在激活新 jar 之前执行成功。
4. **上一版本 assets 保留**：`cp -rn` 回填 193 个旧 hash 文件，规避旧标签页资源 404。
5. **`SYSTEMCTL_SUDO=true`**：服务重启成功，无 `Interactive authentication required`。
6. **生产 Smoke 走 443/域名 + 服务器本机 8080**：`:8080` 从开发宿主不可达为已知行为，不再误判为故障。
7. **测试先行验证**：本次代码与测试环境第 254 次验证版 `09ccb7a84` 完全一致，符合「测试环境全量验证后再上生产」纪律。
8. **Sidecar 变更自动处理**：`remote-deploy.sh` 检测源码 SHA 变化后自动重建镜像，无需手工介入。

### 本次新发现（建议沉淀）

- **`flyway_schema_history` 的 `MAX(version)` 不能用于判断最新版本**：`version` 为 VARCHAR，`MAX()` 按字符串排序会把 `999` 排在 `1225` 之前。判断最新已应用版本必须用 `ORDER BY installed_rank DESC LIMIT 1`（本次报告已按此口径核对）。

---

## 风险提示

1. 本次改动集中在**案例库（casework）与评分解析（scoreparse）**两条业务主线，虽与测试环境验证版一致，仍建议业务侧针对性回归：资质证书一票否决方案案例生成、案例来源展示、AI 匹配后来源保留、评分解析抽屉与人工复核采纳。
2. 案例来源生产回填（!2939）含生产写保护设计，如需实际执行回填，须先走只读预演并人工确认。
3. `sentryEnabled=false` 与上一版本一致（未注入前端 DSN），前端错误追踪仍依赖服务端日志与 Sentry 后端集成。
4. Sidecar 已重建（`converter.py` 变动），PDF 表格抽取相关能力建议回归一次真实标书文件。

---

## 部署确认清单

- [x] 目标环境经用户显式确认（prod / `172.16.10.149`）
- [x] 基线确认（`origin/main` = `f0958752c`，工作区干净）
- [x] Flyway validate 通过（部署前 + 激活前两次）
- [x] DB 全量备份完成（6.5 MB）
- [x] 发布包产物校验（同源 / OBS / 无重复迁移 / sidecar 契约 2.2）
- [x] Sidecar 镜像重建 + 健康检查通过
- [x] 健康检查 3/3 通过
- [x] 零迁移，DB 版本一致性验证通过（285 条 / V1225）
- [x] API + 前端 Smoke 全通过（域名 443 + 服务器本机 8080）
- [x] 上一版本 assets 保留（193 个旧文件）
- [x] 回滚锚点就绪
- [ ] 业务侧功能回归（待业务确认：案例库 + 评分解析 + Sidecar PDF 抽取）
