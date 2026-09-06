<!-- 一旦我所属的文件夹有所变化，请更新我。 -->
# 标书质检 25 项全量摆脱正则演进路线图与契约规范

> **状态**：Active (进行中)  
> **创建时间**：2026-09-05  
> **责任人**：AI 标书质检专项团队 / Agent 协作组  
> **背景**：项目 1358 等标书质检长期依赖纯正则与字符子串匹配，导致表格格式误判、目录页（TOC）超前命中假通过、单方口头声明放行等顽固缺陷。历史曾提交 100+ 单点正则补丁均未能根治。本项目旨在确立统一的「两阶段契约抽取 + 事实要素检验 + 强凭证直通」架构范式，全面替代脆弱的正则体系。

---

## 一、 核心架构原则（Golden Standards）

1. **两阶段流水线原则**：
   - **阶段一（招标文件端）**：在大模型招标要求抽取（`TenderRequirements`）中建立专属结构化契约 record，并辅以纯核心确定性规则（`Deterministic*Extractor`）做双轨融合与事实校准（`fuseWithDeterministic`），杜绝大模型改写与格式幻觉覆盖原文。
   - **阶段二（标书核验端）**：废除简单关键词包含与反向否定（`!hasToc`），必须采用**强制事实要素正向约束**（凭证类型标志 + 具体交易/编号/印章事实数据）。
2. **强凭证直通降本抗抖动原则**：
   - 若确定性规则在标书已命中确凿的真实凭证（带证据事实且状态为 PASS），**直接直通保留结果，坚决不二次调用大模型**；
   - 仅在未命中（`FAIL`）或存疑/模板占位（`MANUAL_REVIEW`）时，接通大模型深度语义裁决与事实核验。
3. **确定性保底审慎合规原则**：
   - 确定性规则在无确凿证据时**返回 null**，绝不武断断言免收；
   - 双轨冲突且无具体金额/明确依据时，保持审慎合规提示核查，杜绝偏向免收放大幻觉。
4. **FP-Java 纯核心无外部依赖**：
   - 所有策略类（Extractor、Policy、Rules）均为纯函数、不可变数据结构（record）、无 Spring 容器注解、无 checked 异常构造。

---

## 二、 25 项全景分类与现状分级矩阵

对照 `BidQualityCheckItem` 25 项枚举：

| 序号 | 代码 (`code`) | 名称 (`title`) | 类别 | 严重度 | 现状底座 | 演进归宿与策略 | 规划批次 |
|---|---|---|---|---|---|---|---|
| 1 | `COVER_INFO` | 封面信息完整性 | 基本信息 | HIGH | 确定性正则 | 实体抽取（项目名/编号/日期）比对 | 第三批次 |
| 2 | `BID_LETTER_FORMAT` | 投标函格式规范 | 基本信息 | HIGH | 两阶段双轨 | **已闭环**（文书契约 + 称谓/落款事实核查 + 强凭证直通，PR #2555） | **已完成** |
| 3 | `AUTHORIZATION_LETTER` | 授权委托书 | 基本信息 | CRITICAL | 两阶段双轨 | **已闭环**（文书契约 + 授权期限/公章事实要素，PR #2556） | **已完成** |
| 4 | `BUSINESS_LICENSE` | 营业执照 | 基本信息 | CRITICAL | 两阶段双轨 | **已闭环**（证照契约 + 统一社会信用代码/印章要素，PR #2557） | **已完成** |
| 5 | `QUALIFICATION_MATCH` | 资质匹配 | 基本信息 | CRITICAL | LLM 语义 | 资质库知识比对深化，保持 LLM | 第二批次 |
| 6 | `PRICE_CONSISTENCY` | 报价一致性 | 基本信息 | CRITICAL | 两阶段双轨 | **已闭环**（最高限价硬核验 + 表函总价一致性比对，PR #2563） | **已完成** |
| 7 | `CURRENCY_UNIT` | 货币单位 | 基本信息 | MEDIUM | 确定性正则 | 物理层正则，零 Token 消耗保持 | 第四批次（保留） |
| 8 | `SCHEDULE_RESPONSE` | 工期响应 | 基本信息 | HIGH | 确定性正则 | 实体抽取（天数/日历天）+ 逻辑计算 | 第三批次 |
| 9 | `SIGNATURE_SEAL` | 签章完整性 | 基本信息 | CRITICAL | 确定性正则 | 印章视觉/文本特征多模态核验 | 第三批次 |
| 10 | `CONTACT_INFO` | 联系信息 | 基本信息 | MEDIUM | 确定性正则 | 联系人/电话正则抽取（确定性） | 第三批次 |
| 11 | `QUALIFICATION_VETO` | 资格条件一票否决 | 分项合规 | CRITICAL | LLM 语义 | 硬资格一票否决，深化 Fact Schema | 第二批次 |
| 12 | `FRONT_TABLE_RESPONSE`| 前附表关键条款填写完整性 | 分项合规 | CRITICAL | LLM 语义 | 前附表条款级对齐 Schema 升级 | 第二批次 |
| 13 | `RESPONSIVENESS_REVIEW`| 响应性评审条款 | 分项合规 | CRITICAL | LLM 语义 | 偏离表深度比对，保持 LLM 语义 | 第二批次 |
| 14 | `QUALIFICATION_REVIEW` | 资格性评审条款响应 | 分项合规 | HIGH | 两阶段双轨 | **部分已闭环**（财务三表与审计报告核验已闭环，PR #2560） | 第二批次 |
| 15 | `SCORING_RESPONSE` | 评分项响应（按满分标准） | 分项合规 | HIGH | LLM 语义 | 评分维度与知识库联动打分 | 第二批次 |
| 16 | `DOCUMENT_COMPLETENESS`| 投标文件组成完整性 | 分项合规 | HIGH | 确定性正则 | 目录章节与招标文件组成清单比对 | 第三批次 |
| 17 | `TEMPLATE_FORMAT` | 格式模板符合性 | 分项合规 | MEDIUM | 确定性正则 | 排版字号物理指标，零 Token 保持 | 第四批次（保留） |
| 18 | `VETO_KEYWORD` | 投标否决项关键字扫描 | 分项合规 | CRITICAL | 混合通道 | 废标词上下文语义否定消除（反向词）| 第三批次 |
| 19 | `CLARIFICATION_RESPONSE`| 澄清文件响应 | 分项合规 | HIGH | LLM 语义 | 澄清问答与标书修改点比对 | 第二批次 |
| 20 | `COMMITMENT_SEAL` | 承诺类文件盖章检查 | 分项合规 | HIGH | 确定性正则 | 承诺函段落印章标记 | 第四批次（保留） |
| 21 | `ID_AUTH_LETTER` | 委托人身份证原件 + 授权委托书 | 附件清单 | HIGH | 两阶段双轨 | **已闭环**（证件契约 + 身份证号/社保特征要素，PR #2558） | **已完成** |
| 22 | `BID_OPENING_TABLE` | 开标一览表/报价表 | 附件清单 | CRITICAL | 两阶段双轨 | **已闭环**（表格契约 + 表格结构与金额提取，PR #2563） | **已完成** |
| 23 | `DEPOSIT_RECEIPT` | 投标保证金回单 | 附件清单 | CRITICAL | 两阶段双轨 | **已闭环**（标杆范例，PR #2553） | **已完成** |
| 24 | `CA_CERTIFICATE` | 电子标 CA 证书 | 附件清单 | HIGH | 两阶段双轨 | **已闭环**（电子标契约 + CA机构与序列号/免收直通） | **已完成** |
| 25 | `DEPOSIT_REFUND` | 保证金退款信息表 / 收据 | 附件清单 | MEDIUM | 确定性联动 | **已闭环**（与第 23 项联动免除） | **已完成** |

---

## 三、 分阶段演进实施路线

### 阶段 0：标杆建立（已完成，PR #2553）
- **覆盖项**：`DEPOSIT_RECEIPT` (第 23 项)、`DEPOSIT_REFUND` (第 25 项)；
- **交付成果**：
  - `TenderRequirements.DepositRequirement` 结构化契约；
  - `DeterministicDepositExtractor` 确定性规则与表格双轨提取；
  - `fuseWithDeterministic` 原文硬事实优先融合算法；
  - `SubItemAndAttachmentRules.isActualDepositVoucherPage` 强事实凭证要素校验；
  - 强凭证确定性直通，可疑/未命中调用 `LlmAdjudicator` 深度裁决；
  - 138 项自动化测试矩阵覆盖，0 失败。

### 阶段 1：攻坚 6 项文书附件凭证类（100% 全面大收官！）
- **攻坚清单**：
  1. `BID_LETTER_FORMAT`（投标函格式规范）—— **已完成**（PR #2555）
  2. `AUTHORIZATION_LETTER`（授权委托书）—— **已完成**（PR #2556）
  3. `BUSINESS_LICENSE`（营业执照）—— **已完成**（PR #2557）
  4. `ID_AUTH_LETTER`（身份证与社保证明）—— **已完成**（PR #2558）
  5. `BID_OPENING_TABLE`（开标一览表/报价表）—— **已完成**（PR #2563）
  6. `CA_CERTIFICATE`（电子标 CA 证书）—— **已完成**（当前 PR）
- **核心成果**：
  - 阶段 1 涉及的 6 项文书附件凭证全部完成专属顶层不可变 Record 契约与确定性双轨提取器；
  - 物理剔除目录点线页（`......`）及未填占位模板（`【此处粘贴...】`、下划线留白），彻底杜绝目录假通过；
  - 规范标书真实凭证（CFCA/沃通/契约锁/北京CA、序列号、算法、有效期等）强凭证直通 PASS（0 次 LLM 耗时抗抖动）；
  - 纸质标/线下标/免收 CA 场景确定性直出 PASS，存疑或未附带凭证调用大模型深度裁决（准则 11）；
  - 单文件 <= 300 行预算与 FP-Java 契约 100% 达标。

### 阶段 2：深化 7 项深度语义项（预计 4~5 人天）
- **覆盖清单**：`QUALIFICATION_MATCH`、`QUALIFICATION_VETO`、`FRONT_TABLE_RESPONSE`、`RESPONSIVENESS_REVIEW`、`QUALIFICATION_REVIEW`、`SCORING_RESPONSE`、`CLARIFICATION_RESPONSE`；
- **核心模式**：在 `TenderRequirements` 补充条款级 Fact Schema，将大模型自然语言 prompt 转化为结构化键值比对。

### 阶段 3：重构 7 项逻辑一致性与计算核验类（预计 3~4 人天）
- **覆盖清单**：`COVER_INFO`、`PRICE_CONSISTENCY`、`SCHEDULE_RESPONSE`、`SIGNATURE_SEAL`、`CONTACT_INFO`、`DOCUMENT_COMPLETENESS`、`VETO_KEYWORD`；
- **核心模式**：实体大模型抽取 + 确定性算法交叉验证。

### 阶段 4：物理层排版项保持确定性（零成本）
- **覆盖清单**：`CURRENCY_UNIT`、`TEMPLATE_FORMAT`、`COMMITMENT_SEAL`；
- **核心模式**：纯核心毫秒级快速规则，坚决不引入大模型，防过度设计。

---

## 四、 单项标准化改造 Check-List（实施准则）

后续每个 Agent 承担具体单项改造任务时，必须严格执行以下 6 步门禁：
1. [ ] **招标端契约**：在 `TenderRequirements` 增加该项专属 record 契约；
2. [ ] **双轨融合提取**：建立 `Deterministic*Extractor`，实现 `fuseWithDeterministic`（原文确定性事实优先）；
3. [ ] **标书端防假**：建立 `isActual*Page`，必须同时满足「强凭证/格式标志 + 真实数据事实要素」，拦截目录点线与占位符；
4. [ ] **引擎接线与直通**：在 `BidDocumentQualityEngine` 落实「确定性强凭证直通，存疑/未命中调用 LLM 语义裁决」；
5. [ ] **测试矩阵闭环**：
   - 真实招标文件回归用例；
   - 目录点线反例（绝不误判 PASS）；
   - 正文包含强关键词但无真实要素反例（真正压满校验函数，转 MANUAL_REVIEW）；
   - 强凭证直通单测（断言不触发 LLM 调用，次数恒为 0）；
   - 标书未附带凭证且非免收时 LLM 裁决 FAIL 单测。
6. [ ] **知识库与 Wiki 沉淀**：更新 `docs/lessons/lessons-learned.md` 与 `.wiki/pages/`。
