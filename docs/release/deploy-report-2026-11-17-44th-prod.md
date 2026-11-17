# 第 44 次生产环境部署报告 — 2026-11-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `0ba490bcd` |
| 基线 commit | `0ba490bcd`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `5cbbf4925`（2026-11-17 08:31:49 CST 激活） |
| 部署时间 | 2026-11-17 21:13:41 CST（激活），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + 域名 Smoke + Sidecar 升级全通过） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/5cbbf4925` + 本次部署前 DB 备份） |

环境门禁：用户通过问答卡片明确选择「确认：生产环境 172.16.10.149」。

---

## 部署概览

从生产版本 `5cbbf4925` 追平至 `origin/main` 最新 `0ba490bcd`，增量 **28 个 commit / 10 个 PR（!3038–!3047）/ 136 个文件（+4380 / -270）**。

- **1 个数据库迁移**：`V1226__add_win_date_to_project_result.sql`（`project_result` 新增 `win_date DATE NULL`，非破坏性，配套回滚脚本 `U1226`），应用时间 2026-11-17 21:13:49，`success=1`。
- **Sidecar 有变更**：SHA `b52dabc9…` → `5a1f94aa…`，schemaVersion `2.2`；已触发镜像重建 + 容器重启，`/health` 返回 `up`。
- **前端产物**：同源构建（`apiBaseUrl=""`），入口 `assets/index-B-LiQuIj.js`；`obsEnabled=true`（Detail chunk `.upload(` = 2），`sentryEnabled=true`。
- **旧资源保留**：`/srv/www/xiyu-bid/assets/` 共 726 个文件（本次新增 + 历史保留 `5cbbf4925`、`df970b534` 两版），`._*` 残留 0。
- 发布包 156M，远端 SHA-256 与本地一致（`42b9174b…`）。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !3038 | 第 43 次生产部署收尾（`._*` 传播链修复 + 部署报告 + E2E 验收） |
| !3039 | scoreparse：修复历史失败重复弹错与技术码直出 |
| !3040 | scoreparse：修复项目 1124 跨页来源闭合与重复计分 |
| !3041 | 第 279 次测试环境部署报告 |
| !3042 | scoreparse：修复项目 1133 目录方案误识别及评分规则边界 |
| !3043 | casework：修复配送能力误推荐等保证书 |
| !3044 | scoreparse：修复业绩日期误计并补齐标书核查硬条件 |
| !3045 | casework：补齐案例推荐诊断日志与合入记录 |
| !3046 | project：结果确认合同信息新增中标时间字段（仅中标必填，不回传 CRM） |
| !3047 | 第 280 次测试环境部署报告 |

> 说明：本批次以「评分解析（scoreparse）」边界修复 + 「案例推荐（casework）」相关性修复为主，配套 1 个结果确认阶段字段新增与测试环境部署报告。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | ✅ 分支 `agent/trae-init`，HEAD = `origin/main` = `0ba490bcd`，工作区干净 |
| 早操三连 | ✅ `sync-env.sh` 本地门禁 7/7 通过；`check-git-wrapper.sh` 通过 |
| 服务器健康（部署前） | `/actuator/health` UP（db / redis / sidecar / aiProvider / jwt 全 UP），服务 active |
| 部署前版本 | `deployed-release.json` → `releaseId=5cbbf4925` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | `V1225`（2026-10-27 应用），`success=1`，无 failed 记录 |
| 新增迁移 | `V1226`（ADD COLUMN，非破坏性），回滚脚本 `U1226` 存在 |
| 磁盘 | `/` 使用 27G / 99G（29%） |

---

## 部署执行

标准打包 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）：

```bash
RELEASE_ID="0ba490bcd" \
VITE_API_BASE_URL= VITE_OBS_ENABLED=true \
VITE_SENTRY_DSN=<生产 DSN> VITE_SENTRY_ENVIRONMENT=production VITE_SENTRY_TRACES_SAMPLE_RATE=0.1 \
COPYFILE_DISABLE=1 bash scripts/release/package-release.sh
```

- 打包：`mvn clean -DskipTests package` BUILD SUCCESS；jar 内迁移版本无重复；OBS 校验 `.upload(` = 2
- 上传：`xiyu-bid-release-0ba490bcd.tar.gz`（156M）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- 远程部署链路：DB 预备份 → Sidecar 升级（镜像重建 + 容器重启）→ AI Provider 守卫 → Flyway validate → 前端 assets 合并 → 停服务 → jar 原子替换 → systemd 启动 → 健康检查 → 前端一致性校验
- DB 预备份：`winbid-0ba490bcd-20261117211329.sql.gz`，7,594,653 字节（7.3M），`gzip -t` 完整性通过

---

## 验证结果

| 验证项 | 结果 |
|---|---|
| 健康检查（服务器内） | ✅ 连续 3/3 通过（第 15 次尝试），`xiyu-bid-backend` active/running |
| `/actuator/health`（域名） | ✅ HTTP 200 UP |
| `/actuator/health/readiness`（域名） | ✅ HTTP 200 |
| 前端 `/`、`/login` | ✅ HTTP 200，入口 `assets/index-B-LiQuIj.js` 与 release 一致 |
| `POST /api/auth/login`（空 body） | ✅ HTTP 400（参数校验生效，路由可达） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（鉴权生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401（鉴权生效） |
| 静态资源缓存 | ✅ `Cache-Control: public, immutable` + `max-age=31536000` + gzip |
| Flyway 迁移 | ✅ `V1226` 已应用（`add win date to project result`，`success=1`，21:13:49） |
| Sidecar | ✅ 容器升级完成，`/health` = `up`，schemaVersion 2.2 |
| 部署记录 | ✅ `deployed-release.json` → `releaseId=0ba490bcd`，`activatedAt=2026-11-17T13:13:41Z` |
| `._*` 残留 | ✅ 0 个（!3038 的清理修复在本次部署中生效） |

> 说明：本机通过 IP:8080 直连生产会超时（部署前同样现象，属本机网络/代理环境问题），Smoke 统一经 `https://winbid.ehsy.com` 执行。

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版后端 jar | `/opt/xiyu-bid/releases/5cbbf4925/backend/app.jar` |
| 上一版前端 | 同目录 `frontend/`（旧 hash 资源已保留在公共目录） |
| 上一版 Sidecar | 回滚需重新解包旧 release 的 sidecar 包并重建镜像 |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-0ba490bcd-20261117211329.sql.gz`（7.3M） |
| 迁移回退 | `U1226`（需手工执行 `ALTER TABLE project_result DROP COLUMN win_date`） |
| 回滚可行性 | 高（仅 1 个非破坏性新增列，后端旧版本不受影响） |
| 是否执行 | 否，未触发 |

---

## 经验沉淀应用情况

| 经验 | 本次执行情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置校验全部执行 |
| 生产构建三要素（同源 + OBS + Sentry） | ✅ 首次打包漏传 `VITE_SENTRY_DSN`（`sentryEnabled=false`），产物校验发现后重新打包修复 |
| macOS `._*` 残留（COPYFILE_DISABLE） | ✅ 打包设置 `COPYFILE_DISABLE=1`，部署后残留 0 |
| 前端跨版本 assets 保留 | ✅ remote-deploy 自动保留最近 2 个已激活版本（`ASSETS_RETAIN_RELEASES=2`），共 726 个 assets |
| `SYSTEMCTL_SUDO=true` | ✅ 已显式传入，服务重启正常 |
| 部署后临时配置检查 | ✅ 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留），无 DEBUG/TRACE |
| 部署报告纪律 | ✅ 本报告 |

---

## 风险提示

- 本次以评分解析边界修复为主（!3039 / !3040 / !3042 / !3044），建议业务方用真实项目走一遍「实际评分 → 评分抽屉 → 证据/引用」，重点验证跨页来源闭合与业绩日期计分。
- `V1226` 新增 `win_date` 为可空列，历史数据不受影响；结果确认「中标」时前端必填校验需业务侧回归确认（!3046）。
- 未执行生产 E2E 验收（本次未要求，且无生产会话凭据）；如需按第 43 次标准做逐项 E2E，请提供验收项目与操作授权。
- 后端 `/etc/xiyu-bid/backend.env` 保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策，非本次引入）。

---

## 部署确认清单

- [x] 环境门禁确认（用户选择生产 172.16.10.149）
- [x] 基线 = `origin/main`，工作区干净
- [x] Flyway validate 通过（285 迁移，DB=V1225 无 failed）
- [x] DB 预备份完成（7.3M，gzip 完整性通过）
- [x] Sidecar 升级 + 健康检查通过
- [x] 后端 jar 原子替换 + systemd 重启成功
- [x] 健康检查连续 3/3 通过
- [x] 域名 Smoke 通过（health / readiness / login / projects / crm / 前端）
- [x] 前端入口与 release 一致（`index-B-LiQuIj.js`）
- [x] OBS 直传 + Sentry 门禁通过（`obsEnabled=true`、`sentryEnabled=true`）
- [x] 旧版前端资源保留（726 个 assets），`._*` 残留 0
- [x] `V1226` 迁移已应用（success=1）
- [x] 回滚锚点就绪
- [x] 生产 E2E 验收（2026-11-17 21:20–21:47，既有项目只读/重算，未新建项目与标讯）

---

## 生产 E2E 验收（2026-11-17 21:20–21:47）

环境：`https://winbid.ehsy.com`，系统管理员会话；未新建任何项目/标讯，仅对既有项目执行重新解析、预判刷新与实际打分。

### 项目 1133「评分项识别不出来」（!3042）

| 验收动作 | 结果 |
|---|---|
| 部署前存量（任务 248，旧版本产出） | 12 项 100 分，但第 12 项「仓储情况」规则尾部混入「合计 100」 |
| 点击「重新解析」并等待新任务（21:23:31 完成） | ✅ `decisionStatus=AUTO_APPROVED`，`fallbackReason` 为空，单一方案（第七章 评分标准），无目录伪方案歧义 |
| 评分项识别 | ✅ 12 项、合计 100 分；权重序列 30/10/6/10/12/10/3/3/3/3/5/5 与招标原件一致 |
| 主观/客观分类 | ✅ 6 客观（权重 66）+ 6 主观（权重 34）；「总体组织实施方案」「商品覆盖程度」「项目团队」均已归为主观项（修复前误归客观） |
| 规则边界污染 | ✅ 「仓储情况」规则尾部不再包含「合计 100」 |
| 正式生效 | ✅ 自动采纳为 12 项生效评分项（生效评分项 12 项） |
| 阶段二实际打分 | ⚠️ 任务完成（21:40:24），但投标文件「辽宁矿投0908-补充.docx」含 1 处 EMF 矢量图无法解析（`coverage PARTIAL`，failedUnit=1），证据守卫按设计返回 6 项失败 + 6 项主观待评审，界面显示中文原因「可核验证据不足，请人工确认」，未泄露原始技术码，重开抽屉无重复弹错（!3039 行为符合预期） |

### 项目 1120「业绩年份误算」（!3044）

| 验收动作 | 结果 |
|---|---|
| 打开评分解析 | ✅ 「供应商业绩」旧预判状态 `STALE`（“知识库预判已过期或缺少计算记录”）——规则封印生效，旧 5/5 结果不再展示 |
| 刷新知识库预判（`POST /score-parse/estimates/refresh`） | ✅ 重算完成（21:41:05），要求 3 项、命中 319 项、预计 5/5，状态「待确认」（首次报价截止日上界仍待按原文核验） |
| 日期筛选 | ✅ 依据明确输出「已筛选条件：签约日期不早于 2023-01-01」 |
| 2023 年前业绩排除 | ✅ 知识库核验：#4=2021-12-30、#7=2020-05-27、#8=2021-01-01、#9=2022-01-01 均不在命中列表；边界 #10=2023-01-01 在命中列表（下限含等号） |
| 数量目标 | ✅ 由旧「要求 5 项」修正为「要求 3 项」（基础 1 项 + 每增 20 分，满分 3 项） |

### 项目 44「配送能力误推荐等保证书」（!3043）

| 验收动作 | 结果 |
|---|---|
| 45 配送能力评价推荐 | ✅ 233 条结果，Top1 为「签约物流月度发运承诺」45%，其后为全疆物流配送承诺 44%、物流配送及仓储能力 41%、仓储物流及配送服务 40%，全部为物流/配送主题 |
| 案例 331（等保三级证书） | ✅ 不在任何结果中（旧算法为 40 分第一），`/api/cases/331` 确认分类为「网站安全等级防护能力」、状态 ACTIVE |
| 关键词过滤 | ✅ 关键词「等保」「等级保护」下案例 331 仍不出现在配送能力项结果中 |
| 观察（需业务确认） | ⚠️ 对「43 网站安全等级防护能力」（明确要求等保三级证书）评分项，案例 331 同样未出现（UI 与 API 均为 0 命中）；与修复声明“保留明确证书要求”存在张力，建议产品确认是否为预期 |

### 数据影响

- 1133：新解析任务覆盖旧候选并自动采纳 12 项正式评分项；实际打分写入 6 项失败/6 项主观待评审结果。
- 1120：供应商业绩预计得分由 5/5（旧规则，含 2023 年前业绩）重算为 5/5（仅 2023-01-01 起命中），状态「待确认」。
- 44：仅读取与推荐查询，无写入。
