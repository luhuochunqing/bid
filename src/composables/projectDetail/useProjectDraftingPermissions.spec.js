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

// 对齐 useProjectDraftingPermissions.js 中 isProjectLeadMatch 的完整逻辑
// 当前用户是否为该项目分配的投标负责人/辅助人员
function computeIsProjectLeadMatch(role, opts = {}) {
  const group = resolveDraftingRoleGroup(role)
  if (group !== 'lead_assist') return false
  if (!opts.currentUserId) return false
  const uid = String(opts.currentUserId)
  const primaryLeadId = opts.primaryLeadId != null ? String(opts.primaryLeadId) : null
  const secondaryLeadId = opts.secondaryLeadId != null ? String(opts.secondaryLeadId) : null
  if (role === 'bid-projectLeader') {
    return !!(primaryLeadId && primaryLeadId === uid)
  }
  if (role === 'bid-Team') {
    return !!((primaryLeadId && primaryLeadId === uid)
      || (secondaryLeadId && secondaryLeadId === uid))
  }
  return false
}

// 对齐 useProjectDraftingPermissions.js 中 canSubmitBid 的完整逻辑
function computeCanSubmitBid(role, opts = {}) {
  const group = resolveDraftingRoleGroup(role)
  if (group === 'admin_lead') return true
  return computeIsProjectLeadMatch(role, opts)
}

// 对齐 useProjectDraftingPermissions.js 中 canReviewBid 的完整逻辑
// 仅指派的审核人本人可操作，与角色无关
function computeCanReviewBid(opts = {}) {
  return !!(opts.reviewerId && opts.currentUserId
    && String(opts.reviewerId) === String(opts.currentUserId))
}

// 对齐 useProjectDraftingPermissions.js 中 canSubmitBidForReview 的完整逻辑
// CO-355：与 canSubmitBid 同口径——admin_lead 直通，lead_assist 需匹配项目级 lead
function computeCanSubmitBidForReview(role, opts = {}) {
  return computeCanSubmitBid(role, opts)
}

// 对齐 canDeleteDocument：仅 admin_lead 可删除文档
function computeCanDeleteDocument(role) {
  return resolveDraftingRoleGroup(role) === 'admin_lead'
}

// 对齐 canManageTaskBoardTopActions：admin_lead ∪ lead_assist 可操作任务看板顶部按钮
function computeCanManageTaskBoardTopActions(role) {
  const group = resolveDraftingRoleGroup(role)
  return group === 'admin_lead' || group === 'lead_assist'
}

// 对齐 useProjectDraftingPermissions.js 中 canSelectReviewer 的完整逻辑
function computeCanSelectReviewer(role, opts = {}) {
  const group = resolveDraftingRoleGroup(role)
  if (group === 'admin_lead') return true
  return computeIsProjectLeadMatch(role, opts)
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

describe('canSubmitBidForReview — 提交投标审核权限（CO-355：对齐 canSubmitBid 的 lead 匹配）', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-administration', false],
    ['bid-otherDept', false],
  ])('角色 %s 无 lead context → %s', (role, expected) => {
    expect(computeCanSubmitBidForReview(role)).toBe(expected)
  })
  it('bid-projectLeader 匹配 primaryLead → true', () => {
    expect(computeCanSubmitBidForReview('bid-projectLeader', { currentUserId: 10, primaryLeadId: 10 })).toBe(true)
  })
  it('bid-projectLeader 非 primaryLead → false', () => {
    expect(computeCanSubmitBidForReview('bid-projectLeader', { currentUserId: 99, primaryLeadId: 10 })).toBe(false)
  })
  it('bid-Team 匹配 secondaryLead → true', () => {
    expect(computeCanSubmitBidForReview('bid-Team', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(true)
  })
  it('bid-Team 都不匹配 → false', () => {
    expect(computeCanSubmitBidForReview('bid-Team', { currentUserId: 99, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
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

describe('canManageTaskBoardTopActions — 任务看板顶部按钮权限（admin_lead ∪ lead_assist）', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-projectLeader', true],
    ['bid-Team', true],
    ['bid-administration', false],
    ['bid-otherDept', false],
    ['staff', false],
    ['sales', false],
    ['', false],
    [null, false],
    [undefined, false],
  ])('角色 %s → canManageTaskBoardTopActions=%s', (role, expected) => {
    expect(computeCanManageTaskBoardTopActions(role)).toBe(expected)
  })
})

describe('canSelectReviewer — 选择标书审核人权限（admin_lead + 项目投标负责人/辅助人员）', () => {
  it.each([
    ['admin', true],
    ['/bidAdmin', true],
    ['bid-TeamLeader', true],
    ['bid-projectLeader', false],
    ['bid-Team', false],
    ['bid-administration', false],
    ['bid-otherDept', false],
  ])('角色 %s 无 lead context → canSelectReviewer=%s', (role, expected) => {
    expect(computeCanSelectReviewer(role)).toBe(expected)
  })

  it('bid-projectLeader 匹配 primaryLeadId → true', () => {
    expect(computeCanSelectReviewer('bid-projectLeader', { currentUserId: 10, primaryLeadId: 10 })).toBe(true)
  })

  it('bid-projectLeader 不匹配 primaryLeadId → false', () => {
    expect(computeCanSelectReviewer('bid-projectLeader', { currentUserId: 10, primaryLeadId: 99 })).toBe(false)
  })

  it('bid-projectLeader 匹配 secondaryLeadId → false', () => {
    expect(computeCanSelectReviewer('bid-projectLeader', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
  })

  it('bid-Team 匹配 primaryLeadId → true', () => {
    expect(computeCanSelectReviewer('bid-Team', { currentUserId: 10, primaryLeadId: 10 })).toBe(true)
  })

  it('bid-Team 匹配 secondaryLeadId → true', () => {
    expect(computeCanSelectReviewer('bid-Team', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(true)
  })

  it('bid-Team 都不匹配 → false', () => {
    expect(computeCanSelectReviewer('bid-Team', { currentUserId: 99, primaryLeadId: 10, secondaryLeadId: 20 })).toBe(false)
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

// ── CO-355：真实调用 composable（reactive 包装后验证解包 + lead 匹配）──
// 消费端 DraftingStage.vue 用 reactive() 包装 composable 返回值，使 perm.xxx 自动解包 ComputedRef。
// 这里复现该消费方式做集成验证，并补充关键权限的真实调用，防下次解包类 bug 漏检。
import { setActivePinia, createPinia } from 'pinia'
import { reactive, h, defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user'
import { useProjectDraftingPermissions } from './useProjectDraftingPermissions'

describe('CO-355: reactive(composable) 真实调用（解包 + 项目级 lead 匹配）', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function permWith(role, opts = {}) {
    const userStore = useUserStore()
    userStore.currentUser = { id: opts.currentUserId ?? 1, role }
    return reactive(useProjectDraftingPermissions({
      primaryLeadId: opts.primaryLeadId ?? null,
      secondaryLeadId: opts.secondaryLeadId ?? null,
      currentUserId: opts.currentUserId ?? 1,
      reviewerId: opts.reviewerId ?? null,
    }))
  }

  it('canSubmitBidForReview 解包为 boolean 而非 ComputedRef（模板 v-if 可用性前置）', () => {
    expect(typeof permWith('admin').canSubmitBidForReview).toBe('boolean')
  })
  it('非该项目 lead 的 bid-projectLeader：canSubmitBidForReview 为 false', () => {
    expect(permWith('bid-projectLeader', { primaryLeadId: 999, currentUserId: 1 }).canSubmitBidForReview).toBe(false)
  })
  it('匹配 primaryLead 的 bid-projectLeader：canSubmitBidForReview 为 true', () => {
    expect(permWith('bid-projectLeader', { primaryLeadId: 1, currentUserId: 1 }).canSubmitBidForReview).toBe(true)
  })
  it('匹配 secondaryLead 的 bid-Team：canSubmitBidForReview 为 true', () => {
    expect(permWith('bid-Team', { primaryLeadId: 999, secondaryLeadId: 1, currentUserId: 1 }).canSubmitBidForReview).toBe(true)
  })
  it('admin：canSubmitBidForReview 为 true（不依赖 lead 匹配）', () => {
    expect(permWith('admin', { primaryLeadId: 999, currentUserId: 1 }).canSubmitBidForReview).toBe(true)
  })

  // 补充关键权限的真实调用验证（之前只有内联副本，无法暴露 composable 实际行为）
  it('canSubmitBid 解包为 boolean 且按 lead 匹配', () => {
    expect(typeof permWith('admin').canSubmitBid).toBe('boolean')
    expect(permWith('bid-projectLeader', { primaryLeadId: 999, currentUserId: 1 }).canSubmitBid).toBe(false)
    expect(permWith('bid-projectLeader', { primaryLeadId: 1, currentUserId: 1 }).canSubmitBid).toBe(true)
  })
  it('canReviewBid 解包为 boolean 且按指派审核人匹配（与角色无关）', () => {
    expect(typeof permWith('admin').canReviewBid).toBe('boolean')
    expect(permWith('admin', { reviewerId: 1, currentUserId: 1 }).canReviewBid).toBe(true)
    expect(permWith('admin', { reviewerId: 999, currentUserId: 1 }).canReviewBid).toBe(false)
  })
  it('canManageBidFiles 解包为 boolean 且按角色组', () => {
    expect(typeof permWith('admin').canManageBidFiles).toBe('boolean')
    expect(permWith('admin').canManageBidFiles).toBe(true)
    expect(permWith('bid-otherDept').canManageBidFiles).toBe(false)
  })
  it('canManageTaskBoardTopActions 解包为 boolean 且按角色组（admin_lead ∪ lead_assist）', () => {
    expect(typeof permWith('admin').canManageTaskBoardTopActions).toBe('boolean')
    expect(permWith('admin').canManageTaskBoardTopActions).toBe(true)
    expect(permWith('/bidAdmin').canManageTaskBoardTopActions).toBe(true)
    expect(permWith('bid-TeamLeader').canManageTaskBoardTopActions).toBe(true)
    expect(permWith('bid-projectLeader').canManageTaskBoardTopActions).toBe(true)
    expect(permWith('bid-Team').canManageTaskBoardTopActions).toBe(true)
    expect(permWith('bid-otherDept').canManageTaskBoardTopActions).toBe(false)
    expect(permWith('bid-administration').canManageTaskBoardTopActions).toBe(false)
    expect(permWith('staff').canManageTaskBoardTopActions).toBe(false)
  })
  it('canSelectReviewer 解包为 boolean 且 admin_lead / 项目投标负责人/辅助人员为 true', () => {
    expect(typeof permWith('admin').canSelectReviewer).toBe('boolean')
    expect(permWith('admin').canSelectReviewer).toBe(true)
    expect(permWith('bid-TeamLeader').canSelectReviewer).toBe(true)
    expect(permWith('bid-projectLeader', { primaryLeadId: 1, currentUserId: 1 }).canSelectReviewer).toBe(true)
    expect(permWith('bid-projectLeader', { primaryLeadId: 999, currentUserId: 1 }).canSelectReviewer).toBe(false)
    expect(permWith('bid-Team', { primaryLeadId: 1, currentUserId: 1 }).canSelectReviewer).toBe(true)
    expect(permWith('bid-Team', { primaryLeadId: 999, secondaryLeadId: 1, currentUserId: 1 }).canSelectReviewer).toBe(true)
    expect(permWith('bid-Team', { primaryLeadId: 999, secondaryLeadId: 998, currentUserId: 1 }).canSelectReviewer).toBe(false)
    expect(permWith('bid-otherDept').canSelectReviewer).toBe(false)
  })
})

// 组件级验证：reactive(perm) + v-if 的真实渲染链路（CO-355 验收证据）
describe('CO-355: 组件渲染——提交标书审核按钮可见性', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountSubmitReviewButton(role, opts = {}) {
    const userStore = useUserStore()
    userStore.currentUser = { id: opts.currentUserId ?? 1, role }
    const TestComp = defineComponent({
      setup() {
        const perm = reactive(useProjectDraftingPermissions({
          primaryLeadId: opts.primaryLeadId ?? null,
          secondaryLeadId: opts.secondaryLeadId ?? null,
          currentUserId: opts.currentUserId ?? 1,
          reviewerId: opts.reviewerId ?? null,
        }))
        return () => perm.canSubmitBidForReview
          ? h('button', { class: 'submit-review-btn' }, '提交标书审核')
          : null
      },
    })
    return mount(TestComp)
  }

  it('admin：按钮渲染', () => {
    const w = mountSubmitReviewButton('admin', { primaryLeadId: 999, currentUserId: 1 })
    expect(w.find('.submit-review-btn').exists()).toBe(true)
  })
  it('非该项目 lead 的 bid-projectLeader：按钮不渲染', () => {
    const w = mountSubmitReviewButton('bid-projectLeader', { primaryLeadId: 999, currentUserId: 1 })
    expect(w.find('.submit-review-btn').exists()).toBe(false)
  })
  it('匹配 primaryLead 的 bid-projectLeader：按钮渲染', () => {
    const w = mountSubmitReviewButton('bid-projectLeader', { primaryLeadId: 1, currentUserId: 1 })
    expect(w.find('.submit-review-btn').exists()).toBe(true)
  })
})
