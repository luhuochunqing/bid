# Quickstart：根治评分项权重合计缺口 — 验证指南

**Branch**: `agent/trae/047-fix-score-weight-gap` | **Date**: 2026-08-18
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

> 本指南为**验证/运行指南**，不含实现细节。实现落在 `tasks.md` 与代码。

## 验证目标

1. 后端纯核心 `WeightNormalizationPolicy` 对任意候选权重集归一后**合计恒=100**（含缺口/超载/恰好100/合计0）。
2. 归一化输出**确定性可复现**、相对比例保持。
3. `summary.originalTotalWeight` 透出正确：归一化时为原合计，未归一化为 `null`。
4. 落库 items 权重合计=100，前端 weightWarning 告警不再出现（project 212 复测）。

## 前提

- 后端可编译运行（`XIYU_DEV_CONFIRMED=1` 环境，主工作区 trae，端口 18089）。
- MySQL 8.0 + Flyway 迁移已应用（`V1194__add_original_total_weight.sql`）。
- 评分解析侧已部署至测试环境（project 212）可触发真实解析。

## 快速验证步骤

### A. 纯核心单测（不依赖运行环境，最快）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=WeightNormalizationPolicyTest
# 期望: 全绿。覆盖 ≥10 用例（缺口/超载/恰好100/合计0/单100/整数舍入/确定性/相对比例/空集）。
```

### B. 编排 + 持久化 + 契约验证（后端集成）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=ScoreParseAppServiceTest   # 若存在；否则手动触发
```

或手动触发一次解析后检查：

```bash
# 触发解析（选择 project 212 及一份投标文件）后查询评分项清单
curl -s http://127.0.0.1:18089/api/projects/212/score-parse/items | python3 -m json.tool
# 期望:
#   summary.totalWeight == 100
#   weightWarning == false
#   originalTotalWeight == 90 (若该投标原始合计 90，当归一化发生)
# SQL 复核落库合计:
#   SELECT project_id, SUM(weight) FROM score_item WHERE project_id=212 GROUP BY project_id;
#   -- 期望 SUM(weight) == 100
# 信号复核:
#   SELECT original_total_weight FROM score_parse_task
#     WHERE project_id=212 AND task_type='PARSE' ORDER BY id DESC LIMIT 1;
#   -- 期望 90（归一化发生）或 NULL（未归一化）
```

### C. 前端回归（真实浏览器，可选，验证 FR-007 决策不影响核心）

- 打开 project 212「评分对标」抽屉，确认不再出现"权重合计与 100 分不符"（兜底提示由 normalize 消除）。
- 若前端后续接入"原 90 已补齐"提示条（FR-007，产品确认后），再据此字段渲染。

### D. 变更/契约完整性检查

```bash
cd /Users/user/xiyu/worktrees/trae
npm run check:line-budgets && npm run check:doc-governance
cd backend && mvn -Pjava-quality checkstyle:check pmd:check
# 期望: 全部通过；迁移 V/U 成对；无模板残留。
```

## 完成判据

- [ ] A 单测全绿（≥10 用例覆盖全部分支）
- [ ] B 中 `summary.totalWeight==100` 且 `originalTotalWeight` 语义正确
- [ ] B 中 SQL `SUM(weight)==100`（落库收敛）
- [ ] C 前端不再出现 weightWarning 缺失告警（project 212 复测）
- [ ] D 门禁全过，迁移 V/U 成对

> 详细字段语义见 [contracts/score-parse-api.md](./contracts/score-parse-api.md)。
> 数据约束见 [data-model.md](./data-model.md)。