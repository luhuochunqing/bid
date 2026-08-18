# Research & Gap Analysis: AI 智能推荐案例（Feature 046）

## 1. 现有资产与现状调研

### 1.1 评分项资产（ScoreItem）
- **实体**：`com.xiyu.bid.scoreparse.entity.ScoreItem`
  - 核心字段：`id`, `projectId`, `code`, `dim`, `detail`, `itemIndex`, `targetChapter` 等。
- **接口**：`GET /api/projects/{projectId}/score-parse/items`
  - 已提供当前项目全量评分项的有序列表。

### 1.2 案例库现状（KnowledgeCase & BidCaseSlice）
- **数据表**：
  - `knowledge_case`（由 V999, V1039, V1051, V1192 维护）：包含 `scoring_point_title`, `requirement_raw`, `response_text`, `reuse_count`, `bid_result`, `scoring_category`, `customer_type`, `project_type`, `is_pinned` 等字段。
  - `bid_case_slice`（由 V1083 维护）：包含正文切片与语义向量索引。
- **服务层**：
  - `KnowledgeCaseRecommendAppService`: 已有基于标题 Jaccard + 类别 + 客户类型的 `KnowledgeCaseMatchPolicy`。
  - `BidCaseSliceRecommendAppService`: 已有基于向量余弦相似度召回 + Jaccard 精排的 `BidCaseSliceMatchPolicy`。

### 1.3 存在的核心差距 (Gaps)
1. **评分项与推荐算法断层**：原推荐算法大多只看 `scoringItemTitle`（标题匹配），未深度消费 `ScoreItem.detail`（评分项具体详细要求）。在投标场景中，标题往往只有几个字（如“项目管理方案”），而真正决定匹配度的技术关键点全在 `detail` 描述中。
2. **PRD 算法粗粒度缺陷**：PRD 原型中提出的“项目级向量整体匹配”会导致跨评分项语义混淆。必须升级为**“基于评分项 detail / 关键词 的混合语义精排策略”**。
3. **接口契约未统一**：PRD 要求标准的 `/api/projects/{projectId}/cases/recommend` 路由和 `/api/cases/{caseId}/reuse` 记录，目前前端抽屉与后端接口路径需要严格对齐。
4. **编辑器复用闭环（Phase 3）**：文档章节树（`targetChapter` / `sectionTree`）定位注入与追加/覆盖/撤销机制需要结构化协议支撑。

---

## 2. 架构设计决策 (ADR)

### ADR-01: 推荐引擎输入源升级
- **决策**：推荐检索的 Query 文本构建规则为：
  `QueryText = (ScoreItem.dim + " " + ScoreItem.detail + " " + ScoreItem.code + " " + optionalKeyword).trim()`
- **理由**：当用户传入 `scoreItemId` 时，系统自动加载该评分项的完整条款要求（`detail`），结合关键词形成强上下文，直接与候选案例的 `requirement_raw` 与 `response_text` 计算语义匹配度，大幅提升召回精确率。

### ADR-02: 纯核心与副作用分层 (FP-Java Architecture)
- **纯核心层 (`domain/policy`)**：
  - `ScoreItemCaseMatchPolicy`：不依赖 Spring / JPA / 网络，纯输入 `ScoreItemContext` + `List<KnowledgeCaseCandidate>`，计算 0~100 分匹配度、高亮匹配片段（`matchedKeywords` / `highlights`）、综合评级（`优质` / `良好` / `一般`）。
- **编排层 (`application/service`)**：
  - `ScoreItemCaseRecommendAppService`：加载 `ScoreItem` 与 `KnowledgeCase` 数据，调用纯核心 Policy 计算，执行权限校验与审计。

### ADR-03: 复用计数与日志审计
- **决策**：复用接口 `POST /api/cases/{caseId}/reuse` 支持传入 `projectId`、`scoreItemId`、`targetChapter` 等上下文信息。
- **机制**：在数据库执行原子更新 `UPDATE knowledge_case SET reuse_count = reuse_count + 1 WHERE id = ?`，并写入 `case_reference_record` 审计表。
