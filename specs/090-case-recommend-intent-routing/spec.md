# Feature Specification: 评分项意图确定性路由与推荐打分公式重构（Phase 1 止血）

**Feature Branch**: `agent/zcode/090-case-recommend-intent-routing`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "案例推荐一直不准，实施 Phase 1 方案：评分项大类确定性路由分流，资质证书项彻底阻断文本切片直连资质库，打分公式改用乘法先验门禁彻底解决背景代偿反客为主。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 评分项意图确定性路由与物理阻断 (Priority: P1)

投标人员在项目详情页打开“AI 智能案例推荐”抽屉，选择某个评分项时，系统首先对评分项的业务意图进行确定性分类识别（TECHNICAL, QUALIFICATION, PERFORMANCE, PERSONNEL, COMMERCIAL）。
当用户选择【资质证书类】（如“ISO9001认证”、“AAA资信等级”）评分项时，后端推荐服务彻底阻断 `knowledge_case` 表文本切片的模糊推荐，零返回文本切片案例；系统直接从企业资质库（`business_qualifications`）装配权威证书原件扫描件及到期日信息返回。

**Why this priority**:
消除“用户选资质证书却推大段技术管理作文方案”的最直接业务痛点，阻断知识库文本切片对证书评分项的假阳性污染。

**Independent Test**:
选择任一包含“ISO9001/体系认证/资质证书”的评分项，调用 `POST /api/projects/{id}/cases/recommend`：
- `items` 列表必须严格为空（`size == 0`）；
- `matchedCertificates` 返回精准匹配的有效证书实体；
- 绝不出现任何文字方案切片。

**Acceptance Scenarios**:
1. **Given** 评分项为“5 质量管理体系认证（ISO9001）”，**When** 执行案例推荐，**Then** 文本切片 `items` 为空，`matchedCertificates` 返回 ISO9001 证书原件及有效期限。
2. **Given** 评分项为“4.2 财务审计报告与资信”，**When** 执行案例推荐，**Then** 技术方案类案例全部被大类互斥门禁拦截（0分），不进入推荐列表。

---

### User Story 2 - 乘法先验门禁打分模型（彻底根除背景特征反客为主） (Priority: P1)

在纯技术与服务方案（TECHNICAL）的案例推荐中，重构 `ScoreItemCaseMatchPolicy` 的加权算分机制。
彻底废除“内容分 + 背景分”的线性加法逻辑，改为“内容实质相关度作为门禁基准，背景作为相对比例加成（Multiplicative Bonus）”：
$$\text{FinalScore} = \text{ContentScore} \times \left(1.0 + \text{BonusFactor}\right)$$
当案例内容实质与评分项不相关时（$ContentScore = 0$），哪怕该案例命中专属同客户实体（三峡/建行）、同为央企、且为历史中标案例，其最终综合得分恒为 0，坚决不进入推荐前台。

**Why this priority**:
彻底拔除导致“配送能力评分项将等保证书排第一”、“商品管理方案将物流仓储排第一”的数学根本诱因，恢复内容主题相关度的一票否决权。

**Independent Test**:
构造与“配送能力/时效”完全无关的“等保证书”或“办公设备”案例，将其客户和项目名称硬编码设为与当前项目完全一致的“三峡集团”：
- 旧算法因实体加分与背景全额解锁得到 40~56 分（优质/良好）；
- 新算法计算 $ContentScore = 0$，最终 $FinalScore \equiv 0$，测试断言通过。

**Acceptance Scenarios**:
1. **Given** 评分项为技术方案“45 配送能力评价”，候选案例为同客户的“等保三级证明”，**When** 执行打分，**Then** 因业务主题完全无关，$ContentScore = 0$，最终得分为 0，被完全过滤。
2. **Given** 评分项为“系统接口对接与集成”，候选案例 A 为跨客户但正文高度吻合的方案（ContentScore=22），候选案例 B 为同客户但内容完全无关的合同（ContentScore=0），**When** 执行推荐，**Then** 案例 A 正常上浮并获得高分推荐，案例 B 得分 0 被拦截。

---

### User Story 3 - 前端抽屉形态自适应切换与证书装配 (Priority: P2)

在项目编制“AI 智能案例推荐”抽屉（`AiRecommendDrawer.vue`）中，当用户切换不同评分项时：
- 若切换到【资质证书项】，自动清空方案列表骨架屏，展示专用的 `EnterpriseCertRecommendList.vue` 证书卡片网格；
- 提供证书有效状态（绿色正常/黄色临期/红色过期）、一键复制证书编号与一键预览大图能力；
- 提示栏清晰注明“当前评分项已直连企业资质库”。

**Why this priority**:
让用户感知到系统具有明确的意图理解能力，避免在同一个界面中给用户造成“这是文章还是证书”的认知混乱。

**Independent Test**:
在浏览器打开 AI 推荐抽屉，点击资质项，抽屉自适应渲染证书缩略图列表；点击技术项，抽屉恢复渲染方案切片列表。

**Acceptance Scenarios**:
1. **Given** 抽屉处于打开状态，**When** 用户点击评分项“ISO14001环境体系认证”，**Then** 界面呈现证书原件卡片，不显示“应答片段摘要/复用”按钮。

---

### User Story 4 - 分词规范化与假词逆向 IDF 权重消除 (Phase 2, Priority: P1)

系统在处理评分项细则条款和案例文本匹配时，消除机械 2-gram / 3-gram 滑窗造成的“跨词生造假词”（如“员须”、“备较”、“高业”、“人须”）缺陷：
- 引入词素质量守卫与助词/连词边界阻断规则（阻断“须/应/需/得/较/把/被/在/是/为/且/及/与/或”等单字切入 2-gram）；
- 废弃无意义盲目 3-gram 切片；
- 确保语料词表 IDF 权重计算时，假词不再因低文档频率（$DF=1$）反常获得 7~10 倍的虚高权重，真正业务词汇（如“人员”、“具备”、“水平”、“方案”）获得平稳主导权。

**Why this priority**:
拔除导致长文本案例仅凭拼凑几个无意义假词就霸占 25 分条款分（Detail Overlap）的技术根因。

**Independent Test**:
调用 `TextSimilarityPolicy.extractTokens("人员须具备较高业务水平")`：
- 提取结果不包含“员须”、“须具”、“备较”、“较高业”等跨词假词；
- 提取结果包含“人员”、“具备”、“业务”、“水平”等真实合法词；
- `Corpus` 统计中假词不再生成异常高 IDF 峰值。

**Acceptance Scenarios**:
1. **Given** 条款包含“投标人技术负责人须具备高级职称”，**When** 执行提取与分词，**Then** 提取出的词汇均为业务合法词素，无跨停用词假词。
2. **Given** 候选案例长篇文本，**When** 评估条款重叠度，**Then** 仅在命中真正业务关键词时获得加分，长文本假词碰撞得分为 0。

---

### User Story 5 - 向量语义相似度通道打通与优雅降级 (Phase 2, Priority: P1)

解决“字面无字词重合即一票否决”的语义鸿沟问题（如“系统对接” vs “接口协同”，“价格管理” vs “自营定价”）：
- 在案例推荐策略与编排中，支持接入切片向量余弦相似度（`VectorCosineSimilarity`）；
- 核心内容评分支持融合双轨信号：当字面 Jaccard / 2-gram 词频无法覆盖同义表述时，向量相似度提供底线语义相关分支撑，将有效语义案例召回；
- **优雅降级 (Graceful Degradation)**：当向量服务不可用、未加载缓存或向量为空时，自动安全回退至纯词法与子主题匹配（Lexical Match），确保在临时 worktree、离线测试及纯内存环境下 100% 稳定运行。

**Why this priority**:
消除“同义词无法召回、差一两个字就 0 分”的死板字面匹配缺陷，激活历史沉淀的 8144+ 向量切片资产。

**Independent Test**:
构造评分项“商城系统对接与接口能力”与候选案例“与外部系统及第三方平台互联互通机制”（两者无字面公共关键词，但向量余弦相似度 $\ge 0.75$）：
- 纯字面模式下 $ContentScore = 0$；
- 激活向量通道后，$ContentScore \ge 20$ 并成功召回；
- 关闭向量服务时，优雅降级，不抛出异常。

**Acceptance Scenarios**:
1. **Given** 向量缓存就绪且可用，**When** 评分项与案例具有深层同义关系，**Then** 案例正常获得语义契合分并排在前列。
2. **Given** 向量缓存为空（如单测或冷启动），**When** 执行推荐，**Then** 自动平滑降级，测试与业务请求 0 失败。

---

### User Story 6 - 行预算治理与编排层解耦 (Phase 2, Priority: P2)

重构 `ScoreItemCaseRecommendAppService`（当前 298 行，上限 300 行）：
- 将候选评估、大类过滤与打分排序逻辑解耦抽取至协作类（如 `ScoreItemCaseRecommendEvaluator`）；
- 使 `ScoreItemCaseRecommendAppService` 单文件行数降至 200 行以下，从架构上彻底远离 300 行棘轮硬门禁。

---

### User Story 7 - 纯模板声明与低质切片准入门禁 (Phase 3, Priority: P1)

系统在案例候选召回与打分全链路中，建立严格的数据质量准入门禁：
- 识别纯空白格式声明（如“法定代表人授权委托书格式”、“投标承诺函格式如下”、“格式自拟”、“签字：______”、“（盖章）：________”、“日期：____年__月__日”等纯填空模板）；
- 识别敷衍占位符切片（如“详见附件”、“见附表”、“见技术标”、“完全满足要求特此承诺”等缺少实质应答方案的极短文本）；
- 在编排层及打分层实施物理拦截：判定为低质废料切片的案例，不计入推荐候选池，不参与 TF-IDF 语料构建，避免污染正常打分分布。

**Why this priority**:
消除“用户在推荐结果中经常被无意义的授权书格式、空白承诺函模板刷屏”的脏数据痛点。

**Independent Test**:
构造“法定代表人授权委托书模板格式如下：我______系______法定代表人，现授权张三代表我方参加三峡集团项目投标...（盖章）：______”等模板文本：
- 即使命中“三峡集团”实体与“项目”关键词，`CaseContentQualityPolicy.isLowQuality(...)` 判定为 true；
- 打分或准入直接拦截（0分，理由包含“纯格式模板/非实质方案”），不进入推荐列表；
- 真实技术方案（如仓储物流、系统对接方案）100% 正常放行（零误杀）。

**Acceptance Scenarios**:
1. **Given** 候选案例为授权书/承诺书空白模板切片，**When** 执行案例推荐，**Then** 被质量门禁彻底拦截，前台不出现该模板。
2. **Given** 候选案例为真实高质技术方案（如三峡电商化接口对接方案），**When** 执行案例推荐，**Then** 质量门禁顺利放行，不发生误杀。

---

### User Story 8 - 排版残片与低信息熵垃圾清洗 (Phase 3, Priority: P2)

系统在处理历史切片时，识别并过滤非实质性的排版残片与低信息熵乱码：
- 识别由点号、下划线、破折号及页码组成的纯目录切片（如“第一节 总体方案 .......... 1 \n 第二节 实施步骤 .......... 5”）；
- 识别符号重复率极高或字符种类极少的低信息熵文本（如单一字符重复、大量标点下划线占比超标）；
- 综合文本质量评分（`qualityScore < 40`）与文本特征综合判定，防止残片混入。

---

## Edge Cases

- 评分项无详情内容（`detail` 为空，仅有泛化章节标题如“第三章 详细评审”）：
  - 系统降级依靠 `dim` 识别，若均为泛标题，则标记为 `OTHER`，不触发过度一票否决，并给出“请补充评分细则以获得精准推荐”的提示。
- 评分项混合了“资质与方案”（如“具备CMMI3以上资质并提供详细开发方案”）：
  - 路由器识别为 `HYBRID` 或优先放行技术方案，同时在证书区域呈现 CMMI3 凭证，两翼齐飞，不发生误杀。
- 企业资质库中暂无匹配的证书原件：
  - 抽屉展示优雅空态（“企业资质库暂未收录该证书”），并提供直达「资质中心」录入的快捷跳转链接，严禁回退乱推文本作文。

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 必须在 `casework/domain/policy/` 建立纯核心 `ScoreItemIntentRouter`，将评分项确定性划分为 `TECHNICAL`, `QUALIFICATION`, `PERFORMANCE`, `PERSONNEL`, `COMMERCIAL`, `OTHER`。
- **FR-002**: 当且仅当评分项大类为 `QUALIFICATION` 时，`ScoreItemCaseRecommendAppService` 必须将 `evaluatedItems` 置空，仅装配 `matchedCertificates`。
- **FR-003**: `ScoreItemCaseMatchPolicy` 必须实现乘法先验门禁：背景特征加成作为乘法系数，严禁无门禁的加法累加；当且仅当内容实质相关度 $ContentScore \ge 1$ 时，才允许背景因子生效。
- **FR-004**: 彻底移除 `calculateDetailOverlap` 与 `calculateTitleSimilarity` 中将泛词/无区分度碎片反向膨胀为高分的漏洞，保证纯核心策略 100% 具备单测反证用例。
- **FR-005**: 响应体 DTO `ScoreItemCaseRecommendResponse` 保持向后兼容，确保旧调用方不崩溃。
- **FR-006**: 必须在 `casework/domain/policy/` 建立纯核心 `CaseContentQualityPolicy`，识别纯格式模板、敷衍占位符、目录排版残片及低信息熵垃圾切片。
- **FR-007**: `ScoreItemCaseRecommendAppService` 与 `ScoreItemCaseMatchPolicy` 必须接入数据质量准入门禁，低质废料切片一票否决不予推荐。

### Non-Functional Requirements

- **NFR-001 (FP-Java)**: 所有核心路由与打分逻辑必须位于 `domain/policy/`，必须为无状态纯函数，无 Spring 注解，无数据库与外部 IO 依赖，100% 可独立运行单测。
- **NFR-002 (性能)**: 单次打分全量候选（300+ 案例）执行耗时在 JVM 内存中必须 $\le 50\text{ms}$。
- **NFR-003 (零 Mock 政策)**: 涉及的真实数据库调用（通过 Adapter）必须使用真实 JPA 映射与 MySQL 容器测试，禁止在业务路径使用伪造假数据。
