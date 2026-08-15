# 第 124 次测试环境部署报告 — 2026-08-15

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 124 次（测试） |
| 部署时间 | 2026-08-15 18:52:05 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `2106ef5d0` |
| 上一版本 Release | `4fc4e1868`（2026-08-12 14:17:40 CST，第 123 次测试部署） |
| 基线 commit | `2106ef5d0`（origin/main HEAD） |
| 激活时间 | 2026-08-15T18:52:05 CST（systemd 启动） |
| 健康检查通过 | 2026-08-15T18:52:16 CST（remote-deploy 内置，80 次尝试后 3/3 连续通过） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 10 个（相对上一版本） |
| 新增 Flyway 迁移 | 1 个（V1186） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | ✅ 已同步 |

## 背景

本次部署包含三块改动：

1. **项目详情-结果确认阶段「竞争对手情况」表格字段改造**：折扣列由整数（88折/95折）改为百分比（保留两位小数），并抽常量 + 加注释 + 过滤空行（code review 修复）。
2. **Testcontainers 与 Docker Desktop 兼容修复 + 容器契约测试 sql_mode/schema 对齐**：升级 Testcontainers 版本，为 6 个容器契约测试类对齐 sql_mode 与 collation（V1077 Error 1292、V1092 Error 1064），新增 V1186 迁移（将 `tender_event_logs.status` 改为 ENUM 对齐实体）。
3. **gitee-pr-helper.sh 脚本修复**：PR 描述字段名 + 支持自定义标题/描述。

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`（主工作区）
- 部署分支：`agent/trae-init`（锚点分支，HEAD = origin/main）
- 早操：sync-env.sh 在锚点分支被守卫拦截（预期行为，部署不走任务分支流程）
- GitHub 镜像：部署前 `github/main..origin/main = 0`，镜像一致

## 增量改动（4fc4e1868 → 2106ef5d0，10 个 commit）

### 关键改动

| Commit | 描述 |
|---|---|
| 7a719c920 | fix(env): 修复 Testcontainers 与 Docker Desktop 兼容 + 容器契约测试 sql_mode/schema 对齐 |
| 44b834c6a | docs(wiki): 容器测试环境兼容复合查询回填 |
| 2106ef5d0 | auto-merge PR !2289（fix-env-compat-testcontainers） |
| 55a18a767 / ff02f348d / a7db4f64e / 350d0db0e / a9d9aae17 | feat/fix/refactor(project-result): 竞争对手情况表格字段改造（折扣百分比化） |
| bfc9d3926 / 96e44aed4 | fix(scripts): gitee-pr-helper.sh 修复 PR 描述字段名 + 支持自定义标题/描述 |
| 22b724af6 | docs(release): 第 123 次测试环境部署报告 |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/src/main/resources/db/migration-mysql | 新增 `V1186__fix_tender_event_logs_status_enum.sql`（status VARCHAR→ENUM） |
| backend/src/test/java/com/xiyu/bid/support | `FlywayMysqlContainerTest`（sql_mode/collation/allowMultiQueries 对齐） |
| backend/src/main/java（project-result） | 竞争对手折扣列改为百分比（保留两位小数）+ 常量抽取 |
| scripts | gitee-pr-helper.sh 修复 |
| docs/wiki | 容器测试兼容回填 |

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（246 migrations） |
| Step 2: DB 已应用版本 | V1185（2026-08-12），源码最新 V1186，差 1 个迁移 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

### 新增迁移 V1186

```sql
ALTER TABLE tender_event_logs
    MODIFY COLUMN status
    ENUM('SENT','FAILED') NOT NULL DEFAULT 'SENT' COMMENT '发送结果：SENT/FAILED';
```

- **幂等性**：MODIFY COLUMN（ENUM 变更），MySQL 8.0 INSTANT 元数据操作，不锁表。
- **风险**：低。应用只写 SENT/FAILED，ENUM 取值与现有数据一致，无需数据清洗。
- **已应用**：`2026-08-15 18:52:13`，success=1。

## 部署步骤

1. ✅ 环境门禁确认（测试环境 172.16.38.78）
2. ✅ 基线确认（git fetch + HEAD=origin/main=2106ef5d0 + GitHub 镜像一致）
3. ✅ 服务器现状检查（deployed-release.json = 4fc4e1868）
4. ✅ Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
5. ✅ 本地打包（RELEASE_ID=2106ef5d0, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
6. ✅ 产物校验（jar 迁移无重复、obsEnabled=true、Detail .upload(=2、index 入口 index-XT-Qnw2F.js）
7. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`（154M archive + remote-deploy.sh）
8. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
9. ✅ Flyway validate 通过（246 migrations validated）
10. ✅ 后端服务重启（active/running，Main PID 20978，2026-08-15 18:52:05 CST）
11. ✅ 健康检查通过（3/3，80 次尝试，无 Kafka 延迟）
12. ✅ 前端一致性验证（assets/index-XT-Qnw2F.js）
13. ✅ 前端资源保留（从上一版本 4fc4e1868 cp -rn 旧 assets）

## 验证结果

### 后端启动验证

- systemd active (running)，Main PID 20978
- 健康检查 UP（health 200 + readiness 200）

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

### 迁移应用验证

```sql
version description                  success installed_on
1185    create tender event logs     1       2026-08-12 11:17:47
1186    fix tender event logs status enum 1  2026-08-15 18:52:13
```

V1186 已成功应用（success=1）。

## GitHub 同步

- 部署前确认 `github/main..origin/main = 0`，镜像一致
- 部署后无需额外同步

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `4fc4e1868` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-2106ef5d0-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/4fc4e1868/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

> V1186 为 INSTANT 元数据操作，回滚时列类型 ENUM→VARCHAR 需额外迁移脚本（如需回滚 V1186，需新增 U 脚本）。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 前端 hash 资源跨版本 404 保留旧 assets（经验 #18）
- ✅ 容器测试门禁逃生阀（`XIYU_SKIP_CONTAINER_TEST=true`）

## 风险提示

1. **容器契约测试失败（本次新发现，非 Docker 兼容）**：`FlywayMysqlContainerTest.v1092MergesUsersWhenTargetRoleAlreadyExists` 报 `Duplicate entry 'bidAdmin' for key 'tmp_role_mappings.PRIMARY'`。根因：V1092 脚本用 `CREATE TEMPORARY TABLE IF NOT EXISTS tmp_role_mappings` + INSERT，Context 启动时 Flyway 已在同一连接池会话跑过 V1092，临时表残留（session 级），测试重放脚本时 `IF NOT EXISTS` 不重建表，INSERT 触发主键冲突。**非生产代码缺陷**（V1092 在真实 DB 只执行一次，不被重放），但阻断打包门禁。本次经用户确认使用 `XIYU_SKIP_CONTAINER_TEST=true` 跳过。**改进方向**：在测试重放 V1092 前，同一会话 `DROP TEMPORARY TABLE IF EXISTS tmp_role_mappings`，让 `CREATE IF NOT EXISTS` 重建，需单独任务整治。
2. **SHOW_DETAILS=always 保留**：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史遗留（第 13/14/15 次均决定保留），非本次引入。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 基线确认（HEAD=origin/main + GitHub 镜像一致）
- [x] Flyway 预检通过（3 步法）
- [x] 打包产物校验通过（jar 迁移无重复 + OBS obsEnabled=true + Detail .upload(=2）
- [x] 部署成功（jar 覆盖 + 服务重启）
- [x] 健康检查通过（3/3）
- [x] 迁移 V1186 应用验证通过（success=1）
- [x] Smoke 测试通过（7/7）
- [x] 前端资源保留（防跨版本 404）
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-15 | 首次创建，基于第 124 次测试环境部署结果 |