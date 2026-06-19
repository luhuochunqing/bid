# CRM 外部系统对接经验教训

> 来源：2026-06-16 CRM 商机查询反复“无数据”事故复盘
> 适用范围：所有与西域 CRM、招标平台等外部系统的集成开发

## 1. 外部系统集成必须先用真实数据验证语义

- 不能只看接口字段名（如 `evaluationTime` vs `报名截止时间`），必须在真实 CRM 上跑一遍，确认字段含义、取值范围、数据质量。
- 匹配策略（精确 / 集团 / 全量）必须在真实数据上验证召回率和准确率后再定默认策略。

## 2. 测试要断言用户结果，不要断言实现方式

- 前端单测不应断言“必须调用 `search-by-tender`”，这会锁定错误策略。
- 应断言：**给定招标主体，弹窗中必须出现可选择的商机**；至于调用哪个接口，应由实现决定。
- 后端单测应断言：给定策略 +  tenderer，返回列表符合预期；而不是断言内部调用了几次 CRM。

## 3. 兜底策略要显式，不要静默 fallback

- 当精确匹配为空、自动切换到集团或全量查询时，必须在 UI 上明确提示用户：
  > “精确匹配为空，已按集团/全部商机展示。”
- 静默 fallback 会让用户误以为返回的就是“最相关”结果，导致选择错误商机。

## 4. 探针脚本必须进流水线

- `scripts/crm-opportunity-probe.mjs` 必须作为部署后 smoke 的一环执行，空结果直接阻断发布（NO-GO）。
- 探针不能只是本地手动跑一次，要配置环境变量 `PROBE_*` 并接入 `scripts/release/run-prod-smoke.mjs`。

## 5. 高风险匹配策略需要 Feature Flag

- 通过 `app.crm.matching-strategy`（`EXACT` / `GROUP` / `ALL`）控制查询策略，支持环境变量覆盖。
- 下次 CRM 字段或数据再变，应能秒切策略并回滚，无需改代码再部署。

## 6. UI 修复和业务逻辑修复要拆 PR

- 弹窗布局优化（UI）和 CRM 查询策略修复（业务逻辑）应分别提 PR：
  - `agent/kimi/fix-crm-selector-ui`
  - `agent/kimi/fix-crm-query-strategy`
- 混在一个 PR 会导致回滚和 review 成本翻倍。

## 7. 后端集成必须记录完整请求/响应体

- `CrmChanceService` 必须记录发给 CRM 的完整 body、响应状态码、返回条数。
- 生产问题排查时，应能直接从日志判断是：参数格式问题、匹配条件问题，还是 CRM 本身无数据。

## 8. 跨系统日期要定义统一格式和时区契约

- 前端使用 ISO 字符串、后端使用 `yyyy-MM-dd HH:mm:ss`、CRM 只接受日期精度，三方必须显式约定：
  - 传输格式：ISO 8601 with offset
  - 存储格式：`yyyy-MM-dd HH:mm:ss`（本地时间，以业务发生时区为准）
  - 外部系统适配：在后端统一转换为 CRM 要求的精度，禁止前端传多种格式让后端猜测
- 该契约应写入本文档和项目记忆，所有外部系统对接复用。

## 相关文档

- [crm-field-mapping.md](./crm-field-mapping.md) — 字段映射与查询策略说明
- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — CRM 接口规范

---

## 9. CRM 商机字段映射错位导致评估表数据错乱（CO-262 / PR795）

> 来源：2026-06-18 CO-262 CRM 商机关联投标评估表字段映射错误事故复盘（PR795）
> 适用范围：所有 CRM 商机字段映射到投标评估表的场景

### 事故一句话总结

CRM 商机关联投标评估表时，`useCrmOpportunitySelector.js` 把 CRM 字段映射到错误的评估表字段：风险预判填了商机备注、支持备注为空、投标建议理由填了风险预判、GAP 附件未回填。

### 根因：字段映射逻辑错位

```javascript
// ❌ 错误映射（PR795 修复前）
basic: {
  riskAssessment: chance.remark || '',           // 风险预判 ← 商机备注（错）
  supportNotes: '',                               // 支持备注 ← 空（错）
  projectPlanGap: chance.projectGap || '',
  // projectPlanGapFiles 未回填（错）
},
recommendation: { shouldBid: !chance.backupPlan, reason: chance.riskPrediction || '' }, // 投标建议理由 ← 风险预判（错）

// ✅ 正确映射（PR795 修复后）
basic: {
  riskAssessment: chance.riskPrediction || '',    // 风险预判 ← CRM 风险预判
  supportNotes: chance.remark || '',              // 支持备注 ← CRM 商机备注
  projectPlanGap: chance.projectGap || '',
  projectPlanGapFiles: chance.gapFile ? [{ fileName: 'GAP附件', fileUrl: chance.gapFile }] : [], // GAP 附件回填
},
recommendation: { shouldBid: !chance.backupPlan, reason: '' }, // 投标建议理由留空给用户填
```

**关键认知**：
- CRM 商机字段语义见 `CustomerChanceVO.java`：`riskPrediction`（风险预判）、`remark`（商机备注）、`gapFile`（GAP 附件 URL）
- 评估表字段语义：`riskAssessment`（风险预判）、`supportNotes`（支持备注）、`projectPlanGapFiles`（GAP 附件列表）
- 映射必须**语义对齐**，不能只看字段名是否"看起来相关"

### 字段映射对照表（PR795 修正后）

| 评估表字段 | CRM 商机字段 | 语义 |
|---|---|---|
| `basic.riskAssessment` | `chance.riskPrediction` | 风险预判 |
| `basic.supportNotes` | `chance.remark` | 商机备注/支持备注 |
| `basic.projectPlanGap` | `chance.projectGap` | 项目计划 GAP |
| `basic.projectPlanGapFiles` | `chance.gapFile` | GAP 附件 URL |
| `basic.unfavorableItems` | `chance.bidDocumentDisadvantage` | 招标文件不利项 |
| `basic.contingencyPlan` | `chance.backupPlan` | 兜底方案 |
| `basic.processKnowledge` | `chance.managerUnderstandProcess` | 客户经理是否了解流程 |
| `recommendation.reason` | （留空） | 投标建议理由，由用户手动填写 |

### 手动输入模式同步修复

手动输入模式（非 CRM 选择）也需同步修复字段映射：

```javascript
// ❌ 错误：手动输入模式
riskAssessment: mf.remark || '',           // 风险预判 ← 备注（错）
supportNotes: '',                          // 支持备注 ← 空（错）

// ✅ 正确：手动输入模式
riskAssessment: mf.projectRiskText || '',  // 风险预判 ← 项目风险文本
supportNotes: mf.remark || '',             // 支持备注 ← 备注
projectPlanGapFiles: [],                   // 手动输入无 GAP 附件
```

### 诊断方法

1. 用户反馈"评估表字段填错"或"该填的没填、不该填的填了"
2. 检查 `useCrmOpportunitySelector.js` 的字段映射逻辑
3. 对照 `CustomerChanceVO.java` 确认 CRM 字段语义
4. 对照评估表 DTO 确认目标字段语义
5. 验证映射是否语义对齐

### 通用规则：字段映射必须语义对齐

1. **不能只看字段名**：`remark`（备注）不等于 `riskPrediction`（风险预判）
2. **必须对照源头定义**：CRM 字段语义以 `CustomerChanceVO.java` 为准，不能凭猜测
3. **留空字段要显式**：如 `recommendation.reason` 留空给用户填，必须显式写 `reason: ''`，不能映射到其他字段
4. **附件字段要回填**：`projectPlanGapFiles` 必须从 `chance.gapFile` 回填，不能遗漏

### 测试验证

修复后新增 2 个测试用例到 `useCrmOpportunitySelector.spec.js`（共 6 个测试全部通过）：
- 测试 1：CRM 选择模式字段映射验证（riskAssessment←riskPrediction, supportNotes←remark, projectPlanGapFiles←gapFile, reason 为空）
- 测试 2：手动输入模式字段映射验证（riskAssessment←projectRiskText, supportNotes←remark）

### 排查方法

```bash
# 查找 CRM 商机字段定义
grep -rn "class CustomerChanceVO" backend/src/main/java/

# 查找字段映射逻辑
grep -rn "riskAssessment\|supportNotes\|projectPlanGapFiles" src/views/Bidding/detail/components/

# 查找评估表 DTO 字段定义
grep -rn "record EvaluationBasicDTO" backend/src/main/java/
```

## 相关文档

- [crm-field-mapping.md](./crm-field-mapping.md) — 字段映射与查询策略说明
- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — CRM 接口规范
- [useCrmOpportunitySelector.js](../../src/views/Bidding/detail/components/useCrmOpportunitySelector.js) — 字段映射修复位置
- [CustomerChanceVO.java](../../backend/src/main/java/com/xiyu/bid/crm/infrastructure/dto/CustomerChanceVO.java) — CRM 商机字段定义

## 11. CRM 回传 code:1 —— buildPayload 用 sourceId 填 code 字段（CO-263）

### 事故一句话总结

CRM 回传 HTTP 200 但业务返回 `{"code":"1","msg":null}`（失败），因为 `WebhookEventListener.buildPayload` 把 `external_id` 的 sourceId 部分（来源系统数据唯一 ID）当成商机编号填到了 bidInfoSync 的 `code` 字段，CRM 侧按 code 找不到商机。

### 根因：code 字段填了错误的值

`bidInfoSync` 契约（`crm-field-mapping.md`、`西域CRM商机对接接口.md`）明确 `code` = **商机编号**。但 `buildPayload` 用 `extractSourceId(event.externalId())` 把 `"CRM:241"` → `"241"` 填到 code 字段，而 "241" 是 CRM 推标讯时的 `sourceId`（来源系统数据唯一 ID），**不是商机编号**。

**证据链（DB 直查对比）**：

| tender | external_id | payload code | CRM 响应 | 结论 |
|---|---|---|---|---|
| 249 | `NULL` | `""`（空） | `code:0` success | 空 code 被 CRM 接受 |
| 268 | `CRM:241` | `"241"` | `code:1` 失败 | "241" 不是商机编号，匹配失败 |

用户确认：`code:0`=成功，`code:1`=失败。

### 深层原因：CRM 推标讯未传 crmId，crm_opportunity_id 为 NULL

服务器日志铁证（traceId `37808f8daf054db4b499013326ecf7c9`）：
```
POST /api/integration/tenders/push - sourceSystem=CRM sourceId=241 title=zf商机001
Created tender id=268 externalId=CRM:241
```
全程无 `Applying CRM link` 日志 → CRM 推送时只传了 `sourceSystem`+`sourceId`，**没传 `crmId`**。

`TenderPushRequest` 里 `sourceId`（生成 external_id 用）和 `crmId`（关联商机用）是两个独立字段。没传 crmId → `CrmTenderLinkService.linkIfPresent` 直接 return → `crm_opportunity_id` 保持 NULL。

### 字段语义澄清：crm_opportunity_id 存的是商机编号(code)，不是 id

- `V118__fix_crm_opportunity_id_type.sql` 注释："存商机编号如 CC20260610180"
- `CrmTenderLinkService` 存 `leader.opportunityCode()`（商机 code）
- `crm-field-mapping.md` 说 `id` 回写 `crm_opportunity_id` —— **此条文档与代码/迁移注释矛盾，待对齐**

> ✅ CO-277 已解决此矛盾：CRM 推送的 `crmOpportunityId` 实测是商机**主键 id**（纯数字如 20916），
> `CrmTenderLinkService.applyCrmLinkAndAssignment`（CO-277）自动识别纯数字 id 并按 id 反查 code 后落库。
> 即：CRM 推 id → 系统反查 code → `crm_opportunity_id` 列存 code（CC... 格式）→ 回传 bidInfoSync 的 code 字段。
> `crm-field-mapping.md` 所说"id 回写"指的是 CRM 推送入口接收 id，但持久化的是反查后的 code。

⚠️ 前端手动关联路径（`useCrmOpportunitySelector.js:177`）传的是 `chance.id`（商机 id，数字），与 V118 设计意图（存 code）不符。这是独立的前端 bug，单独处理。

### 修复内容

`WebhookEventListener.java`：
- 注入 `TenderRepository`，`onTenderStatusChanged` 通过 `event.tenderId()` 查 tender
- `buildPayload`：`code` ← `tender.getCrmOpportunityId()`（商机编号），`name` ← `tender.getCrmOpportunityName()`（商机名称），NULL → 空字符串
- 删除 `extractSourceId` 方法（不再用 externalId 填 code/name）

无关联商机时 code/name 填空，CRM 侧接受（实测 tender 249 返回 `code:0` success）。

### 服务器验证（2026-06-18，jetty@172.16.38.78）

部署修复后重置 tender 268 状态触发弃标，`webhook_delivery_logs` 三组对比铁证：

| 场景 | payload `code` | payload `name` | CRM 响应 | 结论 |
|---|---|---|---|---|
| 修复前（sourceId 错填） | `"241"` | `"241"` | `{"code":"1","msg":null}` | 商机匹配失败 |
| 修复后·无商机关联 | `""` | `""` | `{"code":"0","msg":"success"}` | ✅ 成功 |
| 修复后·有商机关联 | `"CC20260618267"` | 商机名称 | 配合第 12 节 status=6 修复后 CRM 状态变弃标 | ✅ 业务生效 |

**核心结论**：code 字段从错填 sourceId 改为正确填商机编号，修复目标 100% 达成。

### 旁证：前端 4 处 bidInfoSync 调用全是死代码

前端 `useTenderActions.js`（2 处）和 `bidResultPage.actions.js`（2 处）调 `bidInfoSync` 时用 `tender.crmOpportunityCode` / `saved.tenderCode`，但：
- 后端 `TenderDTO` 从不返回 `crmOpportunityCode` 字段
- 后端从不返回 `tenderCode` 字段
- → `if (tender?.crmOpportunityCode)` 永远 false → 前端手动回传从未执行

## 12. CRM 回传 status 映射错位——接口文档枚举写错，弃标应是 6 非 1（CO-263）

### 事故一句话总结

`mapToCrmStatus` 把 ABANDONED 映射成 `1`（照抄接口文档"1-弃标"），但 CRM 真实枚举里 `1=跟进中`、`6=弃标`。回传后 CRM 返回 `code:0` success，却把商机状态改成了"跟进中"而非"弃标"——**接口返回成功，业务结果错误**，险些靠 code:0 蒙混过关。

### 根因：接口文档枚举与 CRM 实际不一致

`西域CRM商机对接接口.md` 原文：
```
status|integer|...|状态 1-弃标 2-中标 3-丢标 4-流标
```

但 CRM 商机操作记录原文（余海燕编辑商机时 CRM 自己显示的枚举）：
```
【项目状态 1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标】
```

两套枚举对 `1` 的定义完全相反：文档说 1=弃标，实际 1=跟进中。照抄文档实现，ABANDONED→1 实际写成了"跟进中"。

### 决定性证据：CRM 操作记录

tender 268 弃标回传 status=1 后，CRM 商机 `CC20260618267` 操作记录：
```
2026-06-18 22:18:38  招投标管理系统 编辑商机
【项目状态】由 【投标中】 变更为 【跟进中】   ← 我们回传 status=1，CRM 写成了"跟进中"
```

修复后回传 status=6，用户确认 CRM 商机状态变成了"弃标"。

### 最大教训：code:0 是谎言，必须核对 CRM 前端实际状态

本次最危险的陷阱：**CRM 对所有"能识别的 code"统一返回 `{"code":"0","msg":"success"}`**，即使 status 写错也会"成功"写入错误状态。

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| `webhook_delivery_logs.response_body` = `code:0` | "成功" | ❌ 不可靠，status 写错也返回 code:0 |
| CRM 前端商机项目状态 | 修复前=跟进中（错），修复后=弃标（对） | ✅ 唯一可靠 |

**铁律**：bidInfoSync 回传后，必须去 CRM 前端核对商机 `项目状态` 字段，不能只看接口响应。`code:0` 只代表"请求被接受"，不代表"状态改对了"。

### 修复内容

`WebhookEventListener.mapToCrmStatus`：
```java
case ABANDONED -> 6;   // 原来是 1（跟进中），修正为 6（弃标）
case WON -> 2;         // 不变
case LOST -> 3;        // 不变
```

同步修正：
- `西域CRM商机对接接口.md` status 枚举改为真实值，标注文档曾误写
- `WebhookEventListener` 注释更新为完整枚举（1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标）
- 单测 abandoned 断言 status=6

### 通用规则：外部系统枚举值必须用对方系统的"源真相"校验

1. 接口文档的枚举值不可全信——文档和实现脱节是常态。必须用对方系统的**操作记录/数据库/前端显示**等"源真相"交叉校验
2. 对接外部系统时，先用**非破坏性探针**（如查一条记录的状态字段）确认枚举语义，再写映射代码
3. 回传类接口的"成功"必须用**对方系统的实际状态变化**验证，不能用响应码代替——`code:0` 可能只是"请求格式合法"
4. 当回传值是状态码时，注释里必须写**完整枚举**（含中间态），不能只写自己用到的几个值，否则无法发现"文档漏列了枚举值"的问题（本次文档漏了 5-投标中、6-弃标）

### 相关文档

- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — bidInfoSync 契约（status 枚举已修正）
- [WebhookEventListener.java](../../backend/src/main/java/com/xiyu/bid/webhook/application/WebhookEventListener.java) — mapToCrmStatus 映射
- [WebhookEventListenerTest.java](../../backend/src/test/java/com/xiyu/bid/webhook/application/WebhookEventListenerTest.java) — status=6 断言
