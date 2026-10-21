# 第 28 次生产环境部署报告 — 2026-10-21

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `adf66925c` |
| 基线 commit | `adf66925c`（`origin/main`） |
| 前一版本 | `1f33f73e0`（2026-10-01 第 27 次生产） |
| 部署时间 | 2026-10-21 18:54–18:56 CST |
| 状态 | ✅ 成功（健康 + Smoke 全通过） |
| 回滚状态 | 未执行，锚点就绪（`1f33f73e0`） |

---

## 部署概览

本次从生产版本 `1f33f73e0` 追平至 `origin/main` 最新 `adf66925c`（增量 95 个 commit，约 20 天，均已在测试环境多轮验证后随 main 合入）。**本次增量不含数据库迁移**（DB schema 保持 V1220），风险面集中在前端与后端业务逻辑，不含破坏性 schema 变更。

部署经环境门禁确认（用户指令"部署升级到正式系统"，在确认框选择「确认：生产环境 172.16.10.149」），符合生产授权红线。

---

## 变更范围与 PR

增量范围 `1f33f73e0..adf66925c` 共 95 个 commit（其中 64 个非 merge 提交），覆盖 PR `!2805`–`!2832` 共 28 个。

主题分布（非 merge 提交）：`fix` 36、`docs` 17、`test` 6、`refactor` 3、`chore` 1。

核心主题：

- **scoreparse（评分解析）稳定性修复**：行引文无外侧分隔符时的完整规则恢复（!2828/!2829）、Markdown 转义价格公式原文绑定（!2826）、已识别评分项展示与人工复核入口恢复（!2824）、章节归属与无序号分组评分表解析（!2823）、评分汇总误判确定性回补（!2816）
- **review-ext P1/P2 单项合计语义回归修复**：真实总分被删、小计单项满分误识别、跨行分句回归钉（round6–9）
- **casework/立项**：来源回填原子校验扫描快照防过期写入；标讯转项目补齐 `bidOpenTime` 带入，修复立项提交校验失败（!2820/!2821）；保护立项已有字段不被覆盖
- **标讯时间格式化**：修复选择 `0点0分` 被改写为 `23:59:59`（!2819）
- **工程工具链**：`gitee-pr-helper.sh` merge 405 误报改判幂等 + create_pr 失败路径重定向缺陷（!2818）；适配 Gitee 仓库更名为 `bid-xiyu`（!2831）
- **清理**：移除误入库的 Python 字节码 `claude/__pycache__`（!2832）
- 其余为测试部署报告（第 222–227 次）与 Wiki/教训库回填

改动文件分布：`backend/src/test` 36、`.wiki` 25、`src/`（前端）22、`specs` 21、`backend/src/main` 18、`docs` 11、`scripts` 8、其它 8。

---

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| 迁移源目录同步（`/tmp/migration-mysql`） | ✅ 部署前补齐 288 个 SQL 文件（已知坑点，服务器目录缺失） |
| `flyway-repair-runner.sh validate` | ✅ VALIDATE OK - all checksums match（280 migrations） |
| DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1220 = 源码最新 V1220，**无 pending 新迁移** |
| `remote-deploy.sh` 内置 validate（激活前） | ✅ 通过 |
| jar 内迁移文件重复版本校验 | ✅ 无重复（279 个 `V*.sql`） |

---

## 部署步骤

1. 环境门禁：`ENV=prod`，目标 `172.16.10.149`，用户确认。
2. 早操：工作区干净，`HEAD=origin/main=adf66925c`（0 ahead / 0 behind）；git wrapper 检查通过。因处于锚点分支 `agent/trae-init`，创建原地任务分支 `agent/trae/prod-deploy-9th`。
3. 服务器现状：原 release `1f33f73e0`（2026-10-01 激活），服务 active，健康全 UP。
4. Flyway 预检 3 步（补齐 `/tmp/migration-mysql` 后 validate 通过，无新迁移）。
5. 本地打包：`RELEASE_ID="adf66925c" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`（BUILD SUCCESS，1m07s）。
6. 产物校验：`obsEnabled=true`、`apiBaseUrl=""`、`sentryEnabled=false`；jar `git.commit.id=adf66925c…`、`dirty=false`；前端入口 `assets/index-BpcZvMz6.js`；包 161M。
7. 上传 `/opt/xiyu-bid/incoming/`（archive + `remote-deploy.sh`）。
8. 远程部署：`remote-deploy.sh` + `SYSTEMCTL_SUDO=true` + `DB_BACKUP_COMMAND`（含 DB 备份 → validate → 激活 → 重启 → 健康检查）。
9. 前端旧 assets 保留：从 `1f33f73e0` 目录 `cp -rn` 旧 assets（当前 277 个），防旧标签页跨版本 404。
10. 验证与 Smoke（见下）。

---

## 验证结果

### 1. 基础服务健康（服务器本地 18080）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ UP / HTTP 200 |
| `/actuator/health/readiness` | ✅ UP（无 Kafka readiness 延迟，健康检查 3/3 通过，共 15 次探针） |
| MySQL / Redis / Sidecar / AI Provider | ✅ 全部 UP |
| 后端服务 `xiyu-bid-backend` | ✅ active (running)，主 PID 28834 |
| 重启后 error 级日志（`journalctl -p err`） | ✅ 无条目 |

### 2. 迁移应用验证

本次无新增迁移。`flyway_schema_history` 最新成功版本仍为 V1220（2026-10-01 应用），与源码一致。

### 3. API / 前端 Smoke（经生产域名 `https://winbid.ehsy.com`）

| 端点 | 期望 | 实际 |
|---|---|---|
| `GET /actuator/health` | 200 UP | ✅ 200 |
| `GET /actuator/health/readiness` | 200 UP | ✅ 200 UP |
| `POST /api/auth/login`（空体） | 400 参数错误 | ✅ 400 |
| `GET /api/projects` | 401 需认证 | ✅ 401 |
| `GET /api/integration/crm/health` | 401 需认证 | ✅ 401 |
| `GET /` | 200 | ✅ 200 |
| `GET /login` | 200 | ✅ 200 |
| 前端入口 hash | 与 release 一致 | ✅ `assets/index-BpcZvMz6.js` |
| `/assets/` 强缓存 + gzip | `public, immutable` + gzip | ✅ `Cache-Control: public, immutable`、`Content-Encoding: gzip` |

> 注：从本地 Mac 直连 `http://172.16.10.149:8080` 全部返回 HTTP 000（本机到生产子网 HTTP 端口无路由，SSH 可达），属**本地网络连通性问题**；服务器本地 `127.0.0.1:8080` 全部 200 且入口 hash 一致，外部验证改走生产域名全部通过。

---

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既定决策（第 13/14/15 次部署起保留，用于健康详情可观测性），非本次临时配置，**保留**。
- 无 `DEBUG` / `TRACE` 残留。
- `/tmp/migration-mysql` 已在部署前同步为最新（288 个 SQL）。

---

## 回滚信息与预案

- 后端回滚锚点：`/opt/xiyu-bid/releases/1f33f73e0/backend/app.jar`。
- 前端回滚锚点：`/opt/xiyu-bid/releases/1f33f73e0/frontend/`。
- 本次发布前 DB 备份：`/opt/xiyu-bid/db-backups/winbid-adf66925c-20261021185424.sql.gz`（5.7 MB）。
- 本次无新迁移，无需执行 U 前缀回滚脚本。
- 回滚命令：
  ```bash
  ssh jetty@172.16.10.149 'sudo /opt/xiyu-bid/bin/remote-deploy.sh rollback'
  ```
- 状态：回滚锚点就绪，暂未执行。

---

## 经验沉淀应用情况

| 经验 | 本次执行 |
|---|---|
| Flyway 预检 3 步法 | ✅ 执行，且补齐服务器缺失的 `/tmp/migration-mysql`（skill 坑点 10） |
| `SYSTEMCTL_SUDO=true` | ✅ 传入，服务正常 restart（坑点 7） |
| 生产同源构建 `VITE_API_BASE_URL=` | ✅ `apiBaseUrl=""` 校验通过（坑点 3） |
| OBS 直传显式开启 | ✅ `VITE_OBS_ENABLED=true` + 产物 `obsEnabled=true` 双校验（坑点 9） |
| 前端旧 hash 资源保留 | ✅ 从上一版本 `cp -rn` 旧 assets（坑点 17） |
| Mac 代理干扰 | ✅ 全部 curl 加 `--noproxy '*'`；IP 端口无路由时改走域名 |
| `COPYFILE_DISABLE=1` 防 `._*` 残留 | ✅ 打包时设置 |
| jar 内迁移版本无重复 | ✅ package-release 内置校验通过 |

---

## 风险提示

1. **无新迁移**，DB 无需回滚脚本；但 scoreparse 逻辑改动较大（36 个 `fix` 提交），建议上线后重点观察评分解析页与人工复核入口。
2. 前端与后端同源构建，浏览器旧标签页可能仍持有旧 chunk；已保留上一版本 assets，建议提示用户刷新一次。
3. 生产数据库备份仅保留在服务器 `/opt/xiyu-bid/db-backups/`，如长期留档需另行归档。

---

## 部署确认清单

- [x] 生产环境服务正常运行（PID 28834，18080 health 200，readiness UP）
- [x] 工作区与 `origin/main` 基线确认（`adf66925c`，0/0）
- [x] 上一版本前后端回滚锚点就绪（`1f33f73e0`）
- [x] 数据库在激活前已执行全量备份
- [x] Flyway validate 预检通过（280 migrations，checksum 全匹配，无 pending）
- [x] 环境门禁 + 生产授权已取得（用户主动发起并确认）
- [x] OBS 直传开启、前端同源构建、Smoke 全部通过（域名入口）
- [x] 上一版本 assets 已保留（防旧标签页跨版本 404）
- [x] 无临时 debug 配置残留
- [x] 本部署报告已生成
