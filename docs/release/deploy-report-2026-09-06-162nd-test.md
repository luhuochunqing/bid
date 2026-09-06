# 第 162 次部署报告（test 环境）

**日期**：2026-09-06
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`a698cc79f`
**部署时间**：21:07 - 21:15 CST
**状态**：✅ 成功

## 部署概览

- 增量 66 个 commit（27 条 PR + docs 回填 + auto-merge），**无新增 Flyway 迁移**（DB 保持 V1211）。

- 本次核心为 **compliance 标书质检 25 项语义路线图**（docs/exec-plans/active/2026-09-05-bid-quality-25-items-semantic-roadmap.md）：

  - **PR !2567–!2578**：标书质检前附表与偏离表两阶段契约、资质匹配/一票否决/资格评审、签章完备性与表格填写完整性、报价一致性与商务条款一致性、评分项响应与澄清响应、财务三表平衡勾稽与进度计划合理性、偏离表结构化提取与响应性深度核验等 12 个深度重构 PR

  - **PR !2552**：Feature 062 评分模型 100 分自洽与前端解释层算术对齐

  - **PR !2566**：商机事件推送 projectLeaderName/customerType 补标讯兜底并对齐预测逻辑

- Flyway 预检通过（272 项迁移全量校验，checksum 全匹配），无新迁移待应用。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `a698cc79f`（origin/main，锚点分支 ff-only 同步，零差异）          |
| 前次部署      | `4bc19117c`（2026-09-05 第 161 次部署）                      |
| 增量 commit | 66 个（`4bc19117c..a698cc79f`）                         |
| 新增迁移      | 无（DB 保持 V1211）                                            |
| 工作区状态     | 干净（本地 compliance 修改已 stash，部署完成后恢复）                 |
| 早操三连      | ff-only 同步至 `a698cc79f`，git 包装器激活                    |

## 改动范围（66 个增量 commit / 27 条 PR）

核心 PR 清单：

| PR    | 描述                                                        |
| ----- | --------------------------------------------------------- |
| !2578 | fix(compliance): 修复长距离单字否定语义错位漏洞并完善分层否定约束（对抗审讯加固） |
| !2577 | feat(compliance): 偏离表结构化提取与响应性深度核验重构 + 清理未使用方法         |
| !2576 | docs(wiki): 补 PR !2574 跟进修复的 log 回填条目                     |
| !2575 | feat(compliance): 财务三表平衡勾稽与进度计划逻辑合理性深度重构                 |
| !2574 | fix(compliance): 评分/澄清占位正则三漏检修复与证据跳目录定位                   |
| !2573 | fix(compliance): 签章反假收紧——纯模板落款说明文字降级人工复核                 |
| !2572 | feat(compliance): 签章完备性与表格填写完整性深度重构                       |
| !2571 | feat(compliance): 报价一致性分项算术与商务条款一致性深度重构                   |
| !2570 | feat(compliance): 评分项响应与澄清响应两阶段穿透与反假升级                   |
| !2569 | feat(compliance): 资质匹配等级层级判定、领域冲突防护与第一阶段确定性穿透            |
| !2568 | feat(compliance): 一票否决与资格评审两阶段契约与反假重构                     |
| !2567 | feat(compliance): 标书质检前附表与偏离表两阶段契约与反假重构                   |
| !2566 | fix(opportunity): 商机事件推送 projectLeaderName/customerType 补标讯兜底并对齐预测逻辑 |
| !2564 | fix(compliance): PR !2561 财务三表检查跟进修复                            |
| !2561 | feat(compliance): 财务三表检查（FinancialStatementCheckPolicy）        |
| !2552 | fix(recommend): 100 分自洽模型重构与前端解释层算术对齐                    |

关键代码：`compliance/domain/policy/` 下 25+ 个 CheckPolicy/Extractor/Calculator 纯核心类（资质匹配、一票否决、签章完备性、报价一致性、财务三表、偏离表等），`compliance/service/` 下 RequirementSectionParser 等；`casework` 评分模型 100 分自洽；`opportunity` 商机事件推送字段补全。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → 初始失败（`Detected applied migration not resolved locally: 1211`），根因为服务器 `/tmp/migration-mysql/` 目录过时缺少 V1211。同步最新迁移文件后重新 validate → **通过**，272 migrations all checksums match。

**Step 2**：DB 最新 applied = V1211（`success=1`，2026-09-05 16:13 已应用），源码最高版本 = V1211 → **无新增迁移待应用**。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                                             |
| -------- | ---------------------------------------------------------------------------------------------------------------- |
| 21:05    | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）+ stash 本地 compliance 修改                                              |
| 21:07    | 早操三连（sync-env ff-only + check-git-wrapper）；服务器现状检查（4bc19117c UP，obsEnabled=true）                       |
| 21:08    | Flyway 预检：同步 /tmp/migration-mysql/ + validate 通过（272 迁移）；DB 版本确认 V1211 = 源码 V1211，无新增迁移                  |
| 21:08    | 本地打包：vite build（3443 modules）+ FlywayMysqlContainerTest 通过 + mvn clean package（3220 文件）+ jar 迁移无重复 + OBS 启用 |
| 21:11    | 产物校验：release-metadata.json obsEnabled=true, apiBaseUrl="" 同源；上传 archive + remote-deploy.sh 到服务器                     |
| 21:12    | 远程部署：Flyway validate 通过 → 前端资源激活 → 停止服务 → 原子更新 jar → 启动服务 → 健康检查等待                            |
| 21:14    | 健康检查通过（145 次尝试，Kafka SDK 启动延迟属已知行为）；前端 index-DPTvZOH1.js 与 release 一致                              |
| 21:15    | Smoke 测试全通过（health/readiness 200, login 400, projects 403, 前端 200）；保留上一版本 assets；配置检查（SHOW_DETAILS=always 保留） |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP   |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录 (/login)     | ✅ 200      |
| OBS 直传启用           | ✅ obsEnabled=true, .upload( 调用数=2 |
| Flyway 迁移          | ✅ 272 项 checksum 全匹配，无新迁移 |
| 上一版本 assets 保留     | ✅ 已 cp -rn 4bc19117c assets |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留） |

## 回滚信息

- **回滚锚点**：前一版本 `4bc19117c`（/opt/xiyu-bid/releases/4bc19117c/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/winbid-a698cc79f-*.sql.gz`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/4bc19117c/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/4bc19117c/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无 schema 变更，无需 DB 回滚

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（Step 1 validate → Step 2 DB 版本对比 → Step 3 remote-deploy 内置）
- ✅ 服务器 /tmp/migration-mysql/ 过时问题（同步最新迁移文件后 validate 通过）
- ✅ Kafka SDK readiness 延迟（145 次健康检查尝试，未急于回滚）
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验）
- ✅ 前端 hash 资源跨版本 404 防护（部署后保留上一版本 assets 24h）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）

## 风险提示

1. **compliance 25 项质检为大规模重构**：涉及 25+ 个纯核心类与大量测试，建议在测试环境重点验证标书质检各检查项（资质、签章、报价、财务、偏离表等）的输出正确性
2. **无新增 Flyway 迁移**：本次为纯代码变更，DB schema 无变化，回滚风险低
3. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受，生产部署前需确认

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = a698cc79f）
- [x] 服务器现状检查（4bc19117c UP）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] Smoke 测试（health/readiness/login/projects/前端）
- [x] 上一版本 assets 保留
- [x] 临时调试配置检查
- [x] 部署报告生成
