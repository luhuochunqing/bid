/**
 * useProjectDraftingPermissions
 *
 * 标书制作页操作按钮权限控制。
 * 按蓝图 3.3.1.2 权限矩阵实现，各按钮的可见/可用性基于用户在当前项目中的角色。
 *
 * 蓝图权限矩阵：
 * | 功能                    | 投标管理员/组长 | 投标负责人/辅助人 | 审核人 | 任务执行人 |
 * |------------------------|:---:|:---:|:---:|:---:|
 * | 查看页面                |  ✅  |  ✅  |  ✅  |  ✅  |
 * | AI评分标准解析          |  ✅  |  ✅  |  —   |  —   |
 * | AI自动拆解任务          |  ✅  |  ✅  |  —   |  —   |
 * | AI智能推荐案例          |  ✅  |  ✅  |  —   |  —   |
 * | AI合规性检查            |  ✅  |  ✅  |  —   |  —   |
 * | 创建任务                |  ✅  |  ✅  |  —   |  —   |
 * | 提交任务                |  —   |  —   |  —   |  ✅  |
 * | 审核任务（通过/驳回）    |  ✅  |  ✅  |  —   |  —   |
 * | 上传文档                |  ✅  |  ✅  |  ✅  |  ✅  |
 * | 下载文档                |  ✅  |  ✅  |  —   |  —   |
 * | 删除文档                |  ✅  |  —   |  —   |  —   |
 * | 提交投标审核            |  —   |  ✅  |  —   |  —   |
 * | 审核投标（通过/驳回）    |  —   |  —   |  ✅  |  —   |
 * | 提交投标                |  ✅  |  ✅  |  —   |  —   |
 *
 * 角色代码说明（RoleProfile）：
 * - /bidAdmin  → 投标管理员（蓝图"投标管理员/组长"）
 * - bid-SystemAdmin → OSS 投标系统管理员（独立角色，权限组同管理级，不再映射为 admin）
 * - bid-TeamLeader   → 投标组长（蓝图"投标管理员/组长"）
 * - bid-projectLeader      → 投标项目负责人（蓝图"投标负责人/辅助人"）
 * - bid-Team → 投标专员，可作为投标负责人或辅助人员（蓝图"投标负责人/辅助人"）
 * - admin      → 本地系统管理员（归入"投标管理员/组长"权限组）
 * - 其他（bid-administration, bid-otherDept 等）→ 默认可见页面，但无特殊操作权限
 */

import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { resolveOpt } from '@/utils/resolveOpt.js'
import { ROLE_CODES, isBidAdminLevelRole } from '@/constants/roleCodes.js'

/**
 * 角色分组：按权限矩阵列合并
 */
function resolveDraftingRoleGroup(role) {
  if (isBidAdminLevelRole(role) || role === ROLE_CODES.BID_LEAD) return 'admin_lead'
  if (role === ROLE_CODES.SALES || role === ROLE_CODES.BID_SPECIALIST) return 'lead_assist'
  return null
}

/**
 * CO-383：删除文档权限纯函数（与后端 ProjectDocumentWorkflowPolicy.canDeleteProjectDocument 对齐）。
 *
 * 规则：
 *   1. admin_lead（admin//bidAdmin/bid-SystemAdmin/bid-TeamLeader）直通 → permit
 *   2. 上传者本人（currentUserId == uploaderId）→ permit（不管角色，对齐用户需求
 *      "应该不管什么角色 都可以在没有保存的时候 删除"）
 *   3. 其他 → deny
 *
 * 设计为纯函数（非 computed）是因为 uploaderId 是 file 级数据，每个 file 不同，
 * 不能放进组件级 perm。DraftingStage.vue 在 v-if 中按 file 调用本函数。
 *
 * @param {{role: string, currentUserId: *, uploaderId: *}} ctx
 * @returns {boolean}
 */
export function canDeleteDocumentAs({ role, currentUserId, uploaderId }) {
  if (resolveDraftingRoleGroup(role) === 'admin_lead') return true
  const uid = currentUserId != null ? String(currentUserId) : null
  const upid = uploaderId != null ? String(uploaderId) : null
  return !!(uid && upid && uid === upid)
}

export function useProjectDraftingPermissions(opts = {}) {
  // opts 支持传入 { primaryLeadId, secondaryLeadId, currentUserId, reviewerId, reviewerIds }
  // 用于在组件中二次约束：
  //   - 仅该项目分配的投标负责人/辅助人员 + 管理员/组长可提交投标
  //   - 仅指派的审核人（reviewerId == currentUserId 或 reviewerIds.includes(currentUserId)）可审核投标
  // 注意：opts 中的值可以是原始值或 ref/computed，内部统一通过 resolveOpt 解包
  // CO-484：reviewerIds（数组）优先；若未传则回退到 reviewerId（单值，向后兼容）
  const userStore = useUserStore()

  const roleGroup = computed(() => resolveDraftingRoleGroup(userStore.userRole))
  const role = computed(() => userStore.userRole)

  const isAdminLead = computed(() => roleGroup.value === 'admin_lead')
  const isLeadAssist = computed(() => roleGroup.value === 'lead_assist')
  const isExecutor = computed(() => roleGroup.value === 'executor')

  /**
   * 当前用户是否为该项目分配的投标负责人/辅助人员。
   * 与后端 {@link BidSubmissionAuthorizationPolicy} 对齐：
   * - bid-projectLeader 匹配 primaryLeadId
   * - bid-Team 匹配 primaryLeadId 或 secondaryLeadId
   */
  const isProjectLeadMatch = computed(() => {
    if (roleGroup.value !== 'lead_assist') return false
    const currentUserId = resolveOpt(opts.currentUserId)
    if (!currentUserId) return false
    const uid = String(currentUserId)
    const primaryLeadId = resolveOpt(opts.primaryLeadId)
    const secondaryLeadId = resolveOpt(opts.secondaryLeadId)
    if (role.value === 'bid-projectLeader') {
      return !!(primaryLeadId != null && String(primaryLeadId) === uid)
    }
    if (role.value === 'bid-Team') {
      return !!((primaryLeadId != null && String(primaryLeadId) === uid)
        || (secondaryLeadId != null && String(secondaryLeadId) === uid))
    }
    return false
  })

  /**
   * CO-499：当前用户是否为该项目的投标负责人（仅匹配 primaryLeadId，不含辅助人员）。
   * 与 isProjectLeadMatch 的区别：排除 secondaryLeadId 匹配，用于"上传投标文件"等
   * 仅限投标负责人本人可操作的权限。投标辅助人员（secondaryLeadId 匹配）对此类字段只读。
   */
  const isPrimaryLeadMatch = computed(() => {
    if (roleGroup.value !== 'lead_assist') return false
    const currentUserId = resolveOpt(opts.currentUserId)
    if (!currentUserId) return false
    const uid = String(currentUserId)
    const primaryLeadId = resolveOpt(opts.primaryLeadId)
    return !!(primaryLeadId != null && String(primaryLeadId) === uid)
  })

  /**
   * CO-558：当前用户是否为「该项目分配的投标负责人/辅助人员」，且角色为 bid-Team（投标专员）。
   *
   * 与 isProjectLeadMatch 的关键区别：显式排除 bid-projectLeader（投标项目负责人/sales）。
   * isProjectLeadMatch 对 bid-projectLeader 有放行分支（line 98-100，当其=primaryLeadId 时返回 true），
   * 那是「投标提交」等流程的语义；而「项目文档下载」需求要求 bid-projectLeader 始终不可见，
   * 仅 bid-Team 且被分配为该项目 lead 才可见。
   */
  const isAssignedBidSpecialist = computed(() =>
    role.value === 'bid-Team' && isProjectLeadMatch.value
  )

  // ── AI 能力 ────────────────────────────────────────────────────────────────

  /** AI评分标准解析（任务看板→评分标准拆解） */
  const canAIScoreDraftDecompose = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** AI自动拆解任务（任务看板→解析招标文件 / 拆解任务） */
  const canAITenderBreakdown = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** AI智能推荐案例（投标文件区域） */
  const canAIRecommendCase = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** AI合规性检查（投标文件区域 + 右侧边栏 AI智能检查） */
  const canAIComplianceCheck = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** AI标书质量核查（投标文件区域） */
  const canAIBidDocumentQualityCheck = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  // ── 任务操作 ─────────────────────────────────────────────────────────────

  /** 创建任务 */
  const canCreateTask = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 任务看板顶部管理按钮总开关（AI评分标准解析、AI自动拆解任务、添加任务） */
  const canManageTaskBoardTopActions = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 提交任务（任务执行人将任务从待办→待审核） */
  const canSubmitTask = computed(() => roleGroup.value === 'executor')

  /** 审核任务（通过/驳回）*/
  const canReviewTask = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  // ── 文档操作 ─────────────────────────────────────────────────────────────

  /** 上传文档 */
  const canUploadDocument = computed(() => true)   // 全员可见

  /** 下载文档 */
  const canDownloadDocument = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 删除文档：CO-383 起改为纯函数 canDeleteDocumentAs（file 级 uploaderId 维度），见文件顶部导出。 */

  /** 归档文档 */
  const canArchiveDocument = computed(() => roleGroup.value === 'admin_lead')

  // ── 投标流程 ─────────────────────────────────────────────────────────────

  /**
   * 上传投标文件、选择标书审核人。
   * CO-499：仅允许投标管理员/组长 + 该项目的投标负责人（primaryLeadId 匹配）。
   * 投标辅助人员（secondaryLeadId 匹配）和项目负责人（sales）对此字段只读。
   */
  const canManageBidFiles = computed(() =>
    isAdminLead.value || isPrimaryLeadMatch.value
  )

  /** 选择标书审核人：管理员/组长 + 当前项目投标负责人/辅助人员 */
  const canSelectReviewer = computed(() =>
    isAdminLead.value || isProjectLeadMatch.value
  )

  /** 审核投标（通过/驳回）— 仅指派的审核人本人可操作，与角色无关（对齐后端 BidReviewPolicy.canApprove/canReject）
   *  CO-484 多人审核：reviewerIds（数组）优先；若未传则回退到 reviewerId（单值，向后兼容） */
  const canReviewBid = computed(() => {
    const currentUserId = resolveOpt(opts.currentUserId)
    if (!currentUserId) return false
    const uid = String(currentUserId)
    // CO-484 优先用 reviewerIds 数组判断
    const reviewerIds = resolveOpt(opts.reviewerIds)
    if (Array.isArray(reviewerIds) && reviewerIds.length > 0) {
      return reviewerIds.some((rid) => rid != null && String(rid) === uid)
    }
    // 回退到 reviewerId 单值
    const reviewerId = resolveOpt(opts.reviewerId)
    return !!(reviewerId && String(reviewerId) === uid)
  })

  /** 提交投标（投标管理员/组长 + 该项目分配的投标负责人/辅助人员） */
  const canSubmitBid = computed(() =>
    isAdminLead.value || isProjectLeadMatch.value
  )

  /** 提交标书审核。
   *  CO-484 v2：取消投标辅助人员（secondaryLead）提交审核权限，仅投标管理员/组长 + 投标负责人本人可提交。
   *  与 {@link #canSubmitBid} 分化：提交投标仍允许辅助人员，提交审核不允许。 */
  const canSubmitBidForReview = computed(() =>
    isAdminLead.value || isPrimaryLeadMatch.value
  )

  // ── 快捷操作（右侧边栏）─────────────────────────────────────────────────

  /** 添加文档（快捷操作栏）*/
  const canAddDocument = computed(() => true)

  /** 分享项目 */
  const canShareProject = computed(() => true)

  /** 导出资料 */
  const canExportProject = computed(() => true)

  /** 设置提醒 */
  const canSetReminder = computed(() => true)

  // 消费端（DraftingStage.vue）需用 reactive() 包装返回值，使 perm.xxx 自动解包 ComputedRef。
  // 不包装时模板拿到的是 ComputedRef 对象（恒 truthy），按钮可见性会失效。
  return {
    role,
    roleGroup,
    isAdminLead,
    isLeadAssist,
    isExecutor,
    isAssignedBidSpecialist,
    // AI
    canAIScoreDraftDecompose,
    canAITenderBreakdown,
    canAIRecommendCase,
    canAIComplianceCheck,
    canAIBidDocumentQualityCheck,
    // 任务
    canCreateTask,
    canSubmitTask,
    canReviewTask,
    canManageTaskBoardTopActions,
    // 文档
    canUploadDocument,
    canDownloadDocument,
    canArchiveDocument,
    // 投标
    canManageBidFiles,
    canSelectReviewer,
    canReviewBid,
    canSubmitBid,
    canSubmitBidForReview,
    // 快捷操作
    canAddDocument,
    canShareProject,
    canExportProject,
    canSetReminder,
  }
}
