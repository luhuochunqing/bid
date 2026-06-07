# Go-Live Wave 2 Plan

## Goal
完成正式上线前第二波门禁建设：清除剩余用户可见的演示边界，新增可执行的发布/备份/恢复脚本，并把 UAT 与 go-live checklist 固化到仓库中。

## Scope
### 1. Remaining visible mock boundaries
- `src/views/Analytics/Dashboard.vue`
  - 清理 drill-down 中对 `mockData.projects` 和伪造 team/files 的依赖
  - API 模式下只展示真实可追溯数据；无真实明细时显示空态和正式提示
- `src/views/Project/Detail.vue`
  - 在 API 模式下隐藏或禁用 `AutoTasks`、`MobileCard` 这类仍处于演示态的入口，避免用户继续看到“可点但不真实”的功能
- `src/components/ai/AutoTasks.vue`
  - 保留为 mock-only 组件，但不再作为 API 模式下可见主入口
- `src/components/ai/MobileCard.vue`
  - 保留为 mock-only 组件，但不再作为 API 模式下可见主入口

### 2. Release and recovery scripts
新增脚本目录并落地可执行脚本：
- `scripts/release/deploy.sh`
  - 前后端构建、后端迁移、基础前置检查、发布摘要输出
- `scripts/release/backup-db.sh`
  - 基于 `mysqldump` 的数据库备份脚本
- `scripts/release/restore-db.sh`
  - 基于 `mysql` 客户端的数据库恢复脚本
- `scripts/release/preflight.sh`
  - 检查必要环境变量、Docker、Java/Node 版本、数据库连接参数

脚本设计原则：
- 只依赖环境变量，不硬编码生产地址
- 默认安全：缺变量即失败，不做 destructive action
- 适合作为 CI/CD 或人工演练入口

### 3. UAT and Go-Live documentation
新增文档：
- `docs/UAT_PLAN.md`
  - 角色、环境、业务场景、验收步骤、通过标准、阻塞标准
- `docs/GO_LIVE_CHECKLIST.md`
  - 发布前、发布中、发布后 checklist
- `docs/ROLLBACK_RUNBOOK.md`
  - 数据库恢复、应用回滚、验证步骤

### 4. README alignment
- 更新 `README.md`，把“纯 Mock POC”描述改为“双模式系统，Mock 用于演示，API 用于真实联调/上线准备”
- 增加新脚本和上线文档入口

## Verification
- `npm run build`
- `VITE_API_MODE=api npm run build`
- `mvn -DskipTests compile`
- `mvn -Dtest=ExpenseControllerIntegrationTest,BarCertificateControllerIntegrationTest test`
- `mvn -Dtest=FlywayMysqlContainerTest test`
- `bash scripts/release/preflight.sh` 在缺少生产变量时应给出明确失败信息

## Risks
- Dashboard 的 drill-down 当前大量基于展示 mock，需避免误删影响主看板
- 发布/恢复脚本只能做到“可执行模板”，仍需在真实环境中演练一次才算完成上线门禁
- README 改动会改变项目定位描述，需要与当前双模式现状一致
