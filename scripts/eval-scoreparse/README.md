# AI 评分标准解析 · 金标夹具回放 v0（spec 057 US3 / plan D-C）

对"评分标准解析"确定性管线建立可回归的数值基线：每个 PR 的后端默认测试套件自动
回放合成金标夹具，输出每案的 TP/FP/FN、Precision、Recall、F1 与各 lane 候选贡献数，
写入 `backend/target/scoreparse-golden-report.json`。LLM 层不在本基线范围（见"两层执行"）。

## 目录布局

```
scripts/eval-scoreparse/
├── README.md         ← 本文件：标注规范 + 匹配谓词口径 + 新增 case 指南
├── report.mjs        ← 渲染 golden-report.json 为 Markdown 表（stdout）
├── record-real-corpus.sh ← 真实语料 candidates.json 录制入口（显式本地 env 才写）
└── nightly-full.sh   ← 第二层全链路采集模板（缺语料目录明确 SKIP）
```

夹具本体位于后端测试资源：

```
backend/src/test/resources/scoreparse-golden/
├── case-dual-section-renumber.md            双评分章节跨维度重号 + 前附表噪声章
├── case-dual-section-renumber.expected.json 对应金标标注（threshold 0.6）
├── case-weight-gap-mixed.md                 权重缺口90 + 幽灵行混合形态
├── case-weight-gap-mixed.expected.json      对应金标标注（threshold 0.5）
├── case-1374-redundant-formula.md           项目1374回归：冗余价格公式行 + 评标基准价重复行（fix 1919ecb41）
├── case-1374-redundant-formula.expected.json  对应金标标注（threshold 0.95，expectTotal 100）
├── case-declared-total-80.md                 Task2回归：声明总分80 + 评分项合计80 不按100误判
├── case-declared-total-80.expected.json      对应金标标注（threshold 0.95，expectTotal 80）
├── case-review-required-80-gap.md            Task3回归：事实项合计80 + 默认百分制缺口进入人工复核闭环
├── case-review-required-80-gap.expected.json 对应金标标注（threshold 0.95，默认expectTotal 100）
├── case-3col-embedded-bracket-weight.md     3列内嵌分值 + 维度名 br 换行（fix a1b3b7c55）
├── case-3col-embedded-bracket-weight.expected.json  对应金标标注（threshold 0.95，strictDim）
├── case-unique-explicit-subtotal-maximum.md           未知表头下唯一显式满分小计列回归
├── case-unique-explicit-subtotal-maximum.expected.json 对应金标标注（threshold 0.95，strictDim）
├── case-1381-pdf-preliminary-and-summary.md  项目1381类PDF：初步评审表上下文 + 8项评分表 + 合计行
├── case-1381-pdf-preliminary-and-summary.expected.json  对应金标标注（threshold 0.95，strictDim，expectTotal 100）
├── case-1386-score-summary-and-leaves.md     项目1386类分值构成摘要（20/45/35）与正式评分叶子项
├── case-1386-score-summary-and-leaves.expected.json 对应合成金标（threshold 0.95，strictDim，expectTotal 100）
├── case-misaligned-summary-row.md            错位合计行：多个纯数字单元格时仍识别整表总分
├── case-misaligned-summary-row.expected.json 对应金标标注（threshold 0.95，strictDim，expectTotal 100）
├── case-multi-table-tech-commercial.md       两张独立评分表（技术60/商务40）多表输入形态（spec 062）
├── case-multi-table-tech-commercial.expected.json  对应金标标注（threshold 0.95，expectTotal 100）
├── case-category-weight-noise-before-score.md 报价品类权重表前置噪声 + 正式评分表完整召回（spec 066）
├── case-category-weight-noise-before-score.expected.json 对应金标标注（threshold 0.95，strictDim，expectTotal 100）
├── case-long-text-scoring-table.md          长篇正文前置 + 末尾评分表完整召回（超长文本回归）
├── case-long-text-scoring-table.expected.json 对应合成金标标注（threshold 0.95，expectTotal 100）
├── case-price-markdown-star.md             合成价格公式星号转义（lessons §431）
├── case-price-markdown-star.expected.json  对应金标与原文offset断言（threshold 0.95，expectTotal 100）
├── case-text-peer-scope.md                  同级履约章节文本证据不得跨入评分方案
└── case-text-peer-scope.expected.json       对应合成范围回归标注（lessons §404）
```

## 两层执行说明

| 层 | 入口 | 覆盖范围 | 触发时机 |
|---|---|---|---|
| 第一层 · 夹具回放 | `npm run eval:scoreparse:replay`（= `mvn -f backend/pom.xml test -Dtest='GoldenCasesReplayTest'`） | 纯确定性六件套：Locator→Extractor（正则结构通道）、TableRecallLane、MergePolicy、GhostFilter、（生产同序的权重有效性过滤）、WeightNormalization | 随默认后端测试套件执行，等效 pre-push 第一层门禁（waiver 见 specs/057/spec.md） |
| 第二层 · 全链路采集 | `bash scripts/eval-scoreparse/nightly-full.sh` | 真实 docx/pdf 语料经 sidecar + LLM 全链路的端到端样本采集 | nightly / 手动；未设置 `XIYU_EVAL_CORPUS_DIR` 时打印 SKIP 原因并以 0 退出 |

## 真实语料金标录制（本地 green path）

真实客户文件、真实原文、离线候选快照均不得入仓；只能放在仓库外的
`XIYU_EVAL_CORPUS_DIR`。仓库只保存录制工具、测试入口和脱敏期望。

默认目录约定：

```text
$XIYU_EVAL_CORPUS_DIR/
└── incident-1358/
    ├── source.md        # 本地真实文档经 sidecar/文档提取链导出的 markdown，禁止入仓
    └── candidates.json  # 录制器生成，禁止入仓
```

录制候选快照：

```bash
XIYU_EVAL_CORPUS_DIR=/secure/local/scoreparse-corpus \
  npm run eval:scoreparse:record-real-corpus
```

如输入或输出路径不是默认值，可覆盖：

```bash
XIYU_EVAL_CORPUS_DIR=/secure/local/scoreparse-corpus \
XIYU_SCOREPARSE_REAL_CASE=incident-1358 \
XIYU_SCOREPARSE_MARKDOWN=/secure/local/scoreparse-corpus/incident-1358/source.md \
XIYU_SCOREPARSE_CANDIDATES=/secure/local/scoreparse-corpus/incident-1358/candidates.json \
  npm run eval:scoreparse:record-real-corpus
```

跑真实语料 green path：

```bash
XIYU_EVAL_CORPUS_DIR=/secure/local/scoreparse-corpus \
  npm run eval:scoreparse:real-corpus
```

`Incident1358RealCorpusGoldenTest` 会读取
`$XIYU_EVAL_CORPUS_DIR/incident-1358/candidates.json`，把候选快照送入生产后处理链，
断言 1358 真实形态仍然 `AUTO_APPROVED`、11 项全部落库、总分 100、并触发预计得分。
未设置 `XIYU_EVAL_CORPUS_DIR` 时测试按 JUnit 条件跳过，不阻塞 CI。

渲染报告：

```bash
npm run eval:scoreparse:report     # 读 backend/target/scoreparse-golden-report.json 输出 Markdown 表
node -e "JSON.parse(require('fs').readFileSync('backend/target/scoreparse-golden-report.json'))"  # 冒烟校验合法 JSON
```

报告文件缺失时 `report.mjs` 以 exit 1 退出并提示先跑第一层。

## expected.json 字段规范

```json
{
  "synthetic": true,
  "desensitization": "脱敏方式一句话（如：全文字段合成改写，项目名以××掩码替代）",
  "focus": "一句话描述该案防的回归形态",
  "threshold": 0.6,
  "expectTotal": 100,
  "strictDim": true,
  "laneHints": ["table"],
  "items": [
    { "code": "1", "dim": "商务-方案完整性", "detail": "<逐字表格原文短语>", "weight": 8, "sourceText": "<原文背书锚点短语>" }
  ]
}
```

| 字段 | 必填 | 语义 |
|---|---|---|
| `synthetic` | 是（新案） | 脱敏声明：必须为 `true`，配套 `desensitization` 说明改写方式（FR-08：真实客户文件禁止入库） |
| `desensitization` | 是（新案） | 脱敏方式：保留了哪些版式特征、替换了哪些敏感字段 |
| `focus` | 是 | 注释性文字：该案针对的失败形态（对应 docs/lessons 编号），不参与判定 |
| `threshold` | 是 | 该案 F1 的最低硬门限；`GoldenCasesReplayTest` 断言 `F1 ≥ threshold` |
| `expectTotal` | 否 | 权重收敛值：声明即断言归一化前合计必须等于该值（如 1374 案防 101 分膨胀声明 100） |
| `strictDim` | 否 | 维度精确性：为 `true` 时对配对命中项逐条断言预测 dim（空白折叠后）逐字等于标注 dim（3列案借此锁定 `<br>` 平滑无残留空格） |
| `laneHints` | 否 | 文档性标注：期望供给该案候选的主要 lane（如 `table`=表格行直抽）；仅用于报告解读，不参与判定 |
| `items[].code` | 否 | 原文编号标签（如 `1`）；**判定时不使用 code**——跨维度重号场景下同编号是常态而非冲突 |
| `items[].dim` | 是* | 评分项名称标签。expected 中允许加 `商务-`/`技术-` 类前缀便于人工区隔重号项；因判定不看 dim，前缀不影响结果。*表格整行降级类候选可能 dim=null，标注时仍应给出名称 |
| `items[].detail` | 是 | **必须逐字复制夹具 md 中的条款原文短语**（表格列单元格全文或段落级原文句）。这是匹配谓词的锚点：改写、缩写、增删标点都会直接改变匹配结果 |
| `items[].weight` | 是* | 该项的真实分值（数字）。判定要求双方 weight 存在且差 ≤0.5，缺 null 即永不匹配。*若夹具该项本无分值则省略，但等于宣告该项不可由谓词命中 |
| `items[].sourceText` | 否 | 原文背书锚点：detail 中的短语子串；声明即断言配对命中的预测候选 `sourceText` 归一化后包含该锚点 |

## 门禁：新版式修复无 fixture 即 fail

- `scripts/pre-push-gate.sh` §9.11（scoreparse 金标门禁）：commit 范围触及
  `backend/src/main/java/**/scoreparse/(domain|application|infrastructure)/` 解析主代码，
  但无 `scoreparse-golden/` 夹具或回放/提取测试（`GoldenCasesReplayTest` /
  `CellAnchoredScoreExtractorTest` / `StructuredTableCandidateExtractorTest` /
  `ScoreTableRecallLaneTest` / `ScoreItemMergePolicyTest` /
  `ScoreGlobalMergeOrchestratorTest`）同步变更时，门禁 fail 阻断推送。
  逃生阀：`SCOREPARSE_GATE_SKIP=1`（仅限非版式逻辑变更，需在 PR 描述说明理由）。
- 本地复核等价命令：`mvn -f backend/pom.xml test -Dtest='GoldenCasesReplayTest'`
  全绿 + `npm run eval:scoreparse:report` 确认 case 数符合预期。

标注纪律：

1. `items` 只收录"真实评分项"。为构造守卫覆盖而埋入夹具的幽灵行、表头重复行、程序条款噪声，一律 **不得** 进入 items——它们被正确滤除体现为 FP 不产生。
2. 合成文本与标注须成对提交：先改 md 再同步 expected，反之亦然。
3. 夹具 md 文件首行必须是 `<!-- SYNTHETIC FIXTURE … -->` 合成声明头注释（FR-08：真实客户文件禁止入库）。

## §匹配谓词（口径唯一出处）

参考实现：`GoldenCasesReplayTest#computePrF1` / `#matches`。规则如下：

1. **归一化**：对待比较的 detail 文本执行 `trim()` 后移除全部空白字符（正则 `\s+` → 空串）。中文全角空格、制表符、表格竖线残留展开后的空白均在消除之列。
2. **单条匹配判定**：预测候选 `p` 匹配期望项 `e` ⇔ 全部成立：
   - `p.detail` 归一化后包含 `e.detail` 归一化结果，或反向互含（容忍 lane 抽取时的片段增减）；
   - `p.weight` 与 `e.weight` 均**非空**，且绝对差 ≤ `0.5`；
   - 任一 weight 为空视为不匹配（不给无分值候选白吃命中率）。
3. **贪心唯一配对**：按 expected 数组顺序遍历期望项，每项取第一个尚未被消费且满足谓词的预测候选配对；预测候选一经消费不可复用。防止一个宽泛候选吞掉多个期望项虚增 Recall。
4. **指标计算**：在守卫过滤后、权重归一化**之前**的候选清单上统计——TP=配对数，FP=未配对预测数，FN=未命中期望数；
   `Precision = TP/(TP+FP)`，`Recall = TP/(TP+FN)`，`F1 = 2PR/(P+R)`（分母为 0 时置 0）。
5. **为何在归一化前度量**：归一化是等比例算术投影（合计≠100 时全体分值重写），而 expected 是人工真值；
   若在归一化后比较会让标注被迫镜像分配器内部数值。归一化本身由测试另做行为断言（originalTotal 记录、缩放后合计=100）并随报告透出。

> 与 Java 守卫（ScoreMergeResultGuard 规则1 的池身份判定）语义一致但独立实现；
> 本节是报告数值解释的唯一出处。

## 如何新增 case

1. 在 `backend/src/test/resources/scoreparse-golden/` 下新增同名一对文件 `<case>.md` 与 `<case>.expected.json`：
   - md 首行为合成声明头注释；合成文本注意经验约束——叙述性段落结尾避免出现「数字+分」收尾（会被正则通道误当评分项），除非你显式想验证该形态；
   - expected 按"字段规范"填写；detail 逐字来自 md。
2. 在 `GoldenCasesReplayTest#cases()` 的 `Stream.of(...)` 里追加一行 `Arguments.of("<case>")`。
3. 单跑核对：`mvn -f backend/pom.xml test -Dtest='GoldenCasesReplayTest'`，确认目标值合理后再定 threshold（给足余量，避免把脆断点当门限）。
   确定性 1.0 的合成夹具（如 detail 逐字命中、合计恰 100）可定 0.95：6 项案单个 FP 即
   F1≈0.923、8 项案单个 FP 即 F1≈0.941，0.95 恰好拦截单项泄漏/缺失，同时容忍零漂移。
4. 更新本文档上方"目录布局"，并在该案 focus 中引用对应 lessons 编号。

## 报告字段

`backend/target/scoreparse-golden-report.json` 结构：

```json
{
  "cases": [
    {
      "case": "case-dual-section-renumber",
      "laneCounts": { "regex": 10, "regexFullText": 14, "table": 14 },
      "tp": 10, "fp": 0, "fn": 0,
      "precision": 1.0, "recall": 1.0, "f1": 1.0,
      "weightTotalBeforeNormalize": 100,
      "weightTotalAfterNormalize": 100,
      "originalTotal": null
    }
  ]
}
```

`laneCounts` 计的是各 lane 回去重前的原始候选数（merge 前）：`regex`=定位章节内的正则结构通道、
`regexFullText`=全文直抽通道、`table`=表格行直抽通道；某 lane 归零或暴涨往往是回归的第一现场。
