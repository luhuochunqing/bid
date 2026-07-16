# Quickstart: CRM 商机关联补偿与认证解耦

**Date**: 2026-07-16
**Feature**: 037-crm-link-compensation
**Status**: Phase 1 Design

本文件提供快速验证脚本，用于在实现完成后验证三个 User Story。

---

## 前置条件

1. 主工作区 `/Users/user/xiyu/worktrees/trae` 已启动开发环境：
   ```bash
   cd /Users/user/xiyu/worktrees/trae
   export XIYU_DEV_CONFIRMED=1
   npm run dev:all
   ```
2. 后端健康检查通过：`curl http://127.0.0.1:18089/actuator/health` 返回 `{"status":"UP"}`
3. 数据库 `xiyu_bid_main` 可访问，`users.crm_sales_no` 列存在
4. CRM 测试环境可达（`CrmProperties.chanceBaseUrl` 配置正确）

---

## 验证 1：User Story 1 — CRM 推送时正确关联（P1）

### 准备

确保测试用户 `04503` 王旭州在 Redis 中**没有** OSS token：
```bash
redis-cli -n 0 DEL "oss:token:04503"
redis-cli -n 0 KEYS "oss:token:04503"  # 应返回空
```

确保 `users` 表中 `04503` 的 `crm_sales_no` 已填充（User Story 3 完成后）：
```sql
-- MySQL
SELECT username, crm_sales_no, full_name FROM users WHERE username = '04503';
-- 期望: crm_sales_no = '04503'
```

### 执行

模拟 CRM 推送一条新标讯（PM 从未登录）：
```bash
curl -X POST http://127.0.0.1:18089/api/integration/tenders \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${CRM_PUSH_API_KEY}" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "7",
    "title": "测试标讯-验证 bidId 反查",
    "customerName": "测试客户",
    "crmId": null,
    "crmOpportunityId": null
  }'
```

### 验证

```sql
-- 检查标讯是否正确关联
SELECT id, external_id, crm_opportunity_id, crm_opportunity_name, project_manager_name, status
FROM tenders
WHERE external_id = 'CRM:7'
ORDER BY id DESC LIMIT 1;
```

**期望结果**：
- `crm_opportunity_id` = `CC2026071568`（非 NULL，非纯数字）
- `crm_opportunity_name` = 商机名称
- `project_manager_name` = `王旭州`
- `status` = `EVALUATED`

### 回归验证

PM 已登录的情况（Redis 有 OSS token）也走相同路径，结果应一致：
```bash
# 模拟 PM 登录（写入 OSS token）
redis-cli -n 0 SET "oss:token:04503" "fake-oss-token-for-test" EX 3600
# 再次推送标讯，验证结果一致
```

---

## 验证 2：User Story 3 — OSS 同步填充 crm_sales_no（P3）

### 准备

找一个 `crm_sales_no = NULL` 的 OSS 用户：
```sql
SELECT id, username, full_name, crm_sales_no, external_org_source_app
FROM users
WHERE external_org_source_app = 'oss' AND crm_sales_no IS NULL
LIMIT 1;
```

### 执行

触发该用户的 OSS 同步事件（通过 OSS 事件 webhook 或手动调同步接口）：
```bash
# 手动触发同步（具体接口取决于 OSS 事件监听器实现）
curl -X POST http://127.0.0.1:18089/api/integration/organization/sync-user \
  -H "Content-Type: application/json" \
  -d '{"username": "<上一步查到的 username>"}'
```

### 验证

```sql
SELECT username, crm_sales_no, full_name
FROM users
WHERE username = '<上一步的 username>';
```

**期望结果**：
- `crm_sales_no` = `username` 值（非 NULL）

---

## 验证 3：单元测试（TDD Red → Green）

### 运行现有测试（回归基线）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=CrmTenderLinkServiceTest
mvn test -Dtest=CrmAuthServiceTest
```

**期望**：所有现有 case 通过（修改前的基线）。

### 新增 Red 测试

1. `CrmTenderLinkServiceTest` 新增 case：
   ```java
   @Test
   void linkByBidIdIfPresent_shouldResolveByBidIdNotChanceId() {
       // Given: sourceId=7 是 bidId，不是 chanceId
       when(crmProjectLeaderService.findProjectLeaderByBidId(7L, "04503"))
           .thenReturn(new ProjectLeaderResult("王旭州", "04503", "商机名", "CC2026071568"));
       // When
       boolean linked = service.linkByBidIdIfPresent(tender, "CRM", "7", "04503");
       // Then
       assertTrue(linked);
       assertEquals("CC2026071568", tender.getCrmOpportunityId());
       verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(anyLong(), anyString());
   }
   ```

2. `CrmAuthServiceTest` 新增 case：
   ```java
   @Test
   void getValidTokenForUser_shouldWorkWithoutOssToken() {
       // Given: 用户 profile 存在但 OSS token 缺失
       when(userProfileCache.get("04503")).thenReturn(Optional.of(profile));
       when(ossUserTokenCache.get("04503")).thenReturn(Optional.empty());  // OSS token 不存在
       when(httpClient.postJson(anyString(), anyString(), anyString()))
           .thenReturn(apiResponseWithJwt("fake-crm-jwt"));
       // When
       String token = service.getValidTokenForUser("04503");
       // Then
       assertEquals("fake-crm-jwt", token);
       verify(httpClient, never()).postWithAuth(anyString(), anyString(), anyString(), anyString());
   }
   ```

### 运行新测试（应先 Red，再 Green）

```bash
mvn test -Dtest=CrmTenderLinkServiceTest#linkByBidIdIfPresent_shouldResolveByBidIdNotChanceId
mvn test -Dtest=CrmAuthServiceTest#getValidTokenForUser_shouldWorkWithoutOssToken
```

---

## 验证 4：架构测试（Constitution 合规）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

**期望**：全绿（本 spec 不违反任何架构规则）。

---

## 验证 5：生产环境手动补偿历史标讯

部署后，手动触发 tender 52 / 53 的补偿（tender 56 已用 SQL 修复）：

```bash
# 生产环境
curl -X PUT http://172.16.10.149:18080/api/integration/tenders/CRM/52 \
  -H "X-API-Key: ${PROD_CRM_PUSH_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"sourceSystem":"CRM","sourceId":"<tender52的sourceId>","title":"<原标题>","forceUpdate":true}'

curl -X PUT http://172.16.10.149:18080/api/integration/tenders/CRM/53 \
  -H "X-API-Key: ${PROD_CRM_PUSH_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"sourceSystem":"CRM","sourceId":"<tender53的sourceId>","title":"<原标题>","forceUpdate":true}'
```

验证：
```sql
SELECT id, external_id, crm_opportunity_id, crm_opportunity_name
FROM tenders
WHERE id IN (52, 53, 56);
```

**期望**：三条标讯的 `crm_opportunity_id` 均为 CC 格式编号。

---

## 完成标准

- [ ] 验证 1：CRM 推送新标讯后 `crm_opportunity_id` 非 NULL（PM 未登录场景）
- [ ] 验证 2：OSS 同步后 `users.crm_sales_no` 填充
- [ ] 验证 3：所有单元测试 Green（现有 + 新增）
- [ ] 验证 4：架构测试全绿
- [ ] 验证 5：生产环境 tender 52/53/56 补偿完成

---

## 回退方案

若部署后发现回归：

1. **回退代码**：`git revert <commit>` + 重新部署
2. **回退数据**：`UPDATE tenders SET crm_opportunity_id = NULL WHERE id IN (...)`（手动清空错误关联）
3. **回退 crm_sales_no**：`UPDATE users SET crm_sales_no = NULL WHERE external_org_source_app = 'oss'`（无需回退，NULL 是历史状态）

回退后系统回到当前状态（tender 56 已用 SQL 修复，不影响）。
