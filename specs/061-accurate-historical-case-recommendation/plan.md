# Implementation Plan: 基于真实数据底座的历史案例精准推荐

**Branch**: `agent/antigravity/061-accurate-historical-case-recommendation` | **Date**: 2026-09-05 | **Spec**: [spec.md](file:///Users/user/xiyu/xiyu-bid-poc/specs/061-accurate-historical-case-recommendation/spec.md)

**Input**: Feature specification from `specs/061-accurate-historical-case-recommendation/spec.md`

## Summary

围绕第一性原理“在 AI 智能案例推荐抽屉中准确推荐相似历史案例”，基于服务器 OBS 上 70 个历史项目的真实数据底座（8144+ 章节切片），采用“先抓特征抽取与清洗流水线，再做模型匹配与策略加固”的实施路径：
1. 编写 `scripts/extract-historical-case-features.py`，从离线文件与切片中自动化提取归一化企业专名、10 大招投标核心业务子主题（Sub-Topic）、内容质量评分，并过滤低信噪比样板段落；
2. 数据库扩展 `knowledge_case.sub_topic` 与 `knowledge_case.quality_score`（Flyway V1211），并将案例库有效容量从 300 条样本扩充到 1000+ 条；
3. 加固 `ScoreItemCaseMatchPolicy` 纯核心策略，引入子主题强契合加权（+25分），配合大类硬互斥门禁与专属客户断层优先，消除 2-gram 碎词与跨大类误推；
4. 验证前台抽屉（`AiRecommendDrawer.vue`）端到端推荐准确度与原版 docx 一键下载体验。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.2), Python 3.10+, Vue 3 + Vite 5
**Primary Dependencies**: JPA/Hibernate, Jackson, Element Plus
**Storage**: MySQL 8.0 (表 `knowledge_case`, `projects`, `tenders`)
**Testing**: JUnit 5, Mockito, Python unittest/assert
**Target Platform**: Linux server (x86_64, CentOS/EulerOS, 西域测试环境 172.16.38.78)
**Project Type**: Web service + Offline data pipeline
**Performance Goals**: 单次推荐接口响应时间 P95 $< 300$ms（纯内存算分，无外部慢调用）
**Constraints**:
- 严格遵循 FP-Java 契约：纯核心策略类不依赖 Spring、入参对象不可变、无副作用；
- 真实数据唯一源，严禁 Mock；
- 数据库脚本遵循 Flyway 严格自增与 U 回滚配对规范；
- 单文件行预算遵守代码治理规范（纯策略类 $< 300$ 行）。

## Constitution Check

- [x] FP-Java 契约：`ScoreItemCaseMatchPolicy` 为纯函数，计算中间态与结果均通过不可变 Record / DTO 传递
- [x] 禁止 Mock：直接基于真实的 `knowledge_case` 表与 70 个离线历史项目数据
- [x] 数据库迁移自增与安全：新增 `V1211__enhance_knowledge_case_sub_topic.sql` 及配对 `U1211`，无锁增列，允许 NULL
- [x] 权限与安全：推荐接口沿用 `ProjectAccessScopeService.assertCurrentUserCanAccessProject`
- [x] 门禁约束：通过 `pre-push-gate.sh`，单元测试全绿，代码行预算守卫通过

## Project Structure

### Documentation (this feature)

```text
specs/061-accurate-historical-case-recommendation/
├── spec.md              # 业务规格与第一性原理定义
├── plan.md              # 本实现计划文档
├── data-model.md        # 数据库与领域模型改动
├── tasks.md             # 任务拆解与推进清单
└── checklists/
    └── requirements.md  # 规格质量校验清单
```

### Source Code Changes

```text
scripts/
├── extract-historical-case-features.py       # [NEW] 70个项目特征提取与清洗流水线脚本
└── import-case-slices-to-knowledge-case.py   # [MODIFY] 支持 sub_topic 与 quality_score 入库

backend/src/main/resources/db/migration-mysql/
└── V1211__add_sub_topic_to_knowledge_case.sql          # [NEW] knowledge_case 扩充字段

backend/src/main/resources/db/rollback/migration-mysql/
└── U1211__add_sub_topic_to_knowledge_case.sql          # [NEW] 回滚脚本

backend/src/main/java/com/xiyu/bid/casework/
├── infrastructure/
│   └── KnowledgeCase.java                                # [MODIFY] JPA 映射 subTopic / qualityScore
├── domain/model/
│   └── KnowledgeCaseReadModel.java                       # [MODIFY] 只读模型契约扩展
├── domain/policy/
│   ├── CaseSubTopicPolicy.java                           # [NEW] 业务子主题推断与匹配纯核心策略
│   └── ScoreItemCaseMatchPolicy.java                     # [MODIFY] 接入子主题加权与分值归一
└── application/service/
    └── ScoreItemCaseRecommendAppService.java             # [MODIFY] 编排层传递推断子主题

src/views/Project/stages/components/
└── AiRecommendDrawer.vue                                 # [VERIFY] 抽屉组件端到端验证
```

## Phase Breakdown

### Phase 1: 特征抽取与数据清洗流水线（优先级最高）
- 编写 `scripts/extract-historical-case-features.py`；
- 实现核心纯函数：企业专名抽取、10 大子主题规则分类器、质量评分计算器；
- 添加完整 `--self-test` 断言测试集，确保抽取逻辑稳定可靠；
- 跑离线测试，验证过滤效果与子主题覆盖率。

### Phase 2: 数据库迁移与全量数据入库
- 编写 `V1211__add_sub_topic_to_knowledge_case.sql` 及配对 `U1211`；
- 更新 `KnowledgeCase.java` 实体；
- 扩展 `scripts/import-case-slices-to-knowledge-case.py`，支持将清洗后的特征与子主题批量写入数据库；
- 运行测试环境入库，将有效案例扩充至 1000+ 条。

### Phase 3: 匹配算法策略 Pure Core 加固
- 实现纯核心类 `CaseSubTopicPolicy.java`，负责根据评分项标题和正文推断子主题，并计算匹配加分；
- 更新 `ScoreItemCaseMatchPolicy.java`，整合子主题匹配得分（+25分），重置各维度满分为 100 分；
- 编写完善的单元测试 `ScoreItemCaseMatchPolicyTest.java`，覆盖大类拦截、三峡/建行专属客户、子主题匹配。

### Phase 4: 端到端验证与交付
- 在测试环境回放典型项目真实案例推荐；
- 验证前台抽屉展示的关键词、推荐理由、正文预览及原文下载；
- 执行 `pre-push-gate.sh` 本地验证门禁。
