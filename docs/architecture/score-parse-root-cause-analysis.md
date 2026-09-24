# 评分项解析根因分析与治理方案

**日期**: 2026-09-23
**状态**: 已确认根因，方案待评审
**范围**: `backend/src/main/java/com/xiyu/bid/scoreparse/`、`document-converter-sidecar/`
**分析方法**: 代码考古（git log + diff）+ 现有教训文档交叉验证，不基于假设

## 背景

评分项解析功能持续约 6 周、160+ commit（其中 fix 类 90 个），准确率仍不稳定，导致下游任务拆解、案例匹配、投标匹配等流程无法可靠推进。本文档不重复描述症状，只沉淀根因链条与治理方向，供后续排期参考。

## 根因链条（因果关系，非并列）

### 根因 1：输入信息在源头损毁，且不可逆

`docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md` 已用五个为什么定位到零号病人：Word 表格的合并单元格信息经 `markitdown`/`mammoth` 转 Markdown 后丢失（rowspan/colspan 结构不可恢复，内容在多行重复），下游 `ScoreTableRowParser` 基于"列对齐"假设解析这段已损毁的文本，候选从这一步就已经错误。该文档 2026-08-28 记录的方案 C（结构化表格提取，即 Sidecar 直接返回 `CellPayload` 含 rowspan/colspan/isMergedContinue）已经实现（对应现在的 `StructuredTableCandidateExtractor` / `DeterministicScoreFactExtractor`），但代码中仍保留走 Markdown 文本管道的路径（`StructuredTableExtractionHandler.extract` 的非结构化分支），两条路径并存导致同一类合并单元格问题在"走了哪条路径"上呈现不确定性。

### 根因 2：结构化通道对列布局的容错来自固定列序假设，不是版式识别

`root-cause-analysis-score-parse-table-merge-cells.md` 的 "Phase 1 补齐" 章节记录了一个关键教训：`StructuredTableCandidateExtractor` 的列角色判定依赖**固定列序假设 + 括号权重解析**（4 列布局 `[编号, 评分类别, 评分因素（N分）, 评分标准]`），一旦表格多一列（如 5 列 `[编号, 评分类别, 评分因素, 评分标准, 权重]`），列序整体错位，括号解析拿不到权重就静默回退到"从 detail 文本里扫`得X分`"，产出看似合理但错误的数值（真实案例：weight 从应有的 5/6/50 变成 100/50/60）。这与更早期 `ScoreTableRowParser`（Markdown 通道）的问题是**同一设计取向的重复**：把"回退扫描"当成兜底，而回退路径的误判率本身很高且不报错。

这条根因解释了为什么"结构化提取"这个正确方向落地后，同类问题仍在新项目上复现：结构化通道解决的是"合并单元格能不能展开"，没有解决"表格有多少种列布局、如何先分类再提取"这个更上层的问题。调研中确认的 `HeuristicTableColumnMapper.java`（300+ 行）里大量形如"兜底策略1/兜底策略2"的 if-else，是在用无穷可枚举的规则应对无穷的版式变体，且规则之间存在耦合（如 codeCol 的判定结果影响 weightCol 推断），这解释了项目33（3次修复）、项目1371（4次以上修复）、跨页表格问题（5次以上修复）反复回归同一模块的模式。

### 根因 3：LLM 的"保真"约束停留在 prompt 文字层面，不是程序化校验

`ScoreParsePrompts.java` 中存在"detail必须逐字来自招标文件原文""严禁AI摘要、概括、缩写或改写"等指令，这是用自然语言约束一个概率模型做确定性的事，本质是软约束。真实故障（客户验收反馈"识别出的评分项文字与招标文件原文不一致"）证明了这类指令会被模型在语义相近但字面不同的输出上违反。目前 `ScoreParseFact` 已经有 `sourceCellIds`/`weightEvidence` 字段的基础设施，说明"让代码从原文位置回填文本、而不是让LLM转述"的方向是对的，但尚未成为强制路径。

### 根因 4（放大器）：测试体系结构性地无法检测出以上问题

- 真实语料的 Golden Case 测试（如 `Incident1358RealCorpusGoldenTest`）依赖环境变量 `XIYU_EVAL_CORPUS_DIR` 指向离线目录，不存在则跳过——CI 默认状态下没有真实招标文件参与验证，日常验证只能靠人工"跑一次看结果对不对"。
- 现存单测断言存在过松的情况：`root-cause-analysis-score-parse-table-merge-cells.md` 记录的教训明确写到"金标断言必须校验 dim+weight 配对与总分闭环，只断言条数无法发现取错列"——这条教训是在 1358 金标测试第一次因为多一列布局而失败之后才补上的，说明**测试本身也是在事故驱动下逐步补齐，而非提前设计**。
- 没有量化的准确率/召回率指标追踪，团队无法判断某次修复是让整体收敛还是在震荡（修好 A 破坏 B）。

## 根因归类与影响估计

| 根因 | 性质 | 对"改一个月改不好"的解释力 |
|---|---|---|
| 1. 输入有损（Markdown 转换丢失合并单元格结构） | 地基问题 | 高——下游所有规则都建立在被污染的输入上 |
| 2. 列布局用固定假设而非版式分类 | 结构性方法论错误 | 高——每个新版式都是新 commit，且规则耦合导致修复有回归风险 |
| 3. LLM 保真靠 prompt 文字而非程序校验 | 局部技术缺陷 | 中——影响准确率但不是主要的"反复复现"来源 |
| 4. 测试体系无法检出问题 | 放大器 | 高——没有它，前三条根因永远发现得晚、验证不了是否真的修复 |

## 治理方向（不涉及具体代码实现，供排期评审）

1. **统一到结构化提取路径，废弃 Markdown 表格文本通道**：`StructuredTableExtractionHandler` 中走非结构化分支的场景应逐步收口，因为 Sidecar v2 的 `CellPayload` 已经带 rowspan/colspan/isMergedContinue，具备完全替代 Markdown 表格解析的能力。这一步的收益最大、风险最低，属于"消灭已知有损环节"而非新增能力。

2. **引入版式分类层，替代固定列序假设**：在进入列角色判定之前，先识别当前表格属于已知的哪一种版式（列数、是否含合并总分行、是否跨页续表等），为每种版式路由到独立的确定性提取器。这样修复一种版式不会波及另一种，从方法论上终止"改一个坏一个"的模式。

3. **将 LLM 保真从软约束改为程序化约束**：强制 LLM 输出原文的 `sourceCellIds`/位置索引而非直接生成文本，由代码从原文回填 detail/dim 字段。基础设施（`ScoreParseFact.sourceCellIds`、`weightEvidence`）已存在，缺的是把它变成强制路径。

4. **建立真实语料的量化回归门禁**：把已复现过问题的项目（33、1117、1358、1359、1371 等）的真实招标文件沉淀为 CI 默认运行的测试集（而非依赖环境变量开关跳过），断言必须覆盖 dim+weight 配对和总分闭环（而非仅条数），每次修改前后对比准确率/召回率，未提升不合并。这是让团队从"盲改"转向"可验证收敛"的关键杠杆。

## 相关文档

- `docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md`——本报告的直接证据来源，含完整的五个为什么推导与 Phase 1 补充教训
- `docs/lessons/scoreparse-golden-corpus/index.md`——金标语料现状说明
- `backend/src/main/java/com/xiyu/bid/scoreparse/`——核心实现目录
