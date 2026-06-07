# Change: add-wecom-oauth2-callback

## Why
目前系统已支持企业微信的基础配置，但尚未实现真实的 OAuth2 回调登录。该功能旨在打通企业微信扫码登录与 SSO 链路，提升用户体验并确保安全性。

## What Changes
- **ADDED** `/api/auth/wecom/callback` 接口用于接收并校验企业微信的 `code` 与 `state`。
- **ADDED** `WeComOAuthService` 用于与企业微信 API 通信，获取用户信息（openid/userid）。
- **ADDED** 用户绑定逻辑：支持企微用户与系统本地用户按手机号或 ID 对齐，实现自动登录。
- **MODIFIED** 前端登录页面，增加“企业微信扫码登录”入口及其状态感知逻辑。

## Impact
- Affected specs: `wecom-auth` (New)
- Affected code: `backend/src/main/java/com/xiyu/bid/auth/`, `src/views/Login.vue`
- Security: 需要处理 CSRF 防护（state 校验）与 JWT 签发。
