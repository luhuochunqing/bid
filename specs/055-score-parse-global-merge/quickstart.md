# Quickstart: 验证指南

## 前置

```bash
export XIYU_DEV_CONFIRMED=1   # 仅开发宿主 trae 需要启动服务；本任务纯后端单测可跳过
```

## 单测验证（核心证据）

```bash
cd backend
mvn test -Dtest='ScoreTableBlockLocatorTest,ScoreTableRowParserTest,ScoreTableAwareChunkerTest,ScoreMergeResultGuardTest,ScoreGlobalMergePromptTest,ScoreParseAppServiceTest' -DfailIfNoTests=false
```

预期：全部 PASS。关键断言映射：

| 测试类 | 对应 spec 验收 |
|--------|----------------|
| ScoreTableBlockLocatorTest | US2-AC：含评分语义表格块被定位，普通表格不误入 |
| ScoreTableRowParserTest | US2-AC：一行=一候选，分值列对齐，合并单元格降级整行 |
| ScoreTableAwareChunkerTest | SC-3：切片边界均在表格行之间 |
| ScoreMergeResultGuardTest | US1-AC4 + 边界情况：幻觉丢弃、漏项回补 |
| ScoreGlobalMergePromptTest | FR-2/契约硬性规则 1-5 出现在 prompt |
| ScoreParseAppServiceTest（新增用例） | US1-AC4：归并失败降级后任务仍 COMPLETED |

## 全量回归（防破坏既有门禁）

```bash
cd backend && mvn test -Dtest='com.xiyu.bid.scoreparse.**' -DfailIfNoTests=false
```

## 行数门禁（宪法 IV）

```bash
node scripts/check-line-budget.mjs || npm run --prefix . lint:lines  # 以仓库实际脚本为准；pre-commit 自动执行
```

## 真实文档端到端（可选，需开发宿主）

项目 1347 重触发解析（POST `/api/projects/1347/score-parse`），完成后 GET items 验证 SC-1：
"投标人提供上架商品价格管控方案"独立成项且 weight=4；价格部分含 5+4 两项。
