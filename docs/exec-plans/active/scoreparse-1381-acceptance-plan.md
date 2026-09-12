# AI 评分解析 1381 端到端收敛计划

## 目标

以项目 1381 的招标 PDF 为验收基线：重新解析后，`/api/projects/1381/score-parse/items` 必须展示招标文件“综合评分法”表中的全部评分项，而不是只证明 Sidecar/facts 局部可用。

## 期望输出

来源：项目 1381 招标文件 `张家口银行股份有限公司宣传品、办公用品和办公耗材电商平台采购项目(最终版).pdf`，“综合评分法”表。

| 编号 | 评分项 | 权重 |
|---|---|---:|
| 1 | 投标报价 | 40 |
| 2 | 公司综合实力 | 6 |
| 3 | 类似业绩 | 10 |
| 4 | 技术方案 | 10 |
| 4 | 供货方案 | 10 |
| 5 | 人员配备 | 6 |
| 6 | 质量管理方案 | 10 |
| 7 | 售后服务方案 | 8 |

总权重：100。

说明：招标文件自身存在两个编号 `4`，验收按原文保留，不自行重编号。

## 当前证据

- 测试环境 `/items` 仅返回 1 项：公司综合实力 6 分。
- 测试环境 `/review` 能看到 PDF facts，但只有 7 个评分项事实，漏掉 `类似业绩（10分）`。
- Sidecar 原始 PDF artifact 实际抽到了 `类似业绩（10分）`，但 page 25 是无表头续表；后端把续表首行当表头跳过。
- page 20 的资格/形式/响应性评审表被误标为 score table，污染 facts。
- 完整性策略把正文里的局部 `满分30分` 当成整表总分，未优先识别表尾 `满分（100分）`。

## 修复原则

1. 先让 1381 类 PDF 的完整评分表通过确定性链路产生 8 个候选项。
2. 保留 `REVIEW_REQUIRED` 不覆盖正式评分项的安全策略；如果 8 项合计 100 且证据完整，应自然判定为 `AUTO_APPROVED`。
3. 不把真实 PDF 入仓；测试使用合成表格 fixture 复现版式：分页续表、无表头续表、表尾合计行、局部满分描述。

## 验收命令

- `pytest document-converter-sidecar/tests/test_pdf_table_artifact.py -q`
- `mvn -f backend/pom.xml -Dtest=PdfEvidencePipelineTest,ScoreCompletenessPolicyTest test`

## 已验证证据（2026-09-12）

- 真实 PDF smoke：`extract_pdf_tables_artifact(project-1381-tender.pdf)` 输出 20 张表、0 warning。
- 误判防线：第 20 页 `资格/形式/响应性评审` 表为 `isScoreTable=false`。
- 完整评分表：第 24/25 页分页续表合并为同一张 `isScoreTable=true` 表，10 行 = 表头 + 8 个评分项 + 合计行。
- 后端决策：1381 类合成 artifact 走 `StructuredTableExtractionHandler`，输出 8 个候选项，权重 `40/6/10/10/10/6/10/8`，`leafWeightSum=100.00`，`declaredTotalScore=100.00`，`decision=AUTO_APPROVED`。
- 回归命令：
  - `pytest document-converter-sidecar/tests/test_pdf_table_artifact.py document-converter-sidecar/tests/test_extract_tables_v2.py -q` → 19 passed。
  - `mvn -f backend/pom.xml -Dtest=DeterministicScoreFactExtractorTest,PdfEvidencePipelineTest,ScoreCompletenessPolicyTest,ScoreCompletenessAcceptanceTest,ScoreParseAppServiceTest test` → 55 tests passed。
