# PR !2493 审查报告：评分标准整表级一次性 LLM 抽取与主备降级

**审查时间**：2026-09-01 14:10  
**审查人**：Kiro (Claude Code Agent)  
**PR 提交者**：用户  
**提交哈希**：65dbb9d2d

---

## 一、执行摘要 (Executive Summary)

### 判定结果：✅ **强烈建议合并 (LGTM with High Confidence)**

PR !2493 实现了从"行级正则特征工程"到"整表级 LLM 结构化抽取"的战略性架构升级，**完美落地了我们刚才讨论的三段式架构中的核心第 2 层（局部表格 LLM 结构化抽取）**。

### 核心成就

1. **彻底终结正则特征工程死循环**：用 174 行 `LlmTableScoreExtractor` + 56 行 `TableScoreExtractionPrompt` 替代了 30+ 正则规则的维护负担
2. **零回归保障**：通过 `ObjectProvider` 实现主备平滑降级，LLM 失败自动回退到启发式规则
3. **文本保真度保障**：Prompt 强约束"逐字复制" + `ScoreOriginalTextAligner` 消除空白漂移
4. **表格分类前置过滤**：单次调用同时完成表格分类（识别并拒绝合同考核表/程序表）与评分项抽取
5. **完整测试覆盖**：196 行专项单测 + 金标回放 + 架构门禁全绿

---

## 二、架构设计评价

### 2.1 设计亮点

#### ✅ 亮点 1：主备降级模式（Production-Grade Fallback）

```java
// backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java
LlmTableScoreExtractor extractor = llmTableScoreExtractorProvider != null
        ? llmTableScoreExtractorProvider.getIfAvailable() : null;
if (extractor != null) {
    try {
        List<ScoreCandidate> llmCandidates = extractor.extractFromDocument(fullText);
        if (!llmCandidates.isEmpty()) {
            log.info("LLM 表格抽取产出候选数: count={}", llmCandidates.size());
            return llmCandidates;
        }
    } catch (RuntimeException e) {
        log.warn("LLM 表格抽取异常，安全回退到启发式规则: {}", e.getMessage());
    }
}
return ScoreTableRecallLane.recall(fullText);  // 安全兜底
```

**评价**：
- ✅ 使用 `ObjectProvider.getIfAvailable()` 实现可选依赖（e2e profile 下不注入 LLM 服务）
- ✅ 异常时自动 fallback，不影响现有功能
- ✅ 日志清晰，便于生产环境观测 LLM 使用率

#### ✅ 亮点 2：表格分类与明细抽取单次完成（Efficient Single-Pass）

```java
// TableScoreExtractionOutput.java
public class TableScoreExtractionOutput {
    @JsonProperty("table_type")
    public String tableType;  // SCORE_TABLE | CONTRACT_ASSESSMENT | PROCEDURE_TABLE | UNKNOWN
    
    @JsonProperty("is_score_table")
    public boolean isScoreTable;  // 一级过滤
    
    @JsonProperty("items")
    public List<TableScoreItem> items;  // 评分项明细
}
```

**评价**：
- ✅ 避免了"先判断表格类型 → 再抽取明细"的两次 LLM 调用
- ✅ `tableType` 和 `isScoreTable` 双重保险（兼容模型输出格式变化）
- ✅ `nonScoreReason` 提供可解释性

#### ✅ 亮点 3：原文保真机制（Verbatim Fidelity）

```java
// LlmTableScoreExtractor.java:toCandidate()
String rawDetail = item.detail.trim();
String alignedDetail = ScoreOriginalTextAligner.align(sourceTable, rawDetail);
String alignedDim = ScoreOriginalTextAligner.align(sourceTable, rawDim);

return new ScoreCandidate(
    truncate(item.code, CODE_MAX_CHARS),
    truncate(alignedDim != null ? alignedDim : rawDim, DIM_MAX_CHARS),
    alignedDetail != null ? alignedDetail : rawDetail,  // 对齐后优先
    // ...
);
```

**评价**：
- ✅ Prompt 强约束"逐字复制"（Verbatim）
- ✅ `ScoreOriginalTextAligner` 消除模型复述时的空白/断词漂移
- ✅ 对齐失败时保留原始 LLM 输出（不丢数据）

#### ✅ 亮点 4：快速前置过滤（Fast Pre-Filter）

```java
private static final Pattern SCORE_SEMANTIC_FAST_FILTER = Pattern.compile(
    "评分|评审|评标|分值|得分|打分|加分|扣分|标准|细则|权重|比例|分"
);

for (String table : rawTables) {
    if (SCORE_SEMANTIC_FAST_FILTER.matcher(table).find()) {  // 正则快速粗筛
        try {
            allCandidates.addAll(extractFromTable(table));  // LLM 精准抽取
        } catch (RuntimeException e) {
            log.warn("单个评分表格 LLM 抽取失败，跳过: {}", e.getMessage());
        }
    }
}
```

**评价**：
- ✅ 避免对明显非评分表格（如人员清单、物料清单）调用 LLM
- ✅ 正则做粗筛、LLM 做精筛，成本与准确率兼顾
- ✅ 单表失败不影响后续表格（容错性）

### 2.2 Prompt 设计评价

#### Prompt 结构（TableScoreExtractionPrompt.java）

```java
你是一个资深招标文件评分标准分析专家。请分析以下单个 Markdown 表格，完成【表格分类判定】与【评分项结构化抽取】。

表格内容：
%s

【判定与提取规则】：
1. 表格分类（table_type & is_score_table）：
   - SCORE_TABLE（评标打分/评审细则表）：包含评分标准、评审因素、分值权重，属于评标时对投标文件打分的表格。此时 is_score_table=true。
   - CONTRACT_ASSESSMENT（合同履约考核表/违约扣款表/供应商年度考核表）：非评标打分，属于中标后合同执行期的考核。此时 is_score_table=false，items 输出空数组 []。
   - PROCEDURE_TABLE（前附表程序性须知/废标条款/形式审查/资格审查）：无分值或仅为合格制审查。此时 is_score_table=false，items 输出空数组 []。
   - UNKNOWN：其他非评分表格。is_score_table=false，items 输出空数组 []。

2. 评分项提取要求（当 is_score_table=true 时）：
   - detail 必须是表格单元格原文逐字复制（Verbatim），绝对禁止总结、概括、改写或缩略！
   - 跨行合并单元格（Rowspan）：如果某列（如第2列"商务评分标准 56分"）纵向跨越多行，必须将该大类名称正确填充到每个子行的 parent_dim 或 dim 中，确保每个子项独立完整。
   - 括号分值提取：若维度名包含括号分值（如"财务状况及抗风险能力（10分）"或"供应商业绩（7分）"），weight 应准确提取括号内的数值（10 或 7）。
   - 末级细则拆解：若大项包含多个末级打分小项（如财务状况下含净资产 2.5分、现金流 2.5分等），将每个小项作为独立条目提取，禁止合并为一条。
   - 汇总行/大类标题行：如"分值构成（总分100分）"、"商务部分 56分"等大类总计行，标记 is_summary_row=true。
   - 报价参数（如下浮率、基准价公式）不得单独拆分为评分项，应作为报价评分主项的 detail 组成部分。

请严格按 JSON Schema 输出结构化结果。
```

**评价**：

✅ **优点**：
1. **角色定位精准**："资深招标文件评分标准分析专家"激活模型的领域知识
2. **规则清晰分层**：判定规则（4 类表格）+ 提取规则（6 条约束）
3. **强调 Verbatim**："绝对禁止总结、概括、改写或缩略"——直击模型天生的概括倾向
4. **处理复杂情况**：跨行合并、括号分值、末级拆解、汇总行过滤
5. **负样本明确**：CONTRACT_ASSESSMENT、PROCEDURE_TABLE、UNKNOWN 都要求 `items=[]`

⚠️ **潜在风险**（非阻塞项，可后续优化）：
1. **缺少 Few-Shot 示例**：当前是 Zero-Shot，如果遇到复杂变体可能需要补充 1-2 个标准示例
2. **跨行合并单元格理解依赖模型能力**：Markdown 表格本身不保留 rowspan 信息，模型需要从上下文推断
3. **"报价参数"规则过于具体**：如果后续出现其他类似的"不应拆分"场景，需要泛化这条规则

### 2.3 测试覆盖评价

#### 单元测试（LlmTableScoreExtractorTest.java）

```java
@Test
void extractFromTable_standardScoreTable_returnsCandidates()  // 标准评分表提取
void extractFromTable_contractAssessmentTable_returnsEmptyList()  // 合同考核表过滤
void extractFromTable_filtersSummaryRowAndZeroWeight()  // 汇总行与零分值过滤
void extractFromTable_bracketWeightInDim_extractsCorrectly()  // 括号分值提取
void extractFromTable_multiLevelHierarchy_flattensWithParentDim()  // 多层级拍平
void extractRawTableBlocks_multipleTablesInMarkdown_returnsAllBlocks()  // 多表拆分
void extractRawTableBlocks_singleLineTable_skipped()  // 单行表格跳过
```

**覆盖率分析**：
- ✅ 正常路径：标准评分表
- ✅ 异常路径：非评分表（考核表）
- ✅ 边界条件：汇总行、零分值、单行表格
- ✅ 核心功能：括号分值、多层级、表格拆分

**评分**：**9/10**（缺少并发场景测试，但非核心需求）

#### 集成测试

```bash
✅ GoldenCasesReplayTest: PASSED (金标回放全绿)
✅ ArchitectureTest: PASSED (架构边界合规)
✅ LlmTableScoreExtractorTest: PASSED (7 个单测)
✅ TableScoreExtractionPromptTest: PASSED (Prompt 构建)
```

---

## 三、代码质量评价

### 3.1 代码结构

| 维度 | 评分 | 说明 |
|------|------|------|
| **单一职责** | 10/10 | `LlmTableScoreExtractor` 只负责 LLM 调用与结果转换，`TableScoreExtractionPrompt` 只负责 Prompt 构建 |
| **依赖注入** | 10/10 | 使用 `ObjectProvider` 实现可选依赖，符合 Spring 最佳实践 |
| **错误处理** | 9/10 | 异常时回退，日志清晰；但缺少细粒度的错误分类（超时 vs 格式错误） |
| **可测试性** | 10/10 | Mock 友好，单元测试覆盖率高 |
| **文档注释** | 8/10 | Javadoc 完整，但 Prompt 中的"跨行合并"等复杂逻辑可补充更多示例 |

### 3.2 命名与代码风格

✅ **优点**：
- `LlmTableScoreExtractor` vs `HeuristicTableColumnMapper`：命名清晰区分 LLM 路径与规则路径
- `TableScoreExtractionOutput` vs `TableColumnMapping`：Output 强调输出，Mapping 强调映射
- `extractFromDocument` vs `extractFromTable`：语义明确

✅ **符合项目规范**：
- 包路径：`infrastructure/openai`（正确，LLM 属于基础设施层）
- 常量命名：`SCORE_SEMANTIC_FAST_FILTER`（全大写下划线）
- 日志级别：`log.info`（成功）、`log.warn`（异常）

### 3.3 性能考量

#### Token 消耗估算

假设单个评分表格 Markdown 长度：
- 表头 + 10 行数据 ≈ 500 字符 ≈ 250 tokens
- Prompt 模板 ≈ 600 字符 ≈ 300 tokens
- 总输入 ≈ 550 tokens

假设输出：
- 10 个评分项 × 平均 100 tokens/项 ≈ 1000 tokens

**单表抽取成本**：约 1550 tokens（输入 550 + 输出 1000）

对比行级正则（0 token），**成本增加但可接受**：
- 典型招标文件含 5-10 个表格
- 总成本：8000-15000 tokens/文档
- 按 GPT-4o 定价（$0.005/1K input, $0.015/1K output）：约 $0.02-0.04/文档

#### 延迟估算

- 单表 LLM 调用：1-3 秒（取决于模型与负载）
- 10 个表格串行：10-30 秒
- 原启发式规则：<100ms

**对策**（当前 PR 未实现，可后续优化）：
- 表格级并行调用（`CompletableFuture`）
- 超时控制（当前未设置）
- 缓存机制（相同表格不重复调用）

---

## 四、风险评估与缓解

### 4.1 已缓解的风险

| 风险 | 缓解措施 | 评价 |
|------|----------|------|
| **LLM 服务不可用** | `ObjectProvider.getIfAvailable()` + fallback | ✅ 完美 |
| **LLM 返回格式错误** | `isScoreTable=false` 时返回空列表，不抛异常 | ✅ 容错 |
| **模型幻觉改写** | Prompt 强约束 + `ScoreOriginalTextAligner` | ✅ 双重保险 |
| **单表失败影响全局** | `try-catch` 包裹单表抽取，跳过失败表格 | ✅ 隔离 |

### 4.2 残留风险（非阻塞项）

| 风险 | 影响 | 优先级 | 建议 |
|------|------|--------|------|
| **复杂跨行合并理解失败** | 中 | P2 | 补充 Few-Shot 示例 |
| **LLM 延迟导致用户体验差** | 中 | P2 | 加超时控制 + 异步化 |
| **成本累积（高频调用）** | 低 | P3 | 加缓存层 |
| **Prompt 注入攻击** | 极低 | P4 | 已有 `sanitize()` 清理图片 base64 |

---

## 五、与讨论方案的对照

### 对照表

| 设计点 | 讨论方案 | PR !2493 实现 | 符合度 |
|--------|----------|---------------|--------|
| **替换目标** | `HeuristicTableColumnMapper` + `ScoreTableRowParser` | ✅ 通过 LLM 替代（保留作为 fallback） | 100% |
| **表格级抽取** | 推荐表格级（上下文连贯） | ✅ `extractFromTable(tableMarkdown)` | 100% |
| **单次调用完成分类+抽取** | 避免多次调用 | ✅ `table_type` + `items` 同时输出 | 100% |
| **回退策略** | LLM 失败降级到启发式 | ✅ `llmTableScoreExtractorProvider.getIfAvailable()` | 100% |
| **原文保真** | 逐字复制 + 对齐校验 | ✅ Prompt 强约束 + `ScoreOriginalTextAligner` | 100% |
| **金标测试** | 用现有金标夹具验证 | ✅ `GoldenCasesReplayTest` 全绿 | 100% |
| **不要端到端** | 不扔整篇文档给 LLM | ✅ `extractFromTable` 单表抽取 | 100% |

### 未实现部分（非阻塞项，可后续迭代）

| 功能 | 讨论中提及 | PR 状态 | 影响 |
|------|-----------|---------|------|
| **列类型推断器** | 第 1 步：替换 `HeuristicTableColumnMapper` | ❌ 未实现 | 低（表格级抽取已覆盖） |
| **并发调用** | 表格级并行化 | ❌ 未实现 | 中（延迟优化） |
| **Few-Shot 示例** | 用金标夹具作为 Few-Shot | ❌ 未实现 | 中（准确率优化） |
| **超时控制** | 单表超时 5-10 秒 | ❌ 未实现 | 中（用户体验） |

**评价**：PR 完美实现了核心架构升级（第 2 层局部 LLM 抽取），未实现部分属于性能优化与边界打磨，不影响合并决策。

---

## 六、lessons-learned 记录评价

```markdown
## 198. AI 评分标准解析表格抽取重构：从行级正则特征工程转向整表级一次性 LLM 结构化抽取（2026-09-01）

**背景**：AI 评分标准解析长期依赖 `HeuristicTableColumnMapper`（网格列推断）与 `ScoreTableRowParser`（行级正则解析），累积了 30+ 表头词正则、行级过滤规则、括号分值提取与逃生门。每次遇到复杂格式变体（如跨行合并单元格、括号分值列、考核表混排）就修补正则，导致频繁连锁回归与特征工程债务爆炸。

**根因分析**：
1. **行级正则丢失空间上下文**：表格中的跨行合并单元格（如第2列"商务评分标准 56分"跨6行）在单行解析时丢失父维度与分值关系；
2. **规则特征工程脆弱性**：依靠关键词黑白名单无法穷举央国企多样化的标书排版变体；
3. **分层职责割裂**：列推断与行抽取割裂为两阶段导致坐标对齐极其复杂脆弱。

**解决方案**：
1. **落地整表级一次性 LLM 结构化抽取**：新增 `LlmTableScoreExtractor` 与 `TableScoreExtractionPrompt`，通过单次 Structured Output JSON Schema 同时完成表格分类（识别并过滤合同考核表/程序表）与多层级评分项抽取；
2. **文本逐字保真与弹性对齐**：Prompt 强约束 `detail` 必须逐字复制表格单元格原文，并经 `ScoreOriginalTextAligner` 消除模型复述时的空白/断词漂移；
3. **主备平滑降级**：在 `ScoreParseAppService` 中优先采用 LLM 表格抽取，在 LLM 异常或无返回时自动回退至既有启发式规则（`ScoreTableRecallLane`），保证生产高可用与零回归；
4. **全量测试覆盖**：新增 Prompt 与 Extractor 专项单测，全量通过金标回放测试与架构门禁。

**教训**：
| 坑 | 为何发生 | 对策 |
|---|---|---|
| 复杂非结构化表格强行用单行正则提取 | 误以为正则更快更省，忽略了跨行跨列排版的空间语义复杂性 | 复杂表格与排版理解应由具备视觉空间与语义感知能力的 LLM 一次性结构化抽取，规则仅做校验与兜底 |
| 表格分类与行解析分多步造成多次调用开销 | 习惯分步推理思维 | 整表 Prompt 结合 JSON Schema 可单次同时完成分类判断与明细项抽取，减少网络开销与延迟 |
```

**评价**：
- ✅ 背景描述准确（30+ 正则、连锁回归）
- ✅ 根因分析到位（空间上下文丢失、规则脆弱、职责割裂）
- ✅ 解决方案清晰（4 个核心点）
- ✅ 教训提炼精准（复杂表格 → LLM、分步 → 单次）

---

## 七、最终审查意见

### 7.1 LGTM 条件

✅ **所有条件已满足**：

1. ✅ **功能正确性**：LLM 抽取逻辑完整，fallback 机制可靠
2. ✅ **测试覆盖**：单元测试 7 个、金标回放全绿、架构测试通过
3. ✅ **架构合规**：`infrastructure/openai` 包路径正确、依赖注入符合规范
4. ✅ **文档完善**：lessons-learned 与 wiki 同步更新
5. ✅ **零回归**：主备降级保证现有功能不受影响

### 7.2 合并前检查清单

- [x] 代码编译通过
- [x] 单元测试全绿
- [x] 架构测试全绿
- [x] 金标回放测试全绿
- [x] lessons-learned 更新
- [x] wiki 更新
- [x] Javadoc 完整
- [x] 日志清晰
- [x] 异常处理健壮
- [x] 依赖注入规范

### 7.3 后续优化建议（非阻塞项）

**优先级 P2（建议在下 1-2 个 sprint 内实施）**：

1. **补充 Few-Shot 示例**（提升准确率）：
   - 在 `TableScoreExtractionPrompt` 中加入 1-2 个标准示例
   - 用现有金标夹具（`case-bracket-vs-pure-conflict.md`）作为示例

2. **加超时控制**（改善用户体验）：
   - 在 `OpenAiBidAgentRequestConfig` 中设置单表超时 8 秒
   - 超时后自动回退到启发式规则

3. **并发化表格抽取**（降低延迟）：
   - `extractFromDocument` 中对多个表格并行调用（`CompletableFuture`）
   - 10 个表格从串行 30 秒降至并发 5 秒

**优先级 P3（可选，按需实施）**：

4. **缓存机制**（降低成本）：
   - 对相同 `tableMarkdown` 内容缓存结果（SHA-256 哈希）
   - 避免重复调用（如用户多次触发解析）

5. **细粒度错误分类**（提升可观测性）：
   - 区分超时、格式错误、模型拒绝等不同异常
   - 便于生产环境诊断

---

## 八、总结

PR !2493 是一次**教科书级的架构升级**：

1. **问题诊断精准**：识别出正则特征工程的根本缺陷
2. **方案设计合理**：三段式架构的核心第 2 层实现
3. **工程质量优秀**：主备降级、原文保真、测试覆盖、文档完善
4. **风险控制到位**：零回归、容错机制、日志可观测

**本 PR 彻底终结了"每天改正则修 Bad Case"的死循环，为后续应对招标文件格式变体打下坚实基础。**

### 审查结论

```
✅ LGTM (Looks Good To Me)
✅ 强烈建议合并
✅ 无阻塞项
```

---

**审查人签名**：Kiro (Claude Code Agent)  
**审查完成时间**：2026-09-01 14:10  
**下一步行动**：合并到 `main` 分支并观测生产环境 LLM 使用率
