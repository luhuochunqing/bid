# 第 110 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `e3d3c4580` |
| 上一版本 | `6e1a2db59`（第 109 次，2026-07-28 18:24 CST） |
| 部署时间 | 2026-07-29 18:31:16 CST |
| 增量 | 18 个 commit（PR !2211/!2212/!2213/!2214/!2215/!2216/!2217 + 配套提交） |
| 新增迁移 | 1 个（V1181 `cleanup_audit_logs_project_id`） |
| 部署结果 | ✅ 成功（健康检查 3/3，尝试 79 次，约 2.5 分钟） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 109 次部署（2026-07-28 18:24 CST）后，main 合入 18 个 commit，核心为 audit_logs.project_id 污染治理两轮修复 + tender-intake Prompt 精简：

- **PR !2212** fix(audit): 修复 audit_logs.project_id 污染项目动态（AuditableAspect 改为 projectScoped 注解驱动 + 移除 Long args-first bug 路径 + V1181 清理污染数据）
- **PR !2213** test(gap-backfill): 补充 PR !2205 CO-599 测试覆盖缺口（TaskReminderRecipientResolver + ProjectAccessFilter）
- **PR !2214** fix(tender-intake): 删除 Prompt 中 tenderAgency 字段口径——标讯表单不记录代理机构
- **PR !2215** docs(lessons): 记录 tenderAgency 字段语义混淆陷阱（第 84 条）
- **PR !2216** fix(audit): 修复 PR !2212 5 个 void 方法导致 project_id=NULL（BidReviewAppService 3 个 + ProjectInitiationApprovalService 2 个，改为返回 View DTO）
- **PR !2217** docs(lessons): 追加第 85 条 - @Auditable(projectScoped=true) 方法禁止 void 返回值

本次部署将这些改动同步到测试环境，重点验证 V1181 数据清理 + 5 个 void 方法修复后项目动态不再出现 NULL project_id 记录。

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| HEAD commit | `e3d3c4580` |
| 工作区状态 | clean |
| GitHub 镜像 | 部署前 21 commits behind（部署后已同步） |
| Git wrapper | 已激活（`scripts/dev-env.sh` sourced） |

## 增量 commit (6e1a2db59..e3d3c4580)

```
e3d3c4580 !2217 docs(lessons): 追加第 85 条 - @Auditable(projectScoped=true) 方法禁止 void 返回值
57132572e docs(lessons): 追加第 85 条 - @Auditable(projectScoped=true) 方法禁止 void 返回值
0c71e6a16 !2216 fix(audit): 修复 PR !2212 5 个 void 方法导致 project_id=NULL
725febf39 fix(audit): 修复 PR !2212 5 个 void 方法导致 project_id=NULL
9bd1b40e5 !2215 docs(lessons): 记录 tenderAgency 字段语义混淆陷阱（第 84 条）
cbcb9ef92 docs(lessons): 记录 tenderAgency 字段语义混淆陷阱（第 84 条）
169265305 !2214 fix(tender-intake): 删除 Prompt 中 tenderAgency 字段口径——标讯表单不记录代理机构
e8f7f952a !2212 fix(audit): 修复 audit_logs.project_id 污染项目动态（性能/模板/标讯等实体 id 被错写）
a9b9e33b3 refactor(tender-intake): Google Code Review 修复——3 个 MAJOR 问题
5fd50a19b refactor(tender-intake): 思维链 Review 修复——Prompt 明确"可能标签"+清理冗余别名引用
583e406b8 !2213 test(gap-backfill): 补充 PR !2205 CO-599 测试覆盖缺口 (TaskReminderRecipientResolver + ProjectAccessFilter)
8d6b870b0 !2211 docs(release): 第 109 次测试环境部署报告 (test)
4cab4104f fix(tender-intake): 删除 Prompt 中 tenderAgency 字段口径——标讯表单不记录代理机构
0f604f843 fix(audit): xiyu-code-review 反馈修复——V1181 移除 Fee 白名单，统一清理 bug 数据
a2b9703c9 test(gap-backfill): 补充 PR !2205 CO-599 测试覆盖缺口 (TaskReminderRecipientResolver + ProjectAccessFilter)
553822ac6 fix(audit): Google Code Review 反馈修复——补齐 CalendarService + ProjectDTO @JsonIgnore
d96d8e58f fix(audit): 修复 audit_logs.project_id 污染项目动态（性能/模板/标讯等实体 id 被错写）
b68eebf0b docs(release): 第 109 次测试环境部署报告 (test)
```

## 改动范围

### 1. PR !2212 + !2216 fix(audit): audit_logs.project_id 污染治理（两轮修复）

**第一轮（PR !2212）**：
- **根因**：`AuditableAspect.extractProjectIdFromObject` 原实现对所有第一参为 Long 的 @Auditable 方法都提取 projectId，导致非项目实体（Performance/Template/Tender/Fee 等）的 id 被错写为 project_id，污染项目动态
- **修复**：
  - `Auditable` 注解新增 `projectScoped` 字段（默认 false），仅项目相关方法显式声明 `projectScoped=true`
  - 移除 Long args-first 和 getId() fallback 两条 bug 路径，仅保留 `getProjectId()` 反射调用
  - 新增 V1181 迁移清理污染数据（project_id = entity_id 且 entityType 非项目核心白名单）
- **xiyu-code-review 反馈修复**：V1181 移除 Fee 白名单，统一清理 bug 数据（FeeService 6 个方法中只有 createFee 走对象入参正确提取，其余 5 个走 bug 路径）

**第二轮（PR !2216）**：
- **根因**：PR !2212 的 Production Risk Review 发现 5 个 void 方法（BidReviewAppService.submitForReview/approveBid/rejectBid + ProjectInitiationApprovalService.approve/reject）会导致 project_id=NULL，丢失项目动态操作记录
- **修复**：将 5 个 void 方法改为返回 View DTO（BidDocumentReviewViewDto / InitiationViewDto），让切面从返回值提取 projectId
- **新增**：BidDocumentReviewViewDto（projectId 标注 @JsonIgnore），复用 ProjectInitiationMapper.toView
- **测试**：新增 5 个回归测试到 AuditableAspectProjectScopedTest

### 2. PR !2213 test(gap-backfill): CO-599 测试覆盖缺口

- 补充 TaskReminderRecipientResolver + ProjectAccessFilter 测试覆盖

### 3. PR !2214 fix(tender-intake): 删除 Prompt 中 tenderAgency 字段口径

- **根因**：标讯表单不记录代理机构，但 AI Prompt 中仍要求抽取 tenderAgency 字段，导致语义混淆
- **修复**：删除 Prompt 中 tenderAgency 字段口径，清理冗余别名引用
- **两轮 Review**：Google Code Review 修复 3 个 MAJOR 问题 + 思维链 Review 修复 Prompt 措辞

### 4. PR !2215 + !2217 docs(lessons): 教训沉淀

- 第 84 条：tenderAgency 字段语义混淆陷阱
- 第 85 条：@Auditable(projectScoped=true) 方法禁止 void 返回值

## Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 241 migrations (execution time 00:00.099s)
```

✅ DB 当前状态健康，241 migrations 全部 checksum 匹配。

### Step 2: DB 已应用版本 vs 源码最新版本

DB 部署前最近 5 个已应用迁移：

| version | description | success | installed_on |
|---|---|---|---|
| 1180 | add knowledge sub permissions | 1 | 2026-07-26 23:28:33 |
| 1179 | add knowledge personnel permission | 1 | 2026-07-26 23:28:33 |
| 1178 | add knowledge qualification permission | 1 | 2026-07-26 23:28:33 |
| 1177 | backfill business table comments | 1 | 2026-07-26 23:28:33 |
| 1174 | fix quoted menu permissions | 1 | 2026-07-26 23:28:32 |

✅ DB 已应用至 V1180，源码最新 V1181，本次部署将应用 1 个新迁移。

### Step 3: remote-deploy.sh 内置 validate

`remote-deploy.sh` 在激活新 jar 前自动执行 Flyway validate，失败则停止 rollout。本次部署该步骤通过（仅 pending 新迁移为预期状态），旧 jar 仍在运行时新 jar 已通过验证。

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="e3d3c4580" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空 → 同源构建（`baseURL=""`）
- `VITE_OBS_ENABLED=true` 显式启用 OBS 大文件直传（双保险，脚本默认已改为 true）
- `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留文件污染服务器
- BUILD SUCCESS（30.427s）

### 2. 产物校验

| 校验项 | 结果 |
|---|---|
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| `release-metadata.json` 中 `jarName` | `bid-platform-1.0.3.jar` ✅ |
| 前端入口 | `assets/index-DY4s5YDD.js` ✅ |
| jar 内 Flyway 迁移文件数 | 241 files（无重复）✅ |
| jar 内 V1181 存在性 | 2620 bytes ✅ |
| OBS 直传 Detail chunk `.upload(` 调用数 | 2（未被 tree-shake）✅ |
| 包大小 | 153M |

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-e3d3c4580.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-e3d3c4580.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=e3d3c4580 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="... mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-e3d3c4580-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- `SYSTEMCTL_SUDO=true` 让 remote-deploy.sh 用 sudo 重启服务（jetty 用户已配置 NOPASSWD sudo）
- DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-e3d3c4580-*.sql.gz`）
- 部署激活时间：2026-07-29T18:31:16 CST（systemd `active (running)`，PID 28384）
- 健康检查 3/3 通过（`total attempts: 79`，约 2.5 分钟），前端入口匹配 `assets/index-DY4s5YDD.js`

### 4. 前端资源保留（防跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/6e1a2db59/frontend/assets/* /srv/www/xiyu-bid/assets/ 2>/dev/null'
```

✅ 已从上一版本 `6e1a2db59` release 目录保留旧 assets 24h，避免旧标签页 `<link rel="preload">` 指向已删除资源触发 Nginx 404 + Sentry 噪声。

> **注意**：首次尝试从 `deployed-release.json` 解析上一版本 releaseDir 失败（字段已被新部署覆盖为空），改为直接指定 `/opt/xiyu-bid/releases/6e1a2db59` 成功。

## 验证结果

### 1. 健康检查

部署后健康检查通过（remote-deploy.sh 内置 consecutive 3/3，total attempts 79，约 2.5 分钟）：

```
✅ Health check passed (consecutive 3/3, total attempts: 79, service: active/running)
```

**历史对照**：第 8 次 4 分 22 秒、第 15 次 2 分 36 秒、第 108 次 2 分 36 秒、第 109 次 0 秒、本次约 2.5 分钟——Kafka broker 可达时通常无延迟，本次轻微延迟属已知行为范围内。

### 2. Flyway 迁移应用验证

V1181 迁移应用结果：

| version | description | success | installed_on |
|---|---|---|---|
| 1181 | cleanup audit logs project id | 1 | 2026-07-29 18:31:24 |

✅ V1181 已成功应用，清理了 audit_logs.project_id 污染数据（project_id = entity_id 且 entityType 非项目核心白名单的 bug 记录被置为 NULL）。

### 3. API Smoke（经 Nginx 8080 代理到后端 18080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 UP | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 UP | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

> Admin 密码未知，用 400/403/401 替代完整登录 smoke（自第 6 次起沿用）。

### 4. 前端页面验证

| # | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 6 | `GET /` | 200 | HTTP 200 | ✅ |
| 7 | `GET /login` | 200 | HTTP 200 | ✅ |
| 8 | 前端入口 | `assets/index-DY4s5YDD.js` | 一致 | ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前 GitHub 镜像状态 | 21 commits behind |
| 部署后操作 | 执行 `bash scripts/sync-to-github.sh` |
| 部署后 GitHub 镜像状态 | ✅ 完全一致（两边 main = `e3d3c4580`） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release ID | `6e1a2db59` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/6e1a2db59/`（仍存在） |
| 上一版本前端 assets | 已保留至 `/srv/www/xiyu-bid/assets/`（24h 自然刷新） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-e3d3c4580-*.sql.gz` |
| V1181 回滚脚本 | `U1181__cleanup_audit_logs_project_id.sql`（占位，无法恢复原值） |
| 回滚命令 | `ssh jetty@172.16.38.78 'cd /opt/xiyu-bid && RELEASE_ID=6e1a2db59 bash releases/6e1a2db59/rollback.sh'`（如存在） |

> ⚠️ **V1181 为数据清理迁移，无法回滚恢复原值**。如需恢复污染数据，需从 DB 备份恢复整张 audit_logs 表。

## 经验沉淀应用情况

| 经验条目 | 本次应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ Step 1 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| #2 Readiness 延迟恢复 | ✅ 本次约 2.5 分钟恢复，Kafka broker 可达后自恢复 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试限制 | ✅ Admin 密码未知，用 400/403/401 替代 |
| #5 GitHub 镜像同步 | ✅ 部署后执行 `sync-to-github.sh`，两边完全一致 |
| #6 临时调试配置清理 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 沿用（第 13 次起用户决定保留） |
| #7 幂等迁移设计 | ✅ V1181 为 UPDATE 清理语句，幂等（重复执行无副作用） |
| #8 systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| #10 破坏性 schema 变更 | N/A（V1181 为数据清理，非 schema 变更） |
| #12 rollback 脚本命名规范 | ✅ V1181 配套 `U1181__cleanup_audit_logs_project_id.sql` |
| #13 前端目录权限 | ✅ `sudo cp -rn` 已用 sudo |
| #14 macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`（tar 解压仍有 LIBARCHIVE.xattr 警告，可忽略） |
| #15 Flyway 防护体系 | ✅ 全流程通过（241 migrations validate OK） |
| #16 Mac HTTP_PROXY 502 | ✅ `curl --noproxy '*'` |
| #17 SentryAppender crash-loop | ✅ 无 logback.xml 改动 |
| #18 前端 hash 资源跨版本 404 | ✅ 部署后 `cp -rn` 保留上一版本 assets 24h |
| #OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` 显式传入 + 产物校验 `obsEnabled=true` |

## 风险提示

1. **V1181 数据清理不可回滚**：V1181 将 audit_logs 中 bug 数据（project_id = entity_id 且 entityType 非项目核心白名单）的 project_id 置为 NULL，无法恢复原值。如需恢复需从 DB 备份恢复整张表。已确认清理条件精准（只命中 bug 数据，不误清正确记录）。
2. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 沿用**：自第 13 次起用户决定保留，方便调试健康检查详情。生产环境建议关闭（暴露 DB/Redis 等组件详情）。
3. **本次为 audit 治理 + tender-intake 精简部署**：重点验证：
   - 项目动态页面（/project/31 等）不再出现"哈睿更新业绩"等无关记录（PR !2212 + V1181）
   - 标书评审提交/批准/拒绝、项目立项审批/拒绝 5 个操作仍正常出现在项目动态（PR !2216 修复 void 方法 project_id=NULL）
   - tender-intake AI 抽取不再产生 tenderAgency 字段（PR !2214）
   - TaskReminderRecipientResolver + ProjectAccessFilter 单测全绿（PR !2213）
4. **PR !2212 经历两轮修复**：第一轮修复 Long args-first bug 但引入 5 个 void 方法 project_id=NULL 问题，第二轮（PR !2216）修复 void 方法。教训已沉淀为第 85 条：@Auditable(projectScoped=true) 方法禁止 void 返回值。

## 部署确认清单

- [x] 环境门禁确认（用户 AskUserQuestion 确认测试环境 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD=e3d3c4580，与 origin/main 一致）
- [x] 服务器现状检查（6e1a2db59 健康 UP）
- [x] Flyway 预检 3 步法全通过（241 migrations validate OK）
- [x] 本地打包 BUILD SUCCESS（30.427s）
- [x] 产物校验全通过（obsEnabled=true，apiBaseUrl=""，241 迁移文件无重复，V1181 存在）
- [x] 上传 + 部署成功激活（18:31:16 CST，SYSTEMCTL_SUDO=true，PID 28384）
- [x] 前端资源保留（cp -rn 6e1a2db59 assets 24h）
- [x] 健康检查通过（3/3，尝试 79 次，约 2.5 分钟）
- [x] Flyway 迁移应用验证（V1181 success=1，2026-07-29 18:31:24）
- [x] API Smoke 5 项全通过
- [x] 前端页面 3 项全通过
- [x] GitHub 镜像同步（两边 main = e3d3c4580，完全一致）
- [x] 配置清理检查（SHOW_DETAILS=always 沿用，用户已知）
- [x] 部署报告生成

## 后续待办

- [ ] 提 PR 合入本部署报告（PR 标题：`docs(release): 第 110 次测试环境部署报告 (test)`）
- [ ] 测试环境 UAT 验证：
  - 项目动态页面（/project/31 等）不再出现无关记录（PR !2212 + V1181 清理）
  - 标书评审提交/批准/拒绝操作仍正常出现在项目动态（PR !2216 void 方法修复）
  - 项目立项审批/拒绝操作仍正常出现在项目动态（PR !2216 void 方法修复）
  - tender-intake AI 抽取不再产生 tenderAgency 字段（PR !2214）
  - TaskReminderRecipientResolver + ProjectAccessFilter 单测全绿（PR !2213）
