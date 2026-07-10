# implementation-notes — 删除全局 03595 happy path

## 目标

真人操作的 CRM 三步鉴权全部走用户身份：

1. 用户 OSS token（登录缓存 `OssUserTokenCache`）
2. `generateToken` 换 CRM JWT（`CrmUserTokenCache`）
3. 业务接口带 CRM JWT

全局账号 `applyOssToken` / `getValidToken()` / `getValidOssToken()` **删除**，不再 silent fallback。

## 决策

| 决策 | 说明 |
|---|---|
| 后台任务 username=null | 降级 empty/null/TokenUnavailable，**不**再调 03595 |
| Webhook 无 operator | 抛 `TokenUnavailableException` → 重试后死信（产品已接受） |
| 测试端点 system-token | 返回错误文案，强制传用户 OSS token |
| 与 !2001 关系 | §4.2 operator_username 仍建议合入，否则结果确认 webhook 无用户身份会死信 |

## 残留

- 配置项 `oauth-username` 等可能仍在 yml，但代码无调用
- 无用户上下文的自动分配 / 外部推送 CRM 反查会静默降级（不阻断主流程）
- 未来若后台必须查 CRM，需单独系统服务账号（可生产登录的专用身份，不是 03595 个人号）

## 验证

```bash
mvn test -Dtest=CrmAuthServiceTest,CrmCompanySearchServiceTest,...ArchitectureTest,...
# 全绿
```
