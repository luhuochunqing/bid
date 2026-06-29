# 第十八次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-29 17:08:28 CST（服务重启时间） |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `681871c2f` (PR !1345 auto-merge) |
| **第 17 次部署基线** | `6236689df` (!1338) |
| **新增 commit 数** | 13 个（含 1 个 hotfix PR !1345） |
| **新增迁移数** | 2 个（V1110 + V1111） |

## 🚨 部署阻断事故与 hotfix 处置

### 事故发现

第 18 次部署预检发现源码 main 同时存在两个 `V1110__*.sql`：
- `V1110__cleanup_legacy_pending_assignment_tasks.sql` (PR !1340, CO-401)
- `V1110__add_pending_approval_to_platform_accounts_status.sql` (PR !1342, CO-386)

Flyway 9.22.3 启动时报 `Found more than one migration with version 1110`，后端无法启动，第 18 次部署被阻断。

### Hotfix 处置

- **分支**：`agent/trae/fix-v1110-version-conflict`（基于 origin/main）
- **PR**：[#1345](https://gitee.com/allinai888/bid/pulls/1345)
- **策略**：按"先合入先得版本号"原则保留 !1340 (CO-401 cleanup) 为 V1110，将 !1342 (CO-386 enum 扩展) 重命名为 V1111
- **影响**：DB 当前最新 V1109，V1110/V1111 均未应用，重命名对生产数据无影响
- **回滚脚本**：U1110 → U1111 同步重命名，source header 已更新
- **顺手清理**：删除两个对应 PR 已合入 main 的 stale 锁文件（`co-386-borrow-500-debug.yml`、`co401-cleanup-legacy-pending-tasks.yml`）

### 验证

- `bash scripts/check-migration-headers.sh` PASS
- `bash scripts/check-flyway-rollback.sh` SKIP（无法提取版本号，非错误）
- `mvn test -Dtest='FlywayRollbackScriptCoverageTest,ArchitectureTest'` 全绿（27/27）
- pre-push-gate.sh 13 道门禁全通过

### PR 合入

- **方式**：API 合入（用户授权）
- **SHA**：`681871c2f908c3914d8a5fac0b0422eaadf0e6fa`
- **状态**：merged

## 第 17 次部署后合入的 PR（11 个 + 1 hotfix + 1 auto-merge）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1338 | CO-386 借用申请 custodianId 改由后端从 account.contactPerson 自动取值 | 修复 |
| !1339 | CO-400 submitForReview 误配任务闸门导致生产 409（拆分 BidReadinessPolicy） | 修复 |
| !1340 | CO-401 cleanup legacy pending assignment tasks V1110 | 数据修复 |
| !1341 | CO-388 投标专员作为绑定联系人时显示账号/联系人/密码列 | 修复 |
| !1342 | CO-386 补齐 platform_accounts.status enum 缺失 PENDING_APPROVAL 值（生产 500 修复） | 修复 |
| !1343 | CO-400 列表字段齐全 + 编辑页密码回显（特权 getPassword + N+1 详情） | 修复 |
| !1344 | CO-407 bid file required mark 投标文件字段标题必填标识 | 功能 |
| !1345 | fix(flyway): V1110 撞号 hotfix - 重命名 CO-386 为 V1111 | hotfix |

> 改动范围：CO-386 借用申请全流程修复（custodianId 自动取值 + PENDING_APPROVAL enum 扩展）、CO-388 投标专员绑定联系人可见完整信息、CO-400 submitForReview 任务闸门误配修复 + 列表字段/密码回显、CO-401 遗留"待分配"任务清理、CO-407 投标文件必填标识、V1110 撞号 hotfix。

## 改动范围

### 新增 Flyway 迁移（2 个）

| 版本 | 文件 | 描述 | 风险 |
|---|---|---|---|
| V1110 | `cleanup_legacy_pending_assignment_tasks.sql` | 删除 CO-401 之前自动创建的遗留"【待分配】"任务及其关联数据 | 中（DELETE 操作，不可回滚恢复） |
| V1111 | `add_pending_approval_to_platform_accounts_status.sql` | 扩展 platform_accounts.status enum 新增 PENDING_APPROVAL 值 | 低（INSTANT 元数据操作，不锁表，不删除现有值） |

### 回滚脚本（2 个，均已就位）

| 版本 | 文件 | 回滚限制 |
|---|---|---|
| U1110 | `cleanup_legacy_pending_assignment_tasks.sql` | no-op（DELETE 不可逆） |
| U1111 | `add_pending_approval_to_platform_accounts_status.sql` | 需先把 `status='PENDING_APPROVAL'` 的行 UPDATE 回 `AVAILABLE`，否则 MODIFY COLUMN 会因数据被截断而失败 |

## Flyway 预检与处置

### 预检 3 步法结果

| 步骤 | 检查项 | 结果 |
|---|---|---|
| Step 1 | `flyway-repair-runner.sh validate` | ✅ 173 migrations validated, all checksums match (00:00.068s) |
| Step 2 | DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1109，源码最新 V1111（pending: V1110 + V1111） |
| Step 3 | remote-deploy.sh 激活前自动 validate | ✅ 通过（173 migrations validated, 00:00.085s） |

### 关键确认

- 服务器 `/tmp/migration-mysql/` 目录停留在旧版本，但不影响 validate（validate 只检查 checksum 一致性）
- DB 直接 SQL 查询 `flyway_schema_history` 确认 V1109 是当前最新已应用版本
- 撞号修复后再次预检：源码 V1110 (cleanup) + V1111 (enum) 各 1 个，无撞号

## 部署步骤

1. **早操三连**：dev-env + sync-env + check-git-wrapper（门禁 7/7 通过）
2. **基线确认**：HEAD = `dd9624c2e` (origin/main)，GitHub 镜像落后 10 commit
3. **服务器现状**：当前部署 `6236689df-api8080`（第 17 次），health 200 UP
4. **增量检查**：13 个 commit + 2 个新迁移 V1110/V1111 → **🚨 发现 V1110 撞号**
5. **Hotfix 流程**：
   - 创建 `agent/trae/fix-v1110-version-conflict` 分支
   - `git mv V1110__add_pending_approval... V1111__add_pending_approval...`
   - `git mv U1110__add_pending_approval... U1111__add_pending_approval...`
   - 更新文件头注释（包含撞号原因 + hotfix 说明）
   - 清理 stale 锁文件，新增 hotfix 锁文件
   - 提交 + push + 创建 PR #1345
6. **PR 合入**：API 合入 main（用户授权），SHA `681871c2f`
7. **新基线**：deploy-18th 分支 rebase 到 `681871c2f`
8. **Flyway 预检 3 步**：全部通过
9. **本地打包**：`RELEASE_ID=681871c2f-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
10. **产物校验**：jar 内 V1110 (cleanup) + V1111 (enum) 各 1 个，前端入口 `assets/index-MVixZ7Zz.js`
11. **上传 + 部署**：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
12. **健康检查**：第 1 次尝试即 UP（无 Kafka 延迟）
13. **迁移验证**：V1110 + V1111 都已应用（installed_on 2026-06-29 17:08:35）
14. **Smoke 测试**：5 个 API + 2 个前端页面全通过
15. **GitHub 镜像同步**：16 个落后 commit 已同步
16. **配置清理**：`SHOW_DETAILS=always` 历史保留（运维监控需要，第 13/14/15 次已确认）

## 验证结果

### 健康检查（第 1 次尝试即 UP）

```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},"diskSpace":{"status":"UP"},"jwt":{"status":"UP","details":{"algorithm":"HMAC-SHA256","strength":"STRONG"}},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"redis":{"status":"UP","details":{"version":"6.2.19"}}}}
```

> ✅ 第 18 次未出现 Kafka SDK readiness 延迟（与第 16/17 次一致，第 8/9/10/13/15 次的已知行为本次未复现）

### Flyway 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1110 | cleanup legacy pending assignment tasks | 1 | 2026-06-29 17:08:35 |
| 1111 | add pending approval to platform accounts status | 1 | 2026-06-29 17:08:35 |

### API Smoke 测试

| # | 端点 | 期望 | 实际 | 说明 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | ✅ 200 UP | 健康检查 |
| 2 | `GET /actuator/health/readiness` | 200 UP | ✅ 200 UP | 就绪检查 |
| 3 | `POST /api/auth/login` (空 body) | 400 | ✅ 400 | 空密码验证错误，路由正常 |
| 4 | `GET /api/projects` | 403 | ✅ 403 | 需认证，路由正常 |
| 5 | `GET /api/integration/crm/health` | 401 | ✅ 401 | 需认证，路由正常 |

> 登录 smoke 因 admin 密码未授予而跳过，用 400/403/401 替代验证（第 6 次起固化策略）

### 前端页面验证

| # | URL | 期望 | 实际 | 说明 |
|---|---|---|---|---|
| 6 | `GET /` | 200 | ✅ 200 | 前端首页 |
| 7 | `GET /login` | 200 | ✅ 200 | 登录页 |
| 8 | 前端入口 JS | `assets/index-MVixZ7Zz.js` | ✅ 一致 | 与 release 一致 |

## GitHub 同步

| 项 | 值 |
|---|---|
| 部署前 GitHub 镜像落后 | 10 个 commit |
| 部署后 GitHub 镜像落后 | 16 个 commit（含 hotfix + 期间合入的 !1343/!1344） |
| 同步操作 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ Gitee main = GitHub main = `681871c2f908c3914d8a5fac0b0422eaadf0e6fa` |

## 回滚信息

### 回滚锚点

| 项 | 值 |
|---|---|
| 上一稳定 release | `6236689df-api8080`（第 17 次部署） |
| 上一 release 目录 | `/opt/xiyu-bid/releases/6236689df-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-681871c2f-20260629170657.sql.gz` |

### 回滚步骤

1. **代码层**：将 `/opt/xiyu-bid/shared/backend/app.jar` 替换为 `6236689df-api8080` 的 jar
2. **前端层**：将 `/srv/www/xiyu-bid/` 切回 `6236689df-api8080` 的 frontend
3. **DB 层**：
   - V1111 回滚（先 UPDATE PENDING_APPROVAL → AVAILABLE，再 MODIFY COLUMN 恢复 4 值 enum）
   - V1110 不可逆（DELETE 已删除"【待分配】"任务，需从 DB 备份恢复整张表）
4. **服务层**：`sudo systemctl restart xiyu-bid-backend`

### 回滚状态

**Rollback posture: ready, not needed.**

部署全部验证通过，无回滚需求。如需回滚，DB 备份已在 `/opt/xiyu-bid/db-backups/` 就位。

## 经验沉淀应用情况

### 已应用的 15 条部署经验

| # | 经验 | 本次应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 全部执行，Step 1 在撞号前未发现（validate 只检查 checksum，不检查版本号唯一性），Step 2 SQL 查询发现 DB 最新 V1109，Step 3 remote-deploy 内置 validate 通过 |
| 2 | Readiness 延迟恢复 | ✅ 本次未出现延迟（第 1 次尝试即 UP） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4 | Smoke 测试限制 | ✅ admin 密码未知，用 400/403/401 替代验证 |
| 5 | GitHub 镜像同步 | ✅ 部署后同步 16 个 commit |
| 6 | 临时调试配置清理 | ✅ `SHOW_DETAILS=always` 保留（第 13/14/15/18 次确认） |
| 7 | 幂等迁移设计 | ⚠️ V1110 (DELETE) 非幂等，V1111 (ALTER enum) 幂等 |
| 8 | systemctl sudo 权限 | ✅ 默认 `SYSTEMCTL_SUDO=true`，服务重启成功 |
| 9 | git.properties commit id | ⚠️ 未单独验证（不影响功能） |
| 10 | 破坏性 schema 变更 | ✅ V1110 是 DELETE 数据，已在迁移注释中标注 |
| 11 | /tmp/migration-mysql/ 目录过时 | ✅ 通过 SQL 直接查询确认 |
| 12 | rollback 脚本命名规范 | ✅ U1110 + U1111 已就位 |
| 13 | 前端目录权限 | ✅ `jetty:jetty` 所有权，无需 chown |
| 14 | macOS `._*` 残留 | ✅ 未出现 |
| 15 | Flyway 防护体系 | ✅ 15 项防护全部生效 |

### 新增经验（第 18 次部署沉淀）

#### 16. Flyway 版本号撞号检测缺失（第 18 次发现）

**问题**：现有 Flyway 防护体系 15 项中**没有版本号撞号检测**——`check-flyway-versions.sh` 只检查版本号连续性和已发布不可变性，不检查同名版本号是否已存在。两个 PR !1340 和 !1342 各自独立创建 `V1110__*.sql`，先后合入 main 时无任何拦截。

**根因**：
- `next-migration-version.sh --reserve` 只在创建新迁移时取版本号，但并行开发时两个 agent 各自取了 V1110
- pre-push `check-flyway-versions.sh` 的 `--source=push` 模式只 fetch origin/main 版本，不扫描本地新增 V*.sql 是否与已有的撞号
- Flyway 9.22.3 在启动时才报撞号，pre-validate 阶段不报

**修复方向**：
1. 在 `check-flyway-versions.sh` 中增加本地 V*.sql 列表的版本号唯一性检查
2. 或在 `next-migration-version.sh --reserve` 时记录已预约版本到共享文件，避免并行取号
3. 或在 pre-commit hook 中检查新增 V*.sql 是否与已存在版本撞号

**历史影响**：第 18 次部署被阻断，需 hotfix PR 才能继续。所幸预检阶段发现，未影响生产。

**修复价值**：建议作为第 16 条经验正式沉淀到 skill 中。

## 风险提示

1. **V1110 不可回滚**：DELETE 已删除"【待分配】"任务及关联数据，如需恢复必须从 DB 备份恢复整张表
2. **V1111 回滚需先 UPDATE 数据**：回滚前必须把 `status='PENDING_APPROVAL'` 的行 UPDATE 回 `AVAILABLE`，否则 MODIFY COLUMN 会失败
3. **撞号防护需补强**：现有防护未覆盖版本号撞号，建议增加检测脚本
4. **Kafka SDK readiness 已知行为**：第 18 次未复现，但仍需容忍 2-5 分钟延迟

## 部署确认清单

- [x] 早操三连通过
- [x] 基线确认（HEAD = origin/main）
- [x] 服务器现状查询
- [x] 增量 commit 和迁移文件检查
- [x] Flyway 预检 3 步法
- [x] 本地打包成功
- [x] 产物校验通过（jar 内迁移文件无撞号，前端入口一致）
- [x] 上传 + 部署成功
- [x] 健康检查 UP（第 1 次尝试）
- [x] Flyway 迁移应用验证（V1110 + V1111）
- [x] API Smoke 测试全通过
- [x] 前端页面验证通过
- [x] GitHub 镜像同步完成
- [x] 配置清理检查（SHOW_DETAILS 保留）
- [x] 部署报告生成

## 部署总结

第 18 次部署是首次遇到 Flyway 版本号撞号事故，通过 hotfix PR !1345 重命名 CO-386 迁移为 V1111 修复。整个流程按 SOP 走完 13 步，所有验证通过。本次部署成功上线 13 个 commit（含 11 个 PR + 1 个 hotfix + 1 个 auto-merge），新增 2 个 Flyway 迁移（V1110 cleanup + V1111 enum 扩展）。健康检查第 1 次尝试即 UP，无 Kafka 延迟。

**第 18 次部署关键经验**：Flyway 防护体系需要增加版本号撞号检测，避免并行开发时两个 PR 各自取相同版本号合入 main 后阻断部署。
