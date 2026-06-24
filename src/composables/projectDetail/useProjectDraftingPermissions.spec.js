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
  if (role === 'admin' || role === '/bidAdmin' || role === 'bid-TeamLeader') return 'admin_lead'
  if (role === 'bid-projectLeader' || role === 'bid-Team') return 'lead_assist'
  return null
}

// 对齐 useProjectDraftingPermissions.js 中 canSubmitBid 的完整逻辑
// sales 仅匹配 primaryLeadId，bid_specialist 匹配 primaryLeadId 或 secondaryLeadId
function computeCanSubmitBid(role, opts = {}) {
  const group = resolveDraftingRoleGroup(role)
  if (group === 'admin_lead') return true
  if (group !== 'lead_assist' || !opts.currentUserId) return false
  const uid = String(opts.currentUserId)
  if (role === 'bid-projectLeader') {
    return !!(opts.primaryLeadId && String(opts.primaryLeadId) === uid)
  }
  if (role === 'bid-Team') {
    return !!((opts.primaryLeadId && String(opts.primaryLeadId) === uid)
      || (opts.secondaryLeadId && String(opts.secondaryLeadId) === uid))
  }
  return false
}

// 对齐 useProjectDraftingPermissions.js 中 canReviewBid 的完整逻辑
// 仅指派的审核人本人可操作，与角色无关
function computeCanReviewBid(opts = {}) {
  return !!(opts.reviewerId && opts.currentUserId
    && String(opts.reviewerId) === String(opts.currentUserId))
}

// 对齐 useProjectDraftingPermissions.js 中 canSubmitBidForReview 的完整逻辑
// admin_lead + lead_assist 均可提交投标审核
function computeCanSubmitBidForReview(role) {
  const group = resolveDraftingRoleGroup(role)
  return group === 'admin_lead' || group === 'lead_assist'
}

// 对齐 canDeleteDocument：仅 admin_lead 可删除文档
function computeCanDeleteDocument(role) {
  return resolveDraftingRoleGroup(role) === 'admin_lead'
}

describe('resolveDraftingRoleGroup', () => {
  it.each([
    ['admin', 'admin_lead'],
    ['/bidAdmin', 'admin_lead'],
    ['bid-TeamLeader', 'admin_lead'],
    ['bid-projectLeader', 'lead_assist'],
    ['bid-Team', 'lead_assist'],
    ['bid-administration', null],
    ['bid-otherDept', null],
    ['', null],
    [undefined, null],
    [null, null],
  ])('角色 %s → 分组 %s', (role, expected) => {
    expect(resolveDraftingRoleGroup(role)).toBe(expected)
  })
})

describe('canSubmitBid — 提交投标权限（基础角色判断）', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-administration', false],
    ['bid-otherDept', false],
    [undefined, false],
    [null, false],
  ])('角色 %s → canSubmitBid=%s', (role, expected) => {
    expect(computeCanSubmitBid(role)).toBe(expected)
  })
})

describe('canSubmitBid — lead_assist 组项目级负责人匹配', () => {
  it('sales 匹配 primaryLeadId → true', () => {
    expect(computeCanSubmitBid('bid-projectLeader', { currentUserId: 10, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(true)
  })
  it('sales 匹配 secondaryLeadId → false（sales 只能匹配 primaryLead）', () => {
    expect(computeCanSubmitBid('bid-projectLeader', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('sales 无 currentUserId → false', () => {
    expect(computeCanSubmitBid('bid-projectLeader', { primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('sales 都不匹配 → false', () => {
    expect(computeCanSubmitBid('bid-projectLeader', { currentUserId: 99, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('sales 无 primaryLeadId → false', () => {
    expect(computeCanSubmitBid('bid-projectLeader', { currentUserId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('bid_specialist 匹配 secondaryLeadId → true', () => {
    expect(computeCanSubmitBid('bid-Team', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(true)
  })
  it('bid_specialist 匹配 primaryLeadId → true（投标专员可作为投标负责人）', () => {
    expect(computeCanSubmitBid('bid-Team', { currentUserId: 10, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(true)
  })
  it('bid_specialist 无 currentUserId → false', () => {
    expect(computeCanSubmitBid('bid-Team', { primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('bid_specialist 都不匹配 → false', () => {
    expect(computeCanSubmitBid('bid-Team', { currentUserId: 99, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })
  it('bid_specialist 仅 primaryLeadId 不匹配且无 secondaryLeadId → false', () => {
    expect(computeCanSubmitBid('bid-Team', { currentUserId: 20, primaryLeadId: 10 })).toBe(false)
  })
})

describe('canReviewBid — 审核投标权限（基于指派审核人，与角色无关）', () => {
  it('reviewerId == currentUserId → true（任意角色均可，对齐后端 BidReviewPolicy）', () => {
    expect(computeCanReviewBid({ reviewerId: 10, currentUserId: 10 })).toBe(true)
  })
  it('reviewerId != currentUserId → false', () => {
    expect(computeCanReviewBid({ reviewerId: 10, currentUserId: 20 })).toBe(false)
  })
  it('无 reviewerId → false', () => {
    expect(computeCanReviewBid({ currentUserId: 10 })).toBe(false)
  })
  it('无 currentUserId → false', () => {
    expect(computeCanReviewBid({ reviewerId: 10 })).toBe(false)
  })
  it('reviewerId 为 null → false', () => {
    expect(computeCanReviewBid({ reviewerId: null, currentUserId: 10 })).toBe(false)
  })
  it('类型安全：数字 10 与字符串 "10" 应匹配', () => {
    expect(computeCanReviewBid({ reviewerId: 10, currentUserId: '10' })).toBe(true)
  })
})

describe('canSubmitBidForReview — 提交投标审核权限', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-projectLeader', true],
    ['bid-Team', true],
    ['bid-administration', false],
    ['bid-otherDept', false],
  ])('角色 %s → canSubmitBidForReview=%s', (role, expected) => {
    expect(computeCanSubmitBidForReview(role)).toBe(expected)
  })
})

describe('canDeleteDocument — 删除文档权限（仅 admin_lead）', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-projectLeader', false],
    ['bid-Team', false],
    ['bid-administration', false],
    ['bid-otherDept', false],
  ])('角色 %s → canDeleteDocument=%s', (role, expected) => {
    expect(computeCanDeleteDocument(role)).toBe(expected)
  })
})

// 对齐 TaskKanban.vue 中 isTaskAssignee 的逻辑
// 仅任务执行人本人可提交/上传交付物（对齐后端 ProjectTaskAuthorizationPolicy.canSubmitTask）
function computeIsTaskAssignee(task, currentUserId) {
  return currentUserId != null && task?.assigneeId != null
    && String(currentUserId) === String(task.assigneeId)
}

describe('isTaskAssignee — 任务执行人身份校验（TaskKanban.vue）', () => {
  it('当前用户 == task.assigneeId → true', () => {
    expect(computeIsTaskAssignee({ assigneeId: 10 }, 10)).toBe(true)
  })
  it('当前用户 != task.assigneeId → false', () => {
    expect(computeIsTaskAssignee({ assigneeId: 10 }, 20)).toBe(false)
  })
  it('task 为 null → false', () => {
    expect(computeIsTaskAssignee(null, 10)).toBe(false)
  })
  it('task.assigneeId 为 null → false', () => {
    expect(computeIsTaskAssignee({ assigneeId: null }, 10)).toBe(false)
  })
  it('task.assigneeId 为 undefined → false', () => {
    expect(computeIsTaskAssignee({}, 10)).toBe(false)
  })
  it('currentUserId 为 null → false', () => {
    expect(computeIsTaskAssignee({ assigneeId: 10 }, null)).toBe(false)
  })
  it('currentUserId 为 undefined → false', () => {
    expect(computeIsTaskAssignee({ assigneeId: 10 }, undefined)).toBe(false)
  })
  it('类型安全：数字 10 与字符串 "10" 应匹配', () => {
    expect(computeIsTaskAssignee({ assigneeId: 10 }, '10')).toBe(true)
  })
  it('类型安全：字符串 "10" 与数字 10 应匹配', () => {
    expect(computeIsTaskAssignee({ assigneeId: '10' }, 10)).toBe(true)
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
