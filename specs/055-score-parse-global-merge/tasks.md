# AI 评分标准解析两阶段归并 + 表格行保真 - 任务拆解 (055)

> 状态标记：`[x]` 完成 / `[ ]` 待做。每个任务完成需附测试证据。TDD：先测后码（Red → Green）。

## 阶段 1：domain 纯核心（表格保真）

- [x] **T1 表格块定位** `ScoreTableBlockLocator`：定位 markdown 管道表格块（连续 `|` 行，含分隔行 `---`），
  仅保留命中评分语义（评分/评审/分值/评审标准等，复用既有关键词语义）的块；返回行文本列表。
  - 验收：评分表块被定位；非评分表格（如报价格式表）不误入；无表格返回空。
- [x] **T2 表格行解析** `ScoreTableRowParser`：一行 → 一个 ScoreCandidate。
  分值列（从右向左首个"得X分/X分"或纯数字列）→ weight；除编号列外最长文本列 → detail；
  列语义无法识别 → 整行原文作 detail（不丢行）；weight 提取失败置 null。
  - 验收：典型评分表行解析正确；合并单元格空列降级整行；detail 与行原文逐字一致。
- [x] **T3 表格感知切片** `ScoreTableAwareChunker`：包装 `List<DocumentChunk>`——
  含管道表格行且超安全阈值（复用 MAX_CHARS 语义）的 chunk 按行边界重切，边界永不落在行内。
  - 验收：SC-3 任意切片首尾均为完整行或非表格文本边界。

## 阶段 2：domain 纯核心（归并结果守卫）

- [x] **T4 归并守卫** `ScoreMergeResultGuard`：规则 1 幻觉过滤（归并输出 detail 归一化后 ⊆ 章节原文，否则丢弃+计数）；
  规则 2 漏项回补（候选池高置信项 = sourceText ⊇ detail 且 weight 非空，未被归并输出覆盖 → 回补）。
  - 验收：US1-AC4 + 边界情况（幻觉丢弃/漏项回补/全保留/全丢弃）。

## 阶段 3：infrastructure（LLM 归并裁决 + 召回五）

- [x] **T5 归并契约** `ScoreGlobalMergeOutput`（Jackson 绑定，风格对齐 `ScoreCandidateOutput`）。
- [x] **T6 归并 prompt** `ScoreParsePrompts.buildGlobalMergePrompt(poolJson, sectionText)`：
  含契约 §5 五条硬性规则、候选池 JSON、章节原文；静态纯函数可断言。
  - 证据：`ScoreGlobalMergePromptTest` 断言关键规则文案与输入嵌入。
- [x] **T7 归并执行器** `ScoreGlobalMergeExecutor`：候选池摘要 JSON 组装（detail 截断 200 字）；
  单次调用重试 1 次，两连败返回 empty；候选 <2 跳过；降级/跳过记日志。
  - 验收：prompt 测试 + 编排层降级测试覆盖（LLM 边界不 Mock，走编排层 fake 注入）。

## 阶段 4：application 编排接入

- [x] **T8 编排改造** `ScoreParseAppService.doExecuteParse`：
  召回四之后并入召回五（locator+parser 直抽表格行候选）；
  ghostFilter+invalidWeight 之后、WeightSumCheck 之前插入全局归并（fail-open + Guard）；
  归并摘要日志（输入/输出计数、合并组数、幻觉丢弃数、回补数、降级标记）。
  - 验收：新增编排用例——归并成功路径 / 归并失败降级路径 / 候选不足跳过路径，任务均 COMPLETED。
- [x] **T9 召回四切片替换**：召回四的 chunk 列表经 `ScoreTableAwareChunker` 后处理再进 LLM。

## 阶段 5：验证与收尾

- [x] **T10 全量验证**：`mvn test`（scoreparse 全包 + ArchitectureTest 等守卫）全绿；
  行数门禁（新类 <200 行，OpenAiScoreAnalyzer/ScoreParseAppService 不超 300 硬上限）；
  `npm run build` 前端不受影响（无前端改动）。
- [ ] **T11 文档**：implementation-notes（决策与取舍）+ wiki 回填（`score-parse-service.md` 追加 055 小节）+ lessons（若踩坑）。
- [ ] **T12 PR**：push 分支 + Gitee PR（`bash scripts/gitee-pr-helper.sh create` 或 pr-create.sh）。
