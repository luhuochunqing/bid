# API Contract: 平台账号密码查看接口

**Endpoint**: `GET /api/platform/accounts/{id}/password`
**Auditable**: `VIEW_PASSWORD` 操作日志（不变）

## 修改前（IllegalStateException → 409）

| 场景 | HTTP 状态码 | Response Body | Sentry 上报 | 日志级别 |
|---|---|---|---|---|
| `currentUser == null` | 409 | `{"code":409,"message":"系统状态冲突，请刷新后重试"}` | ✅ ERROR | ERROR |
| `bid-Team` 非绑定联系人 | 409 | `{"code":409,"message":"系统状态冲突，请刷新后重试"}` | ✅ ERROR | ERROR |
| 非特权非 `bid-Team` 角色 | 409 | `{"code":409,"message":"系统状态冲突，请刷新后重试"}` | ✅ ERROR | ERROR |
| 特权角色（admin/bidAdmin/bid-TeamLeader） | 200 | `"<decrypted-password>"` | N/A | N/A |
| `bid-Team` 且为绑定联系人 | 200 | `"<decrypted-password>"` | N/A | N/A |

## 修改后（AccessDeniedException → 403）

| 场景 | HTTP 状态码 | Response Body | Sentry 上报 | 日志级别 |
|---|---|---|---|---|
| `currentUser == null` | 403 | `{"code":403,"message":"权限不足，无法访问该资源"}` | ❌ 不上报 | WARN |
| `bid-Team` 非绑定联系人 | 403 | `{"code":403,"message":"权限不足，无法访问该资源"}` | ❌ 不上报 | WARN |
| 非特权非 `bid-Team` 角色 | 403 | `{"code":403,"message":"权限不足，无法访问该资源"}` | ❌ 不上报 | WARN |
| 特权角色（admin/bidAdmin/bid-TeamLeader） | 200 | `"<decrypted-password>"` | N/A | N/A |
| `bid-Team` 且为绑定联系人 | 200 | `"<decrypted-password>"` | N/A | N/A |

## 契约变更要点

1. **状态码变更**：409 → 403（仅权限校验失败场景）
2. **Message 变更**：从"系统状态冲突，请刷新后重试"改为"权限不足，无法访问该资源"
3. **Sentry 上报变更**：从 ERROR 级上报改为 WARN 级不上报
4. **成功路径不变**：特权角色和绑定联系人查看密码的行为完全不变

## 前端契约影响

- 前端代码只需识别 4xx 状态码即可（403 和 409 都属于 4xx），**无需修改前端代码**。
- 如前端有专门处理 409 "系统状态冲突" 弹窗的逻辑，需确认是否需要调整为通用 4xx 处理。建议检查 `src/api/` 下相关请求拦截器。
