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

## Phase 5: Verification & Gate

- [x] T012 运行 casework 模块全量回归测试：`mvn test -Dtest="com.xiyu.bid.casework.**.*Test"`。
- [x] T013 使用 `speckit-analyze` 检验 spec.md, plan.md, tasks.md 三位一体一致性。
- [x] T014 产出验证通过证据。
