# 第 177 次部署报告（test 环境）

**日期**：2026-09-13
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`289e26baa`
**部署时间**：10:20 - 10:28 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `de943b6be`（含 PR !2634/!2635），**无新增 Flyway 迁移**。
- 主要变更：
  - **多表评分标准自动拆分**（PR !2637，spec 062 方案 A）：`feat(scoreparse)` 按 `sourceTableId` 分组，独立校验每张评分表的合计分值证据，落库多组 `score_item`/`score_result`。解决文档含多张独立评分表时系统无法确定唯一总分、显示为空的问题。
  - **人工复核保留终态与重复提交修复**（PR !2636/!2638，origin `review-retain-disable`/`trae`）：保留按钮提交后置灰防重复插入审计记录 + 人工复核保留终态澄清。
  - **多表归一化告警误报修复**（`26ab9cecb`）：避免多表文档触发错误的归一化告警并错误计算总权重。
  - **保留无总分表来源映射**（`9943ef81f`）：`fix(scoreparse)` 保留无总分表的来源映射。
  - **slop 清理**（`4397f6ce4`）：删除无调用方的 `ShadowResult` 便捷构造器与 `SplitView`。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `289e26baa`（origin/main，本地 HEAD 已一致）             |
| 前次部署      | `de943b6be`（2026-09-12，含 PR !2634/!2635 scoreparse 修复） |
| 增量 commit | `de943b6be..289e26baa`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `289e26baa`，工作区干净 |

## 增量 commit 变更（`de943b6be..289e26baa`，共 18 个）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `a20d5a...` 系列 | — | spec 062 规格文档 + Red 用例（T002/T003/T007）等筹备 |
| `5a7f2f9bc` | PR !2637 | feat(scoreparse): 多表评分标准自动拆分（spec 062 方案 A） |
| `630fbe2ad`/`9943ef81f` | PR !2638/!2637 | fix(scoreparse): 人工复核保留终态 + 保留无总分表来源映射 |
| `26ab9cecb` | PR !2637 | fix(scoreparse): 多表归一化告警误报修复 + 落库层测试补全 |
| `4397f6ce4` | PR !2637 | refactor(scoreparse): 删除多余构造器/SplitView（slop 清理） |
| `9bc8f51f8`/`3ce2723af` | PR !2637 | docs(scoreparse): 多表拆分金标夹具 + Wiki/知识沉淀 |
| `289e26baa` | PR !2637 | auto-merge（score-multi-table-split 合入） |

## 变更文件范围

- `backend/src/main/java`：`ScoreTableSplitPolicy`（新增，按 `sourceTableId` 分组拆分）、`StructuredTableExtractionHandler`（归一化告警修复）、`ScoreParseRowSourceMapping`（保留无总分表来源映射）、`ShadowResult` 清理等 scoreparse 相关。
- `backend/src/test`：多表拆分 Red/落库层测试补全（scoreparse 测试套件）。
- `scripts/eval-scoreparse/`：金标夹具。
- 文档：`.wiki/` 回填 + spec 062 文档。

## Flyway 预检结果

- **Step 1**：服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（274 migrations）
- **Step 2**：DB 已应用最高版本 `V1214` = 仓库源码最高 `V1214`，两侧一致，无漂移
- **Step 3**：`remote-deploy.sh` 内置 validate 通过（`VALIDATE OK`）

## 部署步骤

1. 环境门禁：用户确认部署到 test（172.16.38.78）
2. 早操：`sync-env.sh` 在锚点分支被守卫拦截 → 手动 `git fetch origin main` 确认 HEAD = origin/main = `289e26baa`，工作区干净
3. 服务器现状：`deployed-release.json` = `de943b6be`（15:38:22Z），health UP
4. Flyway 预检 3 步法通过（无新迁移）
5. 本地打包：`RELEASE_ID=289e26baa VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS（38.8s）
6. 产物校验：`obsEnabled=true`、jar 内 Flyway 迁移无重复、前端入口 `index-DzwHnHa1.js`、归档 155M
7. scp 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）→ 后端重启成功（PID 8770），健康检查通过（3/3，80 attempts）
8. 前端资源保留：当前 release 目录即最新（无跨版本的旧 hash 资源需保留）

## 验证结果

| 检查项 | 结果 |
| ----- | --- |
| `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空 body） | ✅ 400（参数校验错误，路由正常） |
| `GET /api/projects` | ✅ 403（需认证） |
| `GET /api/integration/crm/health` | ✅ 401（需认证） |
| 前端 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| 前端入口 JS | ✅ `assets/index-DzwHnHa1.js`（与 release 一致） |

> 本轮 readiness 立即返回 200，未出现 Kafka SDK 延迟 503 的历史已知行为。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（backend.env）——**历史部署中 3 次由用户决定保留的已知白名单项**，非本次引入，按既有决定保留。
- 无临时 DEBUG/TRACE 配置残留。

## 回滚信息

- 回滚锚点：上一版本 release 目录 `/opt/xiyu-bid/releases/de943b6be/`
- 回滚命令：`remote-deploy.sh` 重跑上一个 Release ID；数据库无迁移变更，无回滚风险
- 回滚 Owner：本次未执行，待人工确认

## 风险提示

- 无数据库结构变更，无数据回滚风险。
- 多表评分标准拆分（spec 062）影响 scoreparse 落库逻辑，建议回归：多张独立评分表的文档解析后各表总分独立展示、汇总行判定、归一化告警不误报。
- 金标夹具已入库（`scripts/eval-scoreparse/`），可跑 eval 验证拆分结果。

## 部署确认清单

- [x] 环境门禁（test 172.16.38.78 已确认）
- [x] 早操同步（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 打包 + 产物校验（OBS 启用、迁移无重复）
- [x] 健康检查 + Smoke 全部通过
- [x] 前端资源一致性
- [x] 配置清理检查
- [x] 部署报告生成