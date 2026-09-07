# PR !2589 后续修复实施笔记（sectionDim miss 可观测性 + 词表收敛）

分支：`agent/codex/pr2589-followup`（自 origin/main，含 !2589）

## 1. 背景

PR !2589 审核结论：1 Major（`inferSectionDimension` 关键词过拟合 1359、失败静默返回 null
导致保护覆盖面不可观测）+ 3 Minor（价格词表三处重复、双启发式无互指、lessons EOF 空行）
+ 1 Info（1371 缺反膨胀断言）。本任务逐项闭环。

## 2. 决策与权衡

### D1. miss 日志落 application 层，纯核心只上报数据

- 约束：`FPJavaArchitectureTest` 禁止 `..domain..` 依赖 `org.slf4j`（第 68 行），
  `ScoreParseArchitectureTest` 规则 1 同理。`StructuredTableCandidateExtractor` 不能打日志。
- 做法：新增 `extractWithSectionReport()` 返回 `SectionReport(candidates, sectionDimMissTables)`，
  旧 `extract()` 原样委托（行为零变更，既有测试零改动）。
  `ScoreParseAppService#tryStructuredTableExtract` 对非空 miss 打 warn（唯一生产调用点）。
- 备选（已否决）：domain 内静态 miss 计数器——违反纯核心不可变规则；
  改 `extract()` 返回类型——破坏调用方与既有测试。

### D2. miss 口径 = 多表且 sectionDim == null

- 单表 null 是混合单表（1358/1371）设计行为，不上报，避免 1371 类项目每次误报。
- 未用"候选是否仍无 parentDim"口径：有编号混合表单项本就靠编号 survive trim，
  上报会产生噪音；多表 null 已足够指示"新措辞独立分表"风险。

### D3. 词表原文逐字迁移，零行为变更

- `ScoreTableSectionPolicy` 内 8 组常量均为 !2589 原字符串逐字搬运（含 1359 特例对，
  命名 `*_1359` 并写明"泛化前保留、禁止反向特例化"）。
- 1359 特例对用 `containsAll` 显式表达 AND 语义，原来是行内 `&&`，语义一致。
- `inferSectionDimension` 加了 `rows.get(i) == null` 防御：原代码无此分支，
  但 `TablePayload` 经 `List.copyOf` 不可能含 null（会早爆），故纯加固、无行为差。

### D4. 1371 断言用"零零分项"而非字面"营业执照"

- 实测 `1371_tables_v1.json` 中"营业执照"出现 0 次，字面断言恒真、无锚定价值。
- 改用 `noneMatch(weight == 0)`：直接锁定 Task1 决策边界
  （1371 无价格公式表 → 不应有任何零分兜底项；击穿即向 44 项膨胀）。
  若未来 1371 类 fixture 引入合法扣分项（0 分），此断言需同步放宽——届时是预期内的测试更新。

### D5. 新文件而非塞入 Extractor

- Extractor 正好 300 行（硬上限），加常量会超；新纯核心文件 + 委托调用反而让
  Extractor 保持 300（删 24 行、加约 24 行），且 PostProcessor（application）可合法引用。

## 3. 改动清单

- 新增 `scoreparse/domain/ScoreTableSectionPolicy.java`（125 行，纯核心）
- `StructuredTableCandidateExtractor.java`：删本地双方法→委托；+`SectionReport`/`extractWithSectionReport`（300 行，未超）
- `ScoreCandidatePostProcessor.java`：`isPriceFormulaItem` 改调共享常量 + 互指注释
- `ScoreParseAppService.java`：miss warn 日志（3 行逻辑）
- 新增 `ScoreTableSectionPolicyTest`（12 tests）；`StructuredTableCandidateExtractorTest` +4 tests +1371 零分断言
- `docs/lessons/lessons-learned.md`：删 EOF 多余空行（`git diff --check` 已绿）

## 4. 验证（2026-09-08 实测）

- [x] 功能 62 tests 全绿：Extractor 25（21 旧 +4 新）/ TrimPolicy 10 / PostProcessor 7 /
      SectionPolicy 12（新）/ FPJava 8 —— `Tests run: 62, Failures: 0, Errors: 0`
- [x] 架构 35 tests 全绿：ScoreParseArchitecture 3 + ArchitectureTest 2+30
- [x] `check-keyword-sync.mjs` PASS；`check-line-budgets.mjs` PASS；
      `git diff --check` 干净
- [x] 1371 零分断言绿：确认 1371 结果无零分项（反膨胀边界成立）
- [x] 1359 上报测试绿：三表均有章节，miss 为空（已知措辞无误报）
- 行预算插曲：AppService 原已 300 顶格，首版 +5 触发 CROSSED_LIMIT；
  以内联调用 + 删无用 import 做到 net 0（300），Extractor 同样守住 300
