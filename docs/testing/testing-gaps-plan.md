# 未覆盖测试缺口 — 验证计划

## 缺口 1：跨浏览器兼容性（Firefox/Safari）

### 当前状态
Playwright 仅配置了 Chromium。Firefox 和 WebKit 项目已添加但仅在本地可用。

### 验证方案
**本地手动执行：**
```bash
# 需要先安装浏览器
npx playwright install firefox webkit

# 运行核心流程
npx playwright test commercial-main-flow --project=firefox --config playwright.config.js
npx playwright test commercial-main-flow --project=webkit --config playwright.config.js

# 运行全部 CI 测试
npx playwright test --project=firefox --config playwright.config.js
npx playwright test --project=webkit --config playwright.config.js
```

**预期差异：**
- Safari 不支持 `navigator.clipboard.writeText()`（SolutionReuse 复制功能备选方案已存在）
- Firefox CSS Grid 在某些旧组件中可能有布局差异
- Element Plus 组件库已在主流浏览器中通过兼容性测试

### CI 扩展（可选）
当 Firefox/WebKit 测试稳定后，可在 CI 中增加 `matrix` 策略：
```yaml
strategy:
  matrix:
    browser: [chromium, firefox]
```
当前不建议做 CI 全量跨浏览器测试（时间成本高），建议在 UAT 阶段手工验证 Firefox 和 Safari。

---

## 缺口 2：大数据量性能测试

### 当前状态
k6 脚本已创建（`k6-tests/load-test.js`），但缺乏真实数据量和基准指标。

### 验证方案
```bash
# 安装 k6
brew install k6

# 基础负载测试（需要后端运行在 18080）
k6 run k6-tests/load-test.js

# 基准测试（无并发）
k6 run --vus 1 --duration 30s k6-tests/load-test.js

# 压力测试（高并发）
k6 run --vus 200 --duration 2m k6-tests/load-test.js
```

### 需要准备的数据量

| 模块 | 建议数据量 | 测试目标 |
|------|-----------|---------|
| 标讯 | 10,000 条 | 列表分页 + 全文搜索 |
| 项目 | 5,000 条 | 项目详情加载 |
| 知识库 | 3,000 条 | 资质/案例搜索 |
| 用户 | 200 个 | 组织管理加载 |

### 性能目标
| 指标 | 目标 | 测量方式 |
|------|------|---------|
| P95 响应时间 | < 2s | k6 http_req_duration |
| 错误率 | < 1% | Rate metric |
| 前端 FCP | < 2s | Lighthouse |
| 前端 LCP | < 3s | Lighthouse |
| 并发用户 | 100 | k6 VUs |

---

## 缺口 3：第三方集成验证

### 当前状态
- CRM：前端卡片已完成，接口已预留
- OA/审批流：接口已预留，等待联调
- 企业微信：配置入口已完成
- 组织架构同步：SDK 方案已定

### 验证步骤
1. **企业微信 SSO 登录**
   - 需要真实 Corp ID、Agent ID、Secret
   - 验证 SSO 登录流程
   - 验证消息推送

2. **泛微 OA 集成**
   - 需要 OA 地址、账号、密钥
   - 验证审批流回调
   - 验证费用/保证金退还流程

3. **CRM 对接**
   - 需要 CRM 地址、Token、Client ID
   - 验证客户信息同步
   - 验证标讯转入

4. **组织架构同步**
   - 需要西域提供配置信息
   - 验证部门/用户同步

### API 测试（无需真实环境）
`api-tests/integration.http` 中已有集成配置保存/读取的测试用例。
联调前可用 Mock 模式验证接口契约。

---

## 缺口 4：系统设置页面 E2E

### 当前状态
排除在 CI 之外。Settings.vue 已修复 `Promise.allSettled` 错误处理。

### 恢复 CI 的条件
- Settings 页面组件不再因单个 API 失败而崩溃 ✅（已修复）
- CI 中后端 API 对 admin 用户的 settings 端点响应正常（需验证）
- 如果仍失败，可能是 `useOrganizationSettings` 内部 API 调用的凭据问题

### 手动验证
```bash
# 用 admin 登录后访问
curl http://127.0.0.1:18080/api/settings \
  -H "Authorization: Bearer $TOKEN"
# 确认返回 200
```
