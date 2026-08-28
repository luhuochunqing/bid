---
title: 评分解析优化记录
space: engineering
category: lessons-learned
sources:
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java
updated: 2026-08-28
health_checked: 2026-08-28
---

# 评分解析优化记录

## 2026-08-28 列对齐策略修复

### 问题
山焦物资招标文件评分标准识别错误，财务状况（10分）被识别为4分。

### 根因
`ScoreTableRowParser`列对齐策略假设错误：优先从最右侧找纯分值列，找不到时用`maxGradeScore`扫描整行"得X分"取最大值，忽略了列3括号中的主项分值。

### 修复
1. 优先从列3提取括号分值（如`"财务状况（10分）"` → `10`）
2. 扩展`CODE_PATTERN`支持括号格式（支持`3.3(1)`）
3. Detail拼接列4+列5（完整评分上下文）
4. 完整回退机制（向后兼容）

### 效果
- 准确率: 50% → 100% (+100%)
- 单元测试: 13/13通过
- E2E测试: 9/9通过（山焦物资文档）

### 相关文档
- 根因分析: `docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md`
- 代码: `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java`
