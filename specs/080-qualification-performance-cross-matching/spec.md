# Feature Specification: 080 评分项跨章节穿透资格项业绩检索与精准匹配

**Feature Branch**: `agent/codex/qual-perf-matching`
**Created**: 2026-10-26
**Status**: In Progress
**Origin Request**: 生产环境标书中业绩评分条款（如"满足资格条件的业绩得基础分60分，每增加一个满足资格条件的业绩加20分"）只引用资格条件，评分项自身无品类/金额，导致被误判为主观项或无法从业绩库匹配。需跨章节检索资格项业绩实质要求并打通匹配。

---

## 1. 业务背景与问题痛点

在标准招投标规范中，招标文件普遍采用**初步评审（资格审查）**与**详细评审（评分标准）**解耦的行文方式：
1. **第三章 资格审查项**：详细规定了供应商必须满足的刚性业绩门槛（如：时间区间 2023-01-01 至今、单笔合同金额 ≥ 1000 万元、指定品类如工业品/MRO/办公耗材、凭证要求如合同首页+盖章页+发票）；
2. **第四章 评分标准项**：在业绩评分条款中高度精炼，写为：*“供应商业绩 | 满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%”*。

### 历史 200+ PR 打转的根因：
- **单行文本孤岛**：过去历次 PR 试图仅凭评分项 `detail` 的单行文字抽取业绩要求。但真实门槛根本不在该行中，导致正则不断修补、不断因新标书失效；
- **主客观性质误判**：具有“基础分 + 递增分”的量化阶梯公式因基数非零或引用了“资格条件”被大模型或旧规则误判为 `SUBJECTIVE`（主观项），短路了知识库匹配逻辑；
- **约束缺失导致假阳性/短路**：评分项提取不出真实行业和金额门槛，向业绩库发起无约束查询，触发系统安全门禁（`未抽取到可执行的业绩匹配约束，禁止全表匹配，待人工确认`）。

---

## 2. 核心用户故事 (User Stories)

### User Story 1 - 评分项穿透资格项获取业绩实质约束 (Priority: P1)
**As a** 投标专员 / 系统使用者
**When** 招标文件中的业绩评分条款只写了“满足资格条件/初步评审/同类业绩”，而没有展开具体品类和金额门槛时
**I want** 系统自动穿透到标书资格审查章节提取真实的业绩门槛（时间、金额、品类、凭证要求）
**So that** 业绩评分项能够获得完整、准确的业务约束，用于后续精准匹配。

#### Acceptance Scenarios:
1. **Given** 标书资格项包含 `近三年承担过单项金额不低于1000万元的工业品供货业绩`，评分项为 `满足资格条件的业绩得基础分60分...`；
   **When** 系统执行评分标准预计得分解析时；
   **Then** 评分项能够关联提取到金额门槛（1000万）与品类（工业品），并作为业绩匹配的查询条件。
2. **Given** 标书资格项未提及任何业绩条件（纯资质证书与法人要求）；
   **When** 评分项声明“满足资格条件”；
   **Then** 系统诚实提示“资格项中未检索到明确业绩门槛，待人工确认”，绝不无约束全量匹配冒充满分。

---

### User Story 2 - “基础分+递增分”量化业绩阶梯客观项识别与计分 (Priority: P1)
**As a** 投标负责人
**When** 遇到“基础分（如60分/满分100）+ 每增加一个加X分”的评分条款时
**I want** 系统将其准确识别为可量化计算的 **`OBJECTIVE` 客观项**，并按阶梯公式自动计算得分
**So that** 页面不会将其误降级为主观项导致预计得分“待确认”。

#### Acceptance Scenarios:
1. **Given** 条款文本包含 `满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩...加20分，满分100分`；
   **When** 执行 `ScoreTypeClassificationPolicy` 判定时；
   **Then** 判定为 `OBJECTIVE`，不被判定为 `SUBJECTIVE`。
2. **Given** 满足条件的业绩匹配到 1 个，权重 5 分；
   **When** 执行计分时；
   **Then** 得分按 60/100 换算为 3.0 分；若匹配到 3 个，得分达到 100/100 换算为满分 5.0 分。

---

### User Story 3 - 评分依据与弹窗证据可追溯透明展示 (Priority: P2)
**As a** 业务审核员
**When** 打开评分项详情弹窗（`ScoreItemDetailModal`）查看依据时
**I want** 弹窗清晰说明业绩的判定依据、穿透来源与匹配明细
**So that** 我能一眼看懂系统为什么给这个分，引用了哪条资格条件。

#### Acceptance Scenarios:
1. **Given** 穿透资格项成功匹配；
   **When** 用户点击查看详情；
   **Then** 得分依据展示：`引用资格项业绩要求：单项金额≥1000万元，品类：工业品；匹配命中公司业绩库 3 份合同，基础分 60 分 + 增项 40 分，得满分`。

---

## 3. 输入输出契约 (Contract Specification)

### 3.1 跨章节穿透输入/输出
```java
public record CrossChapterPerformanceConstraint(
    boolean hasQualificationReference, // 是否引用资格条件
    String rawClauseText,              // 资格项中命中的业绩条款原文
    LocalDate signedAfter,             // 签约起始时间
    BigDecimal minContractAmount,      // 单项合同金额门槛（元/万元归一）
    List<String> targetCategories,     // 品类/行业关键词（如：工业品、MRO、办公用品）
    List<String> customerTypes,        // 客户类型限制（大型集团、央国企等）
    List<String> evidenceRequirements  // 必备证明（发票、合同首页等）
) {}
```

### 3.2 计分规则扩展输入/输出
```java
public record SteppedIncrementalRule(
    BigDecimal baseScore,              // 基础分（如 60）
    int baseCount,                     // 获得基础分所需数量（通常为 1）
    BigDecimal incrementScore,         // 每增加一项加分（如 20）
    int incrementCount,                // 递增单位数量（通常为 1）
    BigDecimal maxScore                // 满分基准（如 100）
) {
    public BigDecimal calculateScore(int qualifiedCount, BigDecimal itemWeight);
    public int requiredCountForMaxScore();
}
```

---

## 4. 边界与负面场景防御 (Guardrails)

1. **绝对禁止全表裸查**：若资格项与评分项均无法提取出实质品类或金额，严禁向业绩库裸查返回全部有效合同，必须置 PENDING 待人工确认；
2. **纯策略无外部副作用**：穿透解析器必须是纯函数（FP-Java），输入标书快照文本/资格列表，输出结构化约束，不直连网络与外部状态；
3. **回归守卫（Golden Cases）**：必须保留既有项目 22、项目 1117、项目 1382 的回归单测，新逻辑不得影响现有纯文本阶梯打分。
