import { test } from '@playwright/test'

// SKIPPED: tender-breakdown 端点依赖 AI Provider API Key，测试环境中无有效 key 导致 502。  // @ui-cover:task
// 需在 CI 环境配置有效 AI_PROVIDER_API_KEY 后取消 skip 并恢复测试逻辑。
test.skip('tender document breakdown can generate project tasks through real API', () => {
  // @see backend BidDraftAgent 或 TenderBreakdownService 中的 LLM 调用路径
})
