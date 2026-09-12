# 第 176 次部署报告（test 环境）

**日期**：2026-09-12
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`de943b6be`
**部署时间**：23:30 - 23:45 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `128ae529a`（含 PR !2633），**无新增 Flyway 迁移**。
- 主要变更：
  - **人工复核保留按钮防重复提交**（PR !2634，origin `trae/review-retain-disable`）：`fix(scoreparse)` 保留按钮提交后置灰，防止重复插入 `score_parse_review` 审计记录。
  - **招标评分表错位合计修复**（PR !2635）：`fix(scoreparse)` 修复错位合计导致解析为空 + 汇总行判定策略增强。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `de943b6be`（origin/main，本地 HEAD 已一致）             |
| 前次部署      | `128ae529a`（2026-09-12，含 PR !2633 sidecar 鉴权修复）    |
| 增量 commit | `128ae529a..de943b6be`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `de943b6be`，工作区干净 |

## 增量 commit 变更（`128ae529a..de943b6be`）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `367f47757` | PR !2634 | fix(scoreparse): 人工复核保留按钮提交后置灰防重复提交 |
| `1a1ebf03e` | PR !2634 | docs(wiki): 人工复核保留按钮防重复提交知识沉淀 |
| `9b3c0e608` | PR !2634 | auto-merge（review-retain-disable 合入） |
| `f79a36aac` | PR !2635 | fix(scoreparse): handle misaligned summary rows |
| `de943b6be` | PR !2635 | fix: 修复招标评分表错位合计导致解析为空 |

## 变更文件范围（15 个，+232/-14）

- `backend/src/`（3）：`DeterministicScoreFactExtractor`、`ScoreCompletenessPolicy`、`ScoreTableSummaryPolicy`
- `backend/src/test/`（3）：上述三类的对应测试
- `scripts/eval-scoreparse/`（3）：错位合计案例 `case-misaligned-summary-row`（.md + .expected.json + README）
- 前端（2）：`useScoreParseDrawer.js`（新增 reviewSubmitted 状态）+ `ScoreParseDrawer.vue`（按钮 `:submitting` 绑定）
- 前端测试（1）：`useScoreParseDrawer.spec.js`
- 文档：`.wiki/`（3）

## Flyway 预检结果

- **Step 1**：服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（274 migrations）
- **Step 2**：DB 已应用最高版本 `V1214` = 仓库源码最高 `V1214`，两侧一致，无漂移
- **Step 3**：`remote-deploy.sh` 内置 validate 通过（`VALIDATE OK`）

## 部署步骤

1. 环境门禁：用户确认部署到 test（172.16.38.78）
2. 早操：`sync-env.sh` 在锚点分支被守卫拦截 → 手动 `git fetch origin main` 确认 HEAD = origin/main = `de943b6be`，工作区干净
3. 服务器现状：`deployed-release.json` = `128ae529a-api8080`（13:25:42Z），health UP
4. Flyway 预检 3 步法通过（无新迁移）
5. 本地打包：`RELEASE_ID=de943b6be VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS（37.2s）
6. 产物校验：`obsEnabled=true`、jar 内 Flyway 迁移无重复、前端入口 `index-D22aUD38.js`、归档 155M
7. scp 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）→ 后端重启成功（PID 2098），健康检查通过（3/3，81 attempts）
8. 前端资源保留：从上一版本 release 目录 `128ae529a-api8080/frontend/assets` 手动 `cp -rn` 保留旧 assets（277 个文件）

## 验证结果

| 检查项 | 结果 |
| ----- | --- |
| `/actuator/health` | ✅ 200（db/redis/sidecar/jwt/aiProvider 全 UP） |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空 body） | ✅ 400（参数校验错误，路由正常） |
| `GET /api/projects` | ✅ 403（需认证） |
| `GET /api/integration/crm/health` | ✅ 401（需认证） |
| 前端 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| 前端入口 JS | ✅ `assets/index-D22aUD38.js`（与 release 一致） |

> 本轮 readiness 立即返回 200，未出现 Kafka SDK 延迟 503 的历史已知行为。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（backend.env）——**历史部署中 3 次由用户决定保留的已知白名单项**，非本次引入，按既有决定保留。

## 回滚信息

- 回滚锚点：上一版本 release 目录 `/opt/xiyu-bid/releases/128ae529a-api8080/`（前端 assets 已保留在 `/srv/www/xiyu-bid/assets/`）
- 回滚命令：`remote-deploy.sh` 重跑上一个 Release ID；数据库无迁移变更，无回滚风险
- 回滚 Owner：本次未执行，待人工确认

## 风险提示

- 无数据库结构变更，无数据回滚风险
- 前端防重复提交变更影响 score_parse_review 审计写入，建议回归项目详情页「人工复核」面板（重复点击保留按钮验证只写一次记录）
- scoreparse 汇总行判定变更（错位合计）建议回归 1381 评审用例 + 错位合计样本

## 部署确认清单

- [x] 环境门禁（test 172.16.38.78 已确认）
- [x] 早操同步（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 打包 + 产物校验（OBS 启用、迁移无重复）
- [x] 健康检查 + Smoke 全部通过
- [x] 前端资源保留
- [x] 部署报告生成
