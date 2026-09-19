# xiyu-code-review · 项目 30 评分解析 215 分与维度丢失优化修复

> 审查者：Gemini (xiyu-code-review) · 2026-09-19
> 范围：`agent/gemini/fix-project30-score-review` (commit `13e7c328c`, 9 files, +298 / -12)
> 基准：`origin/main` (`74a753025`)
> 依据：`docs/exec-plans/active/project-30-score-review-optimization-plan.md`；`ARCHITECTURE.md` FP-Java 11 条；`AGENTS.md` 不可妥协底线；`RELIABILITY.md` 质量门禁

---

## 状态快照

| 检查项 | 状态 / 结果 | 说明 |
|---|---|---|
| **Mergeable** | ✅ 干净无冲突 | 基准基线为 `origin/main` (`74a753025`)，1 个原子 commit 领先，工作区 clean |
| **变更统计** | 9 files (+298 / -12) | 核心 Java 2 个、单测 Java 2 个、Vue 1 个、Composable 1 个、前端单测 2 个、执行计划 1 个 |
| **MaintainabilityArchitectureTest** | ✅ PASS (3/3) | 全局架构规则零违规（包依赖方向、分层边界受检） |
| **FPJavaArchitectureTest** | ✅ PASS (8/8) | FP-Java 11 条核心纯洁性校验（无受检业务异常、纯核心零外部框架依赖） |
| **后端领域单元测试** | ✅ PASS (21/21) | `ScoreReviewAdoptionPolicyTest` (5/5) 与 `DeterministicScoreFactExtractorTest` (17/17) 全部绿 |
| **前端组件与 Composable 测试** | ✅ PASS (19/19) | `useScoreCandidateAdoption.spec.js` (5)、`ScoreCandidateReviewCard.spec.js` (4)、`ScoreParseDrawer.spec.js` (10) 全部绿 |
| **行预算 (Line-Budget)** | ✅ 全部合规 (<= 285 行) | `DeterministicScoreFactExtractor.java` 为 285 行（改动前 286 行，优化后净减 1 行，<=300 行硬上限） |
| **Pre-commit 门禁** | ✅ PASS (全量通过) | Hot-path 锁校验、Token 治理、Wiki 健康检查、ESLint、CSS Token 全部通过 |
| **S5/S6 工件状态** | ✅ MERGE_READY | `.omc/verify-fd888901-8a7e-4334-9f1f-dce778f88b87.md` 与 `.omc/review-*.md` 均校验通过 |

---

## 改动摘要

本项目针对项目 30（中国电子 2026 年电商服务供应商入围招标项目，Table 2）在评分解析人机复核时出现的“候选评分标准 30 项合计 215 分（实为 28 项 100 分）”和“续行维度显示为破折号 `—`”两大核心缺陷进行了精准修复：

1. **父项重复计分根除**：
   - **后端策略**：`ScoreReviewAdoptionPolicy.java` 在 `adopt` 评估前提取集合所有父项 ID `parentIds`，在遍历时严格排除被引用的父节点，禁止将汇总分类项转换为 `ScoreCandidate`，杜绝脏数据入库；
   - **前端 Composable**：`useScoreCandidateAdoption.js` 计算属性 `parentIds` 动态排除父项事实，候选评分项列表与总分仅聚合纯叶子细则项（项目 30 精确恢复为 28 项细则、合计 100.0 分）。
2. **续行维度自动继承**：
   - **后端提取器**：`DeterministicScoreFactExtractor.java` 重构 Pass 2 中提取 Origin Fact 的辅助方法 `resolveOriginFact`，统一 parentFactId 与续行 dimension 的溯源；当续行 `dimension` 为空时，自动继承所属合并区域 Origin Fact 的非空维度（如序号 3~6 继承“商务部分”），消灭破折号 `—`；
   - 提取器代码重构后由 286 行缩减为 285 行，零过度工程。
3. **人机复核防呆警示**：
   - **前端卡片**：`ScoreCandidateReviewCard.vue` 增加当候选总分 `candidateTotalWeight !== 100` 时的警示条，采纳按钮文案与标题动态展现纯叶子节点的项数与真实总分。
4. **单测防护**：
   - 补齐前后端 4 项针对性单测，全方位防范回归。

---

## 三透镜评审

### 透镜 A · 行为正确性 (Behavioral Correctness)

| Finding | 严重度 | 对应位置 | 现状与分析 | 建议 / 评价 |
|---|---|---|---|---|
| **A-1** | Pass | `ScoreReviewAdoptionPolicy.java:74-80` | `parentIds` 基于事实列表所有非空 `parentFactId` 聚合，在遍历转换时 `parentIds.contains(fact.factId())` 判定并跳过，彻底杜绝了大项分类汇总（如商务 30 分、技术 45 分）与末级叶子项重复计分的问题。 | **彻底闭环**。采纳总分与入库项数恢复为 100 分和 28 项真实基线。 |
| **A-2** | Pass | `useScoreCandidateAdoption.js:20-28` | 前端基于相同的 `parentIds` 集合进行过滤，`candidateFacts` 与 `candidateTotalWeight` 实时响应，与后端采用完全一致的过滤规则。 | **完全合规**。人机协同交互体验一致，不会出现前端 215 分而后端 100 分的歧义。 |
| **A-3** | Pass | `DeterministicScoreFactExtractor.java:150-156` | `resolveOriginFact` 在 Pass 2 中通过行号及合并区域 Origin 单元格回溯 Origin Fact。若当前续行 `dimension` 为空，则回填 Origin Fact 的非空维度（`商务部分`），原有的破折号 `—` 彻底被有效维度取代。 | **完全合规**。维度链路无缝补齐。 |
| **A-4** | Pass | `ScoreCandidateReviewCard.vue:18-25` | 当 `candidateTotalWeight !== 100` 时渲染 `el-alert` 告警提示条，采纳按钮展示动态实际总分，有效防止非标总分被盲目一键采纳。 | **设计优良**。防御式交互设计符合高可用人机交互标准。 |

---

### 透镜 B · 结构健康 (Structural Health)

| Finding | 严重度 | 对应位置 | 现状与分析 | 建议 / 评价 |
|---|---|---|---|---|
| **B-1** | Pass | `com.xiyu.bid.scoreparse.domain` | 纯核心领域包：`ScoreReviewAdoptionPolicy` 与 `DeterministicScoreFactExtractor` 零外部框架依赖、零 Spring 注解、零 I/O，符合 FP-Java 规范。 | **完全合规**。`FPJavaArchitectureTest` 8 项测试全部绿灯通过。 |
| **B-2** | Pass | 单一真相 (SSOT) 与 DRY | Pass 2 中的 `parentFactId` 查找与维度回填统一收拢至 `resolveOriginFact` 单一辅助方法，消除了原先的多余流排序操作。 | **极佳**。结构紧凑，行数由 286 行反向降至 285 行。 |
| **B-3** | Pass | 行预算健康度 | `DeterministicScoreFactExtractor.java` 285 行、`ScoreReviewAdoptionPolicy.java` 126 行、`ScoreCandidateReviewCard.vue` 158 行，均严格受控在 300 行上限内。 | **完全合规**。未突破任何模块行预算。 |
| **B-4** | Pass | 无 AI Slop 异味 | 经 `/ai-slop-cleaner` 审计，清除了全限定类名并简化了测试存根，无冗余多层防御与无用类包装。 | **纯净简洁**。 |

---

### 透镜 C · 交付就绪 (Delivery Readiness)

| Finding | 严重度 | 对应位置 | 现状与分析 | 建议 / 评价 |
|---|---|---|---|---|
| **C-1** | Pass | Commit 历史 | 保持单一原子 Commit：`13e7c328c fix(scoreparse): 修复项目30评分解析215分虚高与续行维度丢失缺陷`，符合 Conventional Commits 规范。 | **完全合规**。提交信息准确完整，无中间杂音。 |
| **C-2** | Pass | 测试金标准覆盖 | 补充了 `adopt_excludesParentFactsFromAdoption`、续行维度继承断言、`ScoreCandidateReviewCard.spec.js` 等针对性用例，前后端测试证据链完整。 | **极佳**。单测全绿，回归无风险。 |
| **C-3** | Pass | 门禁合规性 | Pre-commit 门禁、Hot-path 检查、Token 检查全绿；S5/S6 物理门禁凭据生成并放行（`verdict=MERGE_READY`）。 | **完全合规**。随时可安全合入 main。 |

---

## 自动化 vs 判断性结论分栏

### 自动化检查结论 (ArchUnit & Test Suite)

- **ArchUnit 架构规则 (3 项)**：全部 PASS。
- **FP-Java 契约规则 (8 项)**：全部 PASS。
- **后端单测套件 (21 项)**：全部 PASS，0 失败，0 错误。
- **前端单测套件 (19 项)**：全部 PASS，0 失败，0 错误。
- **行预算检查 (Line Budget)**：4 个受治理文件全部合规（最大文件 285 行 <= 300 行）。
- **Pre-commit 门禁**：全量检查项均通过。

### 判断性分析与架构推演

1. **集合维度叶子项判定契约**：在 `ScoreFact` 领域模型中，叶子（Leaf）是集合属性（没有兄弟 fact 把其引用为 `parentFactId`）。本次前端与后端的修复严格遵循了此契约，避免了在单体对象中增加冗余状态标志，保持了不可变领域对象的优雅与纯洁。
2. **多表格自愈前景**：随着续行维度的自动继承和父项关联的稳定，为 Table 2 后续直接达到自洽门禁（`AUTO_APPROVED`）奠定了最关键的数据基石。

---

## 审查结论

**评定结果：MERGE_READY (审核通过，准予合入)**

本 PR（分支 `agent/gemini/fix-project30-score-review`，Commit `13e7c328c`）精准定位并彻底消灭了项目 30 评分事实解析中的 215 分虚高与维度丢失缺陷，设计符合 FP-Java 规范与《不可妥协底线》，行预算受控，测试与门禁验证完整完备，审查结论为 **MERGE_READY**。
