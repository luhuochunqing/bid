import { getProjectStatusText, getProjectStatusType } from '@/views/Project/project-utils.js'

export function useProjectDetailCore(context) {
  const { router, route, project, isDemoMode } = context

  const canSubmit = () => project.value?.status === 'drafting' || project.value?.status === 'reviewing'
  const canRecordResult = () => project.value?.status === 'bidding'
  const getStatusType = (status) => getProjectStatusType(status)
  const getStatusText = (status) => getProjectStatusText(status)
  const getPriorityType = (priority) => ({ high: 'danger', medium: 'warning', low: 'info' }[priority] || 'info')
  const getPriorityText = (priority) => ({ high: '高', medium: '中', low: '低' }[priority] || priority)
  const getTaskStatusType = (status) => ({ todo: 'info', doing: 'warning', done: 'success' }[status] || 'info')
  const getTaskStatusText = (status) => ({ todo: '待办', doing: '进行中', done: '已完成' }[status] || status)

  const toggleAssistantPanel = () => { context.assistantPanelVisible.value = !context.assistantPanelVisible.value }
  const handleOpenCompetitionIntel = () => { context.showCompetitionIntel.value = true }
  const handleOpenRoiAnalysis = () => { context.showROIAnalysis.value = true }
  const handleOpenScoreCoverage = () => context.message.info('评分点覆盖请查看项目创建页Step 4')
  const handleOpenComplianceCheck = () => { context.showComplianceCheck.value = true }
  const handleOpenVersionControl = () => { context.showVersionControl.value = true }
  const handleOpenCollaboration = () => { context.showCollaboration.value = true }
  const handleOpenAutoTasks = () => { context.showAutoTasks.value = true }
  const handleOpenMobileCard = () => { context.showMobileCard.value = true }

  const goBack = () => router.push('/project')
  const goToExpensePage = () => router.push('/resource/expense')
  const goToResultPage = () => router.push('/resource/bid-result')
  const goToSiteDetail = () => { if (context.assetCheckResult.value?.site?.id) router.push(`/resource/bar/site/${context.assetCheckResult.value.site.id}`) }
  const borrowUK = () => {
    if (context.assetCheckResult.value?.site?.id) {
      router.push({ path: `/resource/bar/site/${context.assetCheckResult.value.site.id}`, query: { fromProjectId: String(route.params.id || ''), fromProjectName: project.value?.name || '' } })
    }
  }
  const viewSOP = () => { if (context.assetCheckResult.value?.site?.id) router.push(`/resource/bar/sop/${context.assetCheckResult.value.site.id}`) }
  const goToAssetManagement = () => router.push('/resource/bar')

  return {
    isDemoMode,
    canSubmit,
    canRecordResult,
    getStatusType,
    getStatusText,
    getPriorityType,
    getPriorityText,
    getTaskStatusType,
    getTaskStatusText,
    toggleAssistantPanel,
    handleOpenCompetitionIntel,
    handleOpenRoiAnalysis,
    handleOpenScoreCoverage,
    handleOpenComplianceCheck,
    handleOpenVersionControl,
    handleOpenCollaboration,
    handleOpenAutoTasks,
    handleOpenMobileCard,
    goBack,
    goToExpensePage,
    goToResultPage,
    goToSiteDetail,
    borrowUK,
    viewSOP,
    goToAssetManagement,
  }
}
