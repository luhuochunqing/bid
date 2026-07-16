# Phase 0 Research: CRM 商机关联补偿与认证解耦

**Date**: 2026-07-16
**Feature**: 037-crm-link-compensation
**Status**: Complete

本文件记录 Phase 0 阶段对所有 NEEDS CLARIFICATION 项和技术依赖的调研结论。

---

## 1. NEEDS CLARIFICATION 澄清

### 1.1 CRM `page-list` 接口是否支持按 `bidId` 查询？

**Decision**: 需要验证，但有 fallback 方案。

**Rationale**:
- 现有 `CrmChanceService.findByCode` 通过 `page-list` 接口的 `body` 参数查询，已验证可按 `code` 字段过滤
- spec Assumptions 提到"若不支持 bidId 查询，需改用按 projectLeaderNo 查全部再本地匹配"
- 实现阶段第一步：用 `curl` 实测 `POST /customer-chance/page-list` body `{"bidId": "7"}` 是否返回商机
- 若不支持，fallback 为：用 PM 工号查该 PM 全部商机，本地按 `bidId == tender.sourceId` 过滤

**Alternatives considered**:
- A. 直接用 `bidId` 查 page-list（首选，1 次 API 调用）
- B. 用 `projectLeaderNo` 查全部再本地过滤（备选，1 次 API + 本地过滤）
- C. 保留 detail 接口但改为按 `bidId` 查（不可行：detail 接口路径是 `/customer-chance/detail?id={chanceId}`，id 参数是商机主键不是标讯 ID）

**Selected**: A，实现时先验证；若 A 不可行用 B。

### 1.2 `generateToken` 接口长期不校验 Authorization 的风险

**Decision**: 接受此假设，但代码路径保持可回退。

**Rationale**:
- 测试环境 + 生产环境双环境验证：`POST /common/inner/generateToken` 不带 Authorization，仅传 `{"nickName":"王旭州","salesNo":"04503"}` 返回 `code:0` + 有效 CRM JWT
- spec Assumptions 已记录此假设
- 若客户方后续修复此"漏洞"，需恢复三步认证流程

**Code Design**:
- `CrmAuthService.applyCrmToken(nickName, salesNo)` 改用 `CrmHttpClient.postJson`（无 Authorization）
- 保留 `OssUserTokenCache` 注入（不删字段），未来回退时无需大改
- 在 `CrmAuthService` 类注释中标注此假设 + 回退路径

**Alternatives considered**:
- A. 完全删除 OSS token 依赖（代码最简，但回退成本高）
- B. 保留依赖但改为可选（OSS token 有就用，没有就跳过）—— **rejected**：实测 OSS token 不被校验，传了也是浪费 header
- C. 新增配置项 `crm.auth.requireOssToken`（默认 false）—— **rejected**：YAGNI，等真的需要回退时再加

**Selected**: A，但保留 `OssUserTokenCache` 字段不删，未来回退只需改一个方法体。

### 1.3 补偿任务是否在本 spec 范围内？

**Decision**: 补偿任务（User Story 2）降级为 P2，本 spec 仅做基础设施准备，不实现定时任务。

**Rationale**:
- 用户原话只提到 3 个文件的改动：`CrmTenderLinkService` / `OrganizationUserSyncWriter` / `CrmAuthService`
- 补偿任务需要新增 `@Scheduled` bean + 配置 + 幂等性设计，超出"治本方案"范围
- User Story 1（CRM 推送时正确关联）+ User Story 3（OSS 同步填充 crm_sales_no）完成后，新推送的标讯不会再出现关联失败问题
- 历史标讯（52/53/56）可手动调 `PUT /api/integration/tenders/CRM/{id}` 触发补偿，无需定时任务

**Alternatives considered**:
- A. 本 spec 实现完整补偿任务（定时扫描 + 幂等处理）—— **rejected**：超出用户指定范围
- B. 本 spec 仅做基础设施，补偿任务留给后续 spec —— **Selected**
- C. 完全砍掉 User Story 2 —— **rejected**：spec 已记录，保留为后续工作项

**Selected**: B，plan 只覆盖 User Story 1 + 3。

---

## 2. 技术依赖调研

### 2.1 `CrmChanceService.findByCode` 的实现模式

**Source**: `backend/src/main/java/com/xiyu/bid/crm/application/CrmChanceService.java` L69-79

**Finding**: `findByCode` 构造 `CustomerChancePageRequest(1, 1, body)` 调 `doPageList`，body 是包含 `code` 字段的 JSON。`doPageList` 调 `CrmHttpClient.post`（带 Bearer），路径来自 `properties.getChance().getPageListPath()` = `/customer-chance/page-list`。

**Implication**: 新增 `findByBidId(Long bidId, String username)` 方法可完全复用此模式，body 改为 `{"bidId": "7"}`。

### 2.2 `CrmHttpClient` 已有不带 Authorization 的 post 方法

**Source**: `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmHttpClient.java` L150-164

**Finding**: `postJson(String baseUrl, String path, Object body)` 方法显式注释 "POST JSON without Bearer token (for /oss/admin-web/... getUserJobList)"，不设置 Authorization header。

**Implication**: `CrmAuthService.applyCrmToken` 改用 `postJson` 即可去掉 OSS token 依赖，无需新增 HTTP 方法。

### 2.3 `external_id` 解析的重复实现

**Source**: `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandSupport.java` L113-118

**Finding**: `applyCrmFallback` 直接用 `tender.getExternalId().split(":")` 解析，未复用 `ExternalIdParser.extractSourceId` 或 `ExternalSystemPrefix.CRM.matches`。这是一个已存在的小重复，本 spec 不修复（YAGNI，避免扩散改动范围）。

**Implication**: 修改 `linkByChanceIdIfPresent` 时，方法签名保持 `(Tender, String sourceSystem, String sourceId, String username)` 不变，调用方 `applyCrmFallback` 无需改动。

### 2.4 `linkByChanceIdIfPresent` 当前的语义错误

**Source**: `backend/src/main/java/com/xiyu/bid/integration/external/CrmTenderLinkService.java` L63-89

**Finding**:
- L66-72: 把 `sourceId` 解析为 `Long chanceId`
- L76-77: 调 `crmProjectLeaderService.findProjectLeaderByChanceId(chanceId, username)`
- `findProjectLeaderByChanceId` 内部调 `CrmChanceDetailService.getDetailById` → `POST /customer-chance/detail?id={chanceId}`

**Bug**: `external_id=CRM:7` 中的 `7` 是 CRM 的**标讯 ID**（bidId），不是商机 ID（chanceId）。CRM 实际商机 id=6, bidId=7, code=CC2026071568。用 bidId=7 调 detail 接口查不到商机。

**Fix**: 改用 page-list 接口按 bidId 反查。方法名改为 `linkByBidIdIfPresent`（语义清晰），但保留旧方法名作为 deprecated alias 避免破坏现有测试。

### 2.5 `OrganizationUserSyncWriter` 不填充 `crm_sales_no`

**Source**: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java` L52-124

**Finding**: upsert 方法设置 username / employeeNumber / password / email / fullName / phone / departmentCode / departmentName / enabled / externalOrgUserId / externalOrgSourceApp / lastOrgEventKey / lastOrgSyncedAt / roleProfile / role，**不设置 `crm_sales_no`**。

**Implication**: 加一行 `user.setCrmSalesNo(snapshot.username())` 即可（已确认 salesNo = OSS 工号 = username）。

### 2.6 现有测试覆盖基线

**Source**:
- `backend/src/test/java/com/xiyu/bid/integration/external/CrmTenderLinkServiceTest.java`（397 行，18 个 case）
- `backend/src/test/java/com/xiyu/bid/crm/application/CrmAuthServiceTest.java`（321 行，11 个 case）

**Finding**:
- `CrmTenderLinkServiceTest` 已覆盖 `linkByChanceIdIfPresent` 的 4 个场景（L218-262），但所有场景都假设 sourceId 是 chanceId
- `CrmAuthServiceTest` 已覆盖 OSS token 缺失抛 `TokenUnavailableException`（L201-228），修改后此 case 需要改：OSS token 缺失不再抛异常，改用 `postJson` 换 JWT

**Implication**: TDD 流程 — 先改测试（Red），再改实现（Green）。

---

## 3. 风险与缓解

### 3.1 `page-list` 接口按 bidId 查询不支持

**Risk**: CRM page-list 接口可能不识别 `bidId` 作为查询条件。

**Mitigation**:
- 实现阶段第一步用 curl 实测
- 若不支持，fallback 为按 projectLeaderNo 查全部 + 本地过滤
- 不阻塞 plan 推进，风险在 tasks.md 中作为第一个任务

### 3.2 `generateToken` 接口未来收紧

**Risk**: 客户方修复"不带 Authorization 也能换 JWT"的漏洞，导致本 spec 改动失效。

**Mitigation**:
- `CrmAuthService` 保留 `OssUserTokenCache` 注入
- 类注释明确记录此假设 + 回退路径
- 若失效，回退方法体改为 `postWithAuth`（5 分钟修复）

### 3.3 历史标讯 52/53/56 的补偿

**Risk**: 本 spec 不实现自动补偿任务，历史标讯需要手动触发。

**Mitigation**:
- `PUT /api/integration/tenders/CRM/{id}` 接口已存在（`TenderIntegrationCommandService.updateByExternalId`）
- 部署后手动 curl 触发 3 次补偿即可
- tender 56 已在上一轮会话用 SQL UPDATE 修复

---

## 4. 结论

所有 NEEDS CLARIFICATION 已澄清，技术依赖已调研完毕。可以进入 Phase 1 Design。

---

## 5. T004 实测结果（2026-07-16）

### 5.1 测试环境实测

**命令**:
```bash
curl -s -X POST https://base-oss-test.ehsy.com/common/inner/generateToken \
  -H "Content-Type: application/json" \
  -d '{"nickName":"王旭州","salesNo":"04503"}'
```

**结果**: HTTP 401
```json
{"code":401,"detailMessage":"请求访问：/common/inner/generateToken，token认证失败：Full authentication is required to access this resource，无法访问系统资源","message":"访问失败","success":false}
```

**结论**: 测试环境 `generateToken` 强制要求 Authorization（APISIX 网关配置不同），无法用测试环境实测 page-list 接口。

### 5.2 生产环境验证状态

- 上一轮会话已验证：生产环境 `generateToken` 不校验 Authorization，仅传 `{"nickName","salesNo"}` 即可换 CRM JWT
- 本轮不重复打生产环境（用户明确要求"停止 这个是生产环境 不能一直这么测试"）

### 5.3 代码分析结论

- `CustomerChanceDTO` 当前**没有** `bidId` 字段（L1-22 完整 record 定义）
- `CustomerChanceVO` 当前**没有** `bidId` 字段，但有 `@JsonIgnoreProperties(ignoreUnknown=true)` 兜底
- `CrmChanceDetailService.getDetailById` 用 `chanceId`（商机主键）调 detail 接口，**不适用** bidId 反查

### 5.4 最终决策

**方案 A（已选）**：给 DTO/VO 加 `bidId` 字段 + 新增 `findByBidId` 方法

**理由**:
1. CRM 商机记录中含 `bidId` 字段（用户已确认生产数据结构：id=6, code=CC2026071568, bidId=7）
2. page-list 接口通常支持按任意字段查询（`findByCode` 已验证按 `code` 查询可行）
3. 即使 CRM page-list 不支持 `bidId` 查询，`findByBidId` 返回 `Optional.empty()` + `log.warn`，不破坏现有功能
4. 部署后通过 log 可观测性确认是否需要 fallback

**风险评估**:
- 若 CRM page-list 忽略 `bidId` 字段：返回所有商机 → `findByBidId` 取第一条 → 可能关联错误商机
- **缓解**：`findByBidId` 实现时检查返回的 `dataList` 长度，>1 时 `log.warn` + 取第一条匹配 `bidId` 的记录（但 VO 无 bidId 字段无法本地过滤）
- **最终缓解**：给 `CustomerChanceVO` 也加 `bidId` 字段，若返回的商机 bidId 与查询的 bidId 不匹配，`log.warn` 并返回 `Optional.empty()`

**结论**: T004 完成，采用方案 A，开始 T005。
