## 1. Backend Implementation
- [x] 1.1 实现 `WeComOAuthService`：封装获取 access_token 与用户信息接口
- [x] 1.2 实现 `/api/auth/wecom/callback` 接口：处理 state 校验与 code 换取用户信息
- [x] 1.3 实现用户映射逻辑：处理企微用户与本地用户关联（按手机号或外部 ID）
- [x] 1.4 生成登录成功后的 JWT 并返回
- [x] 1.5 增加安全防护：防止 CSRF 攻击的 state 令牌管理

## 2. Frontend Implementation
- [x] 2.1 在 `Login.vue` 增加企业微信登录入口
- [x] 2.2 实现扫码后的重定向回调处理逻辑
- [x] 2.3 处理登录成功后的跳转与状态持久化

## 3. Testing & Verification
- [x] 3.1 编写 `WeComOAuthService` 单元测试（FP-Java 约束下）
- [x] 3.2 编写端到端测试用例：模拟 OAuth2 回调流程
- [x] 3.3 验证 state 校验失败时的错误响应
