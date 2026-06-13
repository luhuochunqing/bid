## ADDED Requirements

### Requirement: 企业微信 OAuth2 回调登录
系统必须支持通过企业微信 OAuth2 流程进行用户身份认证。

#### Scenario: 成功登录并绑定用户
- **WHEN** 用户通过扫码授权，携带有效的 code 和 state 回调到系统接口
- **THEN** 系统校验 state 合法性，通过 code 获取企微用户信息，并签发本地 JWT 登录令牌

#### Scenario: State 校验失败
- **WHEN** 回调请求中的 state 与系统生成的 state 不匹配
- **THEN** 系统拒绝认证请求，返回 403 错误，并记录安全审计日志

#### Scenario: 未绑定用户首次登录
- **WHEN** 获取到的企微用户在系统中未找到对应的本地用户映射
- **THEN** 系统应重定向到绑定页面或返回特定错误提示用户进行手机号绑定
