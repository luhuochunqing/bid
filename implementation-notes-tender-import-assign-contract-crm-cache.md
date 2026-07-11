# Follow-up：D1/D2 契约收紧 + P1 导入批次 CRM 缓存

**分支**：`agent/kimi/tender-import-assign-contract-crm-cache`  
**叠在**：`79d4f057a`（批量导入传 operator username）之上  

## 范围

| ID | 内容 |
|----|------|
| D1 | 删除 `autoAssignIfPossible(Tender)` 单参重载；调用方必须显式传 `operatorUsername` |
| D2 | username 为空时本地映射失败后直接 noMatch，**不**再进 CRM |
| P1 | `CachedCrmLookupService` ThreadLocal 批次缓存；导入循环 `openBatch`/`closeBatch` |

## 设计决策

1. **缓存挂在 CRM 查询层而非 createTender 内部**  
   符合 031 Out of Scope（不改 createTender 业务逻辑）：`createTender` 仍走 `autoAssignIfPossible`；缓存对单条 create 透明（无 batch 时透传）。

2. **ThreadLocal 生命周期**  
   仅在 `executeImportAsync` 的 for 循环外包 `try/finally`，async 线程内安全；`closeBatch` 必须 finally 防泄漏。

3. **外部推送路径**  
   `TenderIntegrationCommandSupport` 显式 `autoAssignIfPossible(tender, null)` → 仅本地映射（无登录 OSS 上下文，符合去 03595 后的诚实失败）。

## 验证

```text
mvn test -Dtest=TenderAutoAssignmentServiceTest,CachedCrmLookupServiceTest,\
TenderImportAppServiceTest,TenderIntegrationCommandSupportTest,TenderCommandServiceTest
→ 通过
```

## 未做（刻意）

- A2 事务外 CRM HTTP  
- 导入结果展示 autoAssignedCount  
- 双路径 applyAssignmentResult 合并  
