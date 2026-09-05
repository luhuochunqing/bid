# Follow-up 修复实现笔记（PR !2549 复审第三轮残留）

分支：`agent/codex/followup-iscredit-sanitizer`（基线含 70a1256ad）
日期：2026-09-05

## 1. isCredit 收窄：为什么选"主体词 + 失信"而不是"主体词 + 评价/评分"

- 备选方案 A（信用/诚信 + 评价|评分|分值|扣|分 交集）被证伪后否决：
  反向 FP 例"推荐成交候选供应商三名信用评价程序按规定执行"同时含"信用"与"评价"，
  交集方案仍会救援，测试锁不住。凡含"评价"即放行的口子与裸子串豁免同样宽。
- 采用方案 B（仅豁免失信语义行）：`DeductionItemPolicy.isCreditRescue(texts)` =
  任一文本含 诚信|信用 **且** 任一文本含 失信。失信是信用扣分类行区别于真程序性行
  的最小充分特征；有显式规则的行走各自 EXPLICIT 通道，不依赖本方法。
- 接受面（有意不救）：无失信语义的信用行若同时命中程序性词表（如"信用评价纳入履约评价体系"）
  会被滤除。这类行若真有分值，EXPLICIT/括号通道会先救；若两者皆无，本来就不该入池。
- 三处调用点语义对齐但扫描面保留差异（有意）：
  Ghost 扫 (dim, detail, source)，Cell 扫 (finalDim, parentDim, detail, sourceRow)，
  RowParser 扫整行。差异来自各通道持有字段不同，判定谓词同一出处。
- EXPLICIT 空格容忍恢复：`扣\s*\d+...\s*(?:分)?` —— "扣5 分"重新命中。
  附带：裸"扣减/扣除"仍保留在 EXPLICIT（存量单测依赖），"扣除投标保证金"类 FP 若命中
  程序性词表且无信用主体词，仍会被救回——属已知残留，比 isCredit 窄一个量级，暂接受。

## 2. 反斜杠分隔符：后端确实漏了，前端本来就有

- Java 字符串 `"[._/\\-\\s]"` 对应的正则字符类是 `[._/\-\s]`，`\\-` 只是转义连字符，
  类内并无字面反斜杠（要含需源码 `\\\\`）。已改为 `"[._/\\\\\\-\\s]"`，
  涉及 NULL_SEGMENT_CLEAN 前后断言 + 三处清理正则。
- 前端 JS 正则字面量 `[._/\\\-\s]` 的 `\\` 即字面反斜杠，所以前端本来就支持 `null\1`。
  这解释了"前后端对齐"断言为何只在后端加单测。

## 3. 前端 NFKC：从手写映射换成标准库

- 原手写 `[\uFF01-\uFF5E] - 0xFEE0` 只是 NFKC 的子集（全角 ASCII/标点），漏 U+3000 表意空格、
  ①②圈号、上标等后端已归一的字符。改用 `s.normalize('NFKC')` 后与后端逐字对齐。
- 保留项：零宽剥除（NFKC 不处理）、首尾 trim（NFKC 把 U+3000 变成半角空格，需二次 trim）、
  不做 50 截断（展示码不落库，后端落库处截断）。

## 4. 验证证据

- 后端：72 tests（Ghost 12 / Cell 9 / RowParser 25 / Deduction 4 / Sanitizer 4 /
  Project 9 / Warehouse 9）0 失败；架构守卫 FPJava 8 + Responsibility 4 通过。
- 前端：scoreParseTask.spec.js 32 tests 全绿（含新增 NFKC 对齐用例）。
- 可证伪性：Ghost/Cell/RowParser 的反向用例在旧裸 isCredit 下必红（旧逻辑直接救援）；
  纯 isCredit 正向用例刻意不含 不扣/扣减/扣除/得分/扣分/数字/括号，删掉救援即红。

## 5. 审查返工追记（xiyu-code-review 三透镜）
- RowParser 的 EXPLICIT 救援限定程序性侧：考核表行即使含扣分措辞仍过滤
 （`parse_contractAssessmentRowWithoutScore_filtered` 为既有意图，全量并入会翻绿它）。
- `hasExplicitScoreRule` 并入 Policy，Ghost/Cell 删别名层；字段选择差异（Ghost 查 dim+detail、
  Cell 只查 detail）有意保留——统一谓词文本，保留通道字段选择。
- 纯救援正向用例 vs 旧宽口径恒成立（任何保留测试在旧逻辑下都绿），故只作回归锁，
  收窄证明力全部落在反向用例；注释已按此修正，不改 dim 做无意义差分。
- `ScoreItemOrderPolicy` 的裸诚信/信用判定（大类归一化）与救援谓词解耦：前者是分类问题，
  后者是过滤问题，统一会改排序行为，另开 issue，不在本轮动。
