---
title: CRM 集成踩坑集
space: engineering
category: guide
tags: [CRM, 集成, 踩坑, webhook, 商机状态, OAuth, CallerContext]
sources:
  - backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandService.java
  - .wiki/pages/integration-oa-crm.md
  - .wiki/pages/lessons-learned.md
backlinks:
  - _index
  - integration-oa-crm
  - lessons-learned
created: 2026-07-10
updated: 2026-07-10
health_checked: 2026-07-19
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

## 9. 相关文档

- [[integration-oa-crm]] — CRM 接口规范（字段定义、响应格式）
- [[lessons-learned]] §六 — PR 合并验证盲区（CRM bidInfoSync 回传修复案例）
- [[oss-organization-sync-playbook]] — OSS 同步手册（类似的集成模式参考）
- `backend/src/main/java/com/xiyu/bid/integration/crm/` — CRM 集成源码

---

## 10. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 CRM 集成踩坑教训 |
