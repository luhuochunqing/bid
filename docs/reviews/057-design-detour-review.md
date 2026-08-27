# PR #2430 设计弯路审查（思维链 Review）

> 审查者：ZCode · 2026-08-27 · 思维链 13 步推演
> 对象：`agent/gemini/score-eval-guards` @ e81ab9647（含 xcode-review 四项修复后的终态）
> 方法：五维度框架（过度复杂 / 架构规范 / 重复造轮子 / 性能扩展性 / 业务偏差）逐一推演，先列候选再去代码验证真实性，反证排除伪发现，最后按价值÷复杂度排序。

## 总判定

**无方向性设计错误**。spec 057 的三条止血路径（裁决语料完整性、run_summary 遥测、金标回放基线）方向正确且已生效。以下 8 项均为局部弯路或前瞻债：P1~P4 可一个 cleanup PR 半天收口；P5 需用户拍板方向；P6/P7 建议搭后续重构车；P8 进 tech-debt-tracker 并用本 PR 自己的遥测数据设触发条件。

## 发现清单（按价值÷复杂度排序）

### P1 · 手写 JSON 拼装与转义 → 应复用 Jackson（实锤造轮子）

- **位置**：`backend/.../scoreparse/application/RunSummaryAssembler.java:42-96`（assemble 主体 StringBuilder 拼 JSON + `quote()` 手写 escape 20 行）
- **问题**：`llmModel` 等字符串字段的转义正确性由人肉保证——当前只覆盖 `" \ \n \r \t` 与 `<0x20` 控制符。同 PR 的 `GoldenCasesReplayTest:8` 直接 import Jackson ObjectMapper，Spring Boot web 栈 jackson-databind 必在运行时 classpath，"避免依赖"的隐含动机不成立。根因是把设计文档里的"纯函数装配器"误操作化为"手工拼装"——而 `ObjectMapper.writeValueAsString` 同样是纯函数、同样可确定性单测。每新增一个字符串字段都要人工重验 escape，这是自建的正确性风险面（JSON 转义是经典手写禁区）。
- **建议**：assemble 内部改为静态复用的 `ObjectMapper`（`private static final ObjectMapper MAPPER = new ObjectMapper()`），输出字段集合与键序不变。
- **复杂度**：低（~30 分钟）。测试断言全部是 `contains(...)` 语义，无需改。

### P2 · 金标测试双镜像生产逻辑——哨兵守护镜像不如消灭镜像

- **位置**：`GoldenCasesReplayTest.java:236-252`（`toCandidate` 复制 `ScoreCandidateMapper.fromCriterion` 全部映射语义）、`:267-277`（`filterInvalidWeights` 复制 PostProcessor 私有同名规则）、`:390-400`（固定向量漂移哨兵）
- **问题**：镜像存在的原因是 `ScoreCandidateMapper` 为 infrastructure.openai 包级私有类、PostProcessor 过滤器为 private——测试"够不着"只能抄写，再用哨兵+注释防漂移。这是三处联动维护税（改映射要同步镜像+哨兵），本质是用测试资产给可及性设计缺陷打补丁。FP-Java "纯核心可单测"的正确兑现是**让测试直接调用生产纯函数**，而不是"测试模仿生产"。
- **建议**：`fromCriterion` 是无状态纯函数 → 提升可见性（public 或下沉 domain 作 `ScoreCandidate` 静态工厂）；权重有效性规则抽成 domain 层小策略（如 `WeightValidityFilter`），PostProcessor 与金标测试共用同一实现。随后删除两处镜像 + 漂移哨兵（哨兵的历史使命即告完成）。
- **复杂度**：中（~1-2h：跨包可见性调整 + ArchUnit 复验 + 金标重跑确认 F1 数值逐字节不变）。

### P3 · Guard 内循环重复归一化——O(M×P) 次 `replaceAll` 预计算即可消除

- **位置**：`backend/.../scoreparse/domain/ScoreMergeResultGuard.java:82-92`（`isPoolIdentityMatched` 对每个池项现场 `normalize(detail)` + `normalize(sourceText)`）、`:110-118`（`isCovered` 每轮重算 accepted 归一化）
- **问题**：池项 detail/sourceText 在整个 guard 过程中是常量，却在 `外层 merged × 内层 pool` 双层循环里每次从原始字符串重新正则替换。四路召回+表格 lane 全开时池可达数百、merged 数十→数万次冗余正则分配。当前量级毫秒级不致命，但 P4 相关特性（poolBackedKept 旁路）已把这条路径从"截断时偶发"变为"每次裁决常态"，值得预防性收口。
- **建议**：guard 入口预计算 `List<String> normalizedPoolDetails/Sources`，accepted 用携带归一化缓存的小结构（或并行数组）；整体 O((M+P)·L) 单遍历。
- **复杂度**：低（~30 分钟，既有 GuardTest 即锁定行为）。可与 P6 搭同一个 cleanup PR。

### P4 · poolIdentityMatched 生效口径三处不一致（spec 说"截断时"、注释说"截断时"、代码无条件放行）

- **位置**：`ScoreMergeResultGuard.java:43`（注释："语料截断时……不算幻觉"）vs `:46`（主循环无 truncated 参与，旁路无条件生效）；源头 specs/057/spec.md FR-02 文本也是"截断场景"
- **问题**：实现宽于 spec 文本一个量级。推演后**不是 bug**：非截断时能触发该旁路的只剩"LLM 措辞漂移但源自真实池项"的输出，放行合理；残余风险仅剩 LLM 把两个池项杂交出一个单向互含的新 detail（低概率、weight 可疑项会被下游权重过滤拦截）。真正的问题是口径纪律——三处文本各说各话，未来维护者无法判断"无条件放行"是有意还是漏写条件。
- **建议**（二选一）：① 一行注释级收敛：Guard 与 spec 补"常态启用，理由：池来源背书"（推荐，5 分钟）；② 严格化：把 `ctx.truncated()` 传入 guard 仅截断时放行——签名变更牵连 8 个既有测试，收益存疑。
- **复杂度**：低。

### P5 · Meta.runSummary 透出前端属 YAGNI（需拍板）

- **位置**：`ScoreParseItemsDTO.java:48`（Meta 第 12 字段）+ `ScoreParseItemsMetaBuilder.java`（组装透出）；本 PR 无任何 `src/` 前端改动消费它
- **问题**：FR-04 主语是持久化与按模型归因（查库即可满足）；透出到 items API 是顺手预埋。代价不在响应体（~500B），而在**内部遥测 schema 变成事实对外契约**——后续想改 run_summary 字段名就会被潜在前端依赖绑架。
- **建议**：若近期无"解析质量观测面板"排期 → 从 Meta 撤字段（删 1 字段 + MetaBuilder 3 行 + 测试断言 ~30 分钟）；若有明确面板规划 → 保留并在 API contracts 里补一行稳定性声明。两个方向都可辩护，等用户定。

### P6 · MergeStats.skipped/fallback 双工厂方法体逐字节相同

- **位置**：`ScoreGlobalMergeOrchestrator.java:37-43`
- **问题**：skip（前置未过）与 fallback（执行后降级）的分类学既没体现在字段也没体现在行为，语义全靠 skipReason 字符串表达——方法名把同一信息又说了一遍，属于"两份代码零行为差异无声明"的最差中间态。
- **建议**：合并为单个 `notInvoked(reason)` 工厂；若想保留分类学，把它放进 reason 枚举值的一个属性里。P3 的搭车项。

### P7 · OpenAiScoreAnalyzer 的 infrastructure→application 反向穿透（存量债，非本 PR 引入）

- **位置**：`OpenAiScoreAnalyzer.java:17` —— `import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;`
- **问题**：infrastructure 包 import application 包的类，与分层惯例反向。本 PR 在同类内做了 laneCounts/countAligned 改造但没有顺手中翻——范围控制上无可指摘（不应为一个遥测功能翻整个类），但要记入弯路库存防止被继续投资固化。`ScoreDocExcerptExtractor` 本身是无状态纯提取器，下沉 domain 无阻力。
- **建议**：下次触碰 scoreparse 边界时随 ScoreParseConstants 式收敛一并处理。
- **复杂度**：中（~1h：移动类 + import 清理 + ArchUnit 复验）。

### P8 · 12000 硬截断对超长文档的裁决质量塌陷（前瞻债，最重要的一项演化方向）

- **位置**：`ScoreGlobalMergeOrchestrator.resolveSectionText`（终版 :106-131，stopEarly break 后剩余 section 整体丢弃，仅留全局 boolean truncated）
- **问题**：多章节大标（如 40 页评分细则）前 12k 只覆盖商务部分时，技术部分对 LLM 裁决完全不可见——这些项全部走 poolBackedKept 兜底，守卫对文档后半部实质退化为"池身份自证"，幻觉防御失效。truncated=true 目前只有日志/落档，治理链无任何差异化行为。单段截断是 spec 055 时代的简单解，057 引入池背书后防御模型已经变了。
- **建议**：另立 spec 做**分段裁决**（每 ≤12k 语料一次 resolve + 跨段去重合并守卫），或低成本起步方案"按评分关键词密度优先装填 section"。触发条件可直接挂本 PR 的遥测红利：线上 `run_summary` 中 `truncated=true 且 hallucinationDropped≈0` 任务占比超阈值时启动——这正是 FR-04 的第一笔回报。
- **复杂度**：高（LLM 成本×N、跨段去重语义，必须独立 spec，不进本 PR）。

## 反证排除清单（核实过、不是发现）

| 疑似点 | 排除理由 |
|---|---|
| GoldenCasesReplayTest 自实现 P/R/F1 贪心配对 | 匹配谓词是业务定制（归一化互含+权重差≤0.5），通用库不覆盖；scripts 层零依赖纪律 |
| report.mjs 手写报告渲染 | scripts 层 node 原生零依赖是明文规范 |
| nightly-full.sh 只打 curl 模板 | 规格如此（SKIP-safe 两层设计第二层占位），交付缺口已在 README 声明 |
| PostProcessor 8 步链未拆小类 | 每步都是领域对象单行调用，拆开会造成上下文参数爆炸 |
| V1204 TEXT 列性能/索引 | 档案不做 WHERE/ORDER BY，无索引诉求，尺寸 <1KB |
| countAligned 的 contains 全扫 | chunk 受 chunker 尺寸约束、候选数十级，毫秒量级伪热点 |

## 建议处理窗口

- **本 cleanup PR（半天）**：P1 + P3 + P4（+P6 搭车）——纯内部质量，不动行为面，金标数值应逐字节不变作为验收线。
- **需拍板**：P5 方向（撤 or 声明契约）。
- **下次边界重构搭车**：P7。
- **独立 spec + tech-debt-tracker 挂触发条件**：P8（触发器用本 PR 遥测数据）。
