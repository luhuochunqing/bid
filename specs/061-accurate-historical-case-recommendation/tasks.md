# Tasks: 基于真实数据底座的历史案例精准推荐

**Input**: Feature specification from `specs/061-accurate-historical-case-recommendation/spec.md` and `plan.md`.

## Phase 1: 70 个历史项目特征抽取与清洗流水线（核心先决）

- [x] T001 编写 `scripts/extract-historical-case-features.py`，实现项目企业专名抽取（剥离前缀/年份）
- [x] T002 在 `extract-historical-case-features.py` 中实现 10 大业务子主题（`Sub-Topic`）规则分类器
- [x] T003 在 `extract-historical-case-features.py` 中实现文本充实度与质量评分（`quality_score`）逻辑
- [x] T004 增加样板段落物理过滤门禁（过滤发票清单、盖章声明、小于 120 字片段）
- [x] T005 完善 `extract-historical-case-features.py --self-test` 单元断言测试集并验证 100% 通过
- [x] T006 跑通离线测试模式（`--offline`），验证抽取结果和子主题分类覆盖率

## Phase 2: 数据库迁移与案例库扩容入库

- [x] T007 编写 Flyway 迁移脚本 `backend/src/main/resources/db/migration-mysql/V1211__add_sub_topic_to_knowledge_case.sql`
- [x] T008 编写配对回滚脚本 `backend/src/main/resources/db/rollback/migration-mysql/U1211__add_sub_topic_to_knowledge_case.sql`
- [x] T009 更新 JPA 实体 `backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCase.java` 与 ReadModel 映射 `subTopic` 与 `qualityScore`
- [x] T010 扩展 `scripts/import-case-slices-to-knowledge-case.py`，支持将清洗后的特征与子主题入库
- [x] T011 运行 `python3 scripts/import-case-slices-to-knowledge-case.py --self-test` 确保导入测试通过

## Phase 3: 匹配策略 Pure Core 加固与算法对齐

- [x] T012 新建纯核心策略类 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/CaseSubTopicPolicy.java`，实现评分项业务子主题推断
- [x] T013 在 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicy.java` 中引入子主题匹配加权（+25分），并重归一总分为 100 分
- [x] T014 在编排层 `ScoreItemCaseRecommendAppService.java` 中传递推断的子主题，保持纯函数式调用
- [x] T015 编写/扩展 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseMatchPolicyTest.java` 及 `CaseSubTopicPolicyTest.java` 单元测试，覆盖互斥门禁、专属客户、子主题匹配

## Phase 4: 端到端验证与门禁交付

- [x] T016 针对典型评分项（三峡项目、建行项目、接口对接项、售后保障项）进行测试与结果断言
- [x] T017 运行本地门禁 `bash scripts/pre-push-gate.sh`，确保通过架构测试、Flyway 校验与代码规范门禁
