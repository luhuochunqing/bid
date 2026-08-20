# Tasks: 案例沉淀流式化与 LLM 摘录化

**Input**: Design documents from `/specs/049-case-precipitation-streaming/`（spec.md + plan.md）

**Prerequisites**: plan.md (required), spec.md (required)

**Organization**: Tasks grouped by user story. 无 DB 迁移、无前端改动。

## Phase 1: 前置组件重载（Blocking Prerequisite）

**Purpose**: `ScoreDocExcerptExtractor` 增加 dim/detail 纯静态重载，供 casework 摘录复用（向后兼容，`ScoreItemAssessor` 原调用不动）

- [ ] T001 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractor.java` 新增 `extractRelevantExcerpt(String fullText, String dim, String detail, int maxChars)` 重载，复用现有关键词段落打分逻辑
- [ ] T002 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractorOverloadTest.java` 验证重载与原签名行为一致

## Phase 2: 流式加载接入（US1/US2 — 解 OOM）

**Purpose**: `ProjectClosedEventListener` 读取 OBS 标书改为流式加载 + 事务边界

- [ ] T003 修改 `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`：注入 `StreamingBidFileFetcher`、`SidecarStreamTextExtractor`、`TempBidFileStore`
- [ ] T004 改造 `loadBidFileMarkdown`：OBS 路径走 `streamingFetcher.fetch` 二态分派（InMemory→docTextExtractor 快路径 / OnDisk→streamTextExtractor 流式转换），finally 对 OnDisk `tempFileStore.release`；本地路径保持现状
- [ ] T005 事务边界：`onProjectClosed` 去掉方法级 `@Transactional`，注入 `TransactionTemplate`，仅 `knowledgeCaseRepository.saveAll` 与幂等校验包事务
- [ ] T006 新增 `backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerStreamingTest.java`：InMemory 快路径 / OnDisk 流式 / finally release / OBS 失败 rethrow / 事务只包落库

## Phase 3: CaseAiMatcher 摘录化（US3 — 解 LLM 上下文超限）

**Purpose**: 案例匹配改为按评分项摘录逐项调用 LLM

- [ ] T007 修改 `backend/src/main/java/com/xiyu/bid/casework/application/CaseAiMatcher.java`：`extractSlicesWithAi` 重构为逐评分项循环——`extractRelevantExcerpt(markdown, title, ruleText, 12000)` 取摘录 → 单条 prompt 调用 LLM → 解析单条 slice
- [ ] T008 保留现有语义：AI Provider 未配置报错、JSON 非数组/解析失败降级、`extractCategory` 归类、诚实降级不伪造
- [ ] T009 新增 `backend/src/test/java/com/xiyu/bid/casework/application/CaseAiMatcherTest.java`：prompt 只含摘录不含全文 / 调用次数=评分项数 / 非 JSON 降级 / 无关键词命中兜底不伪造

## Phase 4: 回归与端到端验收（US1-US4）

**Purpose**: 全量回归 + 测试环境 1350 验收

- [ ] T010 `cd backend && mvn -q test` 全量回归（重点 scoreparse/casework 包，复用组件测试不受影响）
- [ ] T011 测试环境部署（remote-deploy 流程）
- [ ] T012 项目 1350 触发【AI自动生成案例】端到端验收：SC-001（入库>0）/ SC-002（无 OOM）/ SC-003（<10min）/ SC-004（无 30MB 上限拦截）/ SC-005（失败语义可区分）

## Phase 5: 收尾

**Purpose**: 知识沉淀 + wiki 回填 + PR

- [ ] T013 知识沉淀：`docs/lessons/lessons-learned.md` 追加案例沉淀超大标书攻坚案例 + `docs/implementation-notes/` 更新交接文档为已完成
- [ ] T014 `.wiki/pages/` 回填（case-precipitation / docinsight 相关页）
- [ ] T015 提交 PR（含 spec/049 全部文档），过 wiki-health-check 门禁

**验收定义**：T012 通过（SC-001 为核心）即为关闭，对应交接文档"成功生成案例、入库 >0"。
