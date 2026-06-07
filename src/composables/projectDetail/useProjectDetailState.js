import { computed, ref } from 'vue'
import { getAccessToken } from '@/api/session.js'

export function useProjectDetailState(context) {
  const { route, userStore, projectStore, isDemoMode, isApiProject } = context

  const loading = ref(true)
  const approvalHistory = ref([])
  const assetCheckResult = ref(null)

  const resultDialogVisible = ref(false)
  const competitorDialogVisible = ref(false)
  const processDialogVisible = ref(false)
  const reviewerDialogVisible = ref(false)
  const scoreDraftDialogVisible = ref(false)
  const tenderBreakdownDialogVisible = ref(false)
  const tenderBreakdownParsing = ref(false)
  const approvalDialogVisible = ref(false)
  const currentTask = ref(null)
  const currentApprovalItem = ref({})
  const approvalMode = ref('submit')
  const approvalType = ref({ type: 'project_review', typeName: '立项审批' })

  const showCompetitionIntel = ref(false)
  const showComplianceCheck = ref(false)
  const showVersionControl = ref(false)
  const showCollaboration = ref(false)
  const showROIAnalysis = ref(false)
  const showAutoTasks = ref(false)
  const showMobileCard = ref(false)
  const assistantPanelVisible = ref(false)

  const noticeFileList = ref([])
  const uploadAction = ref('')
  const uploadHeaders = computed(() => {
    const token = getAccessToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  })

  const resultForm = ref({
    result: '',
    amount: null,
    contractPeriod: null,
    skuCount: '',
    noticeFile: '',
    competitors: [],
    techHighlights: '',
    priceStrategy: '',
    customerFeedback: '',
    improvements: '',
  })

  const competitorForm = ref({
    name: '',
    skuCount: '',
    category: '',
    discount: '',
    payment: '',
  })

  const activities = ref([])

  const project = computed(() => {
    if (projectStore.currentProject) return projectStore.currentProject
    return null
  })

  const dialogProjectId = computed(() => String(project.value?.id ?? route.params.id ?? ''))
  const canManageProjectTasks = computed(() => isDemoMode || isApiProject.value)
  const canManageProjectDocuments = computed(() => isDemoMode || isApiProject.value)
  const canSetProjectReminder = computed(() => isDemoMode || isApiProject.value)
  const currentApproval = computed(() => approvalHistory.value[0] || null)
  const currentUserRole = computed(() => userStore.currentUser?.role || '')
  const canApproveCurrent = computed(() => {
    const currentName = userStore.userName || userStore.currentUser?.name || ''
    const isAdmin = userStore.hasPermission('all') ||
      userStore.currentUser?.role === 'admin' ||
      String(userStore.currentUser?.role || '').toLowerCase() === 'admin'
    return currentApproval.value?.currentApproverName === currentName || isAdmin || userStore.hasPermission('task.review')
  })

  return {
    loading,
    approvalHistory,
    assetCheckResult,
    resultDialogVisible,
    competitorDialogVisible,
    processDialogVisible,
    reviewerDialogVisible,
    scoreDraftDialogVisible,
    tenderBreakdownDialogVisible,
    tenderBreakdownParsing,
    approvalDialogVisible,
    currentTask,
    currentApprovalItem,
    approvalMode,
    approvalType,
    showCompetitionIntel,
    showComplianceCheck,
    showVersionControl,
    showCollaboration,
    showROIAnalysis,
    showAutoTasks,
    showMobileCard,
    assistantPanelVisible,
    noticeFileList,
    uploadAction,
    uploadHeaders,
    resultForm,
    competitorForm,
    activities,
    project,
    dialogProjectId,
    canManageProjectTasks,
    canManageProjectDocuments,
    canSetProjectReminder,
    currentApproval,
    currentUserRole,
    canApproveCurrent,
  }
}
