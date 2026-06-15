# QUALITY_SCORE.md — 质量评分

对各个模块和架构层级进行打分，追踪代码质量差距。供"文档园丁"/重构 agent 定期扫描。

> 评分口径：**主观打分 + 现有审计数据**。分数会随重构演进变化；当前为初始基线。

## 评分约定

- 每项满分 10，分维度：**可读性 / 可测性 / 架构合规 / 文档同步**。
- `<5` 标记为差距（gap），并登记到 `docs/exec-plans/tech-debt-tracker.md`。

## 初始评分表（基线，待各模块 owner 校准）

| 模块 / 层 | 可读性 | 可测性 | 架构合规 | 文档同步 | 备注 |
|---|---:|---:|---:|---:|---|
| 后端 domain / core（FP-Java 纯核心） | 7 | 7 | 8 | 6 | 门禁由 `FPJavaArchitectureTest` 守 |
| 后端 controller / service（Imperative Shell） | 7 | 6 | 7 | 6 | — |
| 标书生成 Agent（`biddraftagent`） | 7 | 6 | 7 | 7 | 边界见 ARCHITECTURE.md §架构门禁口径 |
| 项目权限守卫（`ProjectAccessScopeService`） | 7 | 6 | 8 | 7 | 门禁由 `ProjectAccessGuardCoverageTest` 守 |
| 前端视图（`src/views/`） | 6 | 5 | 6 | 5 | 测试覆盖偏低，待补 |
| 前端组件（`src/components/`） | 7 | 5 | 6 | 5 | — |
| Flyway 迁移 | 7 | 6 | 7 | 8 | 现有生成器覆盖回滚；schema 文档由 `db:generate-schema` 生成 |
| E2E（Playwright） | 6 | 6 | 7 | 6 | — |
| 遗留 `frontendDemo` / `demoPersistence` | 4 | 4 | 4 | 4 | 清理对象，见 tech-debt-tracker |

## 数据来源

- `docs/reports/data-permission-coverage-audit.md`
- `docs/reports/doc-consistency-check-report.md`
- `docs/architecture/production-readiness-report.md`、`doc-consistency-report.md`
- `backend/implementation-notes.md`
- 门禁现状：`RELIABILITY.md §审计与质量门禁`

## 差距登记

分数 `<5` 的项，请同步登记到 `docs/exec-plans/tech-debt-tracker.md`。
