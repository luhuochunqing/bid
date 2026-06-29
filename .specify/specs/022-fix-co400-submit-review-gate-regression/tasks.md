# Tasks: 修复 CO-400 submitForReview 误用任务完成闸门

**Spec**: `022-fix-co400-submit-review-gate-regression`
**Created**: 2026-06-29

## T1: 拆分 BidReadinessPolicy

- [ ] T1.1 新增 `checkBidDocumentUploaded(boolean hasBidDocument)` 静态方法（仅校验标书文件已上传）
- [ ] T1.2 重命名 `check` 为 `checkBidSubmissionReady(List<TaskState>, boolean hasBidDocument)`（保留任务全完成 + 标书文件校验）
- [ ] T1.3 修正类注释 L14-15，去掉"两处业务入口复用闸门"误导性措辞，改为说明两个方法的业务语义区别
- [ ] T1.4 保留 `BID_DOCUMENT_CATEGORY` 常量不变

**依赖**：无
**验证**：`mvn test -Dtest=BidReadinessPolicyTest`（待 T3 新增测试后全绿）

## T2: 修改 ProjectDraftingService 调用点

- [ ] T2.1 `submitForReview` L118 改用 `checkBidDocumentUploaded`（移除任务完成闸门）
- [ ] T2.2 `submitBid` L171 保持调用 `checkBidSubmissionReady`（保留 zhoufan 修复语义，不回退）
- [ ] T2.3 拆分 `assertBidReadiness` 私有方法：新增 `assertBidDocumentUploaded` 或参数化现有方法
- [ ] T2.4 验证 `submitForReview` 仍保留角色校验 + 项目访问校验 + BidReviewPolicy 状态校验

**依赖**：T1 完成
**验证**：`mvn test -Dtest=ProjectDraftingServiceTest`（待 T3 新增测试后全绿）

## T3: 防复发测试

- [ ] T3.1 新增 `BidReadinessPolicyTest.checkBidDocumentUploaded_hasDocument_returnsAllow`
- [ ] T3.2 新增 `BidReadinessPolicyTest.checkBidDocumentUploaded_noDocument_returnsDeny`
- [ ] T3.3 新增 `BidReadinessPolicyTest.checkBidSubmissionReady_allCompletedAndHasDocument_returnsAllow`
- [ ] T3.4 新增 `BidReadinessPolicyTest.checkBidSubmissionReady_incompleteTasks_returnsDeny`
- [ ] T3.5 新增 `ProjectDraftingServiceTest.submitForReview_allowsIncompleteTasks`（覆盖本次复发路径）
- [ ] T3.6 新增 `ProjectDraftingServiceTest.submitForReview_missingBidDocument_returns409`（保留标书文件校验反例）
- [ ] T3.7 检查 `submitBid_approvedReview_allowsIncompleteTasks` 是否存在；如已被回退则恢复
- [ ] T3.8 验证 `submitBid_incompleteTasks_returns409` 仍存在（保留 zhoufan 修复语义）

**依赖**：T1、T2 完成
**验证**：`mvn test -Dtest=BidReadinessPolicyTest,ProjectDraftingServiceTest`

## T4: 架构门禁验证

- [ ] T4.1 `mvn test -Dtest=ArchitectureTest` 全绿
- [ ] T4.2 `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest` 全绿
- [ ] T4.3 `mvn test -Dtest=AllTasksCompletedPolicyTest,BidReviewPolicyTest` 全绿（防回归）

**依赖**：T1-T3 完成
**验证**：上述命令全绿

## T5: 文档同步

- [ ] T5.1 在 `docs/lessons/lessons-learned.md` 新增 §25「submitBid / submitForReview 闸门不可共用」教训条目
- [ ] T5.2 新建 `docs/lessons/root-cause-analysis-co-400-submit-review-gate-regression.md`（参考 `root-cause-analysis-submit-bid-review-gate.md` 格式）
- [ ] T5.3 在 lessons-learned §25 列出后续优化项（CANCELLED 枚举、立项种子任务残留）

**依赖**：可并行
**验证**：人工 review

## T6: 提交 + PR

- [ ] T6.1 原子提交：拆分 Policy + 修改 Service + 测试（一个 commit）
- [ ] T6.2 原子提交：lessons-learned 教训条目 + 根因分析文档（一个 commit）
- [ ] T6.3 推送到 `origin agent/codex/fix-co400-submit-review-gate-regression`
- [ ] T6.4 在 Gitee 创建 PR，PR 描述引用本次根因分析报告
- [ ] T6.5 在 PR 描述中明确说明：本修复仅处理 `submitForReview` 路径，`submitBid` 保留现有行为（避免再次反复回退）

**依赖**：T1-T5 完成
**验证**：PR CI 全绿
