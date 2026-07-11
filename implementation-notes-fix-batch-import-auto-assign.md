# 批量导入标讯后 CRM 自动分配失效 — 实施笔记

## 场景澄清（用户口径）

- **是**：标讯中心 → **批量导入 Excel** → 按招标主体反查 CRM 客户负责人 → 自动分配项目经理  
- **不是**：CRM 商机同步 / 外部推送 `TenderIntegrationCommandSupport` 路径  

## 根因

1. 批量导入：`TenderImportAppService` → `createTender(dto, userId)` → `tryAutoAssign`  
2. `autoAssignIfPossible` 里 CRM 反查写死 `tryAutoAssignFromCrm(tender, **null**)`  
3. 删除全局 03595 后，`CrmAuthService.getValidTokenForUser(null)` 直接 `TokenUnavailableException`  
4. 异常被吞，静默 `noMatch` → 标讯停在 **待分配**  

异步导入线程无 SecurityContext，**必须**用 `userId → username` 显式传操作人，才能查 `OssUserTokenCache` 换 CRM JWT。

## 改动

| 文件 | 变更 |
|------|------|
| `TenderAutoAssignmentService` | 新增 `autoAssignIfPossible(tender, username)`；CRM 路径透传 username；空 username 打 warn |
| `TenderCommandService` | `createTender`：`resolveUsername(userId)` 后传入 tryAutoAssign |
| 单测 | mock 改为两参；补「批量导入传 username」断言 |

**未改**：CRM 外部同步 `TenderIntegrationCommandSupport`（仍走无 username 的 1 参重载，与本 bug 无关）。

## 修复后仍可能 noMatch 的条件（非本 PR）

- 招标主体与 CRM 公司名非精确 equals  
- CRM 负责人无 `saleType=19`（集团项目经理）  
- 工号在本地 User 无匹配或已停用  
- 导入人 OSS token 未缓存/已过期（需重新登录）  

## 验证

```text
mvn test -Dtest=TenderAutoAssignmentServiceTest,TenderCommandServiceTest  → 通过
```
