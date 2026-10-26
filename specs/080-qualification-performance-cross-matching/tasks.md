# Task List: 080 评分项跨章节穿透资格项业绩检索与精准匹配

**Branch**: `agent/codex/qual-perf-matching`
**Status**: Ready for Implementation
**Spec**: `specs/080-qualification-performance-cross-matching/spec.md`
**Plan**: `specs/080-qualification-performance-cross-matching/plan.md`

---

## Phase 1: 纯策略核心演进 (FP-Java / 零副作用 / 100% 单测)

- [x] **Task 1.1: 扩展阶梯计分策略支持“基础分+增项分”模型**
  - 在 `IncrementalCountScorePolicy` 中新增 `BASE_INCREMENT_PATTERN` 模式（识别“得基础分X分，每增加Y个加Z分，满分M分”）；
  - 导出计算方法 `score(int qualifiedCount, BigDecimal weight)`；
  - 编写 `IncrementalCountScorePolicyTest` 测试用例（覆盖基础分60、每增1个加20、满分100与按权重折算）。

- [x] **Task 1.2: 修正主客观分类策略误判**
  - 在 `ScoreTypeClassificationPolicy` 中支持检测上述“基础分+增项分”规则；
  - 确保包含“满足资格条件...得基础分60分...加20分”的文本被 100% 判定为 `OBJECTIVE`；
  - 编写 `ScoreTypeClassificationPolicyTest`，验证截图中的生产文本被判定为客观项。

- [x] **Task 1.3: 构建跨章节穿透纯核心策略 `CrossChapterPerformancePolicy`**
  - 识别评分项是否引用“资格条件/资格审查/初步评审”；
  - 定向收集标书资格项及初步评审章节中的业绩实质条款；
  - 结构化提取签约时间、金额门槛、物资品类（MRO/工业品/办公用品等）；
  - 编写独立单测 `CrossChapterPerformancePolicyTest` 验证各种标书写法与噪音防护。

---

## Phase 2: 编排层注入与业绩匹配联动 (EstimatedScoreService & Match)

- [x] **Task 2.1: 穿透信息装配与 `ProjectMatchRequest` 补齐**
  - 在 `EstimatedScoreService` 中注入 `TenderQualificationQueryPort`；
  - 当处理 `CATEGORY_PROJECT` 且评分项引用资格条件时，调用 `CrossChapterPerformancePolicy` 获取跨章节约束；
  - 将穿透出的品类与金额注入 `ProjectMatchRequest`，防止因无约束被门禁拦截。

- [x] **Task 2.2: 阶梯计分与依据格式化**
  - 使用更新后的阶梯策略根据命中数量计算得分；
  - 在 `EstimatedScoreBasisFormatter` 中格式化穿透依据文案（明确指出引用的资格项门槛及命中合同数）；
  - 编写 `EstimatedScoreServiceTest` 单元测试。

---

## Phase 3: 黄金用例固化与全链路回归验证 (Golden Cases & Gates)

- [x] **Task 3.1: 增加真实生产标书黄金回归用例**
  - 新增 `case-production-cross-chapter-perf.json`（固化本案例的招标文件资格与评分条目）；
  - 在 `ProductionCrossChapterPerformanceGoldenReplayTest` 中增加对应验证。

- [x] **Task 3.2: 本地门禁与验证**
  - 运行 `mvn test -Dtest=ScoreTypeClassificationPolicyTest,IncrementalCountScorePolicyTest,EstimatedScoreServiceTest,ProductionCrossChapterPerformanceGoldenReplayTest`；
  - 跑通 `scripts/pre-push-gate.sh` 本地门禁自检。
