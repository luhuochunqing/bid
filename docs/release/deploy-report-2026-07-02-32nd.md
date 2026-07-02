# 第 32 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 32 次 |
| 部署日期 | 2026-07-02 08:08 (CST) |
| Release ID | `88b99604e-api8080` |
| 目标服务器 | `172.16.38.78` (winbid-01.test) |
| 前一次部署 | `d32227acf-api8080`（第 31 次，2026-07-01 15:06） |
| 部署类型 | 纯代码部署（无 Flyway 迁移） |
| 健康检查 | ✅ 第 1 次尝试即通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| 主工作区 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/deploy-32nd-report`（任务分支，基于 `agent/trae-init` 锚点创建） |
| 部署 commit | `88b99604e251f61c1b2c225d5ba661098e209d99` |
| 前一次 commit | `d32227acf`（第 31 次） |
| 增量 commit 数 | 27 个（含 10 个 PR merge + 子 commit） |
| Flyway 迁移文件变更 | 无 |
| GitHub 镜像同步 | ✅ 部署后已同步（27 commit → 0） |

## PR 列表（10 个合入 PR）

| PR | Commit | 描述 |
|---|---|---|
| !1506 | `35ccc3cec` | docs(release): 第 31 次部署报告 |
| !1461 | `03bc3a3bb` | fix(CO-443): closure approved stage nav shows completed |
| !1507 | `229328711` | CO-461: 评标阶段评标文件字段必填 |
| !1508 | `55f55f0c2` | fix(CO-460): 项目内任务审核后交付物/附件丢失——后端治本 + 前端加固 |
| !1509 | `2ef32dc60` | CO-459: CA借用审批入口 + 状态枚举 + 权限优化 |
| !1510 | `3e161f623` | fix-wecom-outbound-log-status-mislabel: Automation skill-progression-map update |
| !1511 | `b858152f3` | fix(organization): 角色未匹配的 OSS 用户不再强制禁用，enabled 反映在职状态 |
| !1512 | `180c91b7b` | fix(release): 打包脚本注入 VITE_SENTRY_DSN 环境变量 |
| !1513 | `88b99604e` | CO-458: 任务提交审核时交付物和完成情况必填校验 |
| !1514 | `b6fd59e03` | CO-448: 缴纳投标保证金任务增加保证金字段 |

## 改动范围

本次部署为纯代码部署，无 DB 迁移文件变更。主要改动涵盖：

1. **CO-458**: 任务提交审核时交付物和完成情况必填校验（含纯核心 TaskTransitionPolicy、应用层集成、前端 API 原子化、UI 必填视觉、useTaskSubmissionValidation composable 抽取）
2. **CO-448**: 缴纳投标保证金任务增加保证金字段（TaskDepositFields 组件 + TaskForm 集成）
3. **CO-459**: CA借用审批入口 + 状态枚举 + 权限优化（CaBorrowPermissionChecker + CaBorrowService 重构）
4. **CO-460**: 项目内任务审核后交付物/附件丢失——后端治本 + 前端加固
5. **CO-461**: 评标阶段评标文件字段必填（EvaluationEvidencePolicy）
6. **CO-443**: 提交结项申请后进度导航栏显示结项为进行中
7. **企微 outbound_log 修复**: 修复 DEAD_LETTER/DROP 误标为 SKIPPED+NOT_BOUND 的 outbound_log
8. **组织 OSS 用户修复**: 角色未匹配的 OSS 用户不再强制禁用，enabled 反映在职状态
9. **打包脚本修复**: 打包脚本注入 VITE_SENTRY_DSN 环境变量
10. **第 31 次部署报告**: 文档归档

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - 191 migrations validated, all checksums match |
| Step 2: DB 版本对比 | ✅ DB 最新版本 V1127（2026-07-01 15:13:50 安装），与源码一致，无新迁移 |
| Step 3: remote-deploy 内置 | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 早操三连（sync-env.sh + check-git-wrapper.sh，7 项门禁全通过）
2. ✅ 确认基线（HEAD = 88b99604e = origin/main，GitHub 镜像落后 27 commits）
3. ✅ 服务器现状检查（前次 release d32227acf，health UP，9 组件全 UP）
4. ✅ Flyway 预检 3 步法通过
5. ✅ 本地打包（RELEASE_ID=88b99604e-api8080，VITE_API_BASE_URL= 同源构建，137M archive）
6. ✅ 产物校验（jar 内 190 个 V*.sql 无重复，前端入口 assets/index-BEd6ciKE.js）
7. ✅ 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true）
8. ✅ 健康检查（第 1 次通过，9 组件 UP，readinessState UP）
9. ✅ Smoke 测试（8/8 通过）
10. ✅ GitHub 镜像同步（27 commits → 0，两边 main = 88b99604e）

## 验证结果

### 后端健康检查

```
status: UP
components:
  aiProvider: UP (doubao, deepseek-v3-2-251201)
  db: UP (MySQL, isValid())
  diskSpace: UP (42.8GB free / 105.5GB total)
  jwt: UP (HMAC-SHA256, 64 bytes, STRONG)
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP (6.2.19)
  sidecar: UP (http://localhost:8000, reachable)
```

### Smoke 测试（8/8 通过）

| # | 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| 3 | `POST /api/auth/login` (empty body) | 400 (验证错误) | 400 | ✅ |
| 4 | `GET /api/projects` (no auth) | 403 (需认证) | 403 | ✅ |
| 5 | `GET /api/integration/crm/health` | 401 (需认证) | 401 | ✅ |
| 6 | `GET /` (frontend root) | 200 | 200 | ✅ |
| 7 | `GET /login` | 200 | 200 | ✅ |
| 8 | Frontend entry js | assets/index-BEd6ciKE.js | assets/index-BEd6ciKE.js | ✅ |

> Smoke 测试通过 SSH 内部访问（`ssh jetty@172.16.38.78 'curl http://127.0.0.1:8080/...'`），绕过 Mac HTTP_PROXY 502 问题（第 16 条经验）。
> 登录 smoke 因 admin 密码未授予而跳过，用 400/403/401 替代验证接口路由（第 4 条经验）。

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 commits | 27 |
| 部署后落后 commits | 0 |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| Gitee main HEAD | `88b99604e251f61c1b2c225d5ba661098e209d99` |
| GitHub main HEAD | `88b99604e251f61c1b2c225d5ba661098e209d99` |
| 状态 | ✅ 完全一致 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 前一次 release ID | `d32227acf-api8080` |
| 前一次 release 目录 | `/opt/xiyu-bid/releases/d32227acf-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-88b99604e-<timestamp>.sql.gz` |
| 回滚方式 | 如需回滚，激活 `d32227acf-api8080` release 目录的 jar + 前端，重启后端服务 |

## 经验沉淀应用情况

本次部署应用了以下历史经验：

| 经验 # | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 部署前主动跑 validate + DB 版本对比 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试 400/403/401 替代 | ✅ admin 密码未知，用替代验证 |
| #5 GitHub 镜像同步 | ✅ 部署后立即同步 |
| #6 临时调试配置清理 | ✅ 无临时配置引入 |
| #8 systemctl sudo | ✅ SYSTEMCTL_SUDO=true（默认值） |
| #15 Flyway 防护体系 | ✅ 15 项防护全部生效 |
| #16 Mac HTTP_PROXY 502 | ✅ Smoke 通过 SSH 内部访问绕过 |

## 风险提示

1. **无新 Flyway 迁移**：本次部署纯代码变更，DB 风险极低
2. **CO-458 任务提交审核校验**：新增提交审核时交付物和完成情况必填校验，可能影响现有任务提交流程，需关注用户反馈
3. **CO-459 CA 借用审批**：新增 CA 借用审批入口和权限，需验证权限矩阵正确性
4. **CO-460 交付物丢失修复**：后端治本 + 前端加固，需验证审核后交付物/附件不再丢失
5. **组织 OSS 用户 enabled 修复**：角色未匹配的 OSS 用户不再强制禁用，需监控用户登录行为

## 部署确认清单

- [x] 早操三连通过（sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = 88b99604e）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（137M archive）
- [x] 产物校验通过（190 迁移文件无重复，前端入口一致）
- [x] 远端部署成功（release 88b99604e-api8080 激活）
- [x] 健康检查通过（9 组件 UP，readiness UP）
- [x] Smoke 测试 8/8 通过
- [x] GitHub 镜像同步（两边 main 一致）
- [x] 部署报告生成

## 部署总结

第 32 次生产部署顺利完成，无 Flyway 迁移、无回滚、无异常。本次部署主要交付了 CO-458 任务提交审核校验、CO-448 保证金字段、CO-459 CA 借用审批、CO-460 交付物丢失修复、CO-461 评标文件必填、CO-443 结项导航修复等 6 个业务需求，以及企微 outbound_log 修复、组织 OSS 用户 enabled 修复、打包脚本 Sentry DSN 注入修复等 3 个基础设施修复。所有验证项全部通过，生产环境运行正常。
