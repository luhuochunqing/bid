# Feature Specification: 商机预测提醒 (054)

## 业务背景与问题定义

工作台的"商机预测"已实现"预测下一轮招标时间"的四条规则（见 `OpportunityPredictionPolicy.predict`），
但当前仅提供列表数据（`WorkbenchOpportunityController` 注释明确"提醒通知暂未接入"）。
预测结果若无人跟进，会在服务周期截止前错过新一轮招标机会。

本需求补齐"提醒"链路：每日扫描预测结果，对已到"预测招标时间"的项目，
按角色通知相关人员去 CRM 创建商机。

## 用户故事与使用场景

### User Story 1: 到点提醒去创建商机 (P1)
**作为** 项目经理 / 投标管理员 / 投标组长，
**我希望** 当某个项目已到"预测招标时间"（如央企服务周期截止前 6 个月）时，系统主动提醒我，
**以便于** 及时在 CRM 创建下一轮商机，不错过招标窗口。

### User Story 2: 多渠道触达 (P1)
**作为** 上述三类角色，
**我希望** 提醒同时到达企微消息、站内信通知中心、CRM 事件库，
**以便于** 无论我在哪个入口都能第一时间看到并处理。

## 功能需求 (Functional Requirements)

- **FR-01 (预测时机复用)**：提醒触发时间复用既有 `OpportunityPredictionPolicy.predict()` 的四条规则结果，
  不做重复实现：流标→开标+2 周；有截止时间→截止-6/3 个月（央企 6/其他 3）；无截止有周期→开标+周期年；都无→开标+1 年。
- **FR-02 (触发条件)**：`今天 >= 预测招标时间` 且该项目从未提醒过，则触发提醒。
- **FR-03 (每项目只提醒一次)**：发送成功后写入 `opportunity_prediction_reminder_log`（`project_id` 唯一），永不重复提醒。
- **FR-04 (通知角色)**：接收人 = 招标主体项目负责人（`Project.managerId`，即自动分配功能落库的 CRM 查得项目经理，按启用状态过滤）+ 投标管理员（`/bidAdmin`）+ 投标组长（`bid-TeamLeader`），合并去重。
- **FR-05 (通知文案)**：`【商机预测】{招标主体名称} 服务周期将于 {到期日期} 截止，预测下次招标时间为 {预测招标时间}，请到CRM创建商机。`；
  无服务周期截止日期时省略"服务周期将于…截止"句。招标主体名称优先 `Tender.purchaserName`，缺失回退 `Project.customer`。
- **FR-06 (渠道)**：站内信（含企微自动镜像，按工号推送，无工号自动降级仅站内信）+ CRM 事件库（`/eventbus/publishEvent`）。
- **FR-07 (定时调度)**：每日 09:00 扫描一次（`@Scheduled`，参考 `WarehouseExpiryScanTask`）。
- **FR-08 (深链)**：通知 payload 携带 `targetUrl=/bidding/customer-opportunities`，点通知直达商机中心。

## 非功能需求 (NFR)

- **NFR-01 (单项目隔离)**：单项目通知失败不影响整个扫描（try-catch 隔离）。
- **NFR-02 (无 N+1)**：项目 / 结果 / 标讯 / 用户均批量加载。
- **NFR-03 (纯核心可测)**：预测、提醒判定、文案模板为纯函数，无 Spring/IO 依赖，可独立单测。
- **NFR-04 (事件发布不阻断)**：事件库发送失败只记日志，不影响站内信/企微主链路。

## 边界与权衡

- 定时任务无登录态，**不做 CRM 实时反查**（`CrmAuthService` 禁止系统服务号换 JWT）。
  项目经理取"自动分配"已落库的 `Project.managerId`，即 CRM 查得结果的本地落库形态。
- 事件库开关 `xiyu.integrations.tender-event.sdk.enabled` 默认 false，需配置为 true 才生效。
- 企微按工号推送，无工号用户只收站内信（既有机制，非本需求新增）。

## 验收条件与成功指标

1. **规则正确**：四条预测规则 + 提醒判定（`today >= predicted` 且未提醒过）单元测试全绿；
2. **编排链路**：命中项目 → 通知（站内信 + 企微镜像）→ 落 `opportunity_prediction_reminder_log` → 发事件库，编排测试全绿；
3. **去重生效**：同一项目扫描两次，第二次不再提醒、不再落日志、不再发事件；
4. **无回归**：后端全量测试通过（含既有 `OpportunityPredictionPolicyTest`）。
