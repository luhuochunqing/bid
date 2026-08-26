# Research: 055 评分解析两阶段归并 + 表格行保真

## R1: 全局归并的裁决上下文来源

**Decision**: 复用 `MarkdownScoreSectionLocator.locate(fullText)` 定位评分区域，拼接所有 section content 作为裁决上下文，上限 12000 字符；定位失败降级为全文前 12000 字符。

**Rationale**: locator 已在召回二使用（`OpenAiScoreAnalyzer.recallLane2DocumentStructure`），评分表定位命中率经线上验证；无需新定位逻辑。12000 字 ≈ 单次 LLM 调用安全输入上界（与 chunk MAX_CHARS=4000 × 3 同量级）。

**Alternatives considered**: 全文输入（超 token 预算且引入无关噪音）；向量检索 top-k（引入新依赖，评分区域结构定位已足够）。

## R2: 归并 LLM 调用形态与失败语义

**Decision**: 单次调用（候选池 JSON 摘要 + 章节原文），RuntimeException 重试恰好 1 次，两连败返回 empty → AppService 保留启发式结果继续（fail-open）。候选池 <2 项跳过调用。

**Rationale**: 与既有 `requestCandidatesWithRetry` 语义一致（spec 046 US3）；归并是增强环节而非正确性前提，降级路径 = 现网现状，回归风险最低。

**Alternatives considered**: 逐项两两裁决（O(n²) 调用爆炸）；失败即任务失败（违反宪法 VII 优雅降级）。

## R3: 幻觉与漏项双向防御

**Decision**: domain 纯核心 `ScoreMergeResultGuard`：
1. **幻觉过滤**——归并输出项 `detail` 必须被章节原文包含（归一化空白后），否则丢弃并计数；
2. **漏项回补**——候选池中"有原文背书（sourceText ⊇ detail）且 weight 非空"的高置信项，若未被任何归并输出项覆盖（detail 归一化互相包含判定），原样回补。

**Rationale**: LLM 归并的两类失效模式（发明新项 / 漏掉旧项）都需要确定性兜底；"原文背书"判据复用既有 `ScoreItemMergePolicy.hasSourceBacking` 语义。

**Alternatives considered**: 仅信 LLM 输出（不可接受，评分项缺失直接影响投标得分）；仅信启发式池（归并失去意义）。

## R4: 表格行保真的切入点

**Decision**: 不改 docinsight 的 `StructuralDocumentChunker`/`TextChunker`（跨模块共享、零侵入）。在 scoreparse 新增 `ScoreTableAwareChunker` 包装：对召回四选出的 chunk 后处理——含管道表格行且长度 > 安全阈值时按行边界重切。召回五独立直抽表格行，不依赖切片结果。

**Rationale**: 切片断行只影响 scoreparse 的 LLM 召回质量；包装层改动面最小、可独立单测。sidecar 已输出 pipe 表格（PDF pdfplumber 实测），Java 侧消费即可，本次不动 Python。

**Alternatives considered**: 修改 TextChunker 加表格感知（影响 biddraftagent/docinsight 全部调用方，回归面大）；改 sidecar 输出 HTML 表格（转换链路风险高、收益不增）。

## R5: 行级列对齐策略

**Decision**: `ScoreTableRowParser` 对一行按 `|` 分列后：(1) 分值列 = 含"得X分/X分/分值"数字模式或纯数字的第一列（从右向左优先）；(2) detail 列 = 除编号列外最长的文本列；(3) 无法识别列语义时整行原文作为 detail（不编造）；(4) weight 从分值列提取数字，提取不到置 null（由既有 filterInvalidWeights 兜底剔除或归并裁决修正）。

**Rationale**: 评分表列序无统一标准（评审内容可能在第 2 或 3 列），最长文本列启发式 + 分值模式匹配覆盖绝大多数真实表格；合并单元格产生的空列走整行降级，保证不丢行。

## R6: dim 归一化归属

**Decision**: 归并 prompt 要求 LLM 为每个输出项给出 dim（取候选池中出现过的 dim 词表内选择，允许合并表述如"技术服务部分"）；不做代码层 dim 词表硬编码。

**Rationale**: dim 是标书相关的大类命名（技术方案/商务部分/价格部分…），词表因标书而异；LLM 在完整上下文中做归属比启发式更准（这正是问题 2 的教训：启发式猜 dim 不可靠）。
