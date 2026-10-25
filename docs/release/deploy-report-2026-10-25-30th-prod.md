# 第 30 次生产环境部署报告 — 2026-10-25

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `838ee5742` |
| 基线 commit | `838ee5742`（`origin/main` HEAD） |
| 前一版本 | `f3ff316f2`（2026-10-25 第 29 次生产） |
| 部署时间 | 2026-10-25 20:58–21:00 CST |
| 状态 | ✅ 成功（健康 + Smoke 全通过，本次无数据库迁移） |
| 回滚状态 | 未执行，锚点就绪（`f3ff316f2` 发布目录 + DB 备份） |

环境门禁：用户指令「部署到生产系统」，确认框选择「确认：生产 172.16.10.149」+「origin/main 最新 HEAD」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `f3ff316f2` 追平至 `origin/main` 最新 `838ee5742`，增量 **27 个 commit**（同日），覆盖 PR `!2868`–`!2875` 共 **8 个**（其中 5 个业务修复、3 个部署报告文档）。

**与上次生产部署的关键差异：本次不含任何数据库迁移**（DB 保持 `V1224`），风险集中在业务判定逻辑（评分解析、资质核查）与少量前端文案，回滚仅需回退 jar + 前端产物。

---

## 变更范围与 PR

增量范围 `f3ff316f2..838ee5742` 共 27 个 commit，覆盖 PR：`!2868 !2869 !2870 !2871 !2872 !2873 !2874 !2875`。

核心主题：

- **scoreparse（评分解析）**：兼容标书章节标题字间空格，避免评分方案同级误截断（!2874，含河南神火金标回放用例）
- **compliance（资质核查）**：根治临期降级文案自相矛盾、目录页伪命中与跨源证据穿透（!2872）；纠正证据上下文并收紧资格自动通过（!2873）；修复 CA 平台误识别与证书匹配（!2871）
- **warehouse（仓库）**：对齐含附件导出的 24 小时下载提示（!2870）
- **docs**：第 234/235 次测试环境部署报告（!2868、!2875）

### 改动文件分布

| 目录 | 文件数 | 增删 |
|---|---|---|
| `.wiki` | 134 | +253 / -135 |
| `backend/src/main` | 25 | +819 / -475 |
| `backend/src/test` | 20 | +1110 / -29 |
| `specs` | 7 | +203 / -0 |
| `docs` | 5 | +582 / -0 |
| `src`（前端） | 4 | +41 / -5 |
| `.specify` | 1 | +3 / -1 |
| **合计** | **196** | **+3011 / -645** |

前端仅 4 个文件：`WarehouseExportPackageDetail.vue`、`QualityCheckDetailDialog.vue` 及对应 spec。

### 数据库迁移（本次 0 个）

DB 当前最高版本 `V1224`（2026-10-25 11:59 第 29 次部署应用），本次源码最高版本同为 `V1224`，无新增迁移。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（284 migrations） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ 双方最高版本均为 `V1224`，无 pending |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ 二次 validate 通过 |

打包阶段另执行容器测试门禁 `FlywayMysqlContainerTest`（Spring 全量上下文装配）通过。

---

## 部署步骤

1. **早操三连**：`sync-env.sh` ff-only 同步锚点分支；确认 `HEAD == origin/main == 838ee5742`，工作区干净
2. **服务器现状**：`deployed-release.json` = `f3ff316f2`，服务 active，health UP
3. **Flyway 预检 3 步**：见上表
4. **本地打包**：
   ```bash
   RELEASE_ID="838ee5742" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
     bash scripts/release/package-release.sh
   ```
   - 产物：`.release/xiyu-bid-release-838ee5742.tar.gz`（156M）
   - 元数据：`apiBaseUrl=""`（同源）、`obsEnabled=true`、`sentryEnabled=false`
   - jar 内 `V*.sql` 无重复版本；OBS 直传校验 `.upload(` 调用数 = 2
5. **上传校验**：scp 归档 + `remote-deploy.sh`，SHA256 双端一致（`43ca65b7…1cc6bf`）
6. **执行部署**：`SYSTEMCTL_SUDO=true`，含 DB 备份 → validate → 原子切换 jar → 重启 → health 等待 → 切换前端
7. **部署后处理**：从 `f3ff316f2` 发布目录 `cp -rn` 旧 assets 回 `/srv/www/xiyu-bid/assets/`（当前 278 个资源），清理检查无 `._*` 残留

---

## 验证结果

### 健康检查

- `remote-deploy.sh` 内置：✅ Health check passed（consecutive 3/3，第 15 次尝试，服务 active/running）
- 后端启动时间：2026-10-25 20:59:36 CST，`NRestarts` 为空（=0）
- 启动日志 `ERROR` 行数：**0**

### Smoke（服务器本地 + 生产域名）

| 检查项 | 服务器本地 `127.0.0.1:8080` | 域名 `https://winbid.ehsy.com` |
|---|---|---|
| `/actuator/health` | 200 | 200 |
| `/actuator/health/readiness` | 200 | 200 |
| `/`（前端首页） | 200 | 200 |
| `/login` | 200 | 200 |
| `POST /api/auth/login`（空体） | 400（参数校验） | 400 |
| `GET /api/projects`（未认证） | 401 | 401 |
| `GET /api/integration/crm/health`（未认证） | 401 | — |

- 前端入口一致性：域名返回 `assets/index-Dfy6RYA7.js`，与发布包 `frontend/index.html` 一致 ✅
- 静态资源缓存：`Content-Encoding: gzip` + `Cache-Control: public, immutable`（max-age=31536000）✅
- 注意：从 macOS 直连 `http://172.16.10.149:8080` 返回 HTTP 000（本机到该端口的网络路径不通，服务器侧与域名侧均 200），属本机网络/代理路径问题，非服务故障。

### 未完成的验证（需业务确认）

- **登录态业务抽查未执行**：生产 admin 密码不在本次会话上下文，Smoke 仅覆盖路由与鉴权边界。建议业务方登录后抽查：
  1. 评分解析：河南神火类标书章节标题带字间空格时，评分方案不再同级误截断（!2874）
  2. 资质核查：临期证书降级文案与结论一致、目录页不再伪命中、证据不跨源穿透（!2872/!2873）
  3. CA 平台证书匹配与原文证据保留（!2871）
  4. 仓库含附件导出的 24 小时下载提示文案（!2870）

---

## 回滚信息

| 项 | 值 |
|---|---|
| 回滚锚点（应用） | `/opt/xiyu-bid/releases/f3ff316f2`（前端产物）+ `/opt/xiyu-bid/incoming/xiyu-bid-release-f3ff316f2.tar.gz` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-838ee5742-20261025205928.sql.gz`（5.8M，部署前生成） |
| 数据库回滚需求 | **无**（本次无 schema 变更） |

回滚步骤（如需）：

```bash
ssh jetty@172.16.10.149 'cd /opt/xiyu-bid/incoming && source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-f3ff316f2.tar.gz \
  APP_ROOT=/opt/xiyu-bid FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=f3ff316f2 SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

---

## 经验沉淀应用情况

| 经验 | 本次执行 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + 内置 validate 全通过 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式置空，元数据 `apiBaseUrl=""` |
| #5 临时调试配置清理 | ✅ 检查 `backend.env`，仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增） |
| #7 `SYSTEMCTL_SUDO=true` | ✅ 服务正常重启，无 `Interactive authentication required` |
| #9/#10 `COPYFILE_DISABLE=1` | ✅ 前端目录无 `._*` 残留（tar 仍报 macOS xattr 头忽略告警，无害） |
| #13 OBS 直传校验 | ✅ `obsEnabled=true` + Detail chunk `.upload(` = 2 |
| #17 旧 hash 资源保留 | ✅ 已从 `f3ff316f2` 补回旧 assets（共 278 个资源） |
| #18 gzip + 强缓存 | ✅ 域名侧实测 gzip + immutable |
| 前端目录属主修复 | ✅ 部署脚本自动 `sudo chown -R jetty` 后切换 |

---

## 风险提示

1. **评分解析逻辑变更（!2874）**：章节标题字间空格兼容会改变评分方案截断边界，建议对重点在评标书做一次金标比对。
2. **资质核查口径收紧（!2871–!2873）**：资格自动通过条件收紧、证据边界收窄，可能出现「原先自动通过、现在转人工」的观感变化，属预期行为，需业务侧知晓。
3. **未做登录态端到端验证**：P0 路由与鉴权已验证，但业务功能需人工抽查（见上节）。
4. **GitHub 镜像落后告警**：`sync-env.sh` 提示 GitHub 镜像与 Gitee 方向不一致，与本次生产部署无关，建议单独治理。
5. **`/opt/xiyu-bid/incoming/` 历史归档堆积**（7–8 月多个 150M+ 包），当前磁盘使用 24%（余 73G），不影响本次部署，建议后续清理。

---

## 部署确认清单

- [x] 环境门禁确认（生产 172.16.10.149）
- [x] 基线 = `origin/main` 最新 `838ee5742`，工作区干净
- [x] Flyway validate 通过，无新增迁移
- [x] 部署前 DB 备份完成
- [x] 发布包 SHA256 双端一致
- [x] 后端服务重启成功，健康检查 3/3
- [x] 前端入口版本一致（`index-Dfy6RYA7.js`）
- [x] Smoke：health / readiness / 前端 / 鉴权边界全部符合预期
- [x] 旧版本 assets 已保留
- [x] 后端启动日志无 ERROR
- [ ] 登录态业务抽查（待业务方确认）
