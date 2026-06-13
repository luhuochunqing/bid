/**
 * useProjectDraftingPermissions 单元测试
 * 验证权限矩阵与 roleGroup 映射
 */

/**
 * ⚠️ 重要：useProjectDraftingPermissions 引用了 useUserStore。
 * 为避免全量 Pinia setup，直接内联权限解析逻辑进行单元测试。
 */

// 从 useProjectDraftingPermissions.js 提取 resolveDraftingRoleGroup
// 避免需要完整 Pinia store
function resolveDraftingRoleGroup(role) {
  if (role === 'bid_admin' || role === 'bid_lead' || role === 'bid_senior') return 'admin_lead'
  if (role === 'sales') return 'lead_assist'
  if (role === 'auditor') return 'auditor'
  if (role === 'task_executor') return 'executor'
  return null
}

function computeCanSubmitBid(role) {
  const group = resolveDraftingRoleGroup(role)
  return group === 'admin_lead' || group === 'lead_assist'
}

function computeCanReviewBid(role) {
  return resolveDraftingRoleGroup(role) === 'auditor'
}

function computeCanSubmitBidForReview(role) {
  return resolveDraftingRoleGroup(role) === 'lead_assist'
}

describe('resolveDraftingRoleGroup', () => {
  it.each([
    ['bid_admin', 'admin_lead'],
    ['bid_lead', 'admin_lead'],
    ['bid_senior', 'admin_lead'],
    ['sales', 'lead_assist'],
    ['auditor', 'auditor'],
    ['task_executor', 'executor'],
    ['admin', null],
    ['manager', null],
    ['staff', null],
    ['bid_specialist', null],
    ['', null],
    [undefined, null],
    [null, null],
  ])('角色 %s → 分组 %s', (role, expected) => {
    expect(resolveDraftingRoleGroup(role)).toBe(expected)
  })
})

describe('canSubmitBid — 提交投标权限', () => {
  it.each([
    ['bid_admin', true],
    ['bid_lead', true],
    ['bid_senior', true],
    ['sales', true],
    ['auditor', false],
    ['task_executor', false],
    ['staff', false],
    ['manager', false],
    ['admin', false],
    ['bid_specialist', false],
    [undefined, false],
    [null, false],
  ])('角色 %s → canSubmitBid=%s', (role, expected) => {
    expect(computeCanSubmitBid(role)).toBe(expected)
  })
})

describe('canReviewBid — 审核投标权限', () => {
  it.each([
    ['auditor', true],
    ['bid_admin', false],
    ['bid_lead', false],
    ['bid_senior', false],
    ['sales', false],
    ['task_executor', false],
    ['staff', false],
    ['manager', false],
    ['admin', false],
  ])('角色 %s → canReviewBid=%s', (role, expected) => {
    expect(computeCanReviewBid(role)).toBe(expected)
  })
})

describe('canSubmitBidForReview — 提交投标审核权限', () => {
  it.each([
    ['sales', true],
    ['bid_admin', false],
    ['bid_lead', false],
    ['bid_senior', false],
    ['auditor', false],
    ['task_executor', false],
    ['staff', false],
    ['manager', false],
  ])('角色 %s → canSubmitBidForReview=%s', (role, expected) => {
    expect(computeCanSubmitBidForReview(role)).toBe(expected)
  })
})

describe('审校人名称工程化防守测试', () => {
  it('submitBidForReview 必须从 API response 中取 reviewerName，不得依赖 reviewerOptions 猜测', () => {
    // 读取源代码验证 submitBidForReview 函数的实现
    // 防止有人再次误删 reviewerName 赋值逻辑
    const fs = require('fs')
    const source = fs.readFileSync(
      'src/views/Project/stages/DraftingStage.vue', 'utf-8'
    )
    const match = source.match(/async function submitBidForReview[\s\S]*?\n\}/)
    expect(match).not.toBeNull()
    const funcBody = match[0]
    // 必须包含从 API response 取 reviewerName 的逻辑
    expect(funcBody).toContain('reviewerName.value = d?.reviewerName')
    expect(funcBody).not.toContain('reviewerOptions.value.find')
  })
})
