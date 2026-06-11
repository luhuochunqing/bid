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
 * - bid_admin  → 投标管理员（蓝图"投标管理员/组长"）
 * - bid_lead   → 投标组长（蓝图"投标管理员/组长"）
 * - sales      → 投标负责人（蓝图"投标负责人/辅助人"）
 * - task_executor → 任务执行人（蓝图"任务执行人"）
 * - auditor    → 审核人（蓝图"审核人"）
 * - 其他（admin, manager, staff 等）→ 默认可见页面，但无特殊操作权限
 */

import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 角色分组：按权限矩阵列合并
 */
function resolveDraftingRoleGroup(role) {
  if (role === 'bid_admin' || role === 'bid_lead') return 'admin_lead'
  if (role === 'sales') return 'lead_assist'       // 投标负责人 / 辅助人
  if (role === 'auditor') return 'auditor'         // 审核人
  if (role === 'task_executor') return 'executor'  // 任务执行人
  return null
}

export function useProjectDraftingPermissions(opts = {}) {
  // opts 支持传入 { primaryLeadId, secondaryLeadId, currentUserId }
  // 用于在组件中二次约束：仅该项目分配的投标负责人/辅助人员 + 管理员/组长可提交投标
  const userStore = useUserStore()

  const roleGroup = computed(() => resolveDraftingRoleGroup(userStore.userRole))
  const role = computed(() => userStore.userRole)

  const isAdminLead = computed(() => roleGroup.value === 'admin_lead')
  const isLeadAssist = computed(() => roleGroup.value === 'lead_assist')
  const isAuditor = computed(() => roleGroup.value === 'auditor')
  const isExecutor = computed(() => roleGroup.value === 'executor')

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

  /** 删除文档 */
  const canDeleteDocument = computed(() => roleGroup.value === 'admin_lead')

  /** 归档文档 */
  const canArchiveDocument = computed(() => roleGroup.value === 'admin_lead')

  // ── 投标流程 ─────────────────────────────────────────────────────────────

  /** 上传投标文件、选择标书审核人（投标管理员/组长 + 投标负责人/辅助人） */
  const canManageBidFiles = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 选择标书审核人（与上传投标文件权限一致） */
  const canSelectReviewer = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 提交投标审核（投标管理员/组长 + 投标负责人/辅助人） */
  const canSubmitBidForReview = computed(() =>
    roleGroup.value === 'admin_lead' || roleGroup.value === 'lead_assist'
  )

  /** 审核投标（通过/驳回）*/
  const canReviewBid = computed(() => roleGroup.value === 'auditor')

  /** 提交投标（投标管理员/组长 + 该项目分配的投标负责人/辅助人员） */
  const canSubmitBid = computed(() => {
    if (roleGroup.value === 'admin_lead') return true
    if (roleGroup.value !== 'lead_assist' || !opts.currentUserId) return false
    const uid = String(opts.currentUserId)
    if (opts.primaryLeadId && String(opts.primaryLeadId) === uid) return true
    if (opts.secondaryLeadId && String(opts.secondaryLeadId) === uid) return true
    return false
  })

  // ── 快捷操作（右侧边栏）─────────────────────────────────────────────────

  /** 添加文档（快捷操作栏）*/
  const canAddDocument = computed(() => true)

  /** 分享项目 */
  const canShareProject = computed(() => true)

  /** 导出资料 */
  const canExportProject = computed(() => true)

  /** 设置提醒 */
  const canSetReminder = computed(() => true)

  return {
    role,
    roleGroup,
    isAdminLead,
    isLeadAssist,
    isAuditor,
    isExecutor,
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
    // 文档
    canUploadDocument,
    canDownloadDocument,
    canDeleteDocument,
    canArchiveDocument,
    // 投标
    canManageBidFiles,
    canSelectReviewer,
    canSubmitBidForReview,
    canReviewBid,
    canSubmitBid,
    // 快捷操作
    canAddDocument,
    canShareProject,
    canExportProject,
    canSetReminder,
  }
}
