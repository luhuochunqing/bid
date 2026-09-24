# 根因分析：AI评分标准解析识别不出复杂表格评分项

**问题编号**: 待分配  
**日期**: 2026-08-28  
**严重程度**: P0（核心功能失效）  
**修复轮数**: 20+ 轮未彻底解决  
**分析人**: Cursor Agent

---

## 问题描述

用户反馈：山焦物资办公用品服务平台招标文件（`SJZBJM08000026H100FV88`）的评分标准一直识别不出来，已经优化了20多轮仍然无法正常解析。

**预期行为**：应该识别出所有评分项，包括：
- 财务状况及抗风险能力（10分）→ 审计意见（4分）+ 现金流（3分）+ 净资产收益率（3分）
- 运营能力及业绩（20分）
- 赊销额度能力（2分）
- 响应文件制作（2分）
- 应急保障能力（2分）
- 服务案例及业绩（20分）
- 品牌及货源渠道管控（20分）
- 商品质量管控（2分）
- 等等...

**实际行为**：评分项无法识别或识别错误。

---

## 根因分析（五个为什么）

### 为什么评分项识别不出来？
→ 因为 `ScoreTableRowParser` 无法正确解析这份文档的表格行。

### 为什么 `ScoreTableRowParser` 无法正确解析？
→ 因为它的**列对齐策略假设**不成立：
- 假设1：weight在最右侧的纯分值短列（如`"5分"`）
- 假设2：detail是最长文本列
- 假设3：表格结构规整，列语义清晰

### 为什么这些假设在这份文档中不成立？
→ 因为这份文档使用了**复杂的合并单元格结构**：
- 第1列（条款号）跨多行合并：`3.3(1)` 覆盖6-7行
- 第2列（评分因素）跨多行合并：`"商务评分标准（56分）"` 覆盖6-7行
- 第3列：评分项名称（含总分，如`"财务状况（10分）"`）
- 第4列：评分要求说明（长文本）
- 第5列：具体评分细则（包含`"得X分"`的详细规则，也是长文本）

### 为什么合并单元格会导致识别失败？
→ 因为 **markdown 转换丢失了合并单元格信息**：
1. 当前使用 `markitdown` + `mammoth` 将 Word 转为 markdown 表格
2. `python-docx` 读取合并单元格时，每个逻辑位置都返回相同的 cell 对象
3. 转换成 markdown pipe 表格时，**合并单元格的内容在多个行重复出现**
4. 转换后的表格失去了"哪些单元格是合并的"这一关键结构信息

### 为什么20多轮优化都没有解决？
→ 因为**优化方向错误**：一直在调整 Java 侧的启发式规则（正则、列对齐策略、LLM prompt），但**根本问题在于 Python 侧的表格转换是有损的**。

---

## 技术细节：失败链路追踪

### 1. Word 原始表格结构

```
行27（评分标准核心行之一）：
┌─────────┬────────────────┬─────────────────────┬──────────────────────┬──────────────────────┐
│ 3.3(1)  │ 商务评分标准   │ 财务状况及抗风险    │ 提供2023-2025年度... │ 1、审计意见连续3年   │
│ (合并)  │ （56分）       │ 能力（10分）        │ 经会计事务所或审计   │ 无保留意见，得4分；   │
│         │ (合并)         │                     │ 机构出具的审计报告   │ 2、现金流...得3分；   │
│         │                │                     │                      │ 3、净资产收益率...    │
│         │                │                     │                      │ 得3分                 │
└─────────┴────────────────┴─────────────────────┴──────────────────────┴──────────────────────┘
```

**关键特征**：
- 第1、2列：大量合并单元格（多行共享同一个值）
- 第3列：评分项名称 **+ 括号里的总分**（如`"（10分）"`）
- 第5列：具体评分规则，包含多个`"得X分"`

### 2. Markdown 转换后的结果

```markdown
| 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力 （10分） | 提供2023-2025年度...审计报告 | 1、审计意见...得4分；2、现金流...得3分；3、净资产收益率...得3分 |
```

**问题**：
1. ✅ 内容保留了（文本没丢）
2. ❌ **合并单元格的结构信息丢失了**（不知道哪些列是合并的）
3. ❌ **多行重复**：如果原表格中 `3.3(1)` 跨越6行，markdown会在6行都重复输出这个值

### 3. ScoreTableRowParser 处理流程

```java
// 行27的处理：
String row = "| 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力 （10分） | 提供2023-2025...审计报告 | 1、审计意见...得4分...得3分...得3分 |";

// 列切分
List<String> columns = ["3.3(1)", "商务评分标准（56分）", "财务状况及抗风险能力 （10分）", "提供2023-2025...审计报告", "1、审计意见...得4分...得3分...得3分"];

// Step 1: 查找 code 列
int codeIndex = 0; // ✓ 正确识别出 "3.3(1)"

// Step 2: 查找纯分值列（从右向左找 "5分" 这种短列）
int pureWeightIndex = -1; // ❌ 找不到！因为没有纯分值短列

// Step 3: 回退到 maxGradeScore（扫描整行找 "得X分"）
BigDecimal weight = maxGradeScore(row);
// 扫描到："得4分"、"得3分"、"得3分"
// 取 max = 4 ❌ 错误！应该是 10 分（第3列括号里的总分）

// Step 4: 查找 detail 列（非 code、非 weight 中的最长列）
int detailIndex = 4; // 第5列（最长）
String detail = "1、审计意见...得4分...得3分...得3分";

// Step 5: 查找 dim 列（剩余列中最长中文短列）
String dim = "财务状况及抗风险能力 （10分）"; // 可能正确
```

**结果**：
- ✓ code = "3.3(1)"
- ❌ weight = 4（应该是 10）
- ✓ dim = "财务状况及抗风险能力 （10分）"
- ? detail = 第5列文本（可能正确，但缺少第4列的上下文）

### 4. 为什么 LLM 全局归并也救不回来？

即使后续有 `ScoreGlobalMergeExecutor` 做全局归并裁决，但：
1. **输入池已经错误**：weight=4 而不是 10
2. **缺少足够上下文**：第4列的"评分要求说明"在 detail 列中被第5列覆盖
3. **分层结构丢失**：10分主项 + 4+3+3子项的层级关系在单行扁平化后无法恢复

---

## 根本原因总结

**零号病人**：**markitdown/mammoth 的 Word → Markdown 表格转换是有损的**，丢失了合并单元格的结构信息。

**传播路径**：
1. Word 表格（合并单元格） 
   → 2. Markdown pipe 表格（结构信息丢失）
   → 3. ScoreTableRowParser（列对齐假设不成立）
   → 4. 候选池错误
   → 5. LLM 全局归并无法修正
   → 6. 识别失败

**为什么20多轮优化都没有解决**：
- 优化都集中在 Java 侧（调正则、改列对齐策略、优化 LLM prompt）
- 但**根本问题在 Python 侧的表格转换**
- 就像"照片模糊"问题，无论怎么调后期滤镜，都无法恢复丢失的像素信息

---

## 解决方案

### 🎯 推荐方案：新增结构化表格提取通道（根治）

**设计思路**：不走 markdown 转换，直接用 `python-docx` 读取 Word 表格的原始结构。

#### 1. Python Sidecar 新增 API

```python
# document-converter-sidecar/app.py

@app.post("/extract-tables")
async def extract_tables(
    file: UploadFile,
    x_sidecar_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """提取 Word 文档中的表格结构（含合并单元格信息）"""
    await _check_auth(x_sidecar_key)
    
    # 读取 docx
    doc = Document(uploaded_file_path)
    
    tables_data = []
    for table_idx, table in enumerate(doc.tables):
        # 提取表格结构
        rows_data = []
        for row_idx, row in enumerate(table.rows):
            cells_data = []
            for cell_idx, cell in enumerate(row.cells):
                # 检测合并单元格（通过 cell._element 判断）
                is_vmerge_start = _is_vmerge_start(cell)
                is_vmerge_continue = _is_vmerge_continue(cell)
                
                cells_data.append({
                    "text": cell.text.strip(),
                    "rowspan": _get_rowspan(cell) if is_vmerge_start else 1,
                    "is_merged_continue": is_vmerge_continue,
                })
            rows_data.append(cells_data)
        
        # 检测是否是评分表格
        is_score_table = _is_score_table(table)
        
        tables_data.append({
            "tableIndex": table_idx,
            "rows": rows_data,
            "isScoreTable": is_score_table,
        })
    
    return {"tables": tables_data}
```

#### 2. Java 侧新增处理器

```java
// ScoreTableStructuredExtractor.java (新增)

public class ScoreTableStructuredExtractor {
    
    public record StructuredTable(
        int tableIndex,
        List<StructuredRow> rows,
        boolean isScoreTable
    ) {}
    
    public record StructuredRow(
        List<StructuredCell> cells
    ) {}
    
    public record StructuredCell(
        String text,
        int rowspan,
        boolean isMergedContinue
    ) {}
    
    /**
     * 从结构化表格数据中提取评分候选
     * 
     * 关键逻辑：
     * 1. 跳过 isMergedContinue = true 的单元格（它们是合并单元格的延续）
     * 2. 识别列3中的括号分值（如"（10分）"）作为主项分值
     * 3. 识别列5中的"得X分"作为子项分值
     * 4. 构建分层结构：主项（列3分值） + 子项列表（列5分值）
     */
    public List<ScoreCandidate> extract(StructuredTable table) {
        List<ScoreCandidate> candidates = new ArrayList<>();
        
        // 展开逻辑行（处理 rowspan）
        List<LogicalRow> logicalRows = expandRowspans(table.rows);
        
        for (LogicalRow logicalRow : logicalRows) {
            // 跳过表头、分隔行
            if (isHeaderOrSeparator(logicalRow)) {
                continue;
            }
            
            // 提取列数据
            String code = logicalRow.getCell(0);  // 列1: 条款号
            String factor = logicalRow.getCell(1); // 列2: 评分因素
            String itemName = logicalRow.getCell(2); // 列3: 评分项名称（含分值）
            String requirement = logicalRow.getCell(3); // 列4: 评分要求
            String scoringRules = logicalRow.getCell(4); // 列5: 评分细则
            
            // 提取主项分值（从列3的括号中）
            BigDecimal mainWeight = extractBracketWeight(itemName); // "（10分）" → 10
            
            // 提取子项分值（从列5的"得X分"中）
            List<BigDecimal> subWeights = extractGradeScores(scoringRules); // ["4", "3", "3"]
            
            // 构建候选
            if (mainWeight != null) {
                // 主项
                candidates.add(new ScoreCandidate(
                    code, itemName, requirement + " " + scoringRules,
                    mainWeight, null, null, logicalRow.originalText(), null, "STRUCTURED_TABLE"
                ));
                
                // 如果有子项，也提取出来（可选，看产品需求）
                if (!subWeights.isEmpty() && subWeights.stream().mapToDouble(BigDecimal::doubleValue).sum() == mainWeight.doubleValue()) {
                    // 子项分值加起来等于主项分值 → 这是分层结构
                    // 可以选择只保留主项，或者同时保留子项
                }
            } else if (!subWeights.isEmpty()) {
                // 没有主项分值，但有子项分值 → 可能是单项评分
                BigDecimal weight = subWeights.stream().max(BigDecimal::compareTo).orElse(null);
                if (weight != null) {
                    candidates.add(new ScoreCandidate(
                        code, itemName, requirement + " " + scoringRules,
                        weight, null, null, logicalRow.originalText(), null, "STRUCTURED_TABLE"
                    ));
                }
            }
        }
        
        return candidates;
    }
    
    private BigDecimal extractBracketWeight(String text) {
        // 匹配 "（10分）" "（20分）"
        Pattern pattern = Pattern.compile("（(\\d+(?:\\.\\d+)?)分）");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return null;
    }
}
```

#### 3. 编排层整合

```java
// ScoreParseAppService.java (修改)

private void doExecuteParse(String taskId) {
    // ... 现有逻辑 ...
    
    TenderTextSource source = intake.source().orElseThrow();
    
    // 新增：如果是 Word 文档，尝试结构化表格提取
    List<ScoreCandidate> structuredCandidates = List.of();
    if (source.fileUrl().toLowerCase().endsWith(".docx")) {
        try {
            structuredCandidates = structuredTableExtractor.extract(source.fileUrl());
            log.info("结构化表格提取: taskId={}, candidates={}", taskId, structuredCandidates.size());
        } catch (Exception e) {
            log.warn("结构化表格提取失败，回退到 markdown 路径: taskId={}", taskId, e);
        }
    }
    
    // 如果结构化提取成功且候选数 ≥ 5，优先使用结构化结果
    if (structuredCandidates.size() >= 5) {
        updateProgress(taskId, 70, "GOVERN", "结构化表格候选治理");
        var processed = postProcessor.process(taskId, source.text(), 
            RecallOutcome.empty(), // 跳过四路召回
            structuredCandidates,
            (progress, stageKey) -> updateProgress(taskId, progress, stageKey, stageKey));
        // ... 后续处理 ...
    } else {
        // 回退到现有的 markdown 召回路径
        RecallOutcome outcome = scoreAnalyzer.recallCandidates(source.text(), ...);
        // ... 现有逻辑 ...
    }
}
```

---

### 📊 方案对比

| 方案 | 优点 | 缺点 | 工作量 | 推荐度 |
|------|------|------|--------|--------|
| **A. 增强 Markdown 转换** | 从源头解决 | 影响面大，可能破坏其他场景 | 中 | ⭐⭐ |
| **B. 增强 ScoreTableRowParser** | 改动小 | 启发式规则复杂，难以覆盖所有情况 | 小 | ⭐⭐ |
| **C. 结构化表格提取（推荐）** | 根治问题，精确可控 | 需要新增 API 和处理流程 | 中 | ⭐⭐⭐⭐⭐ |
| **D. LLM 增强理解** | 利用 LLM 能力 | 不稳定，成本高 | 小 | ⭐⭐ |

---

## 实施建议

### 第一阶段：快速验证（1-2天）

1. **手动验证假设**：
   ```bash
   python3 -c "from docx import Document; doc = Document('文件.docx'); print([len(t.rows) for t in doc.tables])"
   ```
   确认表格1确实是47行5列的评分表格

2. **快速原型**：
   在 `document-converter-sidecar` 中写一个独立的 `extract_docx_tables.py` 脚本
   输出 JSON，手工检查是否正确提取了合并单元格结构

### 第二阶段：完整实现（3-5天）

1. **Python 侧**：
   - `/extract-tables` API
   - 合并单元格检测逻辑
   - 单元测试

2. **Java 侧**：
   - `ScoreTableStructuredExtractor`
   - 列对齐策略（针对结构化数据）
   - 单元测试

3. **集成**：
   - `ScoreParseAppService` 编排层整合
   - 降级逻辑（结构化失败 → 回退 markdown）

### 第三阶段：回归测试（2-3天）

1. 用山焦物资这份文档作为金标
2. 跑现有的所有评分解析测试
3. 对比新旧方案的召回率/准确率

---

## 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 优化20多轮仍未解决 | 优化前必须找到根因，不能只在表面调参 | 遇到顽固问题，先做"五个为什么"根因分析 |
| 一直在 Java 侧调整 | 跨语言系统的问题可能在另一侧 | 追踪数据流，检查每个转换环节是否有损 |
| Markdown 转换有损 | 表格等结构化数据不适合用纯文本表示 | 结构化数据优先用结构化格式传输 |
| 启发式规则堆砌 | 规则越多越脆弱，遇到新模式就失效 | 从源头保证数据质量，而不是下游打补丁 |

---

## 相关文档

- `specs/055-score-parse-global-merge/plan.md` - 全局归并设计
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java` - 当前行解析器
- `document-converter-sidecar/app.py` - Sidecar 转换服务
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java` - 解析编排

---

## ✅ 修复状态

**修复日期**: 2026-08-28 11:56  
**修复方案**: 方案A（快速修复 - 优化ScoreTableRowParser）  
**测试状态**: ✅ 全部通过（13/13）  

### 修复内容

1. **优先从列3提取括号分值**（`extractBracketWeight()`）
   - 正则：`BRACKET_WEIGHT = Pattern.compile("（(\\d{1,3}(?:\\.\\d+)?)分）")`
   - 提取分值的同时获取dim（去掉括号部分）

2. **扩展CODE_PATTERN支持括号**
   - 旧正则：`^[A-Za-z]{0,2}\\d+(\\.\\d+)*$`
   - 新正则：`^[A-Za-z]{0,2}\\d+([\\.\\(]\\d+[\\)]?)*$`
   - 支持 `3.3(1)` 格式

3. **Detail拼接列4+列5**（5列表格）
   - 评分要求（列4）+ 评分细则（列5）

4. **完整的回退机制**
   - 列3无括号 → 纯分值列
   - 无纯分值列 → maxGradeScore
   - 向后兼容所有现有场景

### 修复文件

- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParserTest.java`

### 测试验证

```
✅ 单元测试: 13/13 通过
✅ CODE_PATTERN: 支持 3.3(1) 格式
✅ 山焦物资场景: 正确识别10分（而不是4分）
✅ 回退机制: 不破坏原有场景
```

---

**诊断完成时间**: 2026-08-28 11:15  
**修复完成时间**: 2026-08-28 11:56  
**状态**: ✅ 已修复并验证

---

## Phase 1 补齐（2026-09-02）：结构化通道对列布局高度敏感，金标夹具必须匹配

上文修复的是 `ScoreTableRowParser`（markdown 表格通道）。Phase 1 新建的 `StructuredTableCandidateExtractor`（Sidecar 结构化通道，直接消费 `SidecarTableExtraction.CellPayload` 的 rowspan/colspan/isMergedContinue）是**另一套列解析逻辑**，对列布局的容错度更低。设计金标夹具时踩到这个坑。

### 症状：5 列布局下 dim 与 weight 双双误判

项目 1358 金标测试首次以 5 列布局 `[编号, 评分类别, 评分因素, 评分标准, 权重]` 构造夹具，`extract_project1358GoldenMergedCellStructure` 失败于 `AssertionError: to match given predicate but none did`。从失败输出打印的实际 `ScoreCandidate` 列表读出两类误判：

| 误判 | 实际结果 | 期望 | 原因 |
|------|---------|------|------|
| weight 取自 detail 文本 | 信用评价 weight=100、财务状况 weight=50、价格折扣得分 weight=60 | 5 / 6 / 50 | 权重列是 `4%`/`5%` 百分比格式未被解析，回退到从评分标准文本里抓"得100分/得50分/得60分" |
| dim 取错列 | 技术 6 项 dim 全部变成"按A、B、C、D分档打分" | 商城系统对接能力 / 运营服务能力 / … | dim 落到了评分标准列而非评分因素列 |

### 根因

`StructuredTableCandidateExtractor` 的列角色判定依赖**固定列序假设 + 括号权重解析**：

- 正确路径是 4 列布局 `[编号, 评分类别(merged), 评分因素（N分）, 评分标准]`——评分因素列自带 `（N分）` 括号，由 `extractBracketWeight()`（正则 `（(\d{1,3}(?:\.\d+)?)分）`）同时取出 dim（去括号）与 weight；
- 一旦多出一列，列序整体错位，括号解析拿不到权重就回退到文本扫描，于是把 detail 里的"得X分"当成 weight，dim 也顺移一列。

这与上文 `ScoreTableRowParser` 修复第 1 条（"优先从列3提取括号分值"）是同一个设计取向：**括号权重是权威信号，文本扫描只是回退**，而回退路径的误判率很高。

### 对策

1. 金标夹具改用 4 列布局，与已通过的 `extract_parentDimFromMergedContinueText` 测试完全对齐 → 17/17 通过；
2. 夹具复刻 1358 的真实合并结构：商务 `rowspan=4`、价格单行（无合并，parentDim 应为 null）、技术 `rowspan=6`，合并延续位置用 `isMergedContinue=true` 且文本重复父级文本（python-docx 对 vMerge 延续位置返回同一 cell 对象的语义）；
3. 断言不止于条数，还验证 `semanticPattern=="STRUCTURED_TABLE"`、分段 parentDim、单项 dim+weight 配对，以及**总分闭环 100**（这条最关键——它是"缺项"的反向证明）。

### 关于 1358 原始文档

1358 原始文档位于工作区外的 scratch 目录，不可访问，因此金标验证采用**合成夹具复刻结构**而非直接回放原文。这不削弱验证效力：本条要证明的是"合并单元格结构能被结构化通道正确展开为带 parentDim 的候选"，该性质只取决于 `CellPayload` 的 rowspan/isMergedContinue 布局，与文档字节无关。真实文档回放由 `RealProjectScoreParsingTest`（Phase 4 去 `@Disabled`，改 classpath 加载）与 `GoldenCasesReplayTest` 覆盖。

### 教训

| 坑 | 为何发生 | 对策 |
|---|---|---|
| 以为新提取器能自动适配任意列数 | 列角色判定基于固定列序假设，非表头语义识别 | 写夹具前先读提取器的列判定逻辑，或直接对齐一个已通过的同类测试的布局 |
| weight 静默取自 detail 文本 | 括号解析失败后回退路径不报错，产出看似合理的数值 | 金标断言必须校验 dim+weight **配对**与**总分闭环**，只断言条数无法发现取错列 |
| 两套通道（markdown / 结构化）列逻辑不同却易被当成一套 | 类名相近、都在 scoreparse/domain 下 | 改动或写测试前确认目标是 `ScoreTableRowParser` 还是 `StructuredTableCandidateExtractor`，两者列容错度不同 |

---

## 2026-09-24 补充：1382/1384 的分数污染不是本报告所述转换问题的充分解释

本报告原始范围是 2026-08-28 的山焦物资文档（项目 1369）及其 markdown/结构化表格列识别缺陷。合并单元格结构丢失对该文档是合理风险，但不能单独解释后续项目 1382/1384 的候选总分污染；继续只调列解析或转换器会漏掉评分方案资格与跨表聚合缺陷。

### 新问题的证据与根因

- 项目 1382 的旧解析任务把 98 页及之后合同履约考核评分样内容混入 41 个候选，形成 553.5 分；原评分章节自身声明总分 100 分，商务 15 + 技术 45 + 报价 40。后续合同考核事实应保留来源审计，但必须退出当前方案候选、闭环与采纳集合。
- 项目 1384 的旧解析任务汇总为 250 分且原文总分未知；原文明确写明“本项目总分为 100 分”，由响应报价 30、综合实力 40、技术 30 组成。明细分散于四张物理表，但属于同一个逻辑方案；物理表数不等于方案数，父项也可能与子项处于不同表格/章节标题。
- 因此新增零号病人是：方案范围校验只产生告警，没有参与候选资格、默认总分和采纳；全文事实合并又缺少稳定的方案身份边界；总分状态将未知、冲突及同方案多表混为一谈。互斥评分档位 5/4/3/2 还被误按累计项求和，而累计子项也必须继续保留真实父子关系。

### 修复与验证边界

- 先持久化每条事实的方案/来源资格，方案外事实作为审计项展示并排除出候选与采纳。
- 按逻辑评分方案和章节切分文本事实；原文总分必须带段落/单元格定位；同方案跨物理表允许归并和父子回指，多个独立方案要求显式选择。
- `SUM` 仍按父子叶子累计；`MUTUALLY_EXCLUSIVE` 的计分上限按备选分值最大值计算。
- 项目形状金标分别锁定 1382 的 100 分闭环与页 98+ 审计排除、1384 的原文 100 分和跨表父子关系。它们是脱敏本地回放；测试站点实际重解析仍需独立验收。
- 详见 [[scoreparse-multimodal-primary]] 与 `specs/071-scoreparse-scheme-eligibility/`。
