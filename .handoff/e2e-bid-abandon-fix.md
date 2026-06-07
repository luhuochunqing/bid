# Handoff: E2E 测试 bug 修复 (§4.2.6 bid/abandon)

## 背景

当前 `e2e/bidding-bid-abandon.spec.js` 测试失败，但**非被测功能问题**——是测试代码本身的 bug。

## 失败的两个测试用例

### Test 1: `bid and abandon buttons visible on EVALUATED tender detail page`

**症状**：`POST /api/tenders/{id}/evaluation/submit` 返回 4xx

**根因**：测试使用的 request body 格式错误：
```javascript
// ❌ 当前代码（错误）
body: JSON.stringify({ evaluationResult: 'PASS', remark: '§4.2.6 E2E 测试' })

// ✅ 应改为：直接创建 EVALUATED 状态的 tender，或调用正确的评估提交 API
// 方案 A：创建时就设为 EVALUATED
status: 'EVALUATED'

// 方案 B：使用正确的评估提交 payload（TenderEvaluationSubmitRequest）
// 需包含：projectBackground, competitorAnalysis, contractPeriodStart/End,
//        shortlistedCount, platformServiceFee 等必填字段
```

### Test 2: `no bid/abandon buttons on TRACKING tender detail page`

**症状**：登录后直接跳转 `/bidding/{id}`，被重定向到 `/login`

**根因**：`injectSession` 设置 sessionStorage 后，页面会调用 `/api/auth/me` 刷新 session，未 mock 该接口导致 401 → redirect to login。

参考 `e2e/project-create-full-flow.spec.js` 的正确写法：
```javascript
await injectSession(page, session)

// ✅ 需要添加：mock /api/auth/me
await page.route('**/api/auth/me', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      success: true,
      data: {
        id: session.user.id,
        username: session.user.username,
        fullName: session.user.fullName,
        role: session.user.role,
        roleProfile: session.user.roleProfile
      }
    })
  })
})
```

## 修复要求

1. 修改 `e2e/bidding-bid-abandon.spec.js`
2. 保持测试意图不变：验证 EVALUATED 状态显示投标/放弃按钮，TRACKING 状态不显示
3. 修复后运行 `PLAYWRIGHT_DISABLE_API_BOOTSTRAP=1 PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:18084 PLAYWRIGHT_WEB_BASE_URL=http://127.0.0.1:1318 npx playwright test e2e/bidding-bid-abandon.spec.js --project=chromium --reporter=list` 验证通过

## 协作资源

- 前端端口：1318
- 后端端口：18084
- 数据库：xiyu_bid_cursor
- 启动脚本：`./scripts/start-frontend.sh` / `./scripts/start-backend.sh`
