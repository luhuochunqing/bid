---
title: AI 智能案例推荐数据源修复（score_item 新契约切换 + casework 裸响应陷阱）
space: engineering
category: guide
tags: [案例推荐, score_item, spec041, 裸响应, 二次解包, caseRecommendationApi]
created: 2026-08-21
updated: 2026-11-17
health_checked: 2026-11-17
sources:
  - src/api/modules/caseRecommendation.js
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - backend/src/main/java/com/xiyu/bid/casework/controller/ScoreItemCaseRecommendController.java
backlinks:
  - _index
  - engineering-discipline
---
# AI 智能案例推荐数据源修复（score_item 新契约 + 裸响应陷阱）

> **一句话**：项目详情页「AI 智能案例推荐」抽屉不显示评分项，根因是数据源错配（读旧表）
> + API 层二次解包（裸响应再取 `.data`）。两者都已修复，本页记录契约事实防止回归。

## 根因 1：数据源错配

spec-041 的 AI 评分标准解析链路写入 **`score_item` 新表**，但项目详情抽屉原来读
**`project_score_drafts` 旧表**（spec-041 后不再写入，恒为空）。

修复：`AiRecommendDrawer.vue` 切到 `caseRecommendationApi`（PRD 4.2 契约），后端零改动。

契约字段差异（旧 → 新 ScoreItemDTO）：

| 旧（draft） | 新（score_item） |
| --- | --- |
| `scoreItemTitle` / `category` | `id` / `dim` / `detail` / `code` |
| 推荐响应为裸数组 | `{ total, items }` 分页对象 |
| `caseId` | `id` |
| `score` / `scoreLabel` | `matchScore` / `matchLabel` |
| `matchReason` 字符串 | `matchReasons` 数组 |

组件内 `normalizeRecommendCase` 对新旧字段名都做了 fallback，容忍后端演进。

## 根因 2：casework 后端全部是裸响应（重要陷阱）

`client.js` 响应拦截器已统一返回 `response.data`（blob 除外），**但 casework 模块的
Controller 直接返回 `ResponseEntity`，不走 `{success, data, message}` 统一信封**。
API 模块层若再 `.then(res => res.data)` 会得到 `undefined`（双重解包）。

受影响的 4 个端点（全部裸响应）：

| 端点 | Controller | 返回 |
| --- | --- | --- |
| `GET /api/projects/{id}/score-items` | ScoreItemCaseRecommendController | 裸 `List<ScoreItemDTO>` |
| `POST /api/projects/{id}/cases/recommend` | ScoreItemCaseRecommendController | 裸 `{total, items}` |
| `GET /api/cases/{id}` | KnowledgeCaseController | 裸 `KnowledgeCase` |
| `POST /api/cases/{id}/reuse` | KnowledgeCaseController | 裸 `Map` |

**规则**：`caseRecommendationApi` 的 4 个方法一律裸透传，禁止再加 `.data` 解包。
契约回归锁在 `src/api/modules/caseRecommendation.spec.js`。

## PRD 4.2 复用定位弹窗默认选中（Warning #1 补充）

PRD 1.1/4.2：复用定位弹窗章节树**默认选中当前评分项对应章节（可修改）**。实现链路：

1. 抽屉 `currentDefaultChapter`：优先 `item.targetChapter`（后端章节关联落地后直接生效）
   → 无则按 `dim` 类别关键词回退到内置章节树（商务/报价/资格→第1章，技术/方案/服务→第2章）→ 空（用户手选）
2. `AICaseReuseSectionModal` 打开时 `applyDefaultSelection`：用 `findSectionPathByChapter`
   （`sectionTreeMatch.js`，BFS：完整路径相等 → label 相等 → 最长包含匹配）在章节树里找路径，
   命中则级联预选 `selectedSection`，未命中回退手输框预填
3. 每次打开重置选择，避免上次残留；弹窗已打开时切换评分项会同步刷新默认选中

限制：真实标书章节树 API 属 spec 046 Phase 3，当前弹窗用内置演示树；
后端 `score_item` 无 targetChapter 字段，映射落地前靠 dim 回退。

## PRD 4.2 案例匹配度计算规则与前端浮层可视化（Popover / Tooltip）

### 1. 匹配度计算机制（满分 100 分，对齐 ScoreItemCaseMatchPolicy）
项目详情页编制抽屉推荐接口（`POST /api/projects/{projectId}/cases/recommend`）调用核心策略 `ScoreItemCaseMatchPolicy.java`，综合 8 维度加权评估（封顶 100 分）：
1. **专属客户/项目实体加权（最高 25 分）**：自动提取项目名称与客户主体专有名词（如“三峡”），候选案例命中专属主体直接获断层加权（单实体 15 分，复合 25 分）。
2. **评分条款深度契合（最高 30 分）**：基于滑动 n-gram 分词，过滤招投标领域通用停用词后评估实质特征词重叠度。
3. **评分项标题/正文跨层相似度（最高 20 分）**：支持纯净名称与案例标题、需求原文及应答正文跨层穿透比对。
4. **评分项类别一致性（+15 分）**：技术/商务/服务/资格等大类与业务关键词语义推断一致加 15 分。
5. **项目类型一致性（+10 分）**：工业品/办公/综合等一致加 10 分。
6. **客户类型一致性（+5 分）**：央企/地方国企/政府/民企等一致加 5 分。
7. **搜索关键词命中（+5 分）**：输入额外关键词在候选案例中命中加 5 分。
8. **历史中标加成（+5 分）**：中标案例额外加 5 分。

### 2. 等级划分与标签
- $\ge 80\%$：**优质**（Tag: `danger`）
- $\ge 60\%$：**良好**（Tag: `warning`）
- $< 60\%$：**一般**（Tag: `info`）

### 3. 前端组件与数据透传链路
- **全局规则说明 Tooltip**：`AiRecommendDrawer.vue` 标题栏 `(?) 评分依据说明`，悬浮展开 8 维度规则明细。
- **单卡片命中分析 Popover**：`CaseCard.vue` 左上角匹配度徽标（`el-popover`），悬浮展开综合评级、命中维度列表（`✓ 评分项条款高度契合` 等）与高亮关键词药丸（`kw-pill`）。
- **详情弹窗匹配度 Banner**：`CaseDetailDialog.vue` 弹窗顶部浅蓝色背景条，展示匹配得分与依据明细。
- **纯函数解析层**：`caseLabels.js` 导出 `getMatchLabel`、`getMatchTagType`、`parseStringOrArray`，统一管理阈值与数组/逗号/顿号容错解析。

## 消费方地图（改这个 API 前必读）

- `AiRecommendDrawer.vue`（项目详情抽屉）— 4 个方法全用
- `AICaseReuseSectionModal.vue`（Bidding 侧复用弹窗）— 用 `reuseCase`，返回值经
  `reuse-success` 事件透传，消费方不读 `result` 字段
- `AICaseRecommendDrawer.vue`（Bidding 版抽屉）— 自带双形态兼容写法，
  **当前未被任何页面挂载**（仅 spec 引用），改动不会在生产暴露

## 闲置端点候选池恒空修正（2026-09-01）

- `KnowledgeCaseRecommendAppService`（`GET /api/cases/recommend`、`GET /api/cases/recommend/project`）
  前端无调用方，但存在跨枚举错配：把 `projects.customer_type`（民企/央企…）填进
  `criteria.projectType`，再用于过滤 `knowledge_case.project_type`（综合/工业品/办公…），
  两值域无交集导致候选池恒为空
- 修复：`criteria.projectType` 一律置空（projects 表无项目类型真值字段），
  `fetchCandidates` 移除跨枚举谓词，仅按案例自身字段过滤（与主链路 `findAllActive()` 对齐）
- 回归测试：`KnowledgeCaseRecommendAppServiceTest.recommendForProject_ShouldNotFilterCandidatesByMismatchedProjectType`
- 主链路 `POST /api/projects/{id}/cases/recommend` 不受此缺陷影响，历史切片案例实测可命中（126/160）
- 详见 docs/lessons/lessons-learned.md #195

## 评分项编号前缀剥离与正文穿透匹配升级（2026-09-01）

### 1. 根因
招标文件解析出来的评分项标题通常带有层级编号（如 `2.2.4(3) 商城系统对接能力`、`第一章 (1) 财务审计报告`），
而案例库中的候选案例标题多为通用分类（如 `技术方案`、`商务要求`），其具体的业务应答正文（`responseText`）和要求（`requirementRaw`）
才包含"商城系统对接能力"等具体关键词。由于未剥离编号且仅比对标题 Jaccard 相似度，导致真实相关案例得分低于推荐阈值被过滤。

### 2. 核心改进策略（FP-Java 纯函数）
1. **多轮前缀清洗器（`TextSimilarityPolicy.cleanTitle`）**：
   支持多层级阿拉伯数字（`2.2.4(3)`）、中文章节（`第三章 3.2 (1)`）、带圈数字（`①②③`）、方括号、罗马数字等 11 类前缀递归剥离，提取纯净业务名称。
2. **正文跨层穿透匹配**：
   评分项纯净业务词不仅比对候选案例标题，同时穿透候选案例的 `requirementRaw` 和 `responseText`，命中即赋予满额 20 分标题/正文相似度。
3. **5 大招投标板块推断**：
   构建涵盖技术/服务/商务/资质/人员的领域推断词典，当评分项或案例类别缺失或泛化时，智能推断归属大类赋予 15 分类别分。
4. **条款重叠度（Detail Overlap）弹性计算**：
   引入长文本词频击中底线保护，防止长条款分母过大稀释相似度得分。

## 标点分层实体抽取与大类冲突一票否决重构（2026-09-04）

### 1. 业务痛点与第一性原理
- **切词碎片污染**：过去直接将条款拆解的 2-gram/3-gram 字符碎片（如针对“朱双双的标识不可编辑”切出“双双/双的/的标/不可/可编”）填入 `matchedKeywords`，挤占了有价值的业务实体展示槽位。
- **大类穿透缺陷**：评分项与案例的类别对齐过去仅作为 15 分软加分，导致纯财务审计案例凭借客户实体加分（+25）与中标加成，轻松突破 50 分被错误推荐到技术方案评分项下方，违背了“技术方案与商务方案精准匹配”的第一性原理。

### 2. 解决方案与核心策略（纯 FP-Java）
1. **标点分层实体抽取器（`PunctuationEntityExtractor`）**：
   - 分层提取：句级标点（分号/换行）切分子句，子句内按顿号（`、`）提取并列业务实体（商品、订单、售后、对账），双引号提取专有名词（“三峡e购”）；
   - 词界保护：利用环视正则保护“及时响应”、“涉及范围”、“参与实施”，避免单字连词乱切；
   - 质量守卫（`isQualityEntity`）：拦截内部含标点碎片、助词首尾字符及停用词，放行“在线支付”、“华为云”、“以太网”、“等保三级”等合法实体；
   - 缩写保护：修复 `cleanTitle`，要求编号紧跟分隔符，防止 `ISO9001`、`CMMI`、`B2B`、`ERP`、`AI` 后接空格时被误当做编号剥离。
2. **大类冲突一票否决门禁（`CaseCategoryMatchPolicy`）**：
   - 宏观大类模型：划分 `TECHNICAL`（技术方案）、`COMMERCIAL`（商务财务）、`PERFORMANCE`（同类业绩）、`QUALIFICATION`（资质认证）、`PERSONNEL`（人员团队）；
   - 强互斥阻断：仅在纯技术（`TECHNICAL`）与纯财务（`COMMERCIAL`）冲突时一票否决得 0 分，杜绝项目加分穿透；放行同类业绩、资质和人员与技术方案的跨类兼容。
3. **展示优先级重排**：
   - 优先级 1：项目专属实体（三峡）；
   - 优先级 2：评分项清洗后的纯净标题（商城系统对接能力）；
   - 优先级 3：顿号/分号提取的高纯度并列实体；
   - 优先级 4：搜索词。
   - 封顶展示 8 个，彻底消除 n-gram 切词碎片。

## 关联

- [[score-parse-service]] — spec-041 解析链路（score_item 数据的生产方）
- [[frontend-pitfalls]] — 前端陷阱集
- 提交：`agent/gemini/ai-recommend-tooltip-popover`（匹配度浮层与规则说明可视化）
- 提交：`agent/gemini/fix-case-mismatch`（评分项编号前缀清洗与正文穿透匹配）
- 提交：`agent/gemini/ai-case-recommend-punctuation-refactor`（标点分层抽取与大类一票否决重构）
- 提交：`agent/gemini/fix-entity-tokens-ngram-leak`（拔除 2-gram 滑窗遗留与项目专名层级提炼）

## 拔除 2-gram 机械滑窗与项目专名层级提炼（2026-09-04 二次复发根治）

### 1. 真实日志排查与根因
测试环境部署后针对项目 1358（`朱双双的标讯不可编辑`）回放：
- 推荐无缓存：`recommend` 接口为全内存无状态动态打分（单次 80~170ms），无缓存滞留；
- 真实零号病人：`TextSimilarityPolicy.extractEntityTokens` 对 `>= 4` 字符子句强制执行 `substring(i, i + 2)` 机械 2-gram 滑窗，导致项目名称被切碎成“朱双、双双、双的、的标、标讯、讯不、不可、编辑”；
- 碎片外溢扩散：`calculateEntityScore` 将切片识别为专属实体，`assemblePrioritizedKeywords` 赋予最高展示优先级，且 `reasons` 显示“命中项目专属实体(的标、不可)”。

### 2. 根治策略
1. **物理删除 2-gram 滑窗**：彻底废弃字符滑动切片，严禁产生跨词碎片；
2. **层级前缀剥离（`ORG_GEO_PREFIX_PATTERN`）**：对“中国长江三峡”按地域/央企前缀递归提取“长江三峡”、“三峡”，保留专名主体而不产生“国长”、“江三”等碎词；
3. **噪音与停用词强化**：`ORG_DELIMITER_PATTERN` 纳入 `不可编辑|可编辑|标讯|标包|标段|测试|项目`，`PunctuationEntityExtractor.cleanEntityToken` 剥离首尾助词（“朱双双的” → “朱双双”）；
4. **双重防守门禁**：`calculateEntityScore` 强制 `isQualityEntity` 检验；`assemblePrioritizedKeywords` 限制实体长度 `<= 10`。

---

## Feature 061: 基于真实数据底座的历史案例精准推荐（2026-09-05）

### 1. 真实数据底座沉淀
- **数据源与特征抽取脚本**：新增 `scripts/extract-historical-case-features.py`，从服务器 70 个历史项目 8144+ 切片中提取企业专名、10 大招投标核心业务子主题（Sub-Topic，如 `SLA_SERVICE`、`EMERGENCY_PLAN`、`INTEGRATION`）及质量评分（`quality_score`），过滤发票清单、盖章声明等样板段落；
- **数据库扩展**：Flyway `V1211__add_sub_topic_to_knowledge_case.sql` 与 `U1211` 回滚脚本在 `knowledge_case` 表增加 `sub_topic` 与 `quality_score` 字段及索引；
- **数据导入同步**：`scripts/import-case-slices-to-knowledge-case.py` 拓展新字段批量导入。

### 2. FP-Java 算分加权第一性原理
- **业务子主题精准加权**：新增 `CaseSubTopicPolicy` 纯核心推断，结构化子主题完全对齐 +25 分，正文补偿 +15 分；
- **大类冲突一票否决**：`ScoreItemCaseMatchPolicy` 维持技术项遇纯财务案例强制 0 分；
- **专属客户断层优先**：重点客户（三峡、建行）命中企业专名最高加 25 分；
- **算分归一**：满分 100 分，采用累加后封顶在 100 分的机制，既保障专属客户与子主题的断层领先，又保证通用高质量案例正常评定为“优质”。

---

## Feature 062: 评分模型 100 分自洽闭环与解释层算术对齐（2026-09-05）

### 1. 解释层与核心层算术硬伤定位
- **问题表象**：前端「评分依据说明」浮窗显示：
  专属客户/项目实体（最高 25 分）+ 业务子主题（最高 25 分）+ 评分条款（最高 30 分）+ 评分项标题（最高 20 分）+ 类别一致（+10 分）+ 项目/客户类型（+10/+5 分）+ 加成（各 +5 分）= 140+ 分，但标题写着「满分 100 分」，导致用户和评委一眼看出算术冲突；
- **根因分析**：后端虽通过 `Math.min(score, 100)` 兜底，但对外展示未区分「基础 100 分维度」与「额外加分项（Bonus）」，造成解释层不自洽。

### 2. 100 分制自洽模型架构
- **基础 5 大维度严格闭环（合计 100 分）**：
  1. 业务子主题深度对齐：最高 25 分（SLA保障、应急预案、系统对接等场景对齐）
  2. 评分条款深度契合：最高 25 分（条款与响应方案关键词重合）
  3. 专属客户/项目实体：最高 20 分（命中指定客户、项目或专名标杆）
  4. 评分项标题核心匹配：最高 20 分（评分点标题及语义核心契合）
  5. 评分项类别一致：最高 10 分（大类及属性维度一致）
  `25 + 25 + 20 + 20 + 10 = 100 分`。
- **额外加分机制（Bonus，综合得分封顶 100 分）**：
  - 项目类型一致加成：最高 5 分
  - 客户类型一致加成：最高 3 分
  - 搜索词 / 历史中标 / 优质文本加成：各 +5 分
- **硬门禁与评级**：
  - 大类冲突一票否决（0 分）
  - 评级标准：≥80% 优质，≥60% 良好，<60% 一般

### 3. 前端与宿主交付验证
- `AiRecommendDrawer.vue` 提示浮窗清晰分层展示基础规则与加分项；
- 开发宿主 18089 端口服务热重启，确保 `subTopic` 字段与最新算分策略端到端生效。

---

## 业绩评分项穿透资格项实质要求匹配机制（2026-09-15）

### 1. 业务痛点与防泛化约束
- **痛点**：在编制抽屉选择业绩类评分项（如 `3 同类项目业绩`）时，评分项自身详情往往仅为指针性引用（“需提供业绩证明材料同初步评审第7条供应商资格...”），缺失具体品类与金额要素，导致条款重叠分极低（总分 < 50），被前端门禁过滤导致返回“0 个结果”。
- **约束**：特指评分项与资格审查项中的业绩门槛，严禁在全文档泛化扫描“业绩”词汇，避免假阳性噪声。

### 2. 六边形架构与纯核心策略
- **资格项与全文查询端口**：定义 `TenderQualificationQueryPort` 与 `TenderQualificationQueryAdapter`，Application 层通过端口解耦底层快照存储；
- **穿透检索策略**：`QualificationPerformanceRetriever` 识别业绩评分项，穿透至最新标书快照提取实质门槛（近三年、电商化年度供应、办公用品/工业品、2000万/4500万等）；
- **评分策略扩展**：`ScoreItemCaseMatchPolicy` 在存在资格项实质要求时择优采用条款重叠度，并赋予品类命中（最高 12 分）、企业层级命中（8 分）、门槛与发票凭据（5 分）专属加分；
- **标签呈现**：优先提取命中的资格项核心特征词作为高亮标签，使契合业绩要求的案例达到 80+ 分（“优质”）稳定召回。



---

## 业绩评分项候选准入 + 金额门槛 + 资格项回退（spec 064，2026-09-16）

### 1. 问题
资格项穿透仅作打分 bonus，候选集仍是全量 ACTIVE 案例：人员/技术文案靠通用分越过前端 ≥50 门槛；金额门槛解析后无人消费；双条款只取第一条；「资质业绩」被判资质导致真业绩案例丢类别一致 +10。

### 2. 机制（FP-Java 纯核心）
- **门槛解析** `PerformanceThresholdExtractor`：按 `；;。\n` 分句配对品类与金额（办公用品/标准工业品），金额统一万元；`detectCategories` 暴露品类识别。
- **候选准入** `PerformanceCaseAdmissionPolicy`：`tier1 门槛达成 > tier2 品类命中(缺金额) > tier3 仅业绩信号`，取最小正值 tier 的候选集合；金额明确低于门槛或全无业绩证据 → tier0 拒绝；跨候选择优避免整体空结果。
- **条款收集** `QualificationClauseCollector`：资格项列表 + 资格审查/初步评审章节全部实质条款合并（去重、5 条/4000 字符上限）。
- **分类修正** `CaseCategoryMatchPolicy.classifyText`：PERFORMANCE 优先于 QUALIFICATION（「资质业绩」→ 业绩；纯资质仍为资质）。
- **编排** `ScoreItemCaseRecommendAppService`：业绩评分项执行准入；未选评分项且资格项含业绩要求时回退用资格项上下文；无资格项业绩条款时行为不变。

### 3. 数据缺口
`knowledge_case` 无金额/业绩品类字段，金额证据只能从案例文本提取；结构化金额门禁依赖文本正则，精度受限——已登记 `docs/exec-plans/tech-debt-tracker.md`。

---

## 资质证书评分项与方案类互斥（一票否决）+ 应急方案召回优化（2026-10-23）

### 1. 业务痛点与问题根因
- **痛点 1（资质错配方案）**：在招商积余等项目中，评分项为资质证书（如 ISO9001/14001/45001 体系认证），案例推荐却返回了技术方案（如“新品类的拓展增补”），且匹配度高达 62%（良好）。
  - *根因 A*：大类冲突判定缺少单向否决门禁。评分项为 `QUALIFICATION` 时，技术类与商务类方案未被一票否决。
  - *根因 B*：`ScoreItemCaseMatchPolicy` 中曾存在“候选案例正文只要弱包含评分项清洗标题（如‘管理体系’）直接返回 20 分满分”的旁路，导致长文方案虚假膨胀 20 分。
- **痛点 2（应急方案召回偏少）**：历史投标文件中应急方案大量存在，但选择“7 应急方案”评分项时仅匹配出 1 个无关案例（54% 一般）。
  - *根因*：`CaseSubTopicPolicy` 的 `EMERGENCY_PLAN` 仅定义了“应急预案”，缺少高频招标术语“应急方案”、“应急措施”、“应急管理”，导致评分项被降级推断为 `OTHER`，无法获得 25 分核心子主题契合分。

### 2. 核心架构修复（FP-Java）
1. **资质项非对称一票否决**：
   - 在 `CaseCategoryMatchPolicy.isCategoryConflict` 中强化约束：当评分项大类为 `QUALIFICATION` 时，`TECHNICAL` 与 `COMMERCIAL` 候选案例直接触发一票否决（返回 `true`，综合得分为 0）。
   - 扩充资质关键词词表（增加 `"管理体系"`, `"体系认证"`）。
2. **剔除正文弱包含送满分旁路**：
   - 移除标题相似度中只要正文包含标题即给 20 分的逻辑，使标题分严格基于标题与语义 Jaccard 相似度。
3. **补齐应急子主题关键词**：
   - `CaseSubTopicPolicy` 与离线特征提取脚本 `scripts/extract-historical-case-features.py` 同步补齐“应急方案”、“应急措施”、“应急管理”、“应急组织”等词汇。
4. **前端抽屉资质证书引导**：
   - `AiRecommendDrawer.vue` 增加资质证书类评分项的 Alert 提示，并在空态时引导用户前往「资质中心」选用证书。
5. **存量 OTHER 标签案例穿透激活**：
   - 修复 `CaseSubTopicPolicy.calculateSubTopicScore` 中仅在 `!hasText(candidateSubTopic)` 时才进行文本补偿的漏洞；
   - 允许 `candidateSubTopic` 为 `"OTHER"` 的存量案例穿透检查文本和标题，只要命中应急方案/措施等关键词即可获得 15 分文本契合补偿分，彻底激活存量数据库中被打上 OTHER 的应急方案切片。

---

## 前端展示门禁与资质提示二次误判（测试环境复发，2026-10-24）

### 1. 复发证据

- 项目 1388「BIZ_FIN 经营及财务状况」：`POST /api/projects/1388/cases/recommend` 返回 `total=165`，分数集中在 32–37；前端额外执行 `score >= 50` 后显示 0 条。
- 项目 1388「TECH_QUAL 质量管控」：详细条款中的“强制认证产品质量管理”仅因包含普通“认证”，触发资质证书提示；接口实际返回 81 条案例，形成提示与结果自相矛盾。

### 2. 根因与契约

- 后端 `ScoreItemCaseRecommendAppService` 已用 `matchScore > 0` 完成推荐准入，前端不得叠加未写入 API 契约的分数门槛。
- 匹配度评级允许 `<60%` 为“一般”，因此低分不等于无效结果；抽屉必须展示后端返回项，并使用响应 `total` 表示总命中数。
- 资质提示只识别明确证书对象：标题中的资质、资格、证书、体系认证、管理体系、ISO、许可、执照，或正文中的资质证书、资格证书、体系认证、ISO、许可证、执照、证书复印件、证书扫描件。普通“认证”不是充分条件。

### 3. 自动化门禁

- `AiRecommendDrawer.spec.js` 固化两个复发场景：32/37 分案例仍展示且显示服务端 `total=165`；“质量管控 + 强制认证产品质量管理”不出现资质提示。
- 既有 ISO 管理体系证书用例继续验证真正证书项仍显示提示。

---

## CO-652 优化：资质证书大类穿透与企业资质库原件图片直连推荐（2026-10-30）

### 1. 业务痛点与现场证据
招商积余等项目（1358/1360/1361）中：
1. 招标文件要求资质证书，抽屉却推荐技术方案/服务方案且匹配度高达 65%~69%（虚标为良好）；
2. 历史项目应急方案丰富但召回单一；
3. 用户明确诉求：证书属于客观证明文件，应当精准匹配，并且要求**直连匹配资质证书图片扫描件**，方便查阅与复制。

### 2. 根因剖析
- **泛章节名遮蔽细则大类**：标书常把证书要求置于“技术部分/详细评审/综合能力”章节，原 `CaseCategoryMatchPolicy` 因维度非 OTHER 直接跳过 detail 中的证书识别，一票否决门禁失效。
- **双字切词高频噪声与中标虚假叠加**：双字机械切词使得非相关长文方案仅凭高频通用词与虚假的大类一致加成轻松越过 60% 门槛。
- **资质库数据孤立**：系统内已有完整的 `business_qualifications`（主表）与 `qualification_attachments`（附件图片表），但之前推荐抽屉未打通。

### 3. 架构与契约升级
1. **六边形架构端口与适配器**：
   - 端口：`EnterpriseQualificationQueryPort`（入参 `dim, detail, keyword`）；
   - 适配器：`EnterpriseQualificationQueryAdapter` 直连企业资质库与附件表，装配证书扫描件图片 URL；
2. **FP-Java 资质匹配策略**：
   - `EnterpriseQualificationMatchPolicy`：精准匹配 ISO 三体系、AAA 信用、安全许可等标准，按当前日期校验有效期（有效/临期/过期降级），过滤报废证书；
3. **响应体契约扩充**：
   - `ScoreItemCaseRecommendResponse` 扩展 `matchedCertificates: List<CertificateItem>`，包含 `id, name, certificateNo, issuer, expiryDate, status, certScope, fileUrl, fileName, matchScore, matchReasons`；
4. **前端组件化交互**：
   - 抽离 `EnterpriseCertRecommendList.vue`，渲染证书卡片网格、原件图片缩略图、点击放大预览、有效期标签、一键复制证书信息与一键复制图片链接。

---

## 语料 IDF + BM25 长度归一 + 情境加成门禁与 `PRODUCT_PRICING` 子主题（2026-11-16）

### 1. 现象与根因（生产项目 580）
- 项目 580 选择「商品管理方案」时，物流配送/仓储能力案例排在价格/定价案例之前。
- 根因：评分档位模板（`优秀得8-10分...`）与表头前缀未剥离；`calculateDetailOverlap` 无 IDF 与长度归一且按 `hitCount * 3` 保底，长文本和泛词（`机制`/`措施`/`10`/`sku`）直接拿满 25 分；类别/央企/中标 `WON` 等情境加成无主题相关性门禁；`CaseSubTopicPolicy` 缺失商品管理与定价子主题。

### 2. 修复要点
- `ScoreItemCaseMatchPolicy.evaluateAll(candidates, context)`：候选数 `>= 5` 时计算语料 IDF 权重与 BM25 长度因子（`k1=1.2, b=0.75`）；`requirementTokens` 剥离表头前缀与评分档位模板；标题补充 `titleCoverage`；存在评分项要求时，情境加成按主题相关度 `min(1.0, relevance / 8.0)` 等比折算。
- `CaseSubTopicPolicy.SubTopic` 新增 `PRODUCT_PRICING`（商品管理与定价机制）。
- `ScoreItemCaseRecommendAppService` 改为调用 `matchPolicy.evaluateAll(candidateCases, context)`。


## 配送能力误召回等保证书：修复前只读诊断（2026-11-17）

> 以下为初诊时的证据边界；现场数据库补证及本地修复终态见后续小节。

- **验证基线**：`92d522f8b`，未修改业务实现，未部署；截图现场版本、项目 ID、案例完整字段和推荐响应未取得。
- **截图事实**：「45 配送能力评价」第一条为网站安全等级防护证书，显示 40%，其次为承诺事项 39%、仓储能力 32%。这些分值来自截图，尚未现场重放。
- **已执行实验**：从当前源码用 Java 21 `javac -sourcepath backend/src/main/java` 在系统临时目录编译纯核心策略；输入截图配送条款，构造证书候选作为受控对照，不调用数据库或 API，不代表现场记录。
- **子主题漏识别**：`CaseSubTopicPolicy.inferSubTopic` 对「配送能力评价」和截图详情返回 `OTHER`；物流词表未覆盖配送能力/配送响应速度/配送网点，详情至少需要两种已知词命中。
- **分类拦截非对称**：配送项归 `TECHNICAL`，证书候选归 `QUALIFICATION`，`isCategoryConflict=false`。已有门禁拦截证书评分项推荐方案，反向不拦截。
- **背景信号可冒充主题相关度**：`ScoreItemCaseMatchPolicy` 先把项目/客户专属实体分计入 `score`，再用该累计分决定背景加成是否全额发放。实体命中 12 分已超过全额加成阈值 8，即使子主题、条款和标题均无命中，也会获得完整背景加分。
- **受控对照结果**：无实体命中时证书 0 分；仅加入「三峡」同客户实体，证书 30 分（12 实体 + 5 项目类型 + 3 客户类型 + 5 中标 + 5 质量）；只把候选分类改为「技术方案」，额外获得 10 类别分，总分 40。此 40 分是机制实验，不能当成截图实际分值拆解。
- **放大路径**：应用服务返回所有 `matchScore > 0` 的结果，优先置顶、其次分数、再复用次数；前端直接保留接口顺序，不验证配送相关性。
- **待验证**：截图案例的 `scoringCategory/requirementRaw/responseText/sourceProjectName/subTopic/isPinned/matchReasons` 与现场部署版本。未确认这条案例是否同客户、误分类或置顶，不将实验条件当现场事实。
- **最小修复方向（尚未实施）**：以真实标题/条款/业务子主题相关度约束背景加成；覆盖配送原文子主题识别；依据业务契约限定纯证明文件的适用评分项。不能用统一抬高展示分数阈值代替相关性修复。


### 按工程纪律七大根因复核：重复修复遗漏（2026-11-17）

- **手册来源**：`.wiki/pages/engineering-discipline.md` 第一章、第四章、§7.4；同名 `docs/references/engineering-discipline.md` 是另一份工程约束汇编。
- **已证实归类：1 + 4**。业务主题相关性和项目/客户背景相关性混用，修复围绕错排表现调整词表与权重，却未落实注释承诺的“背景加分只能作用于主题相关案例”。`d357cce13` 新增 `testOffTopicCaseDoesNotGetFullContextBonus` 将项目名和客户名设为空，绕过真实应用始终尝试装配的实体输入；其 `<15` 断言也不能证明无关案例不会进入 `score > 0` 推荐结果。
- **历史版本受控比较**：以同一 `Probe.java` 分别从 `d357cce13^` 和 `d357cce13` 的完整 domain 源码编译执行。无实体的证书由 18 分变为 0 分；同实体证书均 30 分；同实体、候选归技术方案均 40 分。证明最新修复关闭了无实体背景加分，未关闭同实体错主题路径，不能归为最新修复引入的“修 A 破 B”。这些是受控实验，不是截图分值重放。
- **其他分类**：2 外部配置、3 环境差异、6 框架理解偏差暂无证据；7 真实环境验证证据尚未取得，不能据此指控此前未验证。独立只读核查得到相同结论。
- **修复验收约束（尚未实施）**：项目名/客户名/中标/类型/质量/置顶等背景变化，不能把内容无关候选变成符合当前评分要求的推荐；先判内容适用性再计算背景排序，置顶不得绕过适用性。测试必须同时包含同客户异主题反例、异客户同主题正例、配送原文、证书/方案双向及混合要求边界。
- **SOP缺口**：现场请求、完整候选字段、实际排序响应和部署版本尚未固定，不宣称完成现场G1；业务修复、全量回归、真实环境验证和门禁接入均未执行。应把根因行为测试接入现有 pre-push，语义正确性不能仅用源码文本正则检查。


## 配送案例生产数据补证与本地修复（2026-11-17）

### 现场定位及可重放失败
- 生产项目44、评分项571、code=45，与截图的配送条款一致；证书案例331标题和分类均为「网站安全等级防护能力」，实际正文为等保三级证明。未置顶、复用0、LOST、质量60。
- 实际生产JAR版本`5cbbf4925783f6c2eca921b7191fce8fd3c8247d`；三个匹配策略与修复基线`92d522f8b`逐文件无差异。全程只读取数，未改生产数据或配置。
- 完整349条ACTIVE候选快照在本地临时目录重放：旧算法245结果，案例331以40分第一；分解=实体20+模板条款重叠5+标题泛词2+类别10+央企3，与截图完整吻合。原始快照不入仓库。
- 初版仅分离实体/背景后，真实记录仍有38分；证明仅用“证书”标题合成夹具不够，必须用现场标题/分类/评分模板形状反证。新增脱敏最小真实结构测试后再修复。

### 已实施的最小边界
- `ScoreItemCaseMatchPolicy` 先固定内容相关度，再判断准入；主题无命中返回0。实体、搜索词和其他背景不参与背景加成解锁；沿用应用服务先过滤0分再置顶排序。
- `ScoreItemCaseTextMatchPolicy`：标题比较与正文覆盖共用实质词过滤；剥离「得N分」评分模板与纯数字，不把「能力/评价/评分/得分」作主题；搜索词不再进入标题相关性；项目名相似度回退只用于完全无条款/资格要求的默认推荐。
- 在线/离线词表同步新增配送能力、配送响应速度、配送网点。保留明确证书要求、技术资质兼容、跨客户同主题、默认项目推荐。
- 既有`pre-push-gate.sh`执行根因及应用服务回归和离线自测；默认agent分支不会隐式跳过，显式`--skip-tests`遵循既有命令契约。

### 验证边界
- 最终聚焦110项通过：根因11、策略54、应用13、子主题20、架构12。前端构建通过。Python自测、Shell语法、既有dispatch测试和diff检查通过。
- 完整生产快照修后233结果，案例331为0分、条款和标题分均0，不进入推荐。
- 独立恢复评分模板污染或能力泛词污染，11个根因测试分别重新失败1项；修复版本11项全绿。早期整体撤销核心修复也重新失败7项。
- google-code-review与xiyu-code-review独立只读检查无Critical/Major；显式skip命令语义Minor已修复并复核关闭。
- 自动三技能流程清理阶段因Codex子进程沙箱中Mockito/ByteBuddy自附加失败返回ESCALATE，已停止，不生成通过凭证、不创建PR。主执行器应用服务测试正常通过；不能把独立审查报告冒充自动门禁凭证。
- 全量Maven：12217项，94失败、0错误、23跳过；案例模块635项为0失败、0错误、1跳过。94项中83项缺Sidecar Python，复用开发宿主既有venv指定SIDECAR_PYTHON后，当前代码失败子集166项中151通过、4按原有假设中止、11失败。覆盖三个修复前策略类做基线对照，11个失败方法逐项一致，均属于未改动的评分解析模块。
- 没有为了全仓绿灯修改无关模块。初次本地交付时全仓未全绿，自动门禁因沙箱限制阻塞；用户后续授权提交PR。正式PR交付须重新取得三技能门禁凭证并校验远端head；本地目标缺陷验证通过不代表发布验收，未合并、未部署。
