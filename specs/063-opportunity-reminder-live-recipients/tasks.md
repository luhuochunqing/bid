# Tasks: 商机预测实时收件人与历史补发

**Input**: Design documents from `/specs/063-opportunity-reminder-live-recipients/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/operations.md

**Tests**: 本缺陷严格使用 Red → Green → Refactor；每个生产行为先有可观察的失败测试。

## Phase 1: Setup

- [x] T001 固化生产根因、范围与文件清单到 `.omc/entry-gate-01a0a415-2991-7373-b393-556c6e7648d2.md`
- [x] T002 [P] 完成需求与设计资料 `specs/063-opportunity-reminder-live-recipients/spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/operations.md`、`quickstart.md`

---

## Phase 2: Foundational

- [x] T003 [P] 在 `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java` 添加对象型活动角色保真与停用角色过滤红灯测试
- [x] T004 [P] 在 `backend/src/test/java/com/xiyu/bid/workbench/domain/OpportunityPredictionRecipientPolicyTest.java` 添加实时在职状态、目标多角色、非目标角色红灯测试
- [x] T005 在 `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryJsonMapper.java` 最小修复活动角色文本映射并保持字符串响应兼容
- [x] T006 在 `backend/src/main/java/com/xiyu/bid/workbench/domain/OpportunityPredictionRecipientPolicy.java` 实现无框架依赖的 fail-closed 状态与角色规则

**Checkpoint**: OSS 实时响应能无歧义地产生“当前在职 + 当前目标角色”值。

---

## Phase 3: User Story 1 - 实时解析正确收件人 (Priority: P1) 🎯 MVP

**Goal**: 日常扫描只使用 OSS 实时状态和多角色事实解析经理及核心角色。

**Independent Test**: 本地目标角色为 0 且本地经理 enabled 过期时，OSS 返回决定最终收件人；同一用户只出现一次。

- [x] T007 [US1] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionRecipientResolverTest.java` 添加全量工号单次批量查询、在职经理、离职经理、角色去重与部分响应红灯测试
- [x] T008 [US1] 在 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionRecipientResolver.java` 实现本地身份枚举、OSS 批量查询和不可变收件人快照
- [x] T009 [US1] 在 `backend/src/test/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderServiceTest.java` 将扫描测试改为实时快照并证明不再调用本地角色 resolver
- [x] T010 [US1] 在 `backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderService.java` 替换本地角色和 enabled 经理解析，保留现有预测/日志/事件分支
- [x] T011 [US1] 运行 `cd backend && mvn -q -Dtest=OpportunityPredictionRecipientPolicyTest,OpportunityPredictionRecipientResolverTest,OpportunityPredictionReminderServiceTest,OrganizationDirectoryHttpGatewayTest test` 并记录绿灯

**Checkpoint**: User Story 1 可独立发布；未来日常提醒不再依赖错误单角色缓存。

---

## Phase 4: User Story 2 - 补发上线日前历史项目 (Priority: P1)

**Goal**: 从未提醒项目走完整现有链路，已有经理提醒项目只补缺失核心角色。

**Independent Test**: 固定截止日下，128 类项目生成完整提醒，114 类项目不重复经理通知，未来项目不处理。

- [ ] T012 [US2] 在 `scripts/operations/backfill-opportunity-prediction-reminders.py` 先实现只读 dry-run，输出候选、已有/缺失关系、预计新增计数并对基线设保护阈值
- [ ] T013 [US2] 为 `scripts/operations/backfill-opportunity-prediction-reminders.py` 增加显式 execute、逐项目调用现有通知 API、执行前再次查重和中断可重跑语义
- [ ] T014 [US2] 若无现有调用设施，在临时中间提交添加 `/api/admin/**` 手动 `runScan(cutoff)` 入口及测试；生产调用后删除该入口，最终源码不得保留
- [ ] T015 [US2] 在测试/受控环境执行 `scripts/operations/backfill-opportunity-prediction-reminders.py --dry-run`，证明无数据库写入并验证重复 execute 的预计新增数归零

**Checkpoint**: 历史补发路径可在生产先预览、再执行、再复核，并且不重置日志。

---

## Phase 5: User Story 3 - 安全审计并防止重复补发 (Priority: P2)

**Goal**: 生产操作有备份、确认、前后计数和可回滚边界。

**Independent Test**: dry-run 与 execute 使用同一截止日；第二次 dry-run 为零；站内和企微结果分别可核对。

- [ ] T016 [US3] 在 `specs/063-opportunity-reminder-live-recipients/contracts/operations.md` 补齐生产实际命令、确认短语、备份标识和回滚边界
- [ ] T017 [US3] 执行生产 dry-run 并把实际用户数、128/114 或差异解释、预计新增关系数记录到部署报告
- [ ] T018 [US3] 在生产显式 execute 后复核 reminder log、notification、user_notification、delivery task、outbound log 与应用错误日志，再重复 dry-run 验证新增为 0

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T019 [P] 在 `docs/lessons/lessons-learned.md` 追加“单角色缓存不是 OSS 多角色通知事实源”的根因与防复发规则
- [ ] T020 [P] 更新 `.wiki/pages/opportunity-prediction-reminder.md` 的实时接收人口径、失败关闭和历史补发边界
- [ ] T021 运行 ai-slop-cleaner Mode B，删除临时入口、重复 helper、无用抽象与一次性残留并保持定向测试全绿
- [ ] T022 运行 `cd backend && mvn -q -Dtest=ArchitectureTest test && mvn test`、根目录 `npm run build` 与 `bash scripts/pre-push-gate.sh`
- [ ] T023 完成 `speckit-analyze`、health、google-code-review、code-review、interrogate 与 production-risk-review，并将结论写入 `.omc/verify-01a0a415-2991-7373-b393-556c6e7648d2.md` 和 `.omc/review-01a0a415-2991-7373-b393-556c6e7648d2.md`

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → User Story 1 → User Story 2 → User Story 3 → Polish。
- T003 与 T004 可并行写红灯，但 T005/T006 各自必须在对应红灯确认后开始。
- User Story 2 依赖 User Story 1 的实时角色快照；角色补发脚本不改变日常扫描实现。
- T014 仅当现有生产设施无法调用 `runScan` 时执行，而且必须在 T021 前删除。
- 生产 T017/T018 依赖完整测试、评审、备份与生产风险审查通过。

## Parallel Opportunities

- T003 与 T004 位于不同测试包，可并行准备。
- T019 与 T020 是不同知识载体，可在实现稳定后并行更新。
- 其余任务共享收件人语义或生产状态，按顺序执行以避免基线漂移。

## Implementation Strategy

1. MVP 只完成 T003-T011，先证明日常提醒接收人正确。
2. 在 MVP 全绿后编写一次性 dry-run/execute 脚本，不把历史补发塞进每日任务。
3. 生产先发布稳定修复，再核对 dry-run；任何计数偏差停止 execute。
4. 执行历史补发、复核幂等、删除临时入口，最终保留最小稳定代码与审计记录。
