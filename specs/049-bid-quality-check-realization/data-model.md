# Phase 1 Data Model: AI 标书质量核查实质化（049）

**Date**: 2026-08-20 | **Plan**: [plan.md](./plan.md)

> 本功能以**既有模型演进**为主，新增实体仅纯核心 record（无新表、预计无 Flyway 迁移）。持久化沿用 `ComplianceCheckResult` 实体与 JSON 序列化列。

---

## 1. 既有模型（不改结构，作为输入契约）

### 1.1 DocumentContent（compliance/domain）

| 字段 | 类型 | 说明 |
|---|---|---|
| fileName | String | 文件名 |
| fileHash | String | 全文 SHA-256（版本缓存键） |
| fullText | String | 全文 |
| pages | List\<PageContent\> | 物理分页（Sidecar 按 `\f` 切分） |

`PageContent(int pageNumber, String text, List<String> headings)` —— 第一步~第四步的真实页码唯一来源。

### 1.2 ComplianceCheckResult（entity，持久化）

任务与报告持久化载体（taskId / projectId / TaskStatus / progress / 报告 JSON）。存储口径待产品拍板（spec §5.2），本轮不改表结构。

---

## 2. 演进模型

### 2.1 QualityEvidence（record 扩展，向后兼容）

现状：`(sourceType, sourceTitle, pageNumber, quote, sourceRecordId, confidence)`。

本轮语义扩展（字段不增删，语义收口 + 可选新增 `matched`/`knowledgeSource` 承载第三/四步数据）：

| 字段 | 类型 | 本轮约束 |
|---|---|---|
| sourceType | String | 收口为枚举值：`BID` / `TENDER` / `KNOWLEDGE_BASE` |
| sourceTitle | String | 文档名或知识库记录标题 |
| pageNumber | Integer | **真实页码或 null；禁止填 1 充数**（BID/TENDER 类） |
| quote | String | **真实原文摘录或知识库记录内容；禁止固定文案** |
| sourceRecordId | String | 知识库 recordId（KNOWLEDGE_BASE 类必填） |
| confidence | Double | 0~1 |
| knowledgeSource | String（新增，可空） | `qualification` / `brand-authorization` / `personnel` / `performance` |
| matched | Boolean（新增，可空） | 知识库匹配结论（KNOWLEDGE_BASE 类必填） |

**验证规则**：compact constructor 中 knowledgeSource 仅允许上述四值或 null；`sourceType=KNOWLEDGE_BASE` 时 sourceRecordId 非空。
**序列化**：新增字段可空 → 旧 JSON 反序列化不受影响（向后兼容）。

### 2.2 QualityCheckItemResult（不改结构）

`(itemCode, category, title, status, severity, order, conclusion, suggestion, evidences)` 保持不变。MANUAL 态的 reasonCode 写入 `conclusion` 或 `suggestion` 文案前缀约定 `[reasonCode]`（由纯核心统一格式化，避免散落拼接）。

---

## 3. 新增纯核心模型（无持久化）

### 3.1 TenderRequirements（第二步产物，record）

```text
TenderRequirements(
    List<ScoringItem> scoringItems,        # 评分项
    List<String> mandatoryQualifications,  # 硬性要求/资格条件
    List<VetoClause> vetoClauses,          # 否决条款
    List<String> compositionRequirements,  # 投标文件组成要求
    List<String> formatRequirements        # 格式要求
)
ScoringItem(String name, Double maxScore, String criteria)
VetoClause(String clause, List<String> keywords)
```

- **来源**: LLM 结构化提取（契约见 `contracts/tender-requirements-extraction.md`）
- **验证规则**: 各列表允许为空但对象非 null；`maxScore ≥ 0`
- **消费方**: `BidDocumentQualityEngine` LLM lane、替代 `extractRequiredCerts` 硬编码
- **状态转换**: 提取失败 → `TenderRequirements.missing()`（哨兵值，比对项 MANUAL + REFERENCE_MISSING）

### 3.2 KnowledgeMatch（第三步产物，record）

```text
KnowledgeMatch(
    String knowledgeSource,   # qualification / brand-authorization / personnel / performance
    String recordId,          # 真实记录 ID
    String content,           # 记录摘要（如 "ISO27001认证（有效期至 2027-03）"）
    boolean matched           # 匹配结论
)
```

- **来源**: 四库 Repository 真实查询（数据源映射见 research.md R4）
- **验证规则**: recordId 非空；content 必须来自 DB 记录字段拼接，禁止常量文案
- **转换**: `KnowledgeMatch → QualityEvidence`（knowledgeSource/matched 映射新增字段）

### 3.3 EvidenceHit（第四步产物，record）

```text
EvidenceHit(Integer pageNumber, String quote)
```

- **来源**: `EvidenceLocator.locate(pages, keywords)` 纯函数
- **验证规则**: 未命中时返回 `Optional.empty()`（调用方映射 page=null），**禁止返回 pageNumber=1 兜底**
- **quote 生成**: 命中位置前后截取上下文（长度上限约 120 字符），必须是 `PageContent.text` 的子串

---

## 4. 状态转换

### 4.1 任务状态（沿用 ComplianceCheckResult.TaskStatus）

```text
PENDING → PARSING(20%) → CHECKING(60%) → COMPLETED(100%)
                ↘ FAILED（任意阶段异常；trigger 前置校验失败不建任务，直接 4xx，无状态记录）
```

新增语义：LLM 提取/裁决失败**不置任务 FAILED**，降级为单项 MANUAL（任务仍 COMPLETED）。

### 4.2 单项状态（沿用 QualityCheckStatus）

```text
PASS / FAIL / MANUAL_REVIEW
MANUAL_REVIEW 必附 reasonCode ∈ {EVIDENCE_INSUFFICIENT, AMBIGUOUS, LOW_CONFIDENCE,
                                    REFERENCE_MISSING, MANUAL_REVIEW_REQUIRED}
```

### 4.3 触发决策流（第一步新增前置校验）

```text
trigger(projectId, force)
  ├─ 无 BID 类 ProjectDocument        → 4xx "无法执行核查，请先上传投标文件"（不建任务）
  ├─ 有文档但缓存命中且 force=false    → 返回既有 taskId/报告
  └─ 有文档且需执行                    → 创建任务，异步执行
```
