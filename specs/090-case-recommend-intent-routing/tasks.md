# Tasks: 评分项意图确定性路由与推荐打分公式重构（Phase 1 止血）

**Input**: Design documents from `specs/090-case-recommend-intent-routing/`

**Prerequisites**: `spec.md`, `plan.md`

## Organization
Tasks are grouped by user story to enable independent implementation and testing of each story according to TDD principles.

---

## Phase 1: Foundational (Core Routing & Pure Policies)

- [x] T001 [P] [US1] 编写意图路由器单元测试 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemIntentRouterTest.java`，覆盖技术、资质、业绩、人员、商务及章节泛名穿透测试。
- [x] T002 [US1] 创建纯核心意图路由器 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemIntentRouter.java`，定义 `ScoreItemIntent` 枚举与确定性分流逻辑。
- [x] T003 [US1] 运行 `mvn test -Dtest=ScoreItemIntentRouterTest` 确保路由器纯函数 100% 绿灯。

---

## Phase 2: User Story 2 (乘法先验打分模型重构，解决背景代偿)

- [x] T004 [US2] 在 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicyTest.java` 增加乘法门禁回归用例：断言同客户实体等保证书在配送能力评分项下必须为 0 分；内容吻合案例正常获得背景加成。
- [x] T005 [US2] 重构 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java`：
  - 提取 `ContentScore = min(100, SubTopic + Detail + Title)`；
  - 若 `ContentScore == 0`，直接返回 0 分；
  - 将 `Entity/Category/Type/Won/Quality` 转换为乘法比例放大因子：$\text{FinalScore} = \min(100, \text{round}(ContentScore \times (1.0 + BonusFactor)))$。
- [x] T006 [US2] 运行 `mvn test -Dtest=ScoreItemCaseMatchPolicyTest,ScoreItemCaseTextMatchPolicyTest` 验证纯核心算分策略全部通过。

---

## Phase 3: User Story 1 (应用编排层物理阻断与资质直连)

- [x] T007 [US1] 在 `backend/src/test/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppServiceTest.java` 编写资质项切片物理阻断测试：断言资质项返回 `items.isEmpty()` 且 `matchedCertificates` 正常装配。
- [x] T008 [US1] 修改 `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java`：接入 `ScoreItemIntentRouter`，资质项直接短路返回证书列表，不执行 `allActiveCases` 的文本切片评估。
- [x] T009 [US1] 运行 `mvn test -Dtest=ScoreItemCaseRecommendAppServiceTest` 验证应用服务层装配与阻断行为通过。

---

## Phase 4: User Story 3 (前端抽屉组件自适应与端到端验证)

- [x] T010 [US3] 检查并完善 `src/views/Project/stages/components/AiRecommendDrawer.vue`：确保资质项返回时展示证书列表，技术项返回时展示方案卡片。
- [x] T011 [US3] 运行前端测试与构建：`npm test`、`npm run build`。

---

## Phase 5: Verification & Gate (Phase 1)

- [x] T012 运行 casework 模块全量回归测试：`mvn test -Dtest="com.xiyu.bid.casework.**.*Test"`。
- [x] T013 使用 `speckit-analyze` 检验 spec.md, plan.md, tasks.md 三位一体一致性。
- [x] T014 产出验证通过证据。

---

## Phase 6: User Story 4 (分词规范化与假词逆向 IDF 权重消除)

- [x] T015 [P] [US4] 在 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/TextSimilarityPolicyTest.java` 增加分词规范化测试：断言“人员须具备较高业务水平”等长文本不产生跨助词/连词假词（“员须”、“备较”、“高业”），保留真正业务词汇。
- [x] T016 [US4] 重构 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/TextSimilarityPolicy.java`：废弃无边界 3-gram 滑窗，加入助词/连词边界守卫和核心招投标词汇保护。
- [x] T017 [US4] 在 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseTextMatchPolicy.java` 中引入低频假词过滤与 IDF 权重平滑，防止冷僻假词反向霸占条款分。
- [x] T018 [US4] 运行 `mvn test -Dtest=TextSimilarityPolicyTest,ScoreItemCaseTextMatchPolicyTest,ScoreItemCaseMatchPolicyTest` 验证纯核心分词与算分策略 100% 绿灯。

---

## Phase 7: User Story 6 (行预算重构与编排解耦)

- [x] T019 [US6] 创建协作类 `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendEvaluator.java`，将候选切片的分页、置顶排序与 DTO 组装逻辑剥离至该类，控制在 150 行以内。
- [x] T020 [US6] 重构 `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java`，行数降至 200 行以下，运行 `mvn test -Dtest=ScoreItemCaseRecommendAppServiceTest` 确保契约零回归。

---

## Phase 8: User Story 5 (向量语义检索打通与优雅降级)

- [x] T021 [P] [US5] 在 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicyTest.java` 增加向量语义融合测试：验证当字面无公共词但具备向量余弦相似度时，案例突破一票否决门禁获得合理推荐；验证当向量为 null 时优雅降级。
- [x] T022 [US5] 在 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java` 中引入向量语义相似度打分逻辑，将语义相似度与词法相关度融合为 `ContentScore`。
- [x] T023 [US5] 在 `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java` 接入向量语义查询逻辑（支持注入 `BidCaseSliceVectorCache` 或语义计算），支持从上下文透传向量语义分并处理降级。
- [x] T024 [US5] 运行 `mvn test -Dtest=ScoreItemCaseMatchPolicyTest,ScoreItemCaseRecommendAppServiceTest` 验证向量双轨融合与优雅降级全绿。

---

## Phase 9: Verification & Gate (Phase 2 全量验收)

- [x] T025 运行全量单元测试与架构门禁：`mvn test -Dtest="ScoreItemIntentRouterTest,ScoreItemCaseMatchPolicyTest,ScoreItemCaseRelevanceRegressionTest,ScoreItemCaseRecommendAppServiceTest,CaseSubTopicPolicyTest,EnterpriseQualificationMatchPolicyTest,QualificationPerformanceRetrieverTest,TextSimilarityPolicyTest,FPJavaArchitectureTest"`。
- [x] T026 运行 `scripts/review-workflow.py` 自动化审查生成凭证并执行 pre-push 门禁。

---

## Phase 10: User Story 7 & 8 (纯模板声明与低质切片准入门禁)

- [x] T027 [P] [US7] 编写低质切片与纯模板过滤策略测试 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/CaseContentQualityPolicyTest.java`，覆盖格式模板（授权书/承诺函/保函空白表格）、敷衍占位符（详见附件/完全响应）、目录残片、低信息熵重复文本以及真实高质技术方案的零误杀反证用例。
- [x] T028 [US7] 实现纯核心数据质量准入策略 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/CaseContentQualityPolicy.java`，支持 `isLowQuality`、`evaluate` 与 `filterAdmissible` 判定，严格保持 FP-Java 规范。
- [x] T029 [US7] 在 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java` 中增加兜底防御门禁，拦截低质废料切片。
- [x] T030 [US7] 在 `backend/src/main/java/com/xiyu/bid/casework/application/service/ScoreItemCaseRecommendAppService.java` 中引入前置准入过滤，在构建语料库及推荐算分前排除低质切片。
- [x] T031 [US7] 在 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicyTest.java` 与 `ScoreItemCaseRecommendAppServiceTest.java` 中增补低质模板案例过滤用例并验证全部测试通过。

---

## Phase 11: Verification & Gate (Phase 3 全量验收)

- [x] T032 运行全部 casework 模块及架构门禁测试：`mvn test -Dtest="*CaseQuality*,*CaseMatch*,*CaseRecommend*,*TextSimilarity*,FPJavaArchitectureTest"`。
- [ ] T033 运行 `scripts/review-workflow.py` 自动化审查流程生成新凭证。
- [ ] T034 运行 `scripts/pre-push-gate.sh` 门禁并提交推送更新 PR !3050。
