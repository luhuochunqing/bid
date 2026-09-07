# 第 165 次部署报告（test 环境）

**日期**：2026-09-07
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`56a522a1a`
**部署时间**：14:51 - 15:00 CST
**状态**：✅ 成功

## 部署概览

- 增量 3 个 commit（去重后），**无新增 Flyway 迁移**。
- 本次为上一版 scoreparse 精度的**后续收口** + MemU 规则恢复 + 部署报告文档：
  - `c5370ff87` / `c5f0e4740`（PR !2589 gemini + !2590 codex followup）：scoreparse 评分项**全量保全白名单**与**价格公式全量识别**（Task 1）+ 知识沉淀 §342，及 sectionDim miss 上报与价格词表单一真相源。
  - `dc62be4b8`（PR !2588 trae）：恢复 MemU 长期记忆检索规则到 AGENTS.md 契约。
  - `30de88817`（PR !2587 trae）：第 164 次部署报告（文档）。
- 无 Flyway 迁移文件变更，主要为评分解析算法增强与治理配置。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `56a522a1a`（origin/main，基准仓库 main 分支同步，零差异）       |
| 前次部署      | `a234171b6`（2026-09-07 第 164 次部署）                   |
| 增量 commit | `a234171b6..56a522a1a`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 干净                                                 |
| 早操三连      | main 分支同步至 `56a522a1a`，git 包装器检查通过               |

## 增量 commit 变更

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `c5f0e4740` | PR !2589 (gemini) | feat(scoreparse): 评分项全量保全白名单与价格公式全量识别 (Task 1) + 知识沉淀 §342 |
| `f4ed8dd0e` | merge | auto-merge PR !2589 |
| `c5370ff87` | PR !2590 (codex) | fix(scoreparse): PR2589后续：sectionDim miss 上报与价格词表单一真相源 |
| `56a522a1a` | merge | auto-merge PR !2590 |
| `dc62be4b8` | PR !2588 (trae) | chore(agents): 恢复 MemU 长期记忆检索规则到 AGENTS.md 契约 |
| `30de88817` | PR !2587 (trae) | docs(release): 第 164 次测试环境部署报告 |

> 注：`4a57fbf99`（merge PR !2587）在增量范围内，其内容为 docs 报告合并。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，273 migrations all checksums match。

**Step 2**：本次增量 `a234171b6..56a522a1a` **无迁移文件变更**（`git diff --name-only` 为空）。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

部署后 DB 状态不变，仍为 V1212 最新，无需新迁移验证。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 14:51    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）；早操基线同步（基准仓库 main 同步至 56a522a1a，git 干净） |
| 14:51    | 服务器现状检查（a234171b6 UP）；增量 3 commit；迁移文件变更 = 无；Flyway 预检 validate 通过（273 迁移）      |
| 14:53    | 本地打包：前端 vite build + mvn clean package + sidecar 打包（VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 14:56    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2；jar 内 272 迁移无重复 |
| 14:57    | 上传 archive + remote-deploy.sh（SYSTEMCTL_SUDO=true, DB 备份自动执行）→ Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 14:58    | 健康检查通过（145 次尝试，Kafka SDK 启动延迟属已知行为）；部署记录写入已确认 |
| 15:00    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, 前端 200）；配置检查；DB 备份确认   |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP（含 readiness UP） |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-CZzSS-6E.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（部署记录确认）, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 无新增迁移，273 迁移 validate 通过 |
| DB 备份              | ✅ winbid-56a522a1a-20260907145713.sql.gz (12.7MB) |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `a234171b6`（/opt/xiyu-bid/releases/a234171b6/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-56a522a1a-20260907145713.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/a234171b6/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/a234171b6/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无迁移变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比确认无迁移 + remote-deploy 内置）
- ✅ Kafka SDK readiness 延迟（145 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + 部署记录二次确认）
- ✅ 前端 hash 资源跨版本 404 防护（remote-deploy.sh 内置原资源保留）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）

## 风险提示

1. **scoreparse 后续增强**：本次为评分项全量保全白名单 + 价格公式识别 + 价格词表单一真相源（PR !2589/!2590）。涉及解析算法增强，建议在测试环境用历史标书抽查白名单保全与价格公式识别结果符合预期。
2. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
3. **无新增迁移**：本次零 schema 变更，Flyway 风险最低。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操基线同步 + git 包装器检查
- [x] 基线确认（HEAD = origin/main = 56a522a1a）
- [x] 服务器现状检查（a234171b6 UP）
- [x] Flyway 预检 3 步法（validate + 无迁移 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/前端）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成