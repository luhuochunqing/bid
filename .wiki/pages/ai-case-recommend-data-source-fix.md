---
title: AI 智能案例推荐数据源修复（score_item 新契约切换 + casework 裸响应陷阱）
space: engineering
category: guide
tags: [案例推荐, score_item, spec041, 裸响应, 二次解包, caseRecommendationApi]
created: 2026-08-21
updated: 2026-10-23
health_checked: 2026-10-23
sources:
  - src/api/modules/caseRecommendation.js
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - backend/src/main/java/com/xiyu/bid/casework/controller/ScoreItemCaseRecommendController.java
backlinks:
  - _index
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

