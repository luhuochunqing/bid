# Tasks: 修复 V1209 Flyway 迁移失败（CO-606）

## 任务列表（依赖有序）

### Task 1 - 创建 V1210 迁移文件（含回滚占位）
**依赖**: 无
**描述**: 新增 `backend/src/main/resources/db/migration-mysql/V1210__fix_v1209_archive_backfill_updated_at.sql`，含 step1（补 project_archive 头 + updated_at=NOW()）与 step2（回填 archive_file + updated_at=NOW() + 字段防御 LEFT/COALESCE），均幂等（LEFT JOIN + IS NULL）。同步创建回滚占位 `backend/src/main/resources/db/rollback/migration-mysql/U1210__fix_v1209_archive_backfill_updated_at.sql`。
**验收**: 文件存在，SQL 含 updated_at 字段，幂等去重条件完整。
**状态**: TODO

### Task 2 - 本地真实数据库验证 V1210 逻辑
**依赖**: Task 1
**描述**: 在开发宿主 trae 连本地 MySQL（xiyu_bid_main），对 V1210 的两条 INSERT 用 `START TRANSACTION; <SQL>; SELECT ROW_COUNT(); ROLLBACK;` 预览。确认不再报 1364，step1 补 2 条 project_archive、step2 补 ≤6 条 archive_file，且重复执行不增量。
**验收**: 事务回滚后无 1364 错误，影响行数符合预期，幂等验证通过。
**状态**: TODO

### Task 3 - 后端架构验证
**依赖**: Task 2
**描述**: 在开发宿主 trae 运行 `mvn test -Dtest=ArchitectureTest`，确认无架构回归。
**验收**: ArchitectureTest 全绿。
**状态**: TODO

### Task 4 - 提交 PR 并合入
**依赖**: Task 3
**描述**: 编译 spec/plan/tasks + V1210 + 回滚占位，commit 到 `agent/trae/co606-fix-v1209-archive-backfill`，push 并创建 PR 到 origin/main，描述引用 CO-606。CI 通过后合入。
**验收**: PR 已合入 main，CO-606 流转到评审中→已完成。
**状态**: TODO

### Task 5 - 清理 fail-state 并重启后端验证
**依赖**: Task 4
**描述**: 合入后，在开发宿主 trae 移除 `.runtime/dev-services/backend.fail-state`（如存在），重启 launchd/dev-services，确认 V1210 成功执行、后端 /actuator/health UP。
**验收**: 后端启动成功。`flyway_schema_history` 中 V1209 失败记录已被 repair 为 `success=1`（人工 `UPDATE flyway_schema_history SET success=1 WHERE version='1209'`），且新增 `V1210 success=1`。V1209 的 `success=0` 不能保留——否则 Flyway 永远被 failed-migration 阻塞、后端无法启动；V1209 从未写入数据，回填完全由 V1210 承载。
**状态**: TODO
