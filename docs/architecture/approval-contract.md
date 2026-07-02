# 审批接口契约规范

> 本文件是全项目审批类接口（approve/reject）的契约单一源。
> 新增审批接口时，**必须**参照本规范，禁止独立设计契约风格。

## 1. 适用范围

本规范适用于所有提供「审批通过」或「驳回」操作的 REST Controller，包括但不限于：

- 项目立项审核（ProjectInitiationController）
- 标书审核（ProjectDraftingController）
- 结项审核（ProjectClosureController）
- CA 借用审批（CaCertificateController）
- 费用审批（ExpenseController）
- 其他新增审批类接口

## 2. 命名规则

### 2.1 DTO 命名

| 场景 | DTO 命名 | 示例 |
|---|---|---|
| 审批通过 | `XxxApprovalRequest` | `DraftingApprovalRequest`, `ClosureApprovalRequest` |
| 驳回 | `XxxRejectionRequest` | `DraftingRejectionRequest`, `InitiationRejectionRequest` |
| 通用审批（通过+驳回同结构） | `XxxApprovalRequest`（共用） | `CaApprovalRequest`（CA 借用通过和驳回共用） |

### 2.2 字段命名

| 字段名 | 含义 | 必填性 |
|---|---|---|
| `comment` | 审批意见/驳回原因（**统一字段名**） | 驳回必填；通过可选（看业务） |
| 其他业务字段 | 按语义命名 | 按业务要求 |

**禁止**使用 `reason`、`rejectionReason`、`reviewerNotes` 等同义词替代 `comment`。

### 2.3 路径命名

```
POST /api/{resource}/{id}/approve     # 审批通过
POST /api/{resource}/{id}/reject      # 驳回
```

## 3. Controller 签名规范

### 3.1 标准签名（通过操作无需意见）

```java
@PostMapping("/approve")
public ResponseEntity<ApiResponse<XxxDTO>> approve(
        @PathVariable Long id,
        @Valid @RequestBody XxxApprovalRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    // request.comment 可为空（通过操作允许不填意见）
    return ResponseEntity.ok(ApiResponse.success("approved",
            service.approve(id, userDetails, request.getComment())));
}
```

### 3.2 标准签名（驳回操作必填意见）

```java
@PostMapping("/reject")
public ResponseEntity<ApiResponse<XxxDTO>> reject(
        @PathVariable Long id,
        @Valid @RequestBody XxxRejectionRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    // request.comment 必填（@NotBlank）
    return ResponseEntity.ok(ApiResponse.success("rejected",
            service.reject(id, userDetails, request.getComment())));
}
```

### 3.3 禁止的签名风格

```java
// ❌ 禁止：用 Map 接收 body
@PostMapping("/approve")
public ResponseEntity<?> approve(@PathVariable Long id,
        @RequestBody(required = false) Map<String, String> payload) { ... }

// ❌ 禁止：approve 无 body（破坏契约统一）
@PostMapping("/approve")
public ResponseEntity<?> approve(@PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails) { ... }

// ❌ 禁止：字段名用 reason/rejectionReason 而非 comment
public class XxxRejectionRequest {
    @NotBlank private String reason;  // 应该是 comment
}
```

## 4. DTO 规范

### 4.1 通过操作 DTO（comment 可选）

```java
@Data
public class XxxApprovalRequest {
    /** 审批意见（可选，通过操作允许不填） */
    private String comment;

    // 其他业务字段按需添加（如 primaryLeadUserId 等）
}
```

### 4.2 驳回操作 DTO（comment 必填）

```java
@Data
public class XxxRejectionRequest {
    @NotBlank(message = "驳回原因不能为空")
    private String comment;
}
```

### 4.3 通过+驳回共用 DTO（comment 必填）

> **取舍说明**：当通过+驳回共用同一个 DTO 时，`comment` 字段必须加 `@NotBlank`，
> 即通过操作也必须填写 comment。如果业务要求**通过操作 comment 可选**，
> 则**必须**拆分为独立的 `XxxApprovalRequest`（comment 可选）+ `XxxRejectionRequest`（comment 必填），
> 不得共用。参照 §4.1 + §4.2。

```java
@Data
public class XxxApprovalRequest {
    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
```

## 5. 前端调用规范

### 5.1 API 模块（禁止默认参数兜底）

```javascript
// ✅ 正确：强制显式传对象
async approve(id, data) {
  return httpClient.post(`${BASE}/${id}/approve`, data)
},
async reject(id, data) {
  return httpClient.post(`${BASE}/${id}/reject`, data)
}
```

```javascript
// ❌ 禁止：默认参数假防御（data = {} 对 '' 不生效）
async approve(id, data = {}) {
  return httpClient.post(`${BASE}/${id}/approve`, data)
}
```

### 5.2 调用方（显式传对象）

```javascript
// ✅ 正确：显式传对象
await api.approve(id, { comment: '同意' })
await api.reject(id, { comment: '不符合要求' })

// ✅ 正确：通过操作允许空意见
await api.approve(id, { comment: '' })
```

```javascript
// ❌ 禁止：传字符串
await api.approve(id, '')
await api.reject(id, reason)

// ❌ 禁止：不传第二参数
await api.approve(id)
```

## 6. 测试规范

### 6.1 后端 WebMvcTest（必须）

每个审批类 Controller 必须有 `@WebMvcTest`，覆盖 4 种反序列化场景：

```java
@WebMvcTest(XxxController.class)
class XxxControllerWebMvcTest {

    @Test
    void approve_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/xxx/{id}/approve", 1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_emptyString_returns400() throws Exception {
        mockMvc.perform(post("/api/xxx/{id}/approve", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_blankComment_returns400() throws Exception {
        mockMvc.perform(post("/api/xxx/{id}/approve", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());  // 仅驳回必填时
    }

    @Test
    void approve_validComment_returns200() throws Exception {
        mockMvc.perform(post("/api/xxx/{id}/approve", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk());
    }
}
```

### 6.2 前端 E2E（建议）

审批类操作建议有 E2E 烟雾测试，覆盖真实点击→请求→后端 200 落库。

## 7. 迁移历史接口

迁移历史接口（如从 `Map<String,String>` 改为 DTO）时，必须：

1. 新增 DTO 类
2. 修改 Controller 签名
3. **同步更新所有前端调用点**（grep 确认无遗漏）
4. 补 WebMvcTest
5. 跑全量测试验证

## 8. Code Review 检查清单

新增审批类接口 PR 时，Reviewer 必须确认：

- [ ] Controller 签名符合本规范（`@Valid @RequestBody XxxApprovalRequest`）
- [ ] DTO 字段名是 `comment`（不是 `reason`/`rejectionReason`）
- [ ] 前端 API 模块无 `data = {}` 默认参数
- [ ] 前端调用方显式传对象
- [ ] 有 WebMvcTest 覆盖空 body / 空字符串 / 缺字段 / 正常 4 种场景
- [ ] PR 描述中标注"参照了 docs/architecture/approval-contract.md 规范"

## 9. 历史背景

本规范源于 CO-459 审批 bug（PR #1516 修复）。事故根因是项目内审批接口存在四种契约风格，新接口选哪种全凭个人偏好，导致前后端契约脱节。详见 `docs/lessons/lessons-learned.md §31`。
