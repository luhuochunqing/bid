# 第 125 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 125 次（测试） |
| 部署时间 | 2026-08-17 11:36:15 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `9c9b9d91d` |
| 上一版本 Release | `2106ef5d0`（2026-08-15 18:52:05 CST，第 124 次测试部署） |
| 基线 commit | `9c9b9d91d`（origin/main HEAD，含 PR #2303 V1188 修复） |
| 激活时间 | 2026-08-17T11:36:15 CST（systemd 启动） |
| 健康检查通过 | 2026-08-17T11:41:00 CST（remote-deploy 内置，79 次尝试后 3/3 连续通过） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 4 个（V1187、V1188、V1189、V1190） |
| Smoke 测试 | 全部通过（8/8） |
| GitHub 镜像 | ✅ 已同步（部署后 sync-to-github.sh，两边 main 一致） |

## ⚠️ 关键：部署中阻塞并修复 V1188 迁移缺陷

> 本次部署遇到 **V1188 与 V145 列冲突**，导致首次打包失败。经根因分析、修复、提 PR（#2303）合并后重新部署成功。

### 根因

- `V145__performance_library.sql`（2026 年老迁移）**早已定义** `performance_record.contract_amount DECIMAL(15,2) COMMENT '合同金额(万元)'` 列 + 索引 `idx_perf_amount`。
- `V1188__add_contract_amount_to_performance_record.sql`（spec 041 业绩评分）原始版本直接 `ALTER TABLE performance_record ADD COLUMN contract_amount`，**未检查列是否已存在**。
- spec 041 调研 R7 未察觉 V145 已有该列，误判为"新列"。
- **现象**：`SQLSyntaxErrorException: Duplicate column name 'contract_amount'`，FlywayMysqlContainerTest 4 个测试全部 ApplicationContext 加载失败，阻塞 V1189/V1190 及部署。

### 修复（PR #2303）

- **V1188 重写为幂等存储过程版**：用 `information_schema.columns` 检查列存在性，列不存在才 `ADD COLUMN` + `CREATE INDEX`，列已存在则跳过。
- **U1188 改为 No-op rollback**：保护 V145 原列不被误删（`DROP COLUMN` 是破坏性操作）。
- **Wiki 回填**：`.wiki/pages/flyway-migration-pitfalls.md` 新增 §12「ADD COLUMN 必须幂等 — 警惕 V145 等老迁移已定义同名列」，原 §12~§14 顺延为 §13~§15。
- 门禁：`FLYWAY_ALLOW_IMMUTABLE_EDIT=1` 绕过 check-flyway-immutable.sh（必要性：main 上 V1188 有 bug 无法部署）。

### 验证结果

- 修复后本地 `FlywayMysqlContainerTest` 4/4 通过，架构测试 43/43 通过。
- 部署后 V1187~V1190 全部应用成功（success=1），`contract_amount` 列已恢复（DECIMAL(15,2) NULL MUL）。

## 背景

本次部署涵盖增量 155 个 commit（相对上一版本 2106ef5d0），核心是 **spec 041 AI 评分解析** 相关四张表迁移：

1. **V1187 `score_parse_tables`**：评分解析核心表（score_parse_task / score_item / score_result / score_parse_config）。
2. **V1188 `contract_amount`**：业绩记录表新增合同金额列（spec 041 业绩门槛比对，本次修复为幂等版）。
3. **V1189 `analytics_dashboard_permission`**：全局角色新增分析看板权限。
4. **V1190 `score_parse_spend_guard_columns`**：评分解析任务新增花费守护相关列（trigger_source / 内容/子项哈希等）。

同时含早前多个 PR（知识库权限拆分、归档文件 OBS 直传回填、客户收入列注释对齐、竞品详情等）累积改动。

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`（主工作区）
- 部署分支：`agent/trae-init`（锚点分支，ff-only 同步 = origin/main HEAD）
- 调试分支：`agent/trae/fix-v1188-idempotent`（修复 PR #2303 用，已合并删除）
- 早操：sync-env.sh 在锚点分支被守卫拦截（预期行为，部署不走任务分支流程）
- GitHub 镜像：部署前落后 153 commit，部署后 sync-to-github.sh 同步一致

## 增量改动（2106ef5d0 → 9c9b9d91d）

### 新增 Flyway 迁移

| 迁移 | 描述 | 幂等性 |
|---|---|---|
| `V1187__create_score_parse_tables.sql` | 创建评分解析 4 张表 | `CREATE TABLE IF NOT EXISTS` ✅ |
| `V1188__add_contract_amount_to_performance_record.sql` | 业绩合同金额列 | 修复为 information_schema 幂等存储过程 ✅ |
| `V1189__add_analytics_dashboard_permission_to_global_roles.sql` | 分析看板权限 | `UPDATE ... FIND_IN_SET` ✅ |
| `V1190__score_parse_spend_guard_columns.sql` | 评分任务花费守护列 | 幂等存储过程 ✅ |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（247 migrations） |
| Step 2: DB 已应用版本 | V1186（2026-08-15），源码最新 V1190，差 4 个迁移 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（247 migrations validated，checksums match） |

## 部署步骤

1. ✅ 环境门禁确认（测试环境 172.16.38.78）
2. ✅ 早操三连（sync-env + check-git-wrapper）
3. ✅ 基线确认（git fetch + HEAD=origin/main + GitHub 同步检查）
4. ✅ 服务器现状检查（deployed-release.json = 2106ef5d0, health UP）
5. ✅ Flyway 预检 3 步法（validate + DB 版本对比）
6. ⚠️ **首次打包失败**：V1188 与 V145 列冲突（Duplicate column name 'contract_amount'）
7. ✅ **根因分析 + 修复**（V1188 幂等存储过程版 + U1188 No-op + Wiki §12）→ PR #2303
8. ✅ 用户在 Gitee Web 确认合并 PR #2303
9. ✅ 切回锚点分支同步 main（获取 V1188 修复，HEAD=9c9b9d91d）
10. ✅ 本地打包（RELEASE_ID=9c9b9d91d, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
11. ✅ 产物校验（jar 含 V1187~V1190 无重复、obsEnabled=true、Detail .upload(=2、index 入口 index-D7w8-u8O.js）
12. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`（161M archive + remote-deploy.sh）
13. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
14. ✅ Flyway validate 通过（247 migrations validated）
15. ✅ 后端服务重启（active/running，Main PID 3750，2026-08-17 11:36:15 CST）
16. ✅ 健康检查通过（3/3，79 次尝试，无 Kafka 延迟）
17. ✅ 前端一致性验证（assets/index-D7w8-u8O.js）
18. ➖ 前端资源保留（跳过：releaseDir 解析为空，已是最新部署无需保留）

## 验证结果

### 后端启动验证

- systemd active (running)，Main PID 3750
- 健康检查 UP（health 200 + readiness 200）
- health details：MySQL UP、Redis 6.2.19 UP、sidecar UP、diskSpace 正常

### Smoke 测试（全部通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | GET /api/integration/crm/health（无认证） | ✅ HTTP 401 | 需认证 |
| 6 | 前端首页 `/` | ✅ HTTP 200 | — |
| 7 | 前端 /login | ✅ HTTP 200 | — |
| 8 | 前端 asset `index-D7w8-u8O.js` | ✅ HTTP 200 | — |

### 迁移应用验证

```sql
version description                                success installed_on
1187    create score parse tables                  1       2026-08-17 11:36:22
1188    add contract amount to performance record  1       2026-08-17 11:36:22
1189    add analytics dashboard permission to global roles 1 2026-08-17 11:36:22
1190    score parse spend guard columns            1       2026-08-17 11:36:22
```

V1187~V1190 全部成功应用（success=1）。`performance_record.contract_amount` 列已确认恢复（DECIMAL(15,2) NULL MUL）。

## GitHub 同步

- 部署前：`github/main..origin/main = 153`，镜像落后
- 部署后：执行 `bash scripts/sync-to-github.sh`，✅ 两边 main 完全一致（HEAD: 9c9b9d91d）

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `2106ef5d0` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-9c9b9d91d-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/2106ef5d0/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

> V1187~V1190 为表创建/列新增（非破坏性），回滚时旧 jar 不依赖这些表和列；如需回滚 schema 需追加 U 脚本（U1187/U1189/U1190 已由 pre-commit 覆盖检查，U1188 为 No-op rollback）。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ➖ 前端 hash 资源跨版本 404 保留旧 assets（经验 #18，本次跳过）
- ✅ **新经验入库**：ADD COLUMN 必须幂等（`.wiki/pages/flyway-migration-pitfalls.md` §12，V145 列冲突）

## 风险提示

1. **V1188 幂等修复**：已合入 main（PR #2303），生产环境部署时同样适用（V145 已有列则跳过，无列则新增），无副作用。
2. **SHOW_DETAILS=always 保留**：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史遗留（第 13/14/15 次均决定保留），非本次引入。
3. **spec 041 调研需扫描老迁移**：本次暴露调研阶段未溯源 V145 老迁移定义列的问题，建议 future spec 调研增加"grep 现有迁移确认目标列"步骤。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 基线确认（HEAD=origin/main + GitHub 镜像检查）
- [x] Flyway 预检通过（3 步法）
- [x] V1188 迁移缺陷根因分析 + 修复 + PR #2303 合并
- [x] 打包产物校验通过（jar 迁移无重复 + OBS obsEnabled=true + Detail .upload(=2）
- [x] 部署成功（jar 覆盖 + 服务重启）
- [x] 健康检查通过（3/3）
- [x] 迁移 V1187~V1190 应用验证通过（success=1）
- [x] Smoke 测试通过（8/8）
- [x] 部署报告生成（本次）

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-17 | 首次创建，基于第 125 次测试环境部署结果（含 V1188 迁移缺陷修复） |