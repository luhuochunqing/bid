# CRM 商机负责人被自动分配覆盖，导致分错人

> Issue: CO-302 / CO-305 后续
> 日期: 2026-06-26
> 排查者: mimo

---

## 现场还原

**症状素描**: CRM 推送标讯 581 后，王凯毅（工号 08687，User.id 5052）作为 CRM 商机负责人本应担任项目负责人，但实际数据库 `project_manager_id=2556`（郑蓉蓉，工号 06234）。王凯毅在标讯详情和项目列表中都看不到这条数据。

**边界划定**:
- 标讯 569 / 项目 91（历史数据）：`project_manager_id=8687`（工号直接存入）→ 已通过 PR #1173 + 服务器 SQL 修复
- 标讯 581（PR #1173 部署后新建）：`project_manager_id=2556`（已是 User.id，但是错的人）→ 本次修复

**思维沙箱**:
1. PR #1173 修了"工号直接存入"问题，但新数据 still 分错人 → 根因不在工号转换层
2. 王凯毅本应被分配（CRM 商机接口返回的 leader 是他），但最终落库是郑蓉蓉 → 中间有覆盖
3. 郑蓉蓉是海德鲁铝型材的本地 `CrmProjectMapping` 映射 → 自动分配逻辑覆盖了 CRM 商机负责人

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口层

`TenderIntegrationCommandService.createNewTender` 调用链：

```
mapper.toEntity(request)
  → crmTenderLinkService.linkIfPresent(tender, crmId)   ← 设置 CRM 商机负责人（王凯毅 5052）
  → support.applyCrmFallback(tender, crmId, null)
  → tenderRepository.save(tender)                       ← 落库时 manager=王凯毅
  → support.tryAutoAssign(saved)                         ← 按 purchaserName 匹配本地映射，覆盖成郑蓉蓉
```

### Layer 2 — CRM 商机负责人设置（正确路径）

`CrmTenderLinkService.applyLeaderAndStatus` 通过 CRM 商机接口查到 leader 后：

```java
userRepository.findByEmployeeNumber(leader.projectLeaderNo()).ifPresentOrElse(
    user -> {
        tender.setProjectManagerId(user.getId());      // ← 5052（王凯毅 User.id）
        tender.setProjectManagerName(user.getFullName());
    },
    () -> {
        tender.setProjectManagerName(leader.projectLeaderName());  // 工号未匹配时仅设 name
    }
);
```

这一步是对的——`projectManagerId` 被正确设为 User.id。

### Layer 3 — 自动分配覆盖（零号病人）

`TenderIntegrationCommandSupport.tryAutoAssign` **不区分标讯是否已有 CRM 商机负责人**，无条件执行：

```java
void tryAutoAssign(Tender tender) {
    try {
        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);
        if (result.isMatched()) {
            applyAssignmentResult(tender, result);   // ← 直接覆盖 projectManagerId/Name
            // ...
        }
    } catch (RuntimeException e) { ... }
}
```

`autoAssignIfPossible` 按 `purchaserName`（海德鲁铝型材）匹配本地 `CrmProjectMapping` 表，命中郑蓉蓉（06234），返回 `AssignmentResult.success`。`applyAssignmentResult` 随后把 `projectManagerId` 覆盖为 2556（郑蓉蓉 User.id）。

**必然性解释**：
- `tryAutoAssign` 设计初衷是给"未关联商机的标讯"兜底分配负责人
- 但它不检查"是否已有负责人"，对"已有 CRM 商机负责人"的标讯也会执行
- `applyAssignmentResult` 是无条件覆盖，没有"仅在原值为空时才设"的保护
- CRM 商机接口返回的 leader（王凯毅）和本地映射表的结果（郑蓉蓉）不一致时，后者必然覆盖前者

---

## 零号病人定位

**第一行错误**：`TenderIntegrationCommandSupport.tryAutoAssign` 缺少 guard clause

```java
// TenderIntegrationCommandSupport.java（修复前）
void tryAutoAssign(Tender tender) {
    try {
        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);
        // ↑ 未检查 tender.getProjectManagerId() / getProjectManagerName() 是否已有值
```

**必然性解释**：
- 设计 `tryAutoAssign` 时假设所有进入此方法的标讯都未分配负责人
- 但 `createNewTender` 调用链中，`linkIfPresent` 已经为关联商机的标讯设置了负责人
- 两个方法各自正确，组合起来出错——典型的"组合失效"

---

## 验证与修复

### 修复 diff

```java
// TenderIntegrationCommandSupport.java
 void tryAutoAssign(Tender tender) {
+    if (tender.getProjectManagerId() != null || hasText(tender.getProjectManagerName())) {
+        log.info("Tender {} already has project manager (id={}, name={}), skip auto-assignment",
+                tender.getId(), tender.getProjectManagerId(), tender.getProjectManagerName());
+        return;
+    }
     try {
         AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);
         // ...
+    }
+}
+
+private boolean hasText(String value) {
+    return value != null && !value.trim().isEmpty();
 }
```

### 最小验证

1. 新增 2 个用例（共 8/8 通过）：
   - `tryAutoAssign_alreadyHasManager_skipAutoAssignment`：已有 id+name 时跳过，验证不调用 `autoAssignService`
   - `tryAutoAssign_hasNameOnly_skipAutoAssignment`：只有 name 没 id 时也跳过（CRM 工号未匹配本地用户时的兜底场景）
2. 11 个相关测试类 138 用例全绿
3. 架构测试全绿

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `tryAutoAssign` 入口缺 guard clause | ✅ |
| 必然性已证明 | `linkIfPresent` 设的 manager 必然被 `tryAutoAssign` 覆盖 | ✅ |
| 最小验证已设计 | 2 个新增测试用例 + 138 相关用例全绿 | ✅ |
| 修复 diff 已提供 | guard clause + hasText helper | ✅ |
| 防复发测试已设计 | 测试断言"已有 manager 时不调用 autoAssignIfPossible"，回退立即失败 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发策略

1. **覆盖类操作必须检查原值**：任何"设置 manager"的方法在执行前应检查是否已有值，避免无意覆盖
2. **调用链组合测试**：`createNewTender` 这类多步链路必须有端到端测试，覆盖"前置步骤已设值 + 后置步骤不应覆盖"场景
3. **guard clause 优于业务逻辑修改**：在入口加保护比修改 `applyAssignmentResult` 的覆盖逻辑更安全（影响面小）

---

## 与 PR #1173 的关系

| PR | 修复内容 | 状态 |
|---|---|---|
| #1162/#1163/#1167 | `TenderCommandService.applyAssignmentResult` 用 `ProjectManagerIdResolver` 替代 `Long.valueOf(employeeNo)` | 已部署 |
| #1173 | `TenderIntegrationCommandSupport.applyAssignmentResult` 同上修复 + 解耦状态转换异常 | 已部署 |
| **#1179（本 PR）** | **`tryAutoAssign` 加 guard clause，CRM 商机负责人优先** | **本次** |

#1173 修了"工号直接存入"问题，但暴露了"自动分配覆盖 CRM 商机负责人"这一更深层问题。#1179 是 #1173 的后续修复。

---

## 相关文档

- [decisions.md §3](./decisions.md) — CRM 商机负责人优先于本地映射的架构决策
- [crm-integration-lessons.md §11](./crm-integration-lessons.md) — projectManagerId 存储与调用链覆盖经验
- [lessons-learned.md §20](./lessons-learned.md) — 分阶段修复的存量数据策略
