# 第 29 次生产环境部署报告 — 2026-10-25

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `f3ff316f2` |
| 基线 commit | `f3ff316f2`（`origin/main`） |
| 前一版本 | `adf66925c`（2026-10-21 第 28 次生产） |
| 部署时间 | 2026-10-25 11:58–12:00 CST |
| 状态 | ✅ 成功（健康 + 迁移 + Smoke 全通过） |
| 回滚状态 | 未执行，锚点就绪（`adf66925c`） |

---

## 部署概览

本次从生产版本 `adf66925c` 追平至 `origin/main` 最新 `f3ff316f2`，增量 **110 个 commit**（4 天），覆盖 PR `!2834`–`!2867` 共 22 个。

**与上两次生产部署的关键差异：本次含 4 个数据库迁移**（V1221–V1224，DB 从 V1220 升至 V1224），因此风险评估以 schema 变更为主，回滚锚点必须包含数据库备份。

部署经环境门禁确认（用户指令"部署到生产环境"，在确认框选择「确认：生产环境 172.16.10.149」），符合生产授权红线。

---

## 变更范围与 PR

增量范围 `adf66925c..f3ff316f2` 共 110 个 commit（非 merge 提交 75 个），覆盖 PR `!2834`–`!2867` 共 **34 个**：`!2834 !2835 !2836 !2837 !2838 !2839 !2840 !2841 !2842 !2843 !2844 !2845 !2846 !2847 !2848 !2849 !2850 !2851 !2852 !2853 !2854 !2855 !2856 !2857 !2858 !2859 !2860 !2861 !2862 !2863 !2864 !2865 !2866 !2867`。

主题分布（非 merge 提交）：`fix` 31、`docs` 34、`chore` 4、`test` 3、`refactor` 1、`feat` 1。

核心主题：

- **scoreparse（评分解析）**：项目 22 供应商业绩阶梯计分与匹配范围修复（!2855、!2867 系列 8 个提交）；支持泛表头多级管道符维度解构并扩充财务/信用分类（!2860）
- **casework（案例库）**：`task_id` 列类型纠正为 `VARCHAR(36)`（!2861 / V1223）；存量 `OTHER` 标签案例穿透补偿分（!2856）；案例推荐前端误判与结果隐藏修复（!2863）；应答片段摘要标题显示缺陷（!2859）；来源路径页码噪声清洗与存量回填（!2865 / V1224）；案例沉淀并发加固（!2857）
- **auth**：`JwtParser` 单例化，消除 JJWT `ServiceLoader` 并发竞争，并加强 `RateLimitFilter` 旁路降级（!2864）
- **compliance**：标书质量核查误判纠正（!2858）
- **工程工具链**：受管 worktree 脱离共享 gitdir 的守卫（!2866）；Gitee 仓库更名适配、`gitee-pr-helper` 幂等修复
- 其余为测试环境部署报告（第 232–234 次）与 Wiki / 教训库回填

改动文件分布：`backend/src/main` 73、`backend/src/test` 47、`src/`（前端）32、`specs` 20、`.wiki` 18、`docs` 11、`scripts` 10、其它 4。合计 215 文件（+8610 / -835）。

### 数据库迁移（本次 4 个）

| 版本 | 说明 | 风险 |
|---|---|---|
| V1221 | `tender_reminder_logs.reminder_setting_id` 放宽为可空 | 低（加宽约束） |
| V1222 | 新建 `case_precipitation_task` 表 | 低（新增表） |
| V1223 | 纠正 `case_precipitation_task.task_id` 列类型为 `VARCHAR(36)` | 中（建表后立即纠型，表为新建、数据为空） |
| V1224 | 清洗 `knowledge_case` 1254/1255 来源页码；回填 1256–1261 来源标题/路径/文档名 | 中（数据更新，影响 8 行固定 ID，可逆） |

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| 迁移源目录同步（`/tmp/migration-mysql`） | ✅ 部署前补齐 V1221–V1224（已知坑点：服务器目录不含新迁移） |
| 部署前 `flyway-repair-runner.sh validate` | ✅ VALIDATE OK（280 migrations，checksum 全匹配） |
| DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1220，源码最新 V1224 → 预期 4 个 pending |
| `remote-deploy.sh` 内置 validate（激活前） | ✅ 通过（284 migrations） |
| jar 内迁移文件重复版本校验 | ✅ 无重复（含 V1221–V1224） |

---

## 部署步骤

```
1. 环境门禁：ENV=prod，目标 172.16.10.149，用户确认
2. 早操三连：sync-env.sh + git wrapper 检查通过
3. 基线锁定：git checkout --detach f3ff316f2（保证发布包代码严格等于 origin/main）
4. 服务器现状：旧 release adf66925c，health 全 UP，DB V1220
5. 补齐服务器 /tmp/migration-mysql（4 个新迁移）
6. 本地打包：RELEASE_ID=f3ff316f2 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1
   产物校验：obsEnabled=true、apiBaseUrl=""、jar git.commit.id=f3ff316f2（dirty=false）、前端入口 index-CkGSFnxa.js、包 155M
7. scp 上传 archive + remote-deploy.sh 至 /opt/xiyu-bid/incoming/
8. remote-deploy.sh（SYSTEMCTL_SUDO=true + DB_BACKUP_COMMAND）
   → 解包 → DB 备份 → Sidecar 指纹比对 → Flyway validate → 前端激活 → 原子替换 jar → 写 deployed-release.json → 重启 → 健康检查 3/3
9. 前端旧 assets 保留：从 adf66925c 目录 cp -rn（当前 assets 279 个）
```

---

## 验证结果

### 1. 基础服务健康

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP |
| `/actuator/health/readiness` | ✅ 200 UP（无 Kafka readiness 延迟，探针 15 次内 3/3 连续通过） |
| MySQL / Redis / Sidecar / AI Provider / JWT | ✅ 全部 UP |
| 后端服务 `xiyu-bid-backend` | ✅ active (running)，主 PID 29419 |
| 重启后 ERROR 级日志（`journalctl --since -6min`） | ✅ **0 条** |

### 2. 迁移应用验证

| 版本 | success | installed_on |
|---|---|---|
| 1221 | 1 | 2026-10-25 11:59:59 |
| 1222 | 1 | 2026-10-25 11:59:59 |
| 1223 | 1 | 2026-10-25 11:59:59 |
| 1224 | 1 | 2026-10-25 11:59:59 |

DB 版本已追平源码 V1224。

### 3. API / 前端 Smoke（经生产域名）

| 端点 | 期望 | 实际 |
|---|---|---|
| `GET /actuator/health` | 200 | ✅ 200 |
| `GET /actuator/health/readiness` | 200 | ✅ 200 |
| `POST /api/auth/login`（空体） | 400 | ✅ 400 |
| `GET /api/projects` | 401 | ✅ 401 |
| `GET /api/integration/crm/health` | 401 | ✅ 401 |
| `GET /` | 200 | ✅ 200 |
| `GET /login` | 200 | ✅ 200 |
| 前端入口 hash | 与 release 一致 | ✅ `assets/index-CkGSFnxa.js` |
| `/assets/` 强缓存 + gzip | immutable + gzip | ✅ `max-age=31536000, public, immutable` + `Content-Encoding: gzip` |

> 注：本地 Mac 直连 `http://172.16.10.149:8080` 无 HTTP 路由（返回 HTTP 000），与第 28 次一致属本地网络问题；外部验证统一走生产域名，服务器本地 `127.0.0.1:8080` 校验前端一致性通过。

---

## ⚠️ 事故与纠正：激活前 DB 备份实际为 20 字节

**现象**：`remote-deploy.sh` 输出 `DB backup done`，退出码 0，但备份文件仅 20 字节（空 gzip）。

**根因**：`DB_BACKUP_COMMAND` 由 `remote-deploy.sh` 通过 `bash -lc` 执行，**不继承**我这条 SSH 会话中 `source /etc/xiyu-bid/backend.env` 的未导出变量。于是 `${DB_HOST:-127.0.0.1}` 回落到本机、`DB_PASSWORD` 为空 → `mysqldump: Got error 2003 ... (111)`；但 `mysqldump | gzip > file` 的管道退出码取 gzip 状态（成功），`set -e` 与 `&& echo` 均未拦截，失败被完全掩盖。

**纠正**：

1. 立即用 `set -a; source /etc/xiyu-bid/backend.env; set +a` 重做备份 → **`winbid-f3ff316f2-post-20261025.sql.gz`，5.7 MB，EXIT=0**（与第 28 次生产备份同量级，确认连的是正式库）。
2. 风险评估：V1221–V1223 为约束放宽 / 新建表 / 建表后纠型（无存量数据受影响）；V1224 仅按固定 ID 更新 8 行 `knowledge_case` 来源字段。首次迁移已成功且服务健康，数据风险可控。
3. 纪律固化（后续部署必做）：
   - `DB_BACKUP_COMMAND` **必须在命令串内部自带** `set -a; source /etc/xiyu-bid/backend.env; set +a`，不得依赖外层 shell 变量；
   - 备份后**必须校验文件大小**（生产正常量级约 5–6 MB）并检查 `mysqldump` 退出码（用 `PIPESTATUS`，或先落未压缩文件再 gzip）。

---

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既定决策（第 13/14/15 次起保留），非本次临时配置，**保留**。
- 无 `DEBUG` / `TRACE` 残留。
- Sidecar 源码指纹由 `2d5b2175…` 更新为 `24c0104f…`（schemaVersion 2.2），容器已按内容指纹自动升级。

---

## 回滚信息与预案

- 后端回滚锚点：`/opt/xiyu-bid/releases/adf66925c/backend/app.jar`
- 前端回滚锚点：`/opt/xiyu-bid/releases/adf66925c/frontend/`
- 数据库备份：
  - 部署后补做：`/opt/xiyu-bid/db-backups/winbid-f3ff316f2-post-20261025.sql.gz`（5.7 MB）
  - 激活前备份：`/opt/xiyu-bid/db-backups/winbid-f3ff316f2-20261025115943.sql.gz`（**20 字节，无效**，见上节）
  - 上一个有效历史备份：`/opt/xiyu-bid/db-backups/winbid-ce55d7d09-20260719124954.sql.gz`（1.5 MB）
- 迁移回滚脚本：`backend/src/main/resources/db/rollback/migration-mysql/` 下的 `U1221`–`U1224`
- 回滚命令：`ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'`
- 状态：回滚锚点就绪，暂未执行。

---

## 经验沉淀应用情况

| 经验 | 本次执行 |
|---|---|
| 环境门禁 + 生产授权 | ✅ 展示环境信息后由用户明确确认 |
| Flyway 预检 3 步法 + `/tmp/migration-mysql` 补齐 | ✅ 部署前补齐 4 个新迁移，validate 2 次通过 |
| `SYSTEMCTL_SUDO=true` | ✅ 传入，服务正常 restart |
| 生产同源构建 `VITE_API_BASE_URL=` | ✅ `apiBaseUrl=""` 校验通过 |
| OBS 直传显式开启 | ✅ `VITE_OBS_ENABLED=true` + `obsEnabled=true` + Detail chunk `.upload(`=2 三重校验 |
| 前端旧 hash 资源保留 | ✅ 从 `adf66925c` `cp -rn`，assets 279 个 |
| `COPYFILE_DISABLE=1` 防 `._*` 残留 | ✅ 打包时设置（远端仅出现 macOS xattr 扩展头提示，无文件污染） |
| jar 内迁移无重复 | ✅ package-release 内置校验通过 |
| Mac 代理干扰 | ✅ 全部 curl 加 `--noproxy '*'` |
| **DB 备份有效性校验** | ⚠️ 首次失败（管道掩盖），已补做并作为新纪律固化 |

---

## 风险提示

1. **V1224 数据更新**：按固定 ID 更新 8 行 `knowledge_case`。迁移本身可逆——`U1224` 可精确还原（1254/1255 恢复原始来源字段，1256–1261 置 NULL）；但激活前无有效备份，若需回退数据必须依赖该回滚脚本。
2. **scoreparse 与 casework 改动量大**（31 个 fix 提交）：建议上线后重点观察评分解析页、案例推荐与案例沉淀任务列表。
3. 浏览器旧标签页可能持有旧 chunk，已保留上一版本 assets；建议提示用户刷新一次。
4. 备份文件仅留在服务器 `/opt/xiyu-bid/db-backups/`，长期留档需另行归档。

---

## 部署确认清单

- [x] 生产环境服务正常运行（PID 29419，health 200，readiness UP，0 ERROR）
- [x] 基线锁定为 `origin/main` `f3ff316f2`（发布包 `dirty=false`）
- [x] 上一版本前后端回滚锚点就绪（`adf66925c`）
- [x] Flyway 预检通过（284 migrations，checksum 全匹配）
- [x] 4 个新迁移 V1221–V1224 应用成功（`success=1`）
- [x] 数据库备份已**在部署后补做**并校验（`winbid-f3ff316f2-post-20261025.sql.gz`，5.7 MB，EXIT=0）
- [x] 环境门禁 + 生产授权已取得
- [x] OBS 直传开启、前端同源构建、Smoke 全部通过（域名入口）
- [x] 上一版本 assets 已保留（防旧标签页跨版本 404）
- [x] 无临时 debug 配置残留
- [x] 本部署报告已生成
