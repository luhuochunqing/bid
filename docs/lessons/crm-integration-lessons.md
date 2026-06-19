# CRM 集成经验

> 记录与 CRM 系统对接过程中遇到的字段映射、接口契约和踩坑经验。

---

## 1. 客户信息字段名：接口文档与前端矩阵必须对齐

**来源**: CO-266 / CO-267 排查，2026-06-18

### 问题

CRM 按接口文档 `docs/integration-tender-api-v3.1.md` 推送客户信息时使用字段名：
- `CONTACT`
- `EVALUATION_BASIS`

前端客户信息矩阵 `src/views/Bidding/detail/components/customerInfoMatrixConfig.js` 使用的字段名：
- `CONTACT_INFO`
- `INFO_TENDENCY_BASIS`

后端 `TenderIntegrationService.saveEvaluationInternal()` 原样把 CRM 字段名存入 `tender_evaluation_customer_info.info_key`，导致前端按标准字段名读取时匹配不到值，单元格显示为空。

### 教训

1. **接口文档字段名必须与前端矩阵字段名保持一致**。如果外部系统已经按旧字段名对接，后端应提供兼容映射，而不是让前端或外部系统分别适配。
2. **后端是收敛字段名的最佳位置**。在 `TenderIntegrationService` 入口层统一标准化，可以同时覆盖 push / update 两个路径，避免前后端重复映射。
3. **新接入外部系统前，先用真实字段名跑端到端测试**。现有测试使用 `attitude` / `position` 等占位字段，掩盖了真实字段名不一致的问题。

### 修复参考

- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationService.java`
- `backend/src/main/java/com/xiyu/bid/tender/core/TenderEvaluationCustomerInfoPolicy.java`
- `docs/integration-tender-api-v3.1.md`
- `docs/lessons/root-cause-analysis-co-266-co-267.md`

---

## 2. 评估表 `evaluation` 字段不是简单透传，需要入口层处理

**来源**: CO-267 历史修复 `!805`，2026-06-18

### 问题

早期 `TenderPushRequest` 缺少 `evaluation` 字段，CRM 推送时评估表客户信息完全丢失。`!805` 在 DTO 中补了 `evaluation` 字段，并在 `pushTender()` 创建路径调用 `saveEvaluation()`。

但补完字段后，如果字段名不统一，数据仍然无法正确展示。

### 教训

- 增加字段只是第一步，字段名、数据格式、存储模型之间的映射必须同步验证。
- 对 `List<Map<String, Object>>` 这类弱类型字段要特别小心：后端不能假设外部系统传的 key 与内部模型一致。

---

## 3. CRM 标讯回传 URL 配错导致 405 全军覆没（CO-263 / PR !820）

> 来源：2026-06-18 CRM bidInfoSync 回传 405 事故复盘（PR !820，commit 0ca744607 + 后续修正）
> 适用范围：所有向 CRM 推送数据（标讯回传、结果回调）的出站链路配置
> 抢救自 `agent/zcode/webhook-crmbidinfosync-fix` 分支独有内容（该分支清理前沉淀）

### 事故一句话总结

弃标事件出站链路全程正常（监听器 → 入队 → 调度器 → HTTP 请求已发出），但 CRM 返回 **405 Method Not Allowed**，所有任务被标记为 `DEAD_LETTER` 永不重试，**信息从未真正传回 CRM**。修复后 5 条历史死信任务重投递全部 DELIVERED + HTTP 200。

### 根因：双回传路径使用了不同的配置源，其中一处默认值指向了前端静态站

代码库存在两条 CRM 回传路径：

| 路径 | 配置源 | 服务器实际值 | 状态 |
|---|---|---|---|
| 手动 `CrmChanceService.bidInfoSync()` | `app.crm.chance-base-url` | `crm-api-java-test.ehsy.com` ✅ | 正常 |
| 自动 `WebhookHttpSender` (webhook 链路) | `webhook.crm.url` | `crm-test.ehsy.com` ❌ | **405** |

`crm-test.ehsy.com` 是 CRM 前端 Vue SPA 静态站点（GET `/` 返回 `<title>西域CRM</title>` 的 HTML），nginx 对所有 POST 请求返回 405。
`winbid-test.ehsy.com`（`application-dev.yml` 旧默认值）同理，也是前端站。

### 为什么 405 → DEAD_LETTER 永不重试

`WebhookFailureClassifier.classifyStatusCode` 将 4xx 映射为 `CONTRACT_INVALID`，
`WebhookDeliveryJobService` 据此把任务设为 `DEAD_LETTER`。
而 `WebhookDeliveryTaskRepository.findRunnableTasksPaged` 只捞取 `PENDING` / `PENDING_RETRY`，
**不捞 `DEAD_LETTER`** —— 于是废弃任务永远不会被自动重试。

### 诊断方法：curl 证据链（三域名实测对比）

三个候选域名 DNS 都解析到同一个 WAF 入口 `waf-offline.ehsy.com` (124.70.211.25)，靠 Host 头路由，外观极易混淆。

```bash
# 1. ❌ 错误地址 crm-test.ehsy.com：前端静态站，GET 返回 HTML，POST 返回 405
curl -s -o /dev/null -w "%{http_code}" https://crm-test.ehsy.com/   # 200 (HTML, <title>西域CRM</title>)
curl -s -o /dev/null -w "%{http_code}" -X POST https://crm-test.ehsy.com/customer-chance/bidInfoSync -H "Content-Type: application/json" -d '{}'  # 405
curl -s -o /dev/null -w "%{http_code}" -X POST https://crm-test.ehsy.com/common/inner/generateToken -H "Content-Type: application/json" -d '{}'  # 405（连鉴权接口都 405，铁证是前端站）

# 2. ✅ 正确地址 crm-api-java-test.ehsy.com：CRM API 后端，与服务器手动路径同源
curl -s -X POST https://crm-api-java-test.ehsy.com/common/inner/generateToken -H "Content-Type: application/json" -d '{}'  # 200 {"code":"1","msg":"生成Token失败: 参数不能为空"}
curl -s -X POST https://crm-api-java-test.ehsy.com/customer-chance/bidInfoSync -H "Content-Type: application/json" -d '{}'  # 401 {"code":"401","msg":"token验证失败"}

# 3. ⚠️ base-oss-test.ehsy.com：鉴权/组织/菜单接口域，POST bidInfoSync 也通但不与商机接口同源
curl -s -X POST https://base-oss-test.ehsy.com/customer-chance/bidInfoSync -H "Content-Type: application/json" -d '{}'  # 200 {"code":401,"detailMessage":"token认证失败"}
```

### 修复内容

**commit 0ca744607**（首次修复，默认值改为 base-oss-test，后经服务器实测发现非最佳）：
1. `application.yml`: `webhook.crm.url` 默认值 `crm-test.ehsy.com` → `base-oss-test.ehsy.com`
2. `application-dev.yml`: `chance-base-url` 默认值 `winbid-test.ehsy.com` → `base-oss-test.ehsy.com`
3. `WebhookEventListenerTest.java`: 测试常量同步更新

**后续修正**（服务器实测后，统一到与手动路径同源的 `crm-api-java-test.ehsy.com`）：
1. `application.yml`: `webhook.crm.url` 默认值 → `crm-api-java-test.ehsy.com`（与服务器 `XIYU_CRM_CHANCE_BASE_URL` 一致）
2. `application-dev.yml`: `chance-base-url` 默认值 → `crm-api-java-test.ehsy.com`
3. `WebhookEventListenerTest.java`: 测试常量同步
4. `application.yml` 注释更新：标注三个域名的角色（crm-test=前端站/base-oss-test=鉴权域/crm-api-java-test=商机API域）

**服务器侧修复**（`/etc/xiyu-bid/backend.env`）：
1. `WEBHOOK_CRM_URL` 从 `crm-test.ehsy.com` → `crm-api-java-test.ehsy.com`
2. `sudo systemctl restart xiyu-bid-backend.service`（PID 4035→8462，health UP）

**死信任务补偿**（5 条历史 405 DEAD_LETTER 重投递）：
- `target_url` 固化了错误的 `crm-test` 地址，光重置 status 无效（`WebhookDeliveryJobService:56` 用 `task.getTargetUrl()` 发送，不用配置）
- 必须同时改 `target_url` + 重置 `status=PENDING` + `attempt_count=0` + 清错误 + `next_retry_at=NOW()`
- 重投递结果：5 条全部 `DELIVERED` + HTTP 200（1 条 `{"code":"0","msg":"success"}`，4 条 `{"code":"1","msg":null}`）

### 通用规则：出站 URL 必须在配置层与契约层双重校验

1. **客户文档 `Base URLs:` 为空不等于没有约束** —— 域名必须主动与客户确认，不能从"看起来像 CRM 的域名"中猜测
2. **前端域名 ≠ API 域名** —— `crm-test.ehsy.com`（前端 SPA）与 `crm-api-java-test.ehsy.com`（API 后端）外观相似但角色完全不同；三个候选域名 DNS 都指向同一 WAF IP，靠 Host 头路由，极易混淆
3. **出站链路上线前必须用 curl 跑通证据链** —— 至少验证 POST 能拿到业务级响应（200/401/403），而不是网关级 405/404；且要测 generateToken 等已知 API 接口，前端站连鉴权接口都会 405
4. **DEAD_LETTER 要有补偿入口** —— 配置错误导致的 DEAD_LETTER 任务，需要人工重置为 PENDING 后才能重新投递；注意 `target_url` 字段固化在任务里，光改配置不生效
5. **双回传路径配置源必须统一** —— 手动路径和自动路径若使用不同配置源，极易出现一处对一处错的"半瘫痪"状态；本次修复后两条路径都用 `crm-api-java-test.ehsy.com`

### DEAD_LETTER 任务补偿 SQL（部署修复后执行，已在 2026-06-18 实操验证）

> ⚠️ 实际表名是 `webhook_delivery_tasks`（复数），列名是 `last_error_code` / `last_error_message`（带 `last_` 前缀）。
> ⚠️ **光改配置 + 重置 status 无效** —— `target_url` 固化在任务行里，`WebhookDeliveryJobService:56` 用 `task.getTargetUrl()` 发送而非读配置，必须同时改 `target_url`。

```sql
-- 1. 查看被 405 打入死信的任务（注意：表名复数，列名带 last_ 前缀）
SELECT id, tender_id, status, target_url, attempt_count, last_error_code, last_error_message, created_at
FROM webhook_delivery_tasks
WHERE status = 'DEAD_LETTER'
  AND last_error_message LIKE '%405%'
ORDER BY id DESC;

-- 2. 修复：同时改 target_url + 重置 status（确认 payload 仍有效后执行）
--    target_url 必须改，否则重投递还是用旧的错误地址
UPDATE webhook_delivery_tasks
SET target_url = 'https://crm-api-java-test.ehsy.com/customer-chance/bidInfoSync',
    status = 'PENDING',
    next_retry_at = NOW(),
    attempt_count = 0,
    last_error_code = NULL,
    last_error_message = NULL,
    updated_at = NOW()
WHERE status = 'DEAD_LETTER'
  AND target_url LIKE '%crm-test.ehsy.com%';

-- 3. 等待调度器重投递（约 5 秒内，worker-fixed-delay-ms:5000）后验证结果
SELECT id, tender_id, status, target_url, attempt_count, last_error_code, last_error_message
FROM webhook_delivery_tasks
WHERE id IN (<上一步查出的ID列表>)
ORDER BY id;

-- 4. 查投递日志确认 HTTP 200（webhook_delivery_logs 记录每次投递结果）
SELECT id, tender_id, status, status_code, LEFT(response_body, 200) AS resp, created_at
FROM webhook_delivery_logs
WHERE tender_id IN (<对应 tender_id 列表>)
ORDER BY id DESC
LIMIT 20;
```

**2026-06-18 实操结果**：5 条死信任务（tender_id: 263/249/265/266/267）全部重投递成功（`status=DELIVERED`、HTTP 200）。但 CRM 业务层响应分两种：`{"code":"0","msg":"success"}`（成功）与 `{"code":"1","msg":null}`（**失败**——商机匹配不上，详见第 4 节）。HTTP 200 只代表传输层成功，`code:1` 是 CRM 侧业务失败，需单独排查。

### 相关文档

- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — 客户接口契约（已补充权威域名映射表）
- [integration-oa-crm.md](../../.wiki/pages/integration-oa-crm.md) — CRM 接口清单与域名映射
- [crm-field-mapping.md](../references/crm-field-mapping.md) — 字段映射与查询策略说明
- [WebhookHttpSender.java](../../backend/src/main/java/com/xiyu/bid/webhook/infrastructure/WebhookHttpSender.java) — 自动回传 HTTP 发送器
- [CrmChanceService.java](../../backend/src/main/java/com/xiyu/bid/crm/application/CrmChanceService.java) — 手动回传服务
- [WebhookFailureClassifier.java](../../backend/src/main/java/com/xiyu/bid/webhook/application/WebhookFailureClassifier.java) — 405 → DEAD_LETTER 分类逻辑

---

## 4. CRM 回传 code:1 —— buildPayload 用 sourceId 填 code 字段（CO-263 / PR !820）

> 来源：2026-06-18 CRM bidInfoSync code 字段错填事故复盘
> 抢救自 `agent/zcode/webhook-crmbidinfosync-fix` 分支独有内容（该分支清理前沉淀）
> 注：本节代码修复已通过 PR !827 合入 main，此处沉淀的是教训知识

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
- `CrmTenderLinkService:66` 存 `leader.opportunityCode()`（商机 code）
- `crm-field-mapping.md` 说 `id` 回写 `crm_opportunity_id` —— **此条文档与代码/迁移注释矛盾，待对齐**

⚠️ 前端手动关联路径（`useCrmOpportunitySelector.js`）传的是 `chance.id`（商机 id，数字），与 V118 设计意图（存 code）不符。这是独立的前端 bug，已通过 PR !828 修复（手动关联改传商机编号 code）。

### 修复内容（已通过 PR !827 合入 main）

`WebhookEventListener.java`：
- 注入 `TenderRepository`，`onTenderStatusChanged` 通过 `event.tenderId()` 查 tender
- `buildPayload`：`code` ← `tender.getCrmOpportunityId()`（商机编号），`name` ← `tender.getCrmOpportunityName()`（商机名称），NULL → 空字符串
- 删除 `extractSourceId` 方法（不再用 externalId 填 code/name）

无关联商机时 code/name 填空，CRM 侧接受（实测 tender 249 返回 `code:0` success）。

### 服务器验证（2026-06-18，commit b035dea50，jetty@172.16.38.78）

部署修复后重置 tender 268 状态触发弃标，`webhook_delivery_logs` 三组对比铁证：

| 场景 | log id | payload `code` | payload `name` | CRM 响应 | 结论 |
|---|---|---|---|---|---|
| 修复前（sourceId 错填） | 11 | `"241"` | `"241"` | `{"code":"1","msg":null}` | 商机匹配失败 |
| 修复后·无商机关联 | 12 | `""` | `""` | `{"code":"0","msg":"success","data":null}` | ✅ 成功 |
| 修复后·有商机关联 | 13 | `"CC20260616198"` | `"新增项目无标讯"` | `{"code":"1","msg":null}` | payload 正确，CRM 侧匹配问题 |

服务器日志侧印证：
```
Webhook delivery task enqueued for tender 268, crmStatus=1, crmOpportunityCode=(none), url=.../bidInfoSync   # 无商机关联
Webhook delivery task enqueued for tender 268, crmStatus=1, crmOpportunityCode=CC20260616198, url=.../bidInfoSync  # 有商机关联
```

**核心结论**：本次修复目标 100% 达成——code 字段从错填 sourceId 改为正确填商机编号。

⚠️ 第三组（有商机关联）CRM 仍返回 code:1，这是 **CRM 侧匹配规则问题**（商机 `CC20260616198` 可能已关闭/状态不符/归属人限制），**不属于 webhook payload 构造 bug**。payload 的 code/name 已正确，需 CRM 团队确认该商机为何匹配不上。

### 旁证：前端 4 处 bidInfoSync 调用因字段名错配而瘫痪（已删除）

前端曾有 4 处 `crmApi.bidInfoSync` 调用作为冗余兜底，但全部因 guard 字段名错配而从未执行：

| 文件 | 处数 | guard 读取字段 | 后端实际返回字段 | 结果 |
|------|------|---------------|-----------------|------|
| `useTenderActions.js` | 2（投标/弃标） | `tender.crmOpportunityCode` | `TenderDTO.crmOpportunityId`（无 `crmOpportunityCode`） | guard 永远 false |
| `bidResultPage.actions.js` | 2（登记/保存结果） | `saved.tenderCode` / `result.data.tenderCode` | 后端 src 全局 0 命中（幻觉字段，不存在） | guard 永远 false |

→ 4 处 guard 永远 false → 前端手动回传**从未执行过一次**。

这 4 处是"想兜底但兜不了"的死分支，而非"已被后端 webhook 取代才闲置"——它们从写下第一天起就没生效过。所有 CRM 回传实际只走后端 webhook 路径（`WebhookEventListener`，本节及第 12 节所述）。

**现状**：已通过 PR !832（commit `aec5159c4`）按字面清理删除（-72 行，纯删除不修复字段名）。决策依据：guard 永远 false → 从未执行 → 业务影响零；真实业务状态变更走后端 webhook，与本路径无关。

**若未来要让前端冗余回传真正生效**（非当前需求，仅备查）：
1. `useTenderActions.js`：guard 改 `crmOpportunityCode` → `crmOpportunityId`，并修正 status 映射（弃标 `1`→`6`、投标 `2`→`5`）
2. `bidResultPage.actions.js`：`tenderCode` 在后端 DTO 不存在，需二选一——后端 `BidResultFetchResultDTO` 加 `crmOpportunityId` 字段，或前端保存时 `tendersApi.getById(tenderId)` 单独取；statusMap `abandoned: 1` → `6`
3. 启用前务必先评估与后端 webhook 的**重复回传**问题（同一状态变更会被回传两次）

### 通用规则：bidInfoSync 的 code 字段必须填商机编号

1. `code` 字段的唯一正确来源是 `tender.crm_opportunity_id`（商机编号）
2. **切勿**用 `external_id` 的 sourceId 部分填 code——那是来源系统数据唯一 ID，非商机编号
3. 无关联商机时 code 填空字符串（CRM 接受），不要编造值
4. ⚠️ **HTTP 200 + 响应体 `code:0` ≠ 业务生效**。CRM 对"能识别的 code"统一返回 code:0，即使 status 值映射错也会"成功"写入错误状态。必须去 CRM 前端核对实际状态字段，不能只看接口响应（见第 5 节血泪教训）。

### 相关文档

- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — bidInfoSync 契约（code=商机编号）
- [crm-field-mapping.md](../references/crm-field-mapping.md) — 字段映射（id/code 语义）
- [V118__fix_crm_opportunity_id_type.sql](../../backend/src/main/resources/db/migration/V118__fix_crm_opportunity_id_type.sql) — 字段类型修复（注释明确存商机编号）
- [WebhookEventListener.java](../../backend/src/main/java/com/xiyu/bid/webhook/application/WebhookEventListener.java) — 回传 payload 构造
- [CrmTenderLinkService.java](../../backend/src/main/java/com/xiyu/bid/integration/external/CrmTenderLinkService.java) — CRM 推送自动关联商机

---

## 5. CRM 回传 status 映射错位——接口文档枚举写错，弃标应是 6 非 1（CO-263 / PR !820）

> 来源：2026-06-18 CRM bidInfoSync status 枚举映射错位事故复盘
> 抢救自 `agent/zcode/webhook-crmbidinfosync-fix` 分支独有内容（该分支清理前沉淀）
> 注：本节代码修复已通过 PR !827 合入 main，此处沉淀的是教训知识

### 事故一句话总结

`mapToCrmStatus` 把 ABANDONED 映射成 `1`（照抄接口文档"1-弃标"），但 CRM 真实枚举里 `1=跟进中`、`6=弃标`。回传后 CRM 返回 `code:0` success，却把商机状态改成了"跟进中"而非"弃标"——**接口返回成功，业务结果错误**，险些靠 code:0 蒙混过关。

### 根因：接口文档枚举与 CRM 实际不一致

`西域CRM商机对接接口.md` 写的：
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
2026-06-18 22:18:38  招标投标管理系统 编辑商机
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

### 修复内容（已通过 PR !827 合入 main）

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
- [WebhookEventListenerTest.java](../../backend/test/java/com/xiyu/bid/webhook/application/WebhookEventListenerTest.java) — status=6 断言

---

## 6. CRM 推送字段名 crmOpportunityId 与代码 crmId 不匹配——Jackson 静默丢弃，商机未关联（CO-276 / PR !844）

> 来源：2026-06-18 tender 273 "放弃"后 CRM 商机 CC20260619283 状态未变事故复盘
> 代码修复已通过 PR !844 合入 main，本节沉淀教训

### 事故一句话总结

CRM 推标讯时传了 `crmOpportunityId` 字段，但后端 DTO 字段名是 `crmId`。Spring Boot 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false`，未知字段被**静默丢弃**，`crm_opportunity_id` 永远是 NULL。后续 tender "放弃"时 webhook 回传的 payload `code` 字段为空，CRM 匹配不到商机，状态同步失效——全程无任何报错或告警。

> ⚠️ CO-277 纠正：本节初版把 `crmOpportunityId` 认定为"商机编号 code"是错误的。CRM 实测推送的是商机**主键 id**（纯数字如 20916），非 code（CC... 格式）。CO-276 修了"字段名不匹配"，但没发现"语义也不匹配"——因为当时没有诊断日志记录实际推送值，整个分析基于我方接口文档的示例值（CC...），而非 CRM 实际行为。详见第 7 节。

### 根因：接口文档字段名 ≠ 代码字段名，Jackson 默认静默丢弃

| 字段来源 | 字段名 | 语义 |
|---|---|---|
| CRM 推送（CO-277 实测） | `crmOpportunityId` / `crmOpportunityName` | 商机主键 id（纯数字）/ 商机名称 |
| 后端 `TenderPushRequest` / `TenderUpdateRequest` | `crmId` | CRM 商机标识（历史假设是 code，CO-277 实测为 id） |

两者语义相同（都是 CRM 商机标识），但字段名不同。Spring Boot 默认 Jackson 配置 `spring.jackson.deserialization.fail-on-unknown-properties=false`，未声明的字段不会抛异常，直接丢弃。于是 CRM 推送的 `crmOpportunityId` 被吞，`crmId` 保持 null，`CrmTenderLinkService.linkIfPresent` 直接 return，`crm_opportunity_id` 永远 NULL。

### 决定性证据：生产 DB 直查 + 端到端验证

**DB 取证 tender 273**（`jetty@172.16.38.78` 直连）：
```
status=ABANDONED, crm_opportunity_id=NULL, source_type=EXTERNAL_PLATFORM
```
`crm_opportunity_id` 为 NULL，证明 CRM 推送时商机标识（字段名 crmOpportunityId）被 Jackson 丢弃、从未被持久化。

**端到端验证**（修复后手动补数据触发回传）：
1. 由于 ABANDONED 是终态（`TenderStatusTransitionPolicy` 阻断任何状态转换），无法通过 API 重放放弃操作，改为直接 SQL 补 `crm_opportunity_id='CC20260619283'`
2. 调用 `bidInfoSync` 接口传 `status=6`（弃标）
3. CRM 商机项目状态从 `5-投标中` 变为 `6-弃标` ✅

这一步同时验证了第 5 节的 status 枚举（6=弃标）和本节的字段名修复：填上正确的 `crm_opportunity_id` 后，回传链路立即恢复。

### 修复内容（已通过 PR !844 合入 main）

1. **DTO 补字段**：`TenderPushRequest` / `TenderUpdateRequest` 在 `crmId` 后新增 `crmOpportunityId` / `crmOpportunityName` 字段，与接口文档对齐
2. **合并取值**：新增 `firstNonBlank(crmOpportunityId, crmId)` 工具方法，三个调用点统一使用：
   - `pushTender` 创建分支：`isFromCrm = firstNonBlank(r.getCrmOpportunityId(), r.getCrmId()) != null`
   - `pushTender` forceUpdate 分支：`String crmId = firstNonBlank(request.getCrmOpportunityId(), request.getCrmId())`
   - `updateByExternalId`：局部变量 `crmId` 用合并取值，并回退持久化 `crmOpportunityName`
   - `mapToEntity`：`isFromCrm` 判断用合并取值
3. **诊断日志增强**：`pushTender` 入口日志同时记录 `crmId` 与 `crmOpportunityId`，下次再出现字段名不一致可一眼定位
4. **测试覆盖**：3 个 Jackson 反序列化测试 + 3 个合并取值单测

### 最大教训：Jackson 静默丢弃是字段名错配的隐形杀手

本次最隐蔽的陷阱：**字段名不匹配不会报错，只会静默丢数据**。从 CRM 推送到 tender 创建全程 200 OK，日志里没有任何异常，`crm_opportunity_id` 默默变成 NULL，直到"放弃后 CRM 状态没变"才被发现——而这已经是数天后的下游症状。

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| 推送接口 HTTP 200 | "成功" | ❌ 不可靠，字段被吞也 200 |
| 后端日志无异常 | "正常" | ❌ 不可靠，Jackson 丢弃不记日志 |
| `tender.crm_opportunity_id` 字段值 | NULL → 错配；非 NULL → 正常 | ✅ 唯一可靠 |
| CRM 前端商机状态是否同步变化 | 同步 → 链路通；未同步 → 链路断 | ✅ 端到端可靠 |

**铁律**：外部系统推送类接口上线后，必须**反查落库字段值**确认数据真的进来了，不能只看 HTTP 200 和后端无异常。Jackson 默认配置是"宽容但致命"——它假设未知字段是版本演进的可接受情况，但对"字段名硬错配"这种 bug 毫无防御。

### 通用规则：外部接口字段名必须与 DTO 声明对齐，且要在入口层验证

1. **接口文档字段名 ≠ 代码字段名时，必须二选一对齐**——要么改 DTO 字段名匹配文档，要么文档改字段名匹配代码。本次选择 DTO 加字段（兼容两种字段名），因为 CRM 已按文档字段名对接，改文档会破坏既有契约
2. **DTO 字段应与接口文档一一对应**，不要用内部缩写（`crmId`）替代文档原名（`crmOpportunityId`），否则字段名漂移只在端到端联调时才暴露
3. **关闭 `FAIL_ON_UNKNOWN_PROPERTIES` 的代价**：Spring Boot 默认关闭它是为了前向兼容，但对关键字段错配毫无告警。对策是**对必填/关键字段做显式存在性校验**——本次的 `firstNonBlank` 合并取值就是一种软校验，配合诊断日志能在下次错配时快速定位
4. **入口层诊断日志要记录文档字段名**，而不是只记录代码字段名。本次 `pushTender` 日志同时记录 `crmId` 和 `crmOpportunityId`，一眼就能看出 CRM 实际传了哪个
5. **终态数据无法通过 API 重放验证**——ABANDONED 是终态，`TenderStatusTransitionPolicy` 阻断状态转换，`assertCrmLinkAllowed` 也会阻断关联。验证终态回传必须用"DB 直补数据 + 手动调 bidInfoSync"的旁路方式，不能依赖业务 API

### 与第 4、5 节的关系：同一条回传链路的三道独立断点

CO-263 系列（第 3、4、5 节）和 CO-276（本节）是**同一条 CRM 回传链路**上的三道独立断点，任一道断裂都会导致"放弃后 CRM 状态没变"：

| 断点 | 节 | 表现 | 根因 |
|---|---|---|---|
| 出站 URL 配错 | 第 3 节 | 405 → DEAD_LETTER | webhook.crm.url 指向前端站 |
| payload code 错填 | 第 4 节 | code:1 商机匹配失败 | code 填了 sourceId 而非商机编号 |
| status 枚举错位 | 第 5 节 | code:0 但状态写错 | 文档枚举误写，1=弃标实为跟进中 |
| **商机未关联** | **本节** | **code 为空，CRM 匹配不到** | **字段名 crmOpportunityId vs crmId，Jackson 丢弃** |

三道断点症状相似（"放弃后 CRM 状态没变"），但根因完全独立。排查此类问题必须**逐段取证**：DB 看 `crm_opportunity_id` 是否非空 → webhook_delivery_logs 看 payload code/status 是否正确 → CRM 前端看实际状态是否变化。任一段断了都要单独修，不能用一个修复覆盖所有断点。

### 相关文档

- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — PUT 接口字段名 `crmOpportunityId`（已与代码对齐）
- [TenderPushRequest.java](../../backend/src/main/java/com/xiyu/bid/integration/external/TenderPushRequest.java) — DTO 补 `crmOpportunityId`/`crmOpportunityName`
- [TenderUpdateRequest.java](../../backend/src/main/java/com/xiyu/bid/integration/external/TenderUpdateRequest.java) — DTO 补同名字段
- [TenderIntegrationService.java](../../backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationService.java) — `firstNonBlank` 合并取值 + 诊断日志
- [TenderRequestCrmOpportunityIdDeserializationTest.java](../../backend/src/test/java/com/xiyu/bid/integration/external/TenderRequestCrmOpportunityIdDeserializationTest.java) — Jackson 反序列化测试
