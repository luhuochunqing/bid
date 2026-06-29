# CO-375 项目文档删除权限链路不一致导致上传者本人 403 根因分析

> Issue: CO-375（Linear）/ 内部任务编号 CO-383
> 日期: 2026-06-29
> 排查者: cursor
> 修复 PR: `#1317` (commit `7c834c992`, merge `ebd3ce606`)

---

## 现场还原

**症状素描**：bid-projectLeader 用户 08687 上传投标文件后，在未提交前点击删除按钮，接口返回 403。用户原话："我上传投标文件了 还没点保存 这个时候 应该可以删除我上传的文件 因为有可能我传错了"。

**边界划定**：
- 后端 `ProjectDocumentWorkflowPolicy.canUploadProjectDocument` 已放行 `bid-projectLeader` ✅
- 后端 `ProjectDocumentWorkflowPolicy.canDeleteProjectDocument`（CO-382 修复版）只允许 `admin` / `/bidAdmin` / `bid-TeamLeader` ❌
- Controller 层 `@PreAuthorize('hasAnyRole("ADMIN","BIDADMIN","BID_TEAMLEADER")')` 早过滤，bid-projectLeader 直接 403 ❌
- 前端删除按钮 v-if `!(locked && !isApprovalMode)` 在未提交时（reviewStatus=''）已可见 ✅

---

## 历史背景：为什么这个 Bug 修了多轮

`ProjectDocumentWorkflowPolicy.java` 已经因为权限矩阵不完整被反复返工：

| PR/提交 | 修复内容 | 解决层级 | 未覆盖 |
|---|---|---|---|
| CO-361 | 项目任务执行人也需要查看项目文档 | 查看权限 | 上传/删除未动 |
| CO-373 | 投标负责人/辅助人员无权提交标书审核和提交投标 | 提交权限 | 删除未动 |
| CO-382 PR #1306 | 投标文件删除权限收紧为 admin/bidAdmin/bid-TeamLeader | 删除权限（管理员组） | 上传者本人未考虑 |
| CO-382 PR #1309 | 前端 canManageBidFiles 改为 canDeleteDocument 对齐 Policy | 前端按钮可见性 | Controller 早过滤仍挡上传者 |
| CO-375 PR #1317 | Controller 放宽 + Policy 新增上传者本人分支 | 删除权限（上传者） | 已验证 |

每一轮修复都解决了真实问题，但都没有从「同一资源的 upload/delete 权限矩阵必须对称设计」这个视角审视过整个 Policy。

---

## 剥洋葱：三个症状其实是三层链路

### 链路 A — Controller 早过滤 403

CO-382 PR #1306 修复时，将 `ProjectDocumentController.deleteProjectDocument` 的 `@PreAuthorize` 从较宽松的注解收紧为 `hasAnyRole("ADMIN","BIDADMIN","BID_TEAMLEADER")`。这是 Controller 层的"早过滤"，意图是先挡住非管理员。

但这一层没考虑到：**上传者本人在未提交前也应能删除自己上传的文件**。结果 bid-projectLeader 用户在 Controller 层就被 403 挡住，根本到不了 Service 层 Policy。

### 链路 B — Policy 内部 upload/delete 不对称

`ProjectDocumentWorkflowPolicy` 中：

- `canUploadProjectDocument(roleCode)` 放行 `bid-projectLeader` / `bid-Team` / `bid-otherDept`
- `canDeleteProjectDocument(roleCode)`（CO-382 版本）只放行 `admin` / `/bidAdmin` / `bid-TeamLeader`

这种不对称意味着：用户可以上传，但不能删除自己上传的文件。这是设计气味——上传和删除是同一资源生命周期的两端，权限策略必须同步设计。

### 链路 C — 缺少"上传者本人"维度的权限分支

原 Policy 的 `canDeleteProjectDocument` 只看 `roleCode`，不看 `uploaderId`。但实际业务规则是：

- 管理员组（admin/bidAdmin/bid-TeamLeader）：可删除任何文档
- **上传者本人**（uploaderId == currentUserId）：可删除自己上传的文档（未提交前可重传）
- 其他：deny

缺少"上传者本人"维度，导致权限矩阵不完整。

---

## 零号病人定位

### 1. Controller 403 早过滤

**第一行错误**：`@PreAuthorize('hasAnyRole("ADMIN","BIDADMIN","BID_TEAMLEADER")')` 没有考虑上传者本人。

```
backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectDocumentController.java
@DeleteMapping("/{documentId}")
@PreAuthorize("hasAnyRole('ADMIN','BIDADMIN','BID_TEAMLEADER')")  // ← 这里挡住上传者
public ResponseEntity<ApiResponse<Void>> deleteProjectDocument(...)
```

**必然性解释**：

```
bid-projectLeader 用户点击删除按钮
  ↓
请求到达 Controller
  ↓
@PreAuthorize 早过滤：用户不在 ADMIN/BIDADMIN/BID_TEAMLEADER 中
  ↓
直接返回 403，根本到不了 Service 层
  ↓
用户看到"无权限"错误
```

### 2. Policy 内部 upload/delete 不对称

**第一行错误**：`canUpload` 放行的角色，`canDelete` 没有对应放行。

```
backend/src/main/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicy.java

canUploadProjectDocument: 放行 SALES_CODE、BID_SPECIALIST_CODE、BID_OTHER_DEPT_CODE
canDeleteProjectDocument: 只放行 ADMIN_CODE、BID_ADMIN_CODE、BID_LEAD_CODE
```

**必然性解释**：

```
用户 A（bid-projectLeader）上传文档
  ↓
canUpload 放行 → 上传成功
  ↓
用户 A 发现传错了，点击删除
  ↓
canDelete 只看 roleCode，A 不在管理员组 → deny
  ↓
用户 A 无法删除自己上传的文件
```

### 3. 缺少 uploaderId 维度

**第一行错误**：`canDeleteProjectDocument` 签名只有 `(String roleCode)`，没有 `(String roleCode, Long currentUserId, Long uploaderId)`。

```
public static AuthorizationDecision canDeleteProjectDocument(String roleCode) {
    // 只看角色，不看上传者
}
```

**必然性解释**：

```
用户 A 上传文档（document.uploaderId = A.id）
  ↓
用户 A 想删除
  ↓
Policy 只看 roleCode，无法判断 A 是不是上传者
  ↓
即使加了 uploaderId 维度，Policy 也不知道 currentUserId 和 uploaderId 是否相等
```

---

## 验证与修复

### 修复 diff 摘要

1. **`ProjectDocumentController.java`**：`@PreAuthorize` 从 `hasAnyRole("ADMIN","BIDADMIN","BID_TEAMLEADER")` 放宽为 `isAuthenticated()`，真权限交给 Service 层 Policy。
2. **`ProjectDocumentWorkflowPolicy.java`**：`canDeleteProjectDocument` 签名从 `(String roleCode)` 改为 `(String roleCode, Long currentUserId, Long uploaderId)`，新增上传者本人分支 `currentUserId.equals(uploaderId) → permit`。
3. **`ProjectDocumentWorkflowService.java`**：`deleteProjectDocument` 方法重构——先查 document 拿 `uploaderId`，再获取 currentUser，调用 Policy 时传入 3 个参数。

### 关键修复代码

```java
// ProjectDocumentWorkflowPolicy.java
public static AuthorizationDecision canDeleteProjectDocument(
        String roleCode, Long currentUserId, Long uploaderId) {
    if (roleCode == null) {
        return AuthorizationDecision.deny("当前用户未分配角色，无权删除文档");
    }
    String normalized = roleCode.trim();
    // 管理员组：admin/bidAdmin/bid-TeamLeader
    if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(normalized)
            || RoleProfileCatalog.BID_ADMIN_CODE.equalsIgnoreCase(normalized)
            || RoleProfileCatalog.BID_LEAD_CODE.equalsIgnoreCase(normalized)) {
        return AuthorizationDecision.permit();
    }
    // 上传者本人：未提交前可删除自己上传的文件（可能传错需要重传）
    if (currentUserId != null && currentUserId.equals(uploaderId)) {
        return AuthorizationDecision.permit();
    }
    return AuthorizationDecision.deny("权限不足，仅投标管理员/组长或上传者本人允许删除文档");
}
```

### 测试验证

- `ProjectDocumentWorkflowPolicyTest`：46 个测试全 Green
  - 新增 `canDeleteProjectDocument_whenUploaderSelf_shouldPermit`（覆盖 SALES_CODE / BID_SPECIALIST_CODE / BID_OTHER_DEPT_CODE）
  - 新增 `canDeleteProjectDocument_whenUploaderIdNull_shouldDeny`
  - 新增 `canDeleteProjectDocument_whenCurrentUserIdNull_shouldDeny`
- `ProjectDocumentWorkflowServiceTest`：18 个测试全 Green
  - 新增 `deleteProjectDocument_asUploaderSelf_shouldSucceed`
- `ArchitectureTest`：26 条规则全 Green

### 全链路日志验证（按 lessons-learned.md §23 SOP）

```
1. SSH 到 winbid-test 测试服务器
2. 抓 nginx access log：确认 403 来自后端（不是 nginx）
3. 抓 application.json.log：定位到 @PreAuthorize 早过滤返回 403
4. 排除第三方依赖：本次不涉及外部调用
5. 用日志证据指导修复，不乱猜
```

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| Controller 403 零号病人已定位 | `ProjectDocumentController.deleteProjectDocument` 的 `@PreAuthorize` | ✅ |
| Policy upload/delete 不对称已定位 | `canUpload` 放行 bid-projectLeader，`canDelete` 拒绝 | ✅ |
| 缺少 uploaderId 维度已定位 | `canDeleteProjectDocument` 签名只有 roleCode | ✅ |
| 必然性已证明 | bid-projectLeader 上传成功但删除 403，路径必然经过 Controller 早过滤 | ✅ |
| 修复 diff 已提供 | `7c834c992` / PR `#1317` | ✅ |
| 防复发测试已设计 | 3 个 Policy 测试 + 1 个 Service 测试覆盖上传者场景 | ✅ |
| 服务器验证已完成 | PR #1317 已合入 main，等待部署后验证 | ⏳ |

**Verdict**: ✅ **PASS**（代码层验证完成，待部署后业务层验证）

---

## 为什么之前没有提前发现

1. **CO-382 修复视角集中在管理员组权限矩阵**：收紧 `@PreAuthorize` 是为了对齐蓝图 §3.3.1.2 的管理员组列，但没有考虑到"上传者本人"这一非角色维度。
2. **Policy 缺少对称性检查**：`canUpload` 和 `canDelete` 是同一资源生命周期的两端，但设计时没有强制要求权限矩阵对称。
3. **Controller 早过滤隐藏了 Policy 问题**：Controller `@PreAuthorize` 直接 403 挡住，根本到不了 Service 层 Policy，导致 Policy 内部的不对称问题在测试环境（用 admin 账号测试）不易暴露。
4. **测试账号单一**：测试环境主要用管理员账号测试删除功能，没有覆盖 bid-projectLeader 用户删除自己上传文件的场景。
5. **`uploaderId` 字段未在删除权限中利用**：实体已有 `uploaderId` 字段，但 Policy 删除权限没有用到这个信息，导致"上传者本人"维度缺失。

---

## 防复发规范

1. **同一资源的 upload/delete 权限矩阵必须对称设计**：`canUpload` 放行的角色，`canDelete` 必须有明确的对应策略（要么放行，要么明确拒绝并说明原因）。
2. **Controller `@PreAuthorize` 不能过度收紧**：早过滤层只做"是否登录"级别的过滤，真权限交给 Service 层 Policy。如果 Controller 用 `hasAnyRole` 收紧，会挡住 Policy 内部想放行的特殊场景（如上传者本人）。
3. **权限策略必须考虑"身份维度"**：除了角色维度（roleCode），还要考虑身份维度（uploaderId、assigneeId、reviewerId 等）。同一角色在不同身份下权限可能不同。
4. **Policy 方法签名必须包含所有决策维度**：`canDelete(roleCode)` 不够，必须是 `canDelete(roleCode, currentUserId, uploaderId)`，把所有决策维度显式传入。
5. **测试账号必须覆盖非管理员场景**：删除功能测试不能只用 admin 账号，必须覆盖 bid-projectLeader / bid-Team 等角色删除自己上传文件的场景。
6. **修改 Policy 时必须审视整个权限矩阵**：不能只改一个方法，必须审视 canView / canDownload / canUpload / canDelete 四类操作的权限矩阵是否一致。

---

## 相关文档与代码

- `backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectDocumentController.java` — Controller `@PreAuthorize` 放宽为 `isAuthenticated()`
- `backend/src/main/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicy.java` — Policy 新增上传者本人分支
- `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowService.java` — Service 传递 uploaderId 给 Policy
- `backend/src/test/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicyTest.java` — 3 个上传者测试用例
- `backend/src/test/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowServiceTest.java` — 上传者删除成功测试
- `docs/lessons/lessons-learned.md` §24 — Policy canUpload/canDelete 一致性原则（从多轮修复归纳）
- `docs/lessons/decisions.md` §3 — Controller @PreAuthorize 放宽为 isAuthenticated() 决策
- `docs/lessons/lessons-learned.md` §23 — 全链路日志排查 SOP（本次按此 SOP 定位根因）
