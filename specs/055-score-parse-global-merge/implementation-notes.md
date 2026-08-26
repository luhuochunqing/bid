# Implementation Notes: 055 评分解析两阶段归并 + 表格行保真

> 运行实现笔记：记录 spec 之外必须做的决策、变更与取舍。

## 决策记录

### D1 表头行剔除 + 语义判定基于完整块（T1 实现中发现）
spec 未约定表头处理。实测 markdown 表格固定结构为"表头 + 分隔行 + 数据行"，表头行（如"评分项|评审内容|分值"）不是评分条款但**常是块级语义判定的唯一命中来源**（数据行可能不含"评分/评审"字样）。决策：语义判定用完整块（含表头），数据行输出剔除表头与分隔行。

### D2 dim 从表格行大类名列直接提取（偏离 spec 的"dim 置 null"初稿）
初稿计划行级 dim 置 null 全交归并裁决。TDD 过程发现表格行的短中文列（"技术方案""不平衡报价评审"）天然就是大类/名称列——直接提取比置 null 更准，且减轻归并 LLM 负担。新增 `extractDim`：非编号/非分值/非 detail 列中最长中文短列（≤20 字）。

### D3 weight 提取双通道：纯分值列优先，档位给分取 max 兜底
评分表分值列形态不一。决策：(1) 从右向左找纯分值短列（"5"/"5分"）；(2) 无纯分值列时取行内所有"得X分"匹配的最大值（满分语义："优秀得4分，良好得2-3分" → 4）。两者都无 → null（由既有 filterInvalidWeights 兜底）。

### D4 守卫"幻觉全滤"不走整池回退而是回补路径（测试揭示的正确语义）
原计划"守卫全滤 → 回启发式池"。测试证明更优语义：幻觉全滤后**漏项回补**自然接管——返回池的高置信子集（低置信项不回补）。orchestrator 的 accepted.isEmpty() 分支实际不可达（回补保证非空，只要池有高置信项），保留作为最后防线。

### D5 ScoreParseAppService 行数压线 300
新增 3 处接线（召回五/归并/字段）后 304 行超硬上限。压缩：ANY_STATUS 常量消除两处状态列表重复、latestTask 复用 latestTaskOrNull、删 2 个冗余注释/空行 → 恰 300 行。**技术债**：该类已无冗余空间，下次改动需先抽 ScoreCandidatePipeline。

### D6 归并输出 weight 走 Double→BigDecimal（契约对齐既有风格）
ScoreCandidateOutput 用 Double，ScoreCandidate 用 BigDecimal。Executor 映射层转换，契约类保持 Jackson 友好的 public field POJO 风格。

### D7 防御性拷贝修复 UOE（测试揭示）
`outcome.candidates()`（RecallOutcome record）可能携带不可变列表（测试 mock 与未来重构都可能），直接 addAll 抛 UnsupportedOperationException。改为 `new ArrayList<>(outcome.candidates())`——宪法 VII 防御式编码的实例。

### D8 归一化与归并的交互（AppService 测试揭示）
归并输出若只剩 1 项且 weight<100，既有 WeightNormalizationPolicy（spec 047）会把它归一化到 100。这是正确管线语义（归并后门禁照常生效），测试用例改为断言 code/detail 而非整对象。

## 与 spec 的偏差

- spec FR-5 说"召回五与既有四路召回并列进入合并"——实现为 AppService 在 recallCandidates 返回后追加（未改 OpenAiScoreAnalyzer.recallCandidates 签名），行为等价、改动面更小。
- spec 边界情况"归并输出丢失高置信项 → 回补"实现粒度到 detail 归一化互相包含（超集覆盖），比"未被合并"更精确。

## 验证证据

- domain 四件套 20 测 + prompt/executor 7 测 + orchestrator 5 测 + AppService 15 测全绿
- scoreparse 全包 + ArchitectureTest + ResponsibilityArchitectureTest = **406 测试全绿**
- preflight-domain-purity / java-standards / line-budget / testing-gate 全过
- 真实文档端到端（项目 1347 重解析验证 SC-1）待部署后在测试环境执行

## 评审修复（独立评审 pass，2026-08-26）

- **C1 e2e 启动崩溃**：Executor @Profile("!e2e") 但 Orchestrator 强注入 → e2e 下 NoSuchBean（spec 048 同款事故复发）。修复：Orchestrator 改 ObjectProvider 懒解析，Bean 缺失时 fail-open 降级 + 回归用例。
- **M1 rechunk 死代码**：TextChunker 既有 `lastIndexOf('\n')` 行边界回退，chunk 恒 ≤4000 → ScoreTableAwareChunker 永不触发。修复：**删除死代码**（subtract-before-you-add），FR-4 落点改为 TextChunker 上游行为 + 新增 TextChunkerTest 锁定（表格行不被斩断、无内容丢失）。
- **M2 config 解析在 try 外**：resolver 缺配置异常直穿任务判 FAILED。修复：移入重试 try 块。
- **M3 守卫未强制 SC-2**：归并输出内部互相包含的重复会双计权重。修复：采纳时内部去重（保留首个）。
- **m2/m3/m4/m5**：escape 补控制符；sectionText 过 sanitizeUntrusted；LLM 自报 summary 落日志；孤立纯数字单元格行（"| 10 |"）跳过。
- **教训**：包装层"事后重切"无法愈合上游已切断的行——切片类修复必须落在切分发生点；新 @Profile Bean 的消费方必须检查 e2e 启动路径（项目已两次踩坑）。

## 最终验证证据

- scoreparse 全包 + TextChunkerTest + ArchitectureTest = **412 测试全绿**
- 评审 verdict：REQUEST_CHANGES（1C+3M）→ 全部修复，无遗留
