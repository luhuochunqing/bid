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
- 测试环境当前发布版本已能在 `/review` 看到全部 8 个 PDF facts，Sidecar 分页拼接与非评分表过滤已生效。
- `/items` 仍只有旧的“公司综合实力 6 分”，因为完整性决策为 `REVIEW_REQUIRED`，按安全策略没有覆盖旧项。
- `runSummary` 明确记录 `fallbackReason=WEIGHT_SUM_MISMATCH`、`originalTotalWeight=100`、声明总分 30。
- 真实 `/convert` Markdown 把表尾输出为 `| 合计<br>得分 | 满分<br>（100分） |`；现有正文正则漏掉
  100，却匹配评分细则里的局部 `满分30分` / `满分10分`。Artifact 中同一行的结构化 cell 则完整保留
  `合计\n得分` 和 `满分\n（100分）`。

## 修复原则

1. 先让 1381 类 PDF 的完整评分表通过确定性链路产生 8 个候选项。
2. 保留 `REVIEW_REQUIRED` 不覆盖正式评分项的安全策略；如果 8 项合计 100 且证据完整，应自然判定为 `AUTO_APPROVED`。
3. 不把真实 PDF 入仓；测试使用合成表格 fixture 复现版式：分页续表、无表头续表、表尾合计行、局部满分描述。
4. 声明总分以结构化汇总行作为权威证据；Markdown 只作为无结构化证据时的兼容兜底。

## Phase 7 执行清单

- [x] 红测：用 `<br>` 分隔的真实 Markdown 形态稳定复现 30 分误判。
- [x] 纯核心：结构化汇总行识别、总分证据提取、冲突阻断、来源记录。
- [x] 接线：Artifact → Grid → summary evidence → completeness。
- [x] 绿测：1381 八项合计 100 且 `declaredTotalSource=STRUCTURED_SUMMARY_ROW`。
- [x] 保护测试：没有结构化总分时仍走正文；不同结构化总分不得自动通过。
- [x] 回归与架构/静态门禁。
- [ ] 重新部署测试环境后，重新解析项目 1381，并同时核对 `/review`、`/items` 与服务端日志。

## 验收命令

- `pytest document-converter-sidecar/tests/test_pdf_table_artifact.py -q`
- `mvn -f backend/pom.xml -Dtest=PdfEvidencePipelineTest,ScoreCompletenessPolicyTest test`

## Phase 7 本地验证证据（2026-09-12）

- 红测：真实 `<br>` Markdown 形态稳定复现 `declaredTotalScore=30 / REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH`。
- 修复后后端相关回归：77 tests passed，覆盖 PDF Evidence Pipeline、完整性、应用服务落库、人工复核和金标回放。
- 架构与验收：16 tests passed（`FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`、`ScoreCompletenessAcceptanceTest`）。
- Sidecar PDF 契约：19 tests passed。
- Java 静态质量：修改生产文件 Checkstyle 0 violation、PMD passed、SpotBugs 0 bug。
- 治理：line-budget、wiki:check、doc-governance、agent-lock-check、`git diff --check` 全部通过。

> 测试系统部署、项目 1381 重新解析及 `/review`、`/items` 实机复验尚未执行，因此下方完成定义的第 2～5 项保持未完成。

## 前序修复验证证据（2026-09-12，已被线上复验推翻最终结论）

- 真实 PDF smoke：`extract_pdf_tables_artifact(project-1381-tender.pdf)` 输出 20 张表、0 warning。
- 误判防线：第 20 页 `资格/形式/响应性评审` 表为 `isScoreTable=false`。
- 完整评分表：第 24/25 页分页续表合并为同一张 `isScoreTable=true` 表，10 行 = 表头 + 8 个评分项 + 合计行。
- 后端旧合成测试曾输出 8 个候选项和 `AUTO_APPROVED`，但测试手工提供了正则容易识别的
  `合计 得分 满分 （100分）`，没有复现真实 `<br>`/列分隔损失，因此不能作为端到端验收结论。
- 回归命令：
  - `pytest document-converter-sidecar/tests/test_pdf_table_artifact.py document-converter-sidecar/tests/test_extract_tables_v2.py -q` → 19 passed。
  - `mvn -f backend/pom.xml -Dtest=DeterministicScoreFactExtractorTest,PdfEvidencePipelineTest,ScoreCompletenessPolicyTest,ScoreCompletenessAcceptanceTest,ScoreParseAppServiceTest test` → 55 tests passed。

## 完成定义

代码完成不等于测试系统完成。Phase 7 只有在以下证据同时成立后才能勾选完成：

1. 本地 Red→Green 及相关回归全部通过；
2. 修复版本已部署到测试系统；
3. 对项目 1381 重新解析后 `/review` 为 8 个事实且 `AUTO_APPROVED`；
4. `/items` 返回同样 8 项、权重合计 100；
5. 日志中的声明总分来源为结构化汇总行，不再出现 30 分误判。
