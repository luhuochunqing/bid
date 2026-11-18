# Implementation Plan: 评分项意图确定性路由与推荐打分公式重构（Phase 1 止血）

**Branch**: `agent/zcode/090-case-recommend-intent-routing` | **Date**: 2026-11-18 | **Spec**: [specs/090-case-recommend-intent-routing/spec.md](file:///Users/user/xiyu/worktrees/zcode/specs/090-case-recommend-intent-routing/spec.md)

**Input**: Feature specification from `specs/090-case-recommend-intent-routing/spec.md`

## Summary

实现案例推荐 Phase 1 止血重构：
1. **意图路由（`ScoreItemIntentRouter`）**：在纯核心策略中新增确定性意图路由器，将评分项划分为 TECHNICAL, QUALIFICATION, PERFORMANCE, PERSONNEL, COMMERCIAL；
2. **物理阻断**：当评分项为 `QUALIFICATION` 时，编排层直接短路 `knowledge_case` 切片推荐，专精装配企业资质原件扫描件；
3. **打分模型乘法先验门禁**：重构 `ScoreItemCaseMatchPolicy`，将内容相关度作为门禁基准，背景加成作为比例放大因子，彻底斩断无内容相关性案例凭借同客户实体反客为主的数学路径。

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.2, Vue 3, Vite 5  
**Primary Dependencies**: Lombok, JUnit 5, AssertJ, Element Plus  
**Storage**: MySQL 8.0, JPA  
**Testing**: Maven Surefire (`mvn test`), Vitest (`npm test`)  
**Target Platform**: Linux server / macOS local dev  
**Project Type**: Web service (Spring Boot backend + Vue 3 frontend)  
**Performance Goals**: 单次打分 300+ 条案例 $\le 50\text{ms}$  
**Constraints**: 遵循 FP-Java 规范（纯函数、无副作用、无 Spring/DB 依赖、单文件 $\le 300$ 行）  

## Constitution Check

- [x] **FP-Java 契约**：核心路由与算分策略全部放在 `com.xiyu.bid.casework.domain.policy`，100% 可脱离 Spring 容器运行单测。
- [x] **禁止 Mock 政策**：不引入任何 Mock 假数据，集成测试直连真实实体映射。
- [x] **向后兼容**：保持现有 `ScoreItemCaseRecommendResponse` 契约结构不变。
- [x] **原子提交与测试先行**：先写失败的回归测试（反证等保排第一的缺陷），再写实现，最后验证全绿。

## Project Structure

### Documentation (this feature)

```text
specs/090-case-recommend-intent-routing/
├── spec.md              # 需求规格
├── plan.md              # 架构与技术设计方案（本文件）
└── tasks.md             # 任务清单（TDD 拆解）
```

### Source Code

```text
backend/src/main/java/com/xiyu/bid/casework/
├── domain/
│   └── policy/
│       ├── ScoreItemIntentRouter.java          # [新增] 评分项意图确定性路由器
│       ├── ScoreItemCaseMatchPolicy.java        # [修改] 乘法门禁算分重构
│       └── CaseCategoryMatchPolicy.java         # [修改] 大类判定与互斥对齐
└── application/
    └── service/
        └── ScoreItemCaseRecommendAppService.java # [修改] 资质项彻底阻断文本切片，专精返回证书

backend/src/test/java/com/xiyu/bid/casework/
├── domain/policy/
│   ├── ScoreItemIntentRouterTest.java          # [新增] 意图路由全覆盖测试
│   └── ScoreItemCaseMatchPolicyTest.java       # [修改] 验证乘法门禁与等保0分反证
└── application/service/
    └── ScoreItemCaseRecommendAppServiceTest.java # [修改] 资质项切片阻断与证书装配测试
```

## Architecture & Algorithm Design

### 1. 意图路由器（ScoreItemIntentRouter）

输入：`dim`（维度）、`detail`（细则）  
输出：`ScoreItemIntent` 枚举（`TECHNICAL`, `QUALIFICATION`, `PERFORMANCE`, `PERSONNEL`, `COMMERCIAL`, `OTHER`）

规则判定顺序：
1. **穿透分析**：当 `dim` 为宽泛章节名（“详细评审/综合能力/技术部分”）时，以 `detail` 实质内容为准；
2. **QUALIFICATION**：命中资质、资格、证书、认证、管理体系、许可、执照、ISO、AAA、资信等；
3. **PERFORMANCE**：命中同类业绩、合同业绩、供货业绩、类似项目、业绩证明等；
4. **PERSONNEL**：命中项目经理、团队配置、建造师、架构师、社保、职称等；
5. **COMMERCIAL**：命中报价、付款、结算、账期、保函、偏离等；
6. **TECHNICAL**：命中技术、方案、系统、对接、实施、运维、服务、应急、仓储等。

### 2. 乘法先验打分公式重构（ScoreItemCaseMatchPolicy）

**旧公式（加法代偿陷阱）**：
$$Total = SubTopic(25) + Detail(25) + Title(20) + Entity(20) + ContextBonuses(18)$$
当同客户无关案例拿到 8 分弱内容相关时，加成 28 分全额爆发，$8 + 20 + 28 = 56$ 分，形成逆淘汰。

**新公式（乘法先验门禁）**：
1. **基础内容相关分（Content Relevance Score, 0~100）**：
   $$ContentScore = \min(100, SubTopicScore + DetailOverlapScore + TitleSimilarityScore)$$
   - 若 $ContentScore == 0$，直接短路返回：$FinalScore = 0$（一票否决，任何背景分均不得释放）。
2. **背景放大系数（Context Bonus Multiplier, $1.0 \sim 1.5$）**：
   $$BonusFactor = \frac{EntityScore(0 \sim 20) + CategoryScore(0 \sim 10) + TypeScore(0 \sim 5) + WonScore(0 \sim 5) + QualityScore(0 \sim 5)}{100.0}$$
   $$FinalScore = \min(100, \text{round}(ContentScore \times (1.0 + BonusFactor)))$$
- **数学证明**：
  - 若内容相关度为 0（如等保与配送不相关），$0 \times (1.0 + BonusFactor) \equiv 0$ 分，**绝对不可能排在前列**；
  - 若内容相关度高（如 60 分），同客户、央企背景可将其放大到 $60 \times 1.4 = 84$ 分（优质），合理体现标杆客户的优先复用价值。

### 3. 应用服务层（ScoreItemCaseRecommendAppService）物理切分

```java
ScoreItemIntent intent = ScoreItemIntentRouter.route(context.dim(), context.detail());
if (intent == ScoreItemIntent.QUALIFICATION) {
    // 资质证书项：彻底阻断文本切片，仅查企业资质库
    List<CertificateItem> certs = enterpriseQualificationQueryPort.findMatchingCertificates(...);
    return ScoreItemCaseRecommendResponse.builder()
            .total(certs.size())
            .items(List.of()) // 文本切片严格为空
            .matchedCertificates(certs)
            .build();
}
// 方案类：正常执行文本切片匹配与乘法门禁算分
```

---

## Phase 2 架构与技术设计方案（向量语义检索打通与分词规范化）

### 1. 分词规范化与假词逆向 IDF 消除设计（TextSimilarityPolicy & ScoreItemCaseTextMatchPolicy）

#### 痛点根因分析
旧实现直接在 `TextSimilarityPolicy.extractTokens` 中针对任意 CJK 字符串做 `2-gram` 和 `3-gram` 机械切片，造成大量无意义跨词碎片（如“人员须具备较高业务水平”切出“员须”、“须具”、“备较”、“高业”）。在语料库中，这些假词由于是生造的跨词拼凑，文档频率 $DF$ 极低（往往为 1），根据 IDF 公式：
$$\text{IDF}(t) = \ln\left(\frac{N + 1}{\text{DF}(t) + 1}\right)$$
导致生造假词的 IDF 暴增 7~10 倍。长文本只需碰巧包含一个“备较”，即霸占了 25 分条款契合分。

#### 治本方案（FP-Java 纯核心）
1. **停止盲目 3-gram 切片**：废弃无边界的 3-gram 字符滑窗；
2. **2-gram 助词与连词边界守卫**：
   - 提取 2-gram 时，若首字符或尾字符为助词/连词/介词（`的、了、在、是、为、以、由、于、之、向、从、把、被、且、及、与、或、须、应、需、得、较、可、并、等`），直接阻断过滤；
   - 阻断纯数字、英文字母与汉字之间的跨类型无边界碎片；
3. **招投标领域高频词表（Domain Lexicon）直通**：
   - 内置核心招投标术语集合（`方案、技术、管理、服务、系统、应急、仓储、物流、配送、质量、安全、运维、培训、售后、人员、业绩、实施、对接、集成、定价、价格、资质、证书、接口、参数、验收` 等）；
   - 属于领域词汇的词元优先放行并赋予标准基准权重；
4. **语料库 IDF 平滑与低频过滤**：
   - 在 `ScoreItemCaseTextMatchPolicy.Corpus` 构建时，针对 $DF=1$ 且不在领域词表中的边缘碎片进行低通截断，防止冷僻假词权重压倒核心业务词。

---

### 2. 向量语义双轨融合打通与优雅降级（Vector Semantic Dual-Track & Graceful Degradation）

#### 架构设计（六边形端口与纯核心策略）
1. **端口解耦**：
   - 定义 `CaseVectorSemanticQueryPort`（或复用现有 `BidCaseSliceVectorCache`），提供根据文本查询相似向量或根据已有向量计算相似度能力；
2. **纯核心策略扩展（ScoreItemCaseMatchPolicy）**：
   - 在 `ScoreItemContext` 增加可选向量语义分 `semanticScore`（$0 \sim 35$ 分）；
   - 内容相关度融合公式：
     $$\text{ContentScore} = \min(100, \max(\text{LexicalRelevance}, \text{SemanticScore}) + \text{SubTopicScore})$$
     - 当字面无交集（如“接口对接” vs “系统协同”），但向量余弦相似度达到 0.75 时，提供 $0.75 \times 35 \approx 26$ 分的内容基准分，越过一票否决门禁，召回语义吻合的真案例；
3. **优雅降级（Graceful Degradation）**：
   - 当向量服务或缓存未就绪时（如单测、纯源码环境、网络断开），`semanticScore` 默认为 0，自动回退到纯词法/子主题匹配；
   - 保证 100% 向后兼容，全仓单测零回归。

---

### 3. 行预算重构与编排解耦（US6）

`ScoreItemCaseRecommendAppService` 当前 298 行（预算 300 行）。
抽离 `ScoreItemCaseRecommendEvaluator.java`（包级私有协作类，纯计算与组装）：
- 负责 `candidateCases` 批量算分、大类过滤、置顶重排与 DTO 装配；
- 将 `ScoreItemCaseRecommendAppService` 行数降至约 160 行，彻底解除行预算风险。

---

## Phase 3 架构与技术设计方案（候选池数据清洗与低质废料切片过滤）

### 1. 废料切片与模板污染根因
历史积累的切片池（`knowledge_case` / `bid_case_slice`）包含数千条历史文本，其中存在大量低质噪声：
1. **纯格式模板（Boilerplate Form Templates）**：如授权委托书格式、投标承诺函格式、投标保函格式、包含大量空白填空符号（`______`、`（盖章）：________`、`日期：____年__月__日`）；
2. **纯占位符/敷衍文本（Placeholders）**：如“详见附件”、“见附表”、“完全响应招标文件要求，特此承诺”等缺少实质应答方案的极短文本；
3. **目录与排版残片（TOC & Page Artifacts）**：含有大段点号、破折号及页码的目录提取残留（如`第一节 ... 1`）；
4. **低信息熵文本（Low Information Entropy）**：大量重复符号或字符种类极低的数据。

这些废料切片不仅会在前台刷屏影响用户体验，还会因为包含通用词或实体词在 TF-IDF 语料构建时产生噪声。

### 2. 治本设计：纯核心 `CaseContentQualityPolicy` (FP-Java)
在 `casework/domain/policy/` 建立纯无状态策略 `CaseContentQualityPolicy`：
- **短文本拦截**：文本清理后有效长度 $< 30$ 字符直接判定为低质；
- **纯模板识别**：匹配格式声明与签名盖章下划线特征（如“格式如下”、“格式自拟”、“法定代表人（签字或盖章）”、“特此委托”等）；
- **占位符识别**：匹配“详见附件”、“见附表”、“完全满足要求”等敷衍无实质应答模式；
- **排版与目录残片识别**：符号/空白占比 $> 35\%$ 且含有点号、破折号或连续下划线；
- **低信息熵检测**：不重复字符占比 $< 0.20$；
- **综合质量分门禁**：结合文本长度与结构，不达标者（质量分 $< 40$）一票否决；
- **零误杀保护**：具有完整结构、充实方案论述的技术与商务方案 100% 正常放行。

### 3. 双层门禁防御（Defense in Depth）
1. **应用编排层前置过滤**：
   在 `ScoreItemCaseRecommendAppService` 召回候选集后，立即执行 `CaseContentQualityPolicy.filterAdmissible(candidateCases)`：
   - 低质废料切片不参与 `ScoreItemCaseTextMatchPolicy.buildCorpus`（保护 IDF 纯度）；
   - 避免无谓的算分与向量查询开销；
2. **纯核心策略层兜底防御**：
   在 `ScoreItemCaseMatchPolicy.evaluate` 中作为第 0 级门禁检查：
   - 若传入低质切片，直接返回 0 分并标记“低质废料切片/纯模板过滤”。
