---
title: 竞品公司自定义输入与竞争对手库治理
space: engineering
category: module
tags: [竞品, 竞争对手, 字典治理, 结果确认, 幂等upsert, 权限统一]
created: 2026-08-25
updated: 2026-08-25
health_checked: 2026-08-25
sources:
  - specs/055-competitor-custom-input/spec.md
  - specs/055-competitor-custom-input/plan.md
  - specs/055-competitor-custom-input/contracts/competitor-api.md
  - backend/src/main/java/com/xiyu/bid/competitionintel/
  - src/views/System/CompetitorLibrary.vue
backlinks:
  - _index
  - modules
  - roles-and-permissions
  - flyway-migration-pitfalls
---
# 竞品公司自定义输入与竞争对手库治理

## 概览

竞品公司名称从"前端硬编码 24 常量（`COMPETITOR_COMPANY_OPTIONS`）"改为"后端全局竞品字典
`competitors` 动态加载 + 输入即建"，并补齐治理能力（改名/合并/停用）。落地《竞品公司自定义输入》
文档的方案 **B1**：录入端零门槛，治理端兜底。

## 核心链路

- **录入端（结果确认页）**：竞品下拉从 `GET /api/ai/competition/competitors` 动态加载 + `allow-create`；
  输入不存在的名字时，提交时幂等 upsert 入库（`INSERT ... ON DUPLICATE KEY UPDATE`），不打断登记流程。
- **治理端（系统设置 → 竞争对手库）**：治理角色可改名 / 合并（源竞品停用 + `competition_analyses.competitor_id`
  迁移到目标）/ 停用（软删除）。

## 数据模型

| 变更 | 说明 |
|---|---|
| `competitors` 加 `status`（ACTIVE/DISABLED） | 软停用，停用后不出现在录入下拉 |
| `competitors.name` 加唯一约束 | 幂等与并发防重兜底 |
| `competitors` seed 24 竞品 | 迁移现有硬编码常量，录入体验不退化 |
| `project_result_competitor` 不变 | 快照只存 name（不加外键），历史不受字典变动影响 |

## API（`/api/ai/competition`）

| Method | Path | 权限 | 语义 |
|---|---|---|---|
| GET | `/competitors` | isAuthenticated | 默认仅 ACTIVE；`?includeDisabled=true` 返回全部 |
| POST | `/competitors` | `competitor.create` | 幂等 upsert（body 只收 name） |
| PUT | `/competitors/{id}` | `competitor.govern` | 改名 |
| POST | `/competitors/{id}/merge` | `competitor.govern` | 合并到 targetId |
| POST | `/competitors/{id}/disable` | `competitor.govern` | 软停用 |

## 权限（Constitution VI）

- `competitor.create` → `bid-Team` / `bid-TeamLeader` / `bid-projectLeader` / `/bidAdmin` / `bid-SystemAdmin`（admin 经 `all` 覆盖）
- `competitor.govern` → `/bidAdmin` / `bid-TeamLeader` / `bid-SystemAdmin`（admin 经 `all` 覆盖）
- 权限键注册 `RoleProfileCatalog` + Flyway `V1203` 同步；Controller 用 `hasAuthority`，禁止 `hasAnyRole`。

## 关键决策

1. **B1 而非 B2**：不引入"标准库 vs 临时录入"双态，录入零门槛、治理兜底。
2. **快照只存 name**：`project_result_competitor` 不加 `competitor_id` 外键，历史快照与字典解耦。
3. **历史快照不修复**：合并/改名不回溯改写历史 name，理由为审计真实性（而非复杂度）；报表只对合并后新数据规范。
4. **幂等 upsert 用原生 `INSERT ... ON DUPLICATE KEY UPDATE`**：避免 `@Transactional` 下 catch
   `DataIntegrityViolationException` 触发 rollback-only 的坑。
