# 标讯变「投标中」后项目列表不显示 根因分析

> Issue: CO-274
> 日期: 2026-06-19
> 排查者: kimi
> 修复 PR: [#842](https://gitee.com/allinai888/bid/pulls/842)

---

## 现场还原

**症状素描**：标讯详情页点击「投标」后，标讯状态成功变成「投标中」，但跳转项目列表后**看不到对应项目**。

**边界划定**：
- 标讯列表页点击「立即投标」→ 正常跳转 `/project/create` 手工创建项目 ✅
- 标讯详情页点击「投标」→ 标讯变 BIDDING，但项目列表不显示 ❌
- 评估-审核后再点击「投标立项」的流程不受影响 ✅

**思维沙箱**：怀疑项目创建失败，或被列表筛选条件过滤掉。

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

标讯详情页「投标」按钮入口：

```javascript
// src/views/Bidding/detail/useTenderActions.js:16-21
const result = await tendersApi.participate(tenderRef.value.id)
if (result?.success && result?.data?.accepted) {
  try {
    await tendersApi.proceedToBid(tenderRef.value.id)
  } catch { /* 立项创建失败不影响投标状态 */ }
  ElMessage.success('投标成功，已生成项目立项待办')
  await loadTenderDetailFn()
}
```

- `participate` 负责把标讯状态改为 `BIDDING`。
- `proceedToBid` 负责创建项目。
- **关键点**：`proceedToBid` 的异常被 `catch {}` 静默吞掉，用户仍看到成功提示。

### Layer 2 — 核心逻辑层

`POST /api/tenders/{id}/bid` 调用链：

```
TenderEvaluationController.proceedToBid()
  → TenderEvaluationService.proceedToBid()
```

修复前，该服务方法强制要求标讯已经提交评估表：

```java
// backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationService.java
TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId)
        .orElseThrow(() -> new ResourceNotFoundException("标讯尚未提交评估"));
```

### Layer 3 — 数据层

- `participate` 只创建了一个「【待立项】」待办任务，并把 `task.projectId` 临时设为 `tenderId`。
- `proceedToBid` 因找不到 `TenderEvaluation` 直接抛 404，**项目未被创建**。
- 项目列表 `/api/projects` 查询的是 `Project` 表，没有项目自然查不到。

---

## 零号病人定位

**第一行错误（后端接口契约与前端入口不一致）：**

```java
// TenderEvaluationService.java（修复前）
TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId)
        .orElseThrow(() -> new ResourceNotFoundException("标讯尚未提交评估"));
```

`/bid` 被设计为「评估-审核后创建项目」，但详情页「投标」按钮走的是**快速投标**流程，通常不会先提交评估表。

**第二行错误（前端静默吞掉错误）：**

```javascript
// src/views/Bidding/detail/useTenderActions.js:20-21
try {
  await tendersApi.proceedToBid(tenderRef.value.id)
} catch { /* 立项创建失败不影响投标状态 */ }
```

即使后端返回 404，用户也看不到任何错误提示，导致问题被掩盖。

**必然性解释：**
- 快速投标路径不提交评估表 → `/bid` 必然找不到 `TenderEvaluation` → 抛 404。
- 前端 `catch {}` 不报告错误 → 用户以为成功 → 项目列表查询不到项目。

**状态变迁图：**

```
用户点击详情页「投标」
  → participate: TRACKING → BIDDING（成功）
  → 创建待办任务（projectId = tenderId）
  → proceedToBid: 查找 TenderEvaluation 失败 → 404
  → 前端 catch 吞掉异常 → 显示「投标成功」
  → 用户跳转项目列表 → 无项目
```

---

## 验证与修复

### 修复 diff

后端 `proceedToBid` 兼容无评估表场景，并复用 `participate` 已创建的待办任务：

```diff
// backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationService.java
- TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId)
-         .orElseThrow(() -> new ResourceNotFoundException("标讯尚未提交评估"));

- ProjectDTO projectDTO = ProjectDTO.builder()
-         ...
-         .managerId(evaluation.getEvaluatorId())
-         ...;

+ Long projectManagerId = tenderEvaluationRepository.findByTenderId(tenderId)
+         .map(TenderEvaluation::getEvaluatorId)
+         .filter(Objects::nonNull)
+         .orElse(adminId);

+ ProjectDTO projectDTO = ProjectDTO.builder()
+         ...
+         .managerId(projectManagerId)
+         ...;
```

同时新增 `reuseOrCreateInitiationTask()`，把 `participate` 创建的待办任务关联到新建的项目，避免重复待办。

### 最小验证

1. 在详情页点击「投标」后，检查 `/api/projects` 应返回新项目，状态为 `PENDING_INITIATION`。
2. 检查工作台中「【待立项】」任务只有一条，且 `projectId` 指向真实项目。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `useTenderActions.js:20-21` 静默吞异常；`TenderEvaluationService` 强制要求评估表 | ✅ |
| 必然性已证明 | 快速投标不提交评估表 → `/bid` 404 → 前端不报错 → 项目未创建 | ✅ |
| 最小验证已设计 | 详情页点击投标 → 项目列表应出现项目 | ✅ |
| 修复 diff 已提供 | PR #842 | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. `TenderEvaluationServiceTest`：无评估表时，`proceedToBid` 应以当前操作人作为项目经理创建项目。
2. `TenderToProjectListIntegrationTest`：快速投标完整链路 `participate → bid → GET /api/projects` 验证项目以 `PENDING_INITIATION` 状态出现在列表。
3. 未来新增「投标」入口时，必须同时覆盖「有评估表」和「无评估表」两种路径。

---

## 为什么之前没有提前发现

1. **集成测试只覆盖了评估-审核路径**：原有测试走 `submitEvaluation → review → bid`，该路径天然有 `TenderEvaluation`，无法暴露快速投标的问题。
2. **前端静默吞掉错误**：`catch {}` 让后端 404 对用户不可见，问题直到用户去项目列表查看才暴露。
3. **两个投标入口行为不一致**：列表页「立即投标」跳转手工创建页，详情页「投标」自动创建项目。后者没有独立 E2E/集成测试覆盖。
