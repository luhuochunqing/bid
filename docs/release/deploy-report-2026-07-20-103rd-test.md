# 第 103 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `07b34a932` |
| 上一版本 | `ff93698a7`（第 102 次，2026-07-20 18:28） |
| 部署时间 | 2026-07-20 20:19 CST |
| 增量 | 5 个 commit（V1173 迁移 SQL 列名错误紧急修复 + 工作台待办 3 个 Bug + 档案详情 4 项修复） |
| 新增迁移 | V1173（archive_file.file_size 历史空值回填，本次为修正后重新应用） |
| 部署结果 | ✅ 成功（健康检查 78 次 ≈ 2.5 分钟通过，含 Kafka SDK 启动延迟） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

本次部署为**第 102 次部署后紧急修复 + 重新部署**。第 102 次部署 `ff93698a7` 成功后，尝试合入后续 3 个 PR 时触发 V1173 迁移失败导致 backend crash-loop。本次部署核心是修复 V1173 SQL bug 并重新应用，恢复测试环境可用性。

### 触发事件

第 102 次部署后，本地打包 `1052e4fdb`（含 V1173）上传部署时，Flyway 应用 V1173 失败：

```
ERROR 1054 (42S22): Unknown column 'pd.size' in 'where clause'
```

导致 backend 服务反复重启（Flyway 启动校验失败 → systemd Restart=on-failure → crash-loop）。已手动 `systemctl stop xiyu-bid-backend` 阻止循环。

## 修复内容

### 1. V1173 迁移 SQL 列名错误修复（PR !2165）

- **根因**：V1173 SQL 误用 `pd.size`，但 `project_documents` 表实际列名为 `file_size`（VARCHAR，存放带单位字符串如 "1.5MB"）。`size` 是 MySQL 保留字且表中无此列。
- **影响**：第 103 次首次尝试部署失败 + backend crash-loop
- **修复**：将 V1173 中所有 `pd.size` 改为 `pd.file_size`，并加注释说明列名与保留字问题
- **验证**：在服务器上跑 SELECT 测试修复后 CASE WHEN 逻辑：
  - `1MB` → 1048576 ✅
  - `340MB` → 356515840 ✅
- **恢复操作**：
  1. 合并 PR !2165 到 main（squash merge → `07b34a932`）
  2. 删除服务器上失败的 flyway_schema_history 记录（version=1173, success=0）
  3. 重新打包 release `07b34a932`
  4. 重新部署

### 2. 工作台待办 3 个测试环境 Bug 修复（PR !2159，已在上次失败部署中合入）

- 修复工作台待办相关 3 个测试环境 Bug（commit `1052e4fdb`）

### 3. 项目档案详情 4 项修复（PR !2161，已在上次失败部署中合入）

- 列宽/大小 0B/操作人/统计归一化 4 项修复（commit `8f6b6146c`）

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae-init（锚点分支，ff-only 同步到 origin/main） |
| HEAD commit | `07b34a932`（!2165 fix(migration): V1173 修复 pd.size → pd.file_size 列名错误） |
| origin/main | `07b34a932`（与 HEAD 一致） |
| GitHub 镜像 | ✅ 已同步（部署后执行 sync-to-github.sh，两边 HEAD 一致） |
| git wrapper | ✅ 生效（scripts/git） |
| Flyway validate | ✅ 通过（235 migrations, all checksums match） |
| DB 已应用最新版本（部署前） | V1172（第 102 次部署应用） |
| 源码最新迁移版本 | V1173（修复后版本） |

## 增量 commit 列表

```
07b34a932 !2165 fix(migration): V1173 修复 pd.size → pd.file_size 列名错误（!2165）
1052e4fdb !2159 fix(workbench): 修复工作台待办 3 个测试环境 Bug
8f6b6146c !2161 fix(archive-detail): 项目档案详情 4 项修复（列宽/大小0B/操作人/统计归一化）
a715f4629 !2163 fix(workbench-calendar): 投标日历红绿点生效 + 聚合 Tender 开开标/报名截止事件（CO-594）
a0d435202 refactor(archive-detail): 修复 Google Code Review 发现的 6 个问题
```

## 改动范围

### 数据库迁移

- `V1173__backfill_archive_file_size_from_project_documents.sql`（修正版）：
  - 回填 `archive_file.file_size = 0` 的历史记录
  - 通过 `file_path` 关联 `project_documents.file_url`
  - 从 `project_documents.file_size`（VARCHAR 如 "1.5MB"）解析为字节
  - 使用多层 CASE WHEN + CAST + ROUND，覆盖 B/KB/MB/GB 单位
  - 幂等：`WHERE af.file_size = 0` 限定，重复执行不会再次更新已修复的记录

### 后端

- 无 Java 代码改动（仅迁移 SQL 修复）

### 前端

- 工作台待办 3 个 Bug 修复（PR !2159）
- 项目档案详情 4 项修复（PR !2161）
- 投标日历红绿点生效 + 聚合 Tender 事件（CO-594，PR !2163）
- archive-detail Google Code Review 6 个问题修复

## Flyway 预检结果

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: 服务器 validate | ✅ 通过 | 235 migrations, all checksums match |
| Step 2: DB 版本对比 | ✅ 一致 | DB 已应用 V1172，源码最新 V1173（修复后） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | "VALIDATE OK - all checksums match" |
| 失败记录清理 | ✅ 已删除 | `DELETE FROM flyway_schema_history WHERE version='1173' AND success=0;` |

## 部署步骤

1. ✅ 环境门禁确认（test 172.16.38.78）
2. ✅ 早操三连（sync-env + git wrapper check）
3. ✅ 基线确认（HEAD = origin/main = 07b34a932）
4. ✅ 服务器现状检查（backend STOPPED，V1173 失败记录存在）
5. ✅ Flyway 预检 3 步（validate + DB 版本对比 + 内置 validate）
6. ✅ 本地打包（`RELEASE_ID=07b34a932 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1`）
   - 产物校验：`obsEnabled=true`、`apiBaseUrl=""`、`Detail chunk .upload( 调用数=2`
   - jar 内 V1173 SQL 已含 `pd.file_size` 修复
7. ✅ 上传 + 部署（scp + remote-deploy.sh with `SYSTEMCTL_SUDO=true`）
   - Flyway validate 通过
   - Backend 服务启动
   - 健康检查 78 次 ≈ 2.5 分钟通过（consecutive 3/3）
   - 前端资源一致性校验通过（`assets/index-CqY_aeud.js`）

## 验证结果

### Flyway 迁移应用

```
installed_rank  version  description                                       success  installed_on
237             1172     align customer revenue column comment             1        2026-07-20 18:28:37
238             1173     backfill archive file size from project documents 1        2026-07-20 20:19:05
```

✅ V1173 成功应用（success=1）

### API Smoke 测试

| 接口 | HTTP | 预期 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | UP | ✅ |
| `/actuator/health/readiness` | 200 | UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 验证错误 | ✅ |
| `/api/projects`（无 auth） | 403 | 需认证 | ✅ |
| `/api/integration/crm/health`（无 auth） | 401 | 需认证 | ✅ |

### 前端验证

| 路径 | HTTP | 结果 |
|---|---|---|
| `/`（root） | 200 | ✅ |
| `/login` | 200 | ✅ |
| 资源 hash | `assets/index-CqY_aeud.js` | ✅ 与 release 一致 |

### 配置清理检查

- ✅ `/etc/xiyu-bid/backend.env` 无 `SHOW_DETAILS` / `DEBUG` / `TRACE` 调试配置

## GitHub 同步

| 项目 | 值 |
|---|---|
| 部署前 GitHub 镜像状态 | 落后 81 个 commit |
| 部署后同步操作 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（HEAD: 07b34a932fa8bdc0590f649933988a96edfe7475） |
| 备注 | 首次推送 SSH 连接被重置（网络抖动），重试一次成功 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release dir | `/opt/xiyu-bid/releases/ff93698a7`（第 102 次） |
| 上一版本 jar | `/opt/xiyu-bid/releases/ff93698a7/backend/app.jar` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-07b34a932-*.sql.gz` |
| 前端资源保留 | ✅ 已保留 `1052e4fdb/frontend/assets/` 到 `/srv/www/xiyu-bid/assets/`（24h） |

## 经验沉淀应用

### 应用的教训

1. **Flyway 不可变迁移门禁**：V1173 已被推送到 origin/main 触发不可变保护，使用 `FLYWAY_ALLOW_IMMUTABLE_EDIT=1` 紧急例外通道修复（`scripts/check-flyway-immutable.sh` 文档化的逃生舱）
2. **agent-lock 治理**：检测到已合并分支 `agent/trae2/archive-detail-fixes`（PR !2161）的 stale lock 阻塞新提交，先删除 stale lock 再 acquire 新锁
3. **数据库迁移列名校验**：未来新建迁移前应先 `SHOW COLUMNS FROM <table>` 确认列名，避免 MySQL 保留字陷阱
4. **健康检查容忍 Kafka SDK 延迟**：78 次 ≈ 2.5 分钟通过，属正常范围（参考 lessons §Kafka SDK readiness 延迟）

### 新增教训（建议归档到 lessons-learned.md）

- **V1173 列名错误模式**：MySQL 保留字 `size` 不能作为列名引用，且 `project_documents` 表实际列名为 `file_size`（VARCHAR 存放带单位字符串）。后续写迁移 SQL 前应：
  1. `DESC <table>` 或 `SHOW COLUMNS FROM <table>` 确认列名
  2. 检查 MySQL 保留字列表（https://dev.mysql.com/doc/refman/8.0/en/keywords.html）
  3. 对于 VARCHAR 字段存放带单位字符串的场景，使用 CASE WHEN + REGEXP 解析时应在测试环境 SELECT 验证后再写迁移

## 风险提示

1. **V1173 一对多假设**：`project_documents.file_url` 理论上应唯一对应一个 `archive_file.file_path`。当前业务流程下成立；如未来放宽约束需改用子查询去重（已在迁移注释中说明）
2. **V1173 回滚是有损的**：`U1173` 回滚会把所有 `file_size > 0` 且 `file_path` 对应 `project_documents.file_url` 的记录恢复为 0，包括 multipart 路径正常归档的真实字节数据。生产环境回滚前必须先备份 `archive_file` 表
3. **GitHub 同步首次失败**：本次 GitHub SSH 连接首次被重置（`Connection reset by 20.205.243.166 port 22`），重试一次成功。建议在 sync-to-github.sh 中加入自动重试逻辑

## 部署确认清单

- [x] 环境门禁通过（test 172.16.38.78）
- [x] 早操三连通过（sync-env + git wrapper + agent lock check）
- [x] 基线干净（HEAD = origin/main = 07b34a932）
- [x] Flyway 预检 3 步通过
- [x] 本地打包产物校验通过（obsEnabled=true, apiBaseUrl="", V1173 修复已含）
- [x] 远程部署成功（Flyway validate + 健康检查 + 前端一致性）
- [x] V1173 迁移成功应用（success=1）
- [x] API Smoke 测试全通过
- [x] 前端资源保留完成（1052e4fdb assets 保留 24h）
- [x] 配置清理检查通过（无调试配置）
- [x] GitHub 镜像同步完成（两边 HEAD 一致）
- [x] PR !2165 已合并到 main
- [x] 本地任务分支已清理（`agent/trae/fix-v1173-migration` 已删除）
- [x] agent-lock 已释放（`.agent-locks/fix-v1173-migration.yml` 已删除）

## 后续任务

- [ ] 将 V1173 列名错误教训归档到 `docs/lessons/lessons-learned.md`（新增 §76）
- [ ] 提 PR 合入本部署报告到 main
