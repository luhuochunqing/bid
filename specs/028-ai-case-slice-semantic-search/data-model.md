# Data Model: AI 案例切片语义检索

## Entity: BidCaseSlice

**Table**: `bid_case_slice`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGINT | NO | Primary key, auto-increment |
| `project_dir` | VARCHAR(200) | NO | 项目目录名（如 `2026.01.05-中广核办公`） |
| `project_idx` | INT | NO | v9 脚本中的项目序号，用于追溯 |
| `docx_file` | VARCHAR(500) | NO | docx 文件相对路径 |
| `docx_label` | VARCHAR(20) | NO | 文件类别：`商务` / `技术` / `报价` / `其他` |
| `section_idx` | INT | NO | v9 脚本中的全局章节序号 |
| `level` | INT | NO | Heading 层级（1, 2, 3, 4...） |
| `title` | VARCHAR(500) | NO | 章节标题 |
| `text_preview` | TEXT | NO | 正文预览（前 300 字） |
| `text_length` | INT | NO | 正文长度（字符数） |
| `para_count` | INT | NO | 章节下段落数 |
| `embedding` | MEDIUMBLOB | YES | float[] 序列化，1024 维 |
| `embedding_model` | VARCHAR(100) | YES | 使用的 embedding 模型名 |
| `embedding_dim` | INT | YES | 向量维度（默认 1024） |
| `embedding_at` | TIMESTAMP | YES | 向量化完成时间 |
| `created_at` | TIMESTAMP | NO | 记录创建时间 |

**Indexes**:
- `idx_bid_case_slice_project` on `project_dir`
- `idx_bid_case_slice_label` on `docx_label`

**Relationships**:
- 无强外键，独立素材库表。
- 逻辑上通过 `project_dir` 与服务器上的历史项目目录关联。

---

## Entity: BidCaseSliceRecommendation (DTO)

**Not persisted**. 返回给前端的推荐结果。

| Field | Type | Description |
|---|---|---|
| `sliceId` | Long | 切片 ID |
| `projectDir` | String | 来源项目目录 |
| `docxFile` | String | 来源 docx 文件路径 |
| `docxLabel` | String | 文件类别 |
| `sectionTitle` | String | 章节标题 |
| `textPreview` | String | 正文预览 |
| `textLength` | int | 正文长度 |
| `paraCount` | int | 段落数 |
| `cosineScore` | double | 余弦相似度（0~1） |
| `finalScore` | int | 精排后总分（0~100） |
| `matchReason` | String | 匹配理由（如"语义相似、标题匹配、技术文件"） |

---

## Entity: BidCaseSliceMatchCriteria (Input)

**Not persisted**. 精排策略的输入。

| Field | Type | Description |
|---|---|---|
| `queryText` | String | 查询文本（评分项标题 + 规则） |
| `queryVector` | float[] | 查询文本的 embedding |
| `preferredLabel` | String | 期望的文件类别（如`技术`），可空 |
| `queryTokens` | Set<String> | 查询文本的分词集合，用于 Jaccard |

---

## Existing Entity Used: ProjectScoreDraft

**Table**: `project_score_drafts`

本功能消费以下字段作为查询输入：
- `scoreItemTitle` → 评分项标题
- `scoreRuleText` → 评分规则原文
- `category` → 评分项类别（可用于辅助文件类别推断）

不修改 `project_score_drafts` 表结构。
