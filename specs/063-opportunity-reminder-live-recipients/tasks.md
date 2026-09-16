# Tasks: 商机预测实时收件人与历史补发

**Input**: Design documents from `/specs/063-opportunity-reminder-live-recipients/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/operations.md, quickstart.md

**Tests**: 严格执行 Red → Green → Refactor；生产行为先写测试并确认失败，再写最小实现。

## Phase 1: Setup

- [x] T001 固化根因、测试环境基线、生产禁区和文件范围到 `.omc/entry-gate-20260916-opportunity-history-replay.md`
- [x] T002 [P] 将“完整独立补发、启动预演、09:00 执行、项目级幂等”写入 `specs/063-opportunity-reminder-live-recipients/spec.md`、`research.md`、`data-model.md`、`contracts/operations.md`、`plan.md` 和 `quickstart.md`

---

## Phase 2: Foundational - 实时收件人事实

- [x] T003 [P] 在 `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java` 添加对象型活动角色保真与停用角色过滤测试
- [x] T004 [P] 在 `backend/src/test/java/com/xiyu/bid/workbench/domain/OpportunityPredictionRecipientPolicyTest.java` 添加实时在职状态、目标多角色和非目标角色测试
- [x] T005 在 `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryJsonMapper.java` 保留活动 roleCode/roleName 并过滤停用角色
- [x] T006 在 `backend/src/main/java/com/xiyu/bid/workbench/domain/OpportunityPredictionRecipientPolicy.java` 实现无框架依赖的 fail-closed 状态与角色规则

**Checkpoint**: OSS 实时响应可无歧义地产生“当前在职 + 当前目标角色”事实。

---

## Phase 3: User Story 1 - 实时解析正确收件人 (Priority: P1) 🎯 MVP

**Goal**: 日常扫描只使用 OSS 实时状态和多角色事实解析经理及核心角色。

**Independent Test**: 本地目标角色缓存不参与最终判断；OSS 返回决定项目经理与目标角色是否接收，同一用户只出现一次。

- [x] T007 [US1] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionRecipientResolverTest.java` 覆盖全量工号单次批量查询、在职经理、离职经理和角色去重
- [x] T008 [US1] 在 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionRecipientResolver.java` 实现本地身份枚举、OSS 批量查询和不可变收件人快照
- [x] T009 [US1] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderServiceTest.java` 证明正常扫描使用实时快照且保留原预测、日志和事件语义
- [x] T010 [US1] 在 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderService.java` 接入实时快照并按项目经理去重
- [x] T011 [US1] 运行 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 1 节已有实时收件人测试并保留绿灯证据

**Checkpoint**: 正常提醒收件人修复可独立工作，历史补发只复用该快照。

---

## Phase 4: User Story 2 - 补发上线日前历史项目 (Priority: P1)

**Goal**: 对执行日前已有提醒记录的到期项目，向当前完整实时接收人创建一条独立、可识别、可重跑的补发通知。

**Independent Test**: 固定 `runDate/cutoff`，旧提醒是否曾包含经理或角色均不影响补发；未来项目、执行日新日志和已补发项目不写入，原提醒日志与事件状态不变。

- [x] T012 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/workbench/domain/OpportunityPredictionAlertMessageTest.java` 先添加历史补发标题、正文和空招标主体红灯测试
- [x] T013 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionHistoryReplayServiceTest.java` 先添加候选边界、完整去重收件人、preview 零写、已补发跳过、缺失项目/无收件人及单项目失败隔离红灯测试
- [x] T014 [US2] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderTransactionBoundaryTest.java` 先添加独立来源通知的事务创建、重复跳过和失败回滚红灯测试
- [x] T015 [US2] 在 `backend/src/main/java/com/xiyu/bid/workbench/domain/OpportunityPredictionAlertMessage.java` 与 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderSupport.java` 实现用户可见历史标识和带 replay 元数据的纯 payload 组装
- [x] T016 [US2] 在 `backend/src/main/java/com/xiyu/bid/workbench/repository/OpportunityPredictionReminderLogRepository.java`、`backend/src/main/java/com/xiyu/bid/notification/repository/NotificationRepository.java` 和 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderTransactionService.java` 实现候选查询、持久幂等查询及 REQUIRES_NEW 创建
- [x] T017 [US2] 在 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionHistoryReplayService.java` 实现一次快照、项目批量加载、preview/execute 共用计划、逐项目异常隔离和精确计数
- [x] T018 [US2] 运行 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 1 节服务与事务测试，确认先红后绿且正常扫描回归通过

**Checkpoint**: User Story 2 可由显式调用安全预演或执行，不依赖调度器即可独立验证。

---

## Phase 5: User Story 3 - 安全审计并防止重复补发 (Priority: P2)

**Goal**: 一次性任务默认关闭；启动永远预演；仅指定日期 09:00 的 execute 模式允许写入。

**Independent Test**: enabled=false、缺日期、cutoff 晚于 runDate、非执行日、启动事件均不写入；仅合法 09:00 调度调用 EXECUTE，重复执行新增为 0。

- [x] T019 [US3] 在 `backend/src/test/java/com/xiyu/bid/workbench/job/OpportunityPredictionHistoryReplayTaskTest.java` 先添加默认关闭、启动强制 preview、配置无效、日期不符和合法 execute 红灯测试
- [x] T020 [US3] 在 `backend/src/main/java/com/xiyu/bid/workbench/job/OpportunityPredictionHistoryReplayProperties.java` 与 `backend/src/main/resources/application.yml` 添加默认关闭的四项部署配置和 fail-closed 校验
- [x] T021 [US3] 在 `backend/src/main/java/com/xiyu/bid/workbench/job/OpportunityPredictionHistoryReplayTask.java` 实现 `ApplicationReadyEvent` 预演、09:00 薄调度、业务时区比较和异常日志
- [x] T022 [US3] 运行 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 1 节调度测试并确认启动/日期/模式安全门禁全绿

**Checkpoint**: 只有一个明确日期的 09:00 调度可执行写入，重启不会补执行。

---

## Phase 6: Test Deployment & Cross-Cutting Verification

- [x] T023 运行 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 2 节架构、完整后端、前端构建和 pre-push 门禁并记录 pass/fail/skip
- [x] T024 执行 `xiyu-server-deploy` 测试环境 SOP，将当前提交部署到 `172.16.38.78`，按 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 3 节注入测试专用配置并核对启动 preview 为 274 个历史候选
- [x] T025 在测试环境 09:00 后按 `specs/063-opportunity-reminder-live-recipients/quickstart.md` 第 4 节核对通知、接收关系、企微任务、旧 reminder log/event 不变及重跑新增为 0
- [x] T026 在 `docs/lessons/lessons-learned.md` 追加“提醒日志和站内关系不等于外部送达”的根因、防复发规则和一次性补发边界，并按 `.wiki/WIKI.md` 完成 checkpoint
- [x] T027 对当前分支执行 `speckit-analyze`、代码审查和生产风险审查；只提交/推送测试验证结果，不创建或执行任何生产部署

**完成证据**：见 `docs/release/deploy-report-2026-09-16-184th-test.md`。完整后端套件的未改动模块基线失败、企微 662 条绑定缺失死信及生产 HOLD 结论均已如实记录；生产环境未访问。

---

## Dependencies & Execution Order

- T001-T011 已完成，是本轮历史补发的事实与实时收件人基础。
- T012-T014 必须先产生可解释红灯；T015-T017 才能进入 Green；T018 关闭 User Story 2。
- T019 必须先红；T020-T021 完成安全触发；T022 关闭 User Story 3。
- T023 全绿或如实记录已知基线失败后才能 T024；启动 preview 与 274 基线不一致时禁止等待 T025 自动执行。
- T025 完成后才能关闭测试配置并进入 T026-T027 收尾；生产动作始终不在本任务授权内。

## Parallel Opportunities

- T012 与 T013 修改不同测试文件，可并行准备，但本次按单 Agent 顺序执行以保留清晰红灯证据。
- T026 的知识沉淀必须等待运行证据，不与实现并行。
- 其余任务共享事务接口或部署状态，顺序执行以避免假阳性和基线漂移。

## Implementation Strategy

1. 保持 User Story 1 代码不动，以现有实时收件人快照作为唯一人员事实。
2. 先完成 User Story 2 的纯服务和项目级幂等，再完成 User Story 3 的一次性调度安全壳。
3. 不新增脚本、管理端点、表或迁移；历史补发只走应用通知事务与既有企微异步链路。
4. 采用当前会话内联执行，不启动并行子 Agent；每个红绿闭环后立即运行定向测试。
5. 仅部署测试环境并观察 09:00 真实运行；生产截止日和执行必须重新授权。
