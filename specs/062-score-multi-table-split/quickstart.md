# Quickstart: 多表评分标准自动拆分验证指南（062）

> 本文件为验证指南（run guide）。实现细节见 `tasks.md` 与 `plan.md`。

## 前提

- 开发宿主 `/Users/user/xiyu/worktrees/trae`（后端 18089、MySQL xiyu_bid_main 已由 dev-services 拉起）。
- 任务 worktree `/Users/user/xiyu/worktrees/xiyu-trae-score-multi-table-split`（纯源码编辑）。

## 单元测试（纯核心）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=ScoreTableSplitPolicyTest
mvn test -Dtest=ScoreFactShadowPipelineTest
```

**期望**：全绿。`ScoreTableSplitPolicyTest` 覆盖：
- 多表各声明不同总分且各自自洽 → 两张表均 `AUTO_APPROVED`，文档级 `AUTO_APPROVED`；
- 表 A 自洽 / 表 B 权重不自洽 → A `AUTO_APPROVED`、B `REVIEW_REQUIRED`，文档级 `REVIEW_REQUIRED`；
- 表 A 自洽 / 表 B 结构破损 → 文档级 `STRUCTURE_INCOMPLETE`，A 仍可落库；
- 全表无独立总分证据 → 无证据组合并校验，行为与现状一致；
- 单表自洽 → 与改造前结果逐字段一致（回归）。

## 端到端（Evidence Pipeline 多表场景）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=PdfEvidencePipelineTest
```

**期望**：新增多表用例 Red→Green——改造前多表文档整体 `REVIEW_REQUIRED` 且正式评分项为空；
改造后两张自洽表全部自动落库，`score_item` 出现两表全部评分项。

## 回归门禁

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=ScoreParseArchitectureTest
mvn test   # scoreparse 相关全量回归
```

```bash
cd /Users/user/xiyu/worktrees/trae
npm run build
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
```

**期望**：全绿；无新增 DB 迁移；无前端改动。

## 验收（人工 Smoke）

1. 测试环境上传一份含多张独立评分表（各表声明总分不同、表内自洽）的 docx 招标文件。
2. 进入 AI 评分标准解析，等待任务完成。
3. 断言：正式评分项列表出现两张表的全部评分项（不再为空）；run_summary 含 `tableSplits` 明细。
