# implementation-notes — CRM 鉴权闭环（!2002 修订）

## 审核结论（已吸收）

首版「只删 03595 + 后台静默降级」**不能合生产**（休克疗法）：

- 自动分配 / 外部推送 CRM 反查被截肢
- 无 operator webhook 会死信
- 401 只清 JWT 不清 OSS → 坏钥匙死循环

## 闭环设计（当前）

### 两条显式身份（禁止 silent 混用）

| 路径 | 入口 | ① OSS | ② generateToken | ③ 业务 |
|---|---|---|---|---|
| **用户** | `getValidTokenForUser(username)` | `OssUserTokenCache`（登录缓存） | 用户 nick/salesNo | CRM JWT |
| **系统集成账号** | `getValidTokenForSystem()` | 配置账号 oauth login | 配置 nick/salesNo | CRM JWT |

路由：`getValidTokenForCaller(username)`  
- username 非空 → 用户  
- username 为空 → **显式**系统集成账号（不是 03595 暗门回退）

### 配置（须为可生产登录的服务身份）

```
XIYU_CRM_OAUTH_USERNAME / XIYU_CRM_OAUTH_PASSWORD
XIYU_CRM_GENERATE_TOKEN_NICK_NAME / XIYU_CRM_GENERATE_TOKEN_SALES_NO
```

文档与 yml 注释标明：**系统集成账号**，禁止个人号。

### 401 联合清理

`handleUnauthorizedForUser`：清 CRM JWT + profile + **OSS token**  
`handleUnauthorizedForSystem`：清系统 OSS + CRM JWT 缓存  
`handleUnauthorizedForCaller`：按 username 路由

### Webhook

- 有 operator → 用户路径  
- 无 operator → 系统集成账号（自动分配/批量/历史任务可同步 CRM）

### WebhookCrmTokenResolver

缩为委托 `CrmAuthService`，消除双实现漂移。

## 运维前提（合生产前必须）

1. 向客户/CRM 侧 **申请专用系统集成账号**（非 03595 个人号）  
2. 写入生产 `backend.env` 上述 4 个变量并验证 `oauth/login` + `generateToken`  
3. 建议同批合 !2001（§4.2 operator_username）以减少结果回调对系统账号的依赖  

## 验证

```bash
mvn test -Dtest=CrmAuthServiceTest,...ArchitectureTest,FPJavaArchitectureTest
```
