# 第 32 次生产环境部署报告 — 2026-10-30

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `9f468fedb` |
| 基线 commit | `9f468fedb`（`origin/main` HEAD） |
| 前一版本 | `045ee5948`（2026-10-27 第 31 次生产） |
| 部署时间 | 2026-10-30 06:50–06:58 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`045ee5948` 发布目录 + DB 备份 `winbid-9f468fedb-20261030065722.sql.gz`） |

环境门禁：用户指令「部署到生产系统」，确认框选择「确认：生产环境 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `045ee5948` 追平至 `origin/main` 最新 `9f468fedb`，增量 **113 个 commit**，覆盖 PR `!2887`–`!2926` 共 **40 个**。

**与上次生产部署的关键差异：本次零数据库迁移**（部署前后 DB 最高版本均为 `V1225`），schema 层面无风险。

代码内容与测试环境第 251 次部署（`0b3854b45`，含 E2E 真实复核）**完全一致**——HEAD 相比测试验证版仅多 1 个部署报告文档 commit，`backend/` 与 `src/` 零差异。

---

## 变更范围与 PR

增量范围 `045ee5948..9f468fedb` 共 113 个 commit，覆盖 PR：`!2887 !2888 !2889 !2890 !2891 !2892 !2893 !2894 !2895 !2896 !2897 !2898 !2899 !2900 !2901 !2902 !2903 !2904 !2905 !2906 !2907 !2908 !2909 !2910 !2911 !2912 !2913 !2914 !2915 !2916 !2917 !2918 !2919 !2920 !2921 !2922 !2923 !2924 !2925 !2926`。

commit 类型分布：`fix(scoreparse)` 22、`feat(scoreparse)` 5、`test(scoreparse)` 5、`fix(settings)` 2、`feat(settings)` 1、`fix(compliance)` 2、`fix(sidecar)` 1、`fix(baidu-ocr)` 1，其余为 `docs(release)` / `docs(wiki)` 治理文档。

核心主题：

- **scoreparse（评分解析，绝对主线）**：
  - 百度 OCR 作为评分主干接管表格抽取与 2D 拓扑网格修复（!2913）
  - 第一性原理双轨双保险评分解析引擎与文本数学核验（!2923）
  - 百度智能云表格识别旁路对照管线与模型适配；`BaiduTableArtifactEnricher` 补齐 PDF 跨页续表与大单元格表格证据
  - 跨页续表合并容忍偶发噪声表 + 空间拓扑边框判定
  - 系列根治修复：DOCX 嵌套纵向合并打分表行闭合与标书原文回填、跨行评分项原文恢复、单数字评分项识别缺陷与人工复核采纳死锁、项目 1392 百度 OCR 85 分倒退至 100 分全闭环、大模型漏抽吞分确定性对账回补（95 分问题）、非末列规则断裂跨页逻辑行缝合（95 分问题）
  - 人工复核体验：允许人工确认/核对后采纳评分候选；保留跨表自动发布的证据门槛
- **settings（系统设置）**：前端可视化配置百度表格 OCR 引擎及连通性测试（方案 A）；修复 `defineModel` 解构绑定导致的 v-model 编译失败并补齐前端构建门禁与 SFC 规范
- **compliance（标书质量核查）**：2 个误判修复
- **sidecar**：按表头动态识别规则列与跨页逻辑行精准缝合

### 改动文件分布

| 目录 | 文件数 | 增删 |
|---|---|---|
| `backend` | 116 | +6110 / -237 |
| `docs` | 19 | +2796 / -0 |
| `src`（前端） | 14 | +553 / -34 |
| `document-converter-sidecar` | 4 | +344 / -23 |
| `.wiki` | 13 | +334 / -12 |
| `specs` | 3 | +53 / -0 |
| `scripts` / `FRONTEND.md` / `RELIABILITY.md` / `decisions.tsv` | 4 | +28 / -3 |
| **合计** | **173** | **+10218 / -309** |

### 数据库迁移（本次 0 个）

无新增迁移文件、无回滚脚本变更。DB 部署前最高版本 `V1225`，部署后仍为 `V1225`。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate`（首次） | ❌ `Detected applied migration not resolved locally: 1225` — 服务器 `/tmp/migration-mysql/` 目录过时（缺 `V1225`），**经验 #10 复发** |
| Step 1 修复 | 用 `tar | ssh` 同步本地 `migration-mysql/` 至服务器 `/tmp/migration-mysql/`（284 个 V*.sql，含 V1225）；注：生产服务器无 `rsync`，`rsync` 方案报 `status 127` |
| Step 1：重跑 validate | ✅ `VALIDATE OK - all checksums match`（285 migrations） |
| Step 2：DB 已应用版本 vs 源码最新 | ✅ DB 最高 `V1225` = 源码最高 `V1225`，零 pending |
| Step 3：`remote-deploy.sh` 激活前内置 validate | ✅ 二次 validate 通过 |

打包阶段校验：jar 内 Flyway 迁移版本无重复（284 个 V*.sql 与源码一致）。

---

## 部署步骤

| 阶段 | 动作 | 结果 |
|---|---|---|
| 0 | 环境门禁确认（prod） | ✅ |
| 1 | 早操三连（`sync-env.sh` + 本地门禁自检 + git wrapper） | ✅ 7/7 门禁就绪，`agent/trae-init` = `origin/main`（`9f468fedb`） |
| 2 | 服务器现状核对 | 旧版本 `045ee5948`，服务 active，health UP；增量 113 commit / 零迁移 |
| 3 | 打包 `RELEASE_ID=9f468fedb VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1` | ✅ `apiBaseUrl=""`、`obsEnabled=true`、Detail chunk `.upload(` = 2、sidecar schemaVersion=2.2 |
| 4 | scp 发布包 + `remote-deploy.sh` | ✅ DB 备份 → validate → 前端激活 → 停服 → 原子换 jar → 启服 |
| 5 | 健康检查 | ✅ 连续 3/3，16 次尝试内通过，无 Kafka readiness 长延迟 |
| 6 | 上一版本 assets 保留 | ✅ 从 `045ee5948/frontend/assets` `cp -rn` 回填 |
| 7 | 部署报告 | 本文件 |

部署中脚本自动处理事项：前端目录存在 5 个非 jetty 属主的历史条目，`remote-deploy.sh` 自动 `sudo chown -R` 修复后完成激活（符合经验 #12 防护）。

---

## 验证结果

### 后端 / API

本地 Mac 直连 `:8080` 不可达（已知问题，第 31 次已沉淀：TCP 通但 HTTP 无响应，疑似防火墙/WAF 只放通 80/443），Smoke 改走 `:80` 与域名入口，并在服务器本机经 Nginx `:8080` 补验，两处结果一致。

| 检查 | 期望 | 实际（`:80` / 服务器本机 `:8080`） |
|---|---|---|
| `/actuator/health` | 200 UP | ✅ 200 / 200 |
| `/actuator/health/readiness` | 200 UP | ✅ 200 / 200 |
| `POST /api/auth/login`（空 body） | 400 参数校验 | ✅ 400 / 400 |
| `/api/projects`（未认证） | 401/403 | ✅ 401 / 401 |
| `/api/integration/crm/health`（未认证） | 401 | ✅ 401 / 401 |

### 前端

| 检查 | 实际 |
|---|---|
| `http://172.16.10.149/`（80） | ✅ 200 |
| `http://172.16.10.149/login`（服务器本机 8080） | ✅ 200 |
| `https://winbid.ehsy.com/` | ✅ 200 |
| 入口 asset | `assets/index-y3uloFVf.js`，与发布包 / `deployed-release.json` 一致 |

### 数据库

| 检查 | 实际 |
|---|---|
| `flyway_schema_history` 最高版本 | ✅ `V1225`，`success=1`（部署前后一致，零新迁移） |
| 部署前备份 | ✅ `winbid-9f468fedb-20261030065722.sql.gz`（6.3 MB） |

### 配置清理检查

`sudo grep -E "SHOW_DETAILS|DEBUG|TRACE" /etc/xiyu-bid/backend.env` 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次引入）。

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | release 目录 `/opt/xiyu-bid/releases/045ee5948` |
| 后端回滚 | `cp /opt/xiyu-bid/releases/045ee5948/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` |
| 前端回滚 | 用 `045ee5948/frontend` 覆盖 `/srv/www/xiyu-bid`（旧 hash 资源已在本次部署中保留） |
| DB 回滚 | 本次零迁移，无需 DB 回滚；部署前全量备份已就绪 |
| 是否执行 | ❌ 未执行（验证全通过） |

---

## 经验沉淀应用情况

1. **同源构建**：显式传 `VITE_API_BASE_URL=`，产物 `apiBaseUrl=""`。
2. **OBS 直传双保险**：显式传 `VITE_OBS_ENABLED=true`，产物校验 `obsEnabled=true` + Detail chunk `.upload(` = 2。
3. **DB 备份前置**：`DB_BACKUP_COMMAND` 在激活新 jar 之前执行。
4. **上一版本 assets 保留**：`cp -rn` 回填，规避旧标签页 hash 资源 404。
5. **生产 Smoke 走 80/域名**：`:8080` 从开发宿主不可达为已知行为，不再误判为故障。
6. **测试先行验证**：本次部署代码与测试环境第 251 次（E2E 真实复核）完全一致，符合「测试环境全量验证后再上生产」纪律。

### 本次新发现（建议沉淀）

- **经验 #10（服务器 `/tmp/migration-mysql/` 过时）首次导致 validate 阻断**：此前记录为"不影响 validate"，本次实测 DB 已应用 `V1225` 而 `/tmp` 缺失时，`flyway-repair-runner.sh validate` 会报 `applied migration not resolved locally` 并 FAILED。**建议**：将"部署前同步 `/tmp/migration-mysql/`"从建议项升级为 SOP 强制步骤（本次已顺手完成同步，服务器 `/tmp` 当前与源码一致）。
- **生产服务器无 `rsync`**：`rsync` 上传报 `child exited with status 127`（远端未安装）。`tar -czf - | ssh 'tar -xzf -'` 管道是可用替代。

---

## 风险提示

1. 本次改动高度集中在**评分解析链路**（百度 OCR 主干、双轨双保险引擎、跨页表格缝合），虽经测试环境第 251 次 E2E 复核，仍建议业务侧针对性回归：评分解析抽屉、人工复核采纳流程、项目 1392 类百度 OCR 场景。
2. `sentryEnabled=false` 与上一版本一致（未注入前端 DSN），前端错误追踪仍依赖服务端日志与 Sentry 后端集成。
3. 设置页新增百度 OCR 引擎可视化配置入口，生产上需确认 OCR 凭据配置正确后再启用该引擎。

---

## 部署确认清单

- [x] 目标环境经用户显式确认（prod / `172.16.10.149`）
- [x] 基线确认（`origin/main` = `9f468fedb`，工作区干净）
- [x] Flyway validate 通过（部署前 + 激活前两次）
- [x] DB 全量备份完成（6.3 MB）
- [x] 发布包产物校验（同源 / OBS / 无重复迁移 / sidecar 契约 2.2）
- [x] 健康检查 3/3 通过
- [x] 零迁移，DB 版本一致性验证通过（V1225）
- [x] API + 前端 Smoke 全通过（80 端口 + 域名 + 服务器本机 8080）
- [x] 上一版本 assets 保留
- [x] 回滚锚点就绪
- [ ] 业务侧功能回归（待业务确认：评分解析链路）
