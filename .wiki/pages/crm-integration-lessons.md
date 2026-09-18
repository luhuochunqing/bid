---
title: CRM 集成踩坑集
space: engineering
category: guide
tags: [CRM, 集成, 踩坑, webhook, 商机状态, OAuth, CallerContext, OBS预签名]
created: 2026-07-10
updated: 2026-08-18
health_checked: 2026-09-18
sources:
  - backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandService.java
  - .wiki/pages/integration-oa-crm.md
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - engineering-discipline
  - evaluation-form-field-adjustments
  - spring-pitfalls
---
# CRM 集成踩坑集

> 从 8 个工作区历史对话中提取的 CRM 集成实战教训。
> 涵盖 webhook 回传、商机状态映射、OAuth 认证、字段语义、CallerContext 模式等高频踩坑点。
> 接口规范见 [[integration-oa-crm]]，本文只记录工程实战陷阱。

---

## 1. 商机状态映射：弃标 = 6 而非 1

### 1.1 事故

CRM 商机状态回传时，弃标状态被错误映射为 `1`，导致 CRM 端把"弃标"显示为"跟进中"。

### 1.2 根因

`WebhookEventListener.mapToCrmStatus` 中：

```java
// ❌ 错误
case ABANDONED -> 1;

// ✅ 正确（CRM 真实枚举：1=跟进中，6=弃标）
case ABANDONED -> 6;
```

开发者凭直觉把"弃标"映射为 1（以为 1=终止），未核对 CRM 真实枚举值。

### 1.3 教训

- **枚举值映射必须以 CRM 真实枚举为唯一真相**，不能凭语义直觉
- **集成方的枚举语义不可猜测**，必须查阅对方文档或抓包验证
- **测试用例必须覆盖所有状态映射**，不能只测 happy path

### 1.4 验证命令

```bash
# 检查状态映射是否正确
grep -n "case ABANDONED" backend/src/main/java/com/xiyu/bid/integration/crm/**/*.java
# 期望：-> 6 而非 -> 1
```

---

## 2. code 字段：crm_opportunity_id（商机编号）而非 sourceId

### 2.1 事故

CRM webhook 回传 `code` 字段时，错误使用了 `sourceId`（投标系统的内部 ID），导致 CRM 端无法关联到对应的商机。

### 2.2 根因

```java
// ❌ 错误
String code = tender.getSourceId();

// ✅ 正确
String code = tender.getCrmOpportunityId(); // 商机编号
```

`sourceId` 是投标系统的内部 ID，CRM 端不识别；`crm_opportunity_id` 是 CRM 创建商机时返回的 numeric ID，才是 CRM 端能识别的商机编号。

### 2.3 教训

- **跨系统字段必须使用对方能识别的 ID**
- **字段名相似但语义不同的字段要区分清楚**（sourceId vs crm_opportunity_id）
- **集成接口的字段语义必须以对方文档为准**

---

## 3. CRM code:0 表示"请求接收"而非"业务成功"

### 3.1 陷阱

CRM 接口响应格式：

```json
{
  "code": 0,
  "msg": "success",
  "data": null
}
```

`code: 0` 仅表示 **请求已被 CRM 接收**，不代表业务操作成功完成。开发者容易把它当作"业务成功"处理。

### 3.2 正确处理

```java
// ❌ 错误（把 code:0 当业务成功）
if (response.getCode() == 0) {
    markAsSynced(); // 可能实际未同步成功
}

// ✅ 正确（区分请求接收和业务成功）
if (response.getCode() == 0 && response.getData() != null) {
    // 检查 data 中的实际业务结果
    verifyBusinessOutcome(response.getData());
}
```

### 3.3 教训

- **外部系统的响应码语义必须查阅对方文档**，不能套用本地约定
- **code:0 ≠ 业务成功**，必须检查 data 字段的实际业务结果
- **集成测试必须覆盖"请求接收但业务失败"的场景**

---

## 4. webhook 格式：bidInfoSync vs tender.status_changed

### 4.1 问题

CRM 接收的 webhook 有两种格式：
- `bidInfoSync` — 标讯信息同步（项目创建/更新）
- `tender.status_changed` — 标讯状态变更（项目状态流转）

两种格式的字段结构、字段名、必填项都不同，但容易混淆。

### 4.2 区分要点

| 格式 | 触发场景 | 关键字段 | code 字段 |
|------|---------|---------|-----------|
| `bidInfoSync` | 项目创建/更新 | tender_no, title, status | crm_opportunity_id |
| `tender.status_changed` | 状态流转 | tender_no, old_status, new_status | crm_opportunity_id |

### 4.3 教训

- **webhook 格式必须按 type 字段分发到不同 handler**
- **两种格式的字段不能混用**（如 bidInfoSync 的 status 字段含义与 tender.status_changed 的 new_status 不同）
- **测试必须覆盖两种格式**

---

## 5. CRM OAuth 配置项含义

### 5.1 配置项

```bash
# /etc/xiyu-bid/backend.env
XIYU_CRM_OAUTH_USERNAME=xxx       # CRM 系统账号（系统凭证，非个人账号）
XIYU_CRM_OAUTH_PASSWORD=xxx       # CRM 系统密码
XIYU_CRM_GENERATE_TOKEN_SALES_NO=xxx   # 销售工号
XIYU_CRM_GENERATE_TOKEN_NICK_NAME=xxx   # 销售昵称
```

### 5.2 陷阱

- **这 4 个配置项是 CRM API 认证凭证**，不是个人账号
- `CRM_CLIENT_ID` 和 `CRM_CLIENT_SECRET` 是**预留字段，当前未使用**（CRM 通信走 OAuth username/password + API Key，不依赖 clientId/clientSecret）
- 启动日志中打印 `clientId=` 只是信息性日志，不影响功能

### 5.3 教训

- **配置项声明 ≠ 配置项使用**，预留字段会误导排障
- **日志中打印的配置项不一定都是必需的**，要区分"信息性"和"阻塞性"
- **部署清单中的"未配置"提示必须区分严重性**

---

## 6. CallerContext 模式 — 区分内部用户 vs 外部系统调用

### 6.1 问题

同一个 Service 方法被两种调用方触发：
- **内部用户**（通过 Controller）— 需要 `CurrentUserResolver` 解析用户上下文
- **外部系统**（通过 webhook / Kafka）— 没有 HTTP 请求上下文，`CurrentUserResolver` 会失败

### 6.2 CallerContext 模式

```java
public class CrmSyncService {
    public void syncTender(Tender tender, CallerContext context) {
        if (context.isExternal()) {
            // 外部系统调用，使用 SYSTEM_USER_ID
            auditLogger.log(SystemUserConstants.SYSTEM_USER_ID, "CRM_SYNC");
        } else {
            // 内部用户调用，使用当前用户
            auditLogger.log(context.getUserId(), "CRM_SYNC");
        }
    }
}
```

### 6.3 教训

- **Service 方法要显式接收 CallerContext**，不要隐式依赖 HTTP 上下文
- **外部系统调用必须显式标记**，不能 fallback 到"匿名用户"
- **审计日志必须区分内部用户和外部系统**

---

## 7. CRM 联系人 position 重复处理

### 7.1 问题

CRM 联系人接口返回的 `position`（职位）字段可能重复（同一人在 CRM 中有多个职位记录），导致同步时唯一约束冲突。

### 7.2 处理方式

```java
// 取第一个有效的 position，去重
List<String> positions = contact.getPositions();
String position = positions.stream()
    .filter(StringUtils::isNotBlank)
    .findFirst()
    .orElse(null);
```

### 7.3 教训

- **外部系统数据不保证唯一性**，本地存储必须做去重或取第一个
- **唯一约束字段必须在外部数据同步时显式处理**

---

## 8. API Key 通过 URL 参数支持浏览器直接下载

### 8.1 场景

某些 CRM 接口需要支持浏览器直接下载（如导出商机列表），但浏览器无法设置 Authorization header。

### 8.2 实现

API Key 同时支持两种传递方式：
- **Header**：`Authorization: Bearer <token>`（程序调用）
- **URL 参数**：`?apiKey=<token>`（浏览器直接下载）

### 8.3 教训

- **同一认证凭证要支持多种传递方式**，适配不同调用场景
- **URL 参数传递 token 必须用 HTTPS**，避免中间人攻击
- **URL 参数中的 token 必须做 URL 编码**

---

## 9. 回调下发 OBS 预签名 URL 会过期 — 外部系统必须给永久端点

### 9.1 事故

2026-08 客户投诉：§4.2 项目结果回调 feedback.evidenceFiles[].fileUrl 无法访问。
生产验证（uploadId=4c15375b）：文件 09:52:38 上传 → 09:52:48 回调 DELIVERED，
fileUrl 是 `ObsShareUrlSigner` 签发的 1 小时预签名 URL（Expires = 签发时刻+3600s），
CRM 存储该字符串后 1 小时即永久失效。

### 9.2 根因

- OBS 文件永久存储，但预签名 URL 有效期由签发时 expireSeconds 决定（本项目硬限 [60, 3600] 秒）
- `ProjectResultPayloadAssembler` 组装 feedback 时把 `obs-direct:{uploadId}` 实时签成预签名 URL 下发，
  适合"立即点开"场景，不适合"对方存下来以后用"场景
- **文件永久 ≠ URL 永久**；OBS 签名上限约 7 天，做不到永久 URL

### 9.3 修复模式

给外部系统下发**永久下载端点**而非签名 URL：

1. `TenderAttachmentUrlResolver.toIntegrationEndpointUrlOrNull`（共享分发，
   toIntegrationDownloadUrl/toIntegrationFullUrl 共用）：`obs-direct:` 包装为
   `{publicBaseUrl}/api/integration/tenders/attachments/download?fileUrl=obs-direct%3A...`（仅外部系统路径，
   内部用户 `toDownloadUrl()` 仍走预签名）
2. 下载端点统一走 `ObsDirectRedirects.tryRedirect`（file/application 包，2026-08-18 重构收口）：
   复用 `ObsShareUrlSigner.signObsDirect` 富结果实时签发后 302 重定向，
   精确区分 404（BidFile 不存在）/ 502（OBS 故障），不写第二套签名
3. **对接契约**（标讯集成接口文档 §4.2）：CRM 侧必须跟随 302 重定向；端点免认证（URL-as-capability）
4. **部署依赖**：`xiyu.public-base-url` 必须配置（未配置时 Resolver 打 warn，但下发的相对路径 CRM 不可用）

历史数据无需修 DB（源头存的是伪协议，永不过期），上线后对历史项目重推 §4.2 回调即可覆盖 CRM 侧旧 URL。

### 9.4 教训

- **给外部系统持久化存储的 URL 必须是永久端点**，预签名 URL 只适合短时消费
- **排障先验证前提**：最初把文件名里的毫秒时间戳误当上传时间，误判"6 天有效期、非我们代码签发"，
  生产 SQL（bid_file/project_documents/webhook_delivery_logs 三表时间线）才还原真相
- **复用既有签名器**（ObsShareUrlSigner），不要在新端点里写第二套签名逻辑；
  多处相同的"obs-direct → 签发 → 302"行为要收口到单一共享组件（ObsDirectRedirects），避免三份拷贝各自演化
- **永久免认证端点是安全权衡**：URL-as-capability 能力窗口从 1 小时扩到永久，
  收紧需 CRM 方配合（改传 api_key），属集成契约变更，不能单方面改

### 9.5 历史数据重推工具（2026-08-19 落地）

- 运维接口：`POST /api/webhooks/operations/project-result/{resultId}/redeliver`（仅 ADMIN）
- 实现：`ProjectResultRedeliveryService` 从 DB（project_result / project_result_competitors /
  projects / users）重建 `ProjectResultConfirmedEvent` 并重新发布，现有
  `ProjectResultConfirmedWebhookListener` 用当前代码组装 payload 入队（零重复组装逻辑），
  复用 1min/5min/15min 重试机制；occurredAt 取原登记时间保持 businessKey 一致
- 排查入口：历史回调实际 payload 存在 `webhook_delivery_tasks.payload`（logs 表不存 payload）
- 测试环境实锤：task 348（08-16，project 225）payload 中 fileUrl 为已过期 OBS 预签名 URL，
  全库带佐证文件的历史回调仅此 1 条

### 9.6 对接人座机号字段名驼峰/下划线不匹配（2026-08-28 修复）

- 现象：测试环境 tender 4136 关联商机 CC20260828857 后，评估表客户信息 TEL（对接人座机号）始终为空
- 双证据定位：journalctl 里 `CrmHttpClient` 响应体日志显示 CRM 实际返回 `"fixedPhone":"021-56478789"`（驼峰），DB `tender_evaluation_customer_info` 无 TEL 行
- 根因：`ContactPersonInfoVO` 按接口文档声明 `@JsonProperty("fixed_phone")`（下划线），`@JsonIgnoreProperties(ignoreUnknown=true)` 把驼峰 key 静默丢弃 → `fixedPhone` 反序列化 null → TEL 行不落库。容错注解把字段名不匹配从显式报错降级成静默丢数据
- 修复：`@JsonAlias("fixedPhone")` 双兼容；`@JsonAlias` 只影响反序列化，序列化输出 key 仍为 `fixed_phone`（前端消费契约不变）
- 通用对策：集成类 VO 的测试用例必须来自真实环境日志原文（至少一条），不抄接口文档；外部字段丢失类工单先翻 `CrmHttpClient` 响应体日志比对 key，详见 [[lessons-learned]] §176

---

## 10. 相关文档

- [[integration-oa-crm]] — CRM 接口规范（字段定义、响应格式）
- [[lessons-learned]] §六 — PR 合并验证盲区（CRM bidInfoSync 回传修复案例）
- [[oss-organization-sync-playbook]] — OSS 同步手册（类似的集成模式参考）
- `backend/src/main/java/com/xiyu/bid/integration/crm/` — CRM 集成源码

---

## 11. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 CRM 集成踩坑教训 |
| 2026-08-18 | 新增 §9：回调下发 OBS 预签名 URL 过期事故与永久端点修复模式 |
| 2026-08-18 | §9 更新：ObsDirectRedirects 重构收口、404/502 语义精确化、对接契约与安全姿态声明 |
| 2026-08-19 | 新增 §9.5：历史数据重推运维接口落地（webhook_delivery_tasks.payload 为排查入口） |
| 2026-08-28 | 新增 §9.6：对接人座机号 fixedPhone 驼峰/下划线字段名不匹配，@JsonAlias 双兼容修复 |
